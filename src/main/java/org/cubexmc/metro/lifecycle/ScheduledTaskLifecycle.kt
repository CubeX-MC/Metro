package org.cubexmc.metro.lifecycle

import org.bukkit.Bukkit
import org.bukkit.entity.Minecart
import org.cubexmc.core.hasFlag
import org.cubexmc.core.setFlag
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.SchedulerUtil
import org.cubexmc.metro.util.VersionUtil

/**
 * Owns startup scheduled tasks that are not tied to a single listener.
 */
class ScheduledTaskLifecycle {
    private val plugin: Metro
    private val lineManager: LineManager?
    private val stopManager: StopManager?
    private val portalManager: PortalManager?
    private val scheduler: TaskScheduler
    private val folia: Boolean
    private var autoSaveTaskId: Any? = null
    private var legacyMinecartMigrationTaskId: Any? = null

    constructor(plugin: Metro, lineManager: LineManager?, stopManager: StopManager?) : this(
        plugin,
        lineManager,
        stopManager,
        null,
    )

    constructor(
        plugin: Metro,
        lineManager: LineManager?,
        stopManager: StopManager?,
        portalManager: PortalManager?,
    ) : this(
        plugin,
        lineManager,
        stopManager,
        portalManager,
        SchedulerUtilTaskScheduler(),
        VersionUtil.isFolia(),
    )

    constructor(
        plugin: Metro,
        lineManager: LineManager?,
        stopManager: StopManager?,
        portalManager: PortalManager?,
        scheduler: TaskScheduler,
        folia: Boolean,
    ) {
        this.plugin = plugin
        this.lineManager = lineManager
        this.stopManager = stopManager
        this.portalManager = portalManager
        this.scheduler = scheduler
        this.folia = folia
    }

    fun start() {
        autoSaveTaskId = scheduler.schedule(plugin, Runnable { processAsyncSaves() }, 1200L, 1200L)
        if (folia) {
            plugin.logger.info("Skipped legacy Metro minecart tag migration on Folia; full-world entity scans are not region-owned.")
            return
        }
        legacyMinecartMigrationTaskId = scheduler.schedule(plugin, Runnable { migrateLegacyMinecartTags() }, 100L, -1L)
    }

    fun shutdown() {
        val autoSave = autoSaveTaskId
        if (autoSave != null) {
            scheduler.cancel(autoSave)
            autoSaveTaskId = null
        }
        val migration = legacyMinecartMigrationTaskId
        if (migration != null) {
            scheduler.cancel(migration)
            legacyMinecartMigrationTaskId = null
        }
    }

    private fun processAsyncSaves() {
        lineManager?.processAsyncSave()
        stopManager?.processAsyncSave()
        portalManager?.processAsyncSave()
    }

    private fun migrateLegacyMinecartTags() {
        val minecartKey = MetroConstants.getMinecartKey() ?: return
        for (world in Bukkit.getWorlds()) {
            for (entity in world.getEntitiesByClass(Minecart::class.java)) {
                if (
                    MetroConstants.METRO_MINECART_NAME == entity.customName &&
                    !entity.persistentDataContainer.hasFlag(minecartKey)
                ) {
                    entity.persistentDataContainer.setFlag(minecartKey)
                    plugin.logger.info("Migrated legacy Metro Minecart to PDC data: " + entity.uniqueId)
                }
            }
        }
    }

    interface TaskScheduler {
        fun schedule(plugin: Metro, task: Runnable, delay: Long, period: Long): Any?

        fun cancel(taskId: Any)
    }

    private class SchedulerUtilTaskScheduler : TaskScheduler {
        override fun schedule(plugin: Metro, task: Runnable, delay: Long, period: Long): Any? =
            SchedulerUtil.globalRun(plugin, task, delay, period)

        override fun cancel(taskId: Any) {
            SchedulerUtil.cancelTask(taskId)
        }
    }
}
