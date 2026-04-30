package org.cubexmc.metro.lifecycle;

import java.util.List;
import java.util.Locale;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.integration.BlueMapIntegration;
import org.cubexmc.metro.integration.DynmapIntegration;
import org.cubexmc.metro.integration.MapIntegration;
import org.cubexmc.metro.integration.SquaremapIntegration;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Owns optional web map integrations and their refresh lifecycle.
 */
public class MapIntegrationLifecycle {

    private static final String AUTO_PROVIDER = "AUTO";
    private static final List<String> AUTO_PROVIDER_ORDER = List.of("BLUEMAP", "DYNMAP", "SQUAREMAP");

    private final Metro plugin;
    private final IntegrationFactory integrationFactory;
    private final RefreshScheduler refreshScheduler;
    private MapIntegration activeIntegration;
    private String activeProvider;
    private boolean refreshQueued;
    private Object refreshTaskId;

    public MapIntegrationLifecycle(Metro plugin) {
        this(plugin, provider -> createDefaultIntegration(plugin, provider), new SchedulerUtilRefreshScheduler());
    }

    MapIntegrationLifecycle(Metro plugin, IntegrationFactory integrationFactory, RefreshScheduler refreshScheduler) {
        this.plugin = plugin;
        this.integrationFactory = integrationFactory;
        this.refreshScheduler = refreshScheduler;
    }

    public void enable() {
        activateConfiguredProvider();
    }

    public void disable() {
        cancelQueuedRefresh();
        if (activeIntegration != null) {
            activeIntegration.disable();
        }
        activeIntegration = null;
        activeProvider = null;
    }

    public void refresh() {
        try {
            if (!activateConfiguredProvider()) {
                return;
            }
            activeIntegration.refresh();
        } catch (Throwable e) {
            plugin.getLogger().warning("[Map] Failed to refresh " + activeProvider
                    + " integration: " + e.getMessage());
        }
    }

    public void requestRefresh() {
        if (plugin.getConfigFacade() == null || !plugin.getConfigFacade().isMapIntegrationEnabled() || refreshQueued) {
            return;
        }
        refreshQueued = true;
        long delay = plugin.getConfigFacade().getMapRefreshDelayTicks();
        refreshTaskId = refreshScheduler.schedule(plugin, () -> {
            refreshQueued = false;
            refreshTaskId = null;
            refresh();
        }, delay, -1L);
    }

    private void cancelQueuedRefresh() {
        if (refreshTaskId != null) {
            refreshScheduler.cancel(refreshTaskId);
            refreshTaskId = null;
        }
        refreshQueued = false;
    }

    private boolean activateConfiguredProvider() {
        if (plugin.getConfigFacade() == null || !plugin.getConfigFacade().isMapIntegrationEnabled()) {
            disable();
            return false;
        }

        String provider = normalizeProvider(plugin.getConfigFacade().getMapProvider());
        if (provider == null || provider.isBlank()) {
            plugin.getLogger().warning("[Map] map_integration.provider is empty. Skipping map integration.");
            disable();
            return false;
        }

        if (AUTO_PROVIDER.equals(provider)) {
            return activateAutoProvider();
        }

        if (activeIntegration != null && provider.equals(activeProvider)) {
            return true;
        }

        disable();
        try {
            activeIntegration = integrationFactory.create(provider);
            if (activeIntegration == null) {
                plugin.getLogger().warning("[Map] Unknown map provider '" + provider
                        + "'. Expected AUTO, BLUEMAP, DYNMAP, or SQUAREMAP.");
                return false;
            }
            if (!activeIntegration.isAvailable()) {
                plugin.getLogger().info("[Map] " + provider + " is not available, skipping map integration.");
                activeIntegration = null;
                return false;
            }
            activeProvider = provider;
            activeIntegration.enable();
            return true;
        } catch (Throwable e) {
            plugin.getLogger().info("[Map] " + provider + " API not found, skipping map integration.");
            activeIntegration = null;
            activeProvider = null;
            return false;
        }
    }

    private boolean activateAutoProvider() {
        if (activeIntegration != null && activeProvider != null && activeIntegration.isAvailable()) {
            return true;
        }

        disable();
        for (String provider : AUTO_PROVIDER_ORDER) {
            try {
                MapIntegration integration = integrationFactory.create(provider);
                if (integration == null || !integration.isAvailable()) {
                    continue;
                }
                activeIntegration = integration;
                activeProvider = provider;
                plugin.getLogger().info("[Map] AUTO selected " + provider + " map provider.");
                activeIntegration.enable();
                return true;
            } catch (Throwable e) {
                plugin.getLogger().info("[Map] " + provider + " API not found, skipping map integration.");
            }
        }
        plugin.getLogger().warning("[Map] AUTO provider could not find BlueMap, Dynmap, or Squaremap.");
        return false;
    }

    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toUpperCase(Locale.ROOT);
    }

    private static MapIntegration createDefaultIntegration(Metro plugin, String provider) {
        return switch (provider) {
            case "BLUEMAP" -> new BlueMapIntegration(plugin);
            case "DYNMAP" -> new DynmapIntegration(plugin);
            case "SQUAREMAP" -> new SquaremapIntegration(plugin);
            default -> null;
        };
    }

    @FunctionalInterface
    interface IntegrationFactory {
        MapIntegration create(String provider);
    }

    interface RefreshScheduler {
        Object schedule(Metro plugin, Runnable task, long delay, long period);

        void cancel(Object taskId);
    }

    private static final class SchedulerUtilRefreshScheduler implements RefreshScheduler {
        @Override
        public Object schedule(Metro plugin, Runnable task, long delay, long period) {
            return SchedulerUtil.globalRun(plugin, task, delay, period);
        }

        @Override
        public void cancel(Object taskId) {
            SchedulerUtil.cancelTask(taskId);
        }
    }
}
