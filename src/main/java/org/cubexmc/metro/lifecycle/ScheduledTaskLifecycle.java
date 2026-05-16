package org.cubexmc.metro.lifecycle;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Owns startup scheduled tasks that are not tied to a single listener.
 */
public class ScheduledTaskLifecycle {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;
    private Object autoSaveTaskId;

    public ScheduledTaskLifecycle(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    public void start() {
        this.autoSaveTaskId = SchedulerUtil.globalRun(plugin, this::processAsyncSaves, 1200L, 1200L);
        SchedulerUtil.globalRun(plugin, this::migrateLegacyMinecartTags, 100L, -1L);
    }

    public void shutdown() {
        if (autoSaveTaskId != null) {
            SchedulerUtil.cancelTask(autoSaveTaskId);
            autoSaveTaskId = null;
        }
    }

    private void processAsyncSaves() {
        if (lineManager != null) {
            lineManager.processAsyncSave();
        }
        if (stopManager != null) {
            stopManager.processAsyncSave();
        }
    }

    private void migrateLegacyMinecartTags() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Minecart.class)) {
                if (MetroConstants.METRO_MINECART_NAME.equals(entity.getCustomName())
                        && !entity.getPersistentDataContainer().has(
                        MetroConstants.getMinecartKey(), PersistentDataType.BYTE)) {
                    entity.getPersistentDataContainer().set(
                            MetroConstants.getMinecartKey(), PersistentDataType.BYTE, (byte) 1);
                    plugin.getLogger().info("Migrated legacy Metro Minecart to PDC data: " + entity.getUniqueId());
                }
            }
        }
    }
}
