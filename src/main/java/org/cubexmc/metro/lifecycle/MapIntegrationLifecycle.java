package org.cubexmc.metro.lifecycle;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.integration.BlueMapIntegration;
import org.cubexmc.metro.integration.DynmapIntegration;
import org.cubexmc.metro.integration.SquaremapIntegration;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Owns optional web map integrations and their refresh lifecycle.
 */
public class MapIntegrationLifecycle {

    private final Metro plugin;
    private BlueMapIntegration blueMapIntegration;
    private DynmapIntegration dynmapIntegration;
    private SquaremapIntegration squaremapIntegration;
    private boolean refreshQueued;

    public MapIntegrationLifecycle(Metro plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        try {
            this.blueMapIntegration = new BlueMapIntegration(plugin);
            this.blueMapIntegration.enable();
        } catch (Throwable e) {
            plugin.getLogger().info("BlueMap API not found, skipping BlueMap integration.");
        }

        try {
            this.dynmapIntegration = new DynmapIntegration(plugin);
            this.dynmapIntegration.enable();
        } catch (Throwable e) {
            plugin.getLogger().info("Dynmap API not found, skipping Dynmap integration.");
        }

        try {
            this.squaremapIntegration = new SquaremapIntegration(plugin);
            this.squaremapIntegration.enable();
        } catch (Throwable e) {
            plugin.getLogger().info("Squaremap API not found, skipping Squaremap integration.");
        }
    }

    public void disable() {
        if (blueMapIntegration != null) {
            blueMapIntegration.disable();
        }
        if (dynmapIntegration != null) {
            dynmapIntegration.disable();
        }
        if (squaremapIntegration != null) {
            squaremapIntegration.disable();
        }
        refreshQueued = false;
    }

    public void refresh() {
        if (blueMapIntegration != null) {
            blueMapIntegration.refresh();
        }
        if (dynmapIntegration != null) {
            dynmapIntegration.refresh();
        }
        if (squaremapIntegration != null) {
            squaremapIntegration.refresh();
        }
    }

    public void requestRefresh() {
        if (plugin.getConfigFacade() == null || !plugin.getConfigFacade().isMapIntegrationEnabled() || refreshQueued) {
            return;
        }
        refreshQueued = true;
        SchedulerUtil.globalRun(plugin, () -> {
            refreshQueued = false;
            refresh();
        }, 1L, -1L);
    }
}
