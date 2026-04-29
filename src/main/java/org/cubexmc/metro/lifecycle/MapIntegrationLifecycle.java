package org.cubexmc.metro.lifecycle;

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

    private final Metro plugin;
    private MapIntegration activeIntegration;
    private String activeProvider;
    private boolean refreshQueued;

    public MapIntegrationLifecycle(Metro plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        activateConfiguredProvider();
    }

    public void disable() {
        if (activeIntegration != null) {
            activeIntegration.disable();
        }
        activeIntegration = null;
        activeProvider = null;
        refreshQueued = false;
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
        SchedulerUtil.globalRun(plugin, () -> {
            refreshQueued = false;
            refresh();
        }, delay, -1L);
    }

    private boolean activateConfiguredProvider() {
        if (plugin.getConfigFacade() == null || !plugin.getConfigFacade().isMapIntegrationEnabled()) {
            disable();
            return false;
        }

        String provider = plugin.getConfigFacade().getMapProvider();
        if (provider == null || provider.isBlank()) {
            plugin.getLogger().warning("[Map] map_integration.provider is empty. Skipping map integration.");
            disable();
            return false;
        }
        provider = provider.toUpperCase();

        if (activeIntegration != null && provider.equals(activeProvider)) {
            return true;
        }

        disable();
        try {
            activeIntegration = createIntegration(provider);
            if (activeIntegration == null) {
                plugin.getLogger().warning("[Map] Unknown map provider '" + provider
                        + "'. Expected BLUEMAP, DYNMAP, or SQUAREMAP.");
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

    private MapIntegration createIntegration(String provider) {
        return switch (provider) {
            case "BLUEMAP" -> new BlueMapIntegration(plugin);
            case "DYNMAP" -> new DynmapIntegration(plugin);
            case "SQUAREMAP" -> new SquaremapIntegration(plugin);
            default -> null;
        };
    }
}
