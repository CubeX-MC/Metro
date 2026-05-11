package org.cubexmc.metro.lifecycle;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.VersionUtil;

/**
 * Owns startup scheduled tasks that are not tied to a single listener.
 */
public class ScheduledTaskLifecycle {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;
    private final PortalManager portalManager;
    private final TaskScheduler scheduler;
    private final boolean folia;
    private Object autoSaveTaskId;
    private Object legacyMinecartMigrationTaskId;

    public ScheduledTaskLifecycle(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this(plugin, lineManager, stopManager, null);
    }

    public ScheduledTaskLifecycle(Metro plugin, LineManager lineManager, StopManager stopManager,
            PortalManager portalManager) {
        this(plugin, lineManager, stopManager, portalManager, new SchedulerUtilTaskScheduler(),
                VersionUtil.isFolia());
    }

    ScheduledTaskLifecycle(Metro plugin, LineManager lineManager, StopManager stopManager,
            PortalManager portalManager, TaskScheduler scheduler, boolean folia) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
        this.portalManager = portalManager;
        this.scheduler = scheduler;
        this.folia = folia;
    }

    public void start() {
        this.autoSaveTaskId = scheduler.schedule(plugin, this::processAsyncSaves, 1200L, 1200L);
        if (folia) {
            plugin.getLogger().info("Skipped legacy Metro minecart tag migration on Folia; full-world entity scans are not region-owned.");
            return;
        }
        this.legacyMinecartMigrationTaskId = scheduler.schedule(plugin, this::migrateLegacyMinecartTags, 100L, -1L);
    }

    public void shutdown() {
        if (autoSaveTaskId != null) {
            scheduler.cancel(autoSaveTaskId);
            autoSaveTaskId = null;
        }
        if (legacyMinecartMigrationTaskId != null) {
            scheduler.cancel(legacyMinecartMigrationTaskId);
            legacyMinecartMigrationTaskId = null;
        }
    }

    private void processAsyncSaves() {
        if (lineManager != null) {
            lineManager.processAsyncSave();
        }
        if (stopManager != null) {
            stopManager.processAsyncSave();
        }
        if (portalManager != null) {
            portalManager.processAsyncSave();
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

    interface TaskScheduler {
        Object schedule(Metro plugin, Runnable task, long delay, long period);

        void cancel(Object taskId);
    }

    private static final class SchedulerUtilTaskScheduler implements TaskScheduler {
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
