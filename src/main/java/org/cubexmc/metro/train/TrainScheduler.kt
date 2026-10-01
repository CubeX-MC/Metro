package org.cubexmc.metro.train

import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Entity
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.SchedulerUtil

/**
 * Tracks scheduled work owned by one train session so cancellation is centralized.
 */
open class TrainScheduler(private val plugin: Metro) {
    private val tasks: MutableSet<Any> = ConcurrentHashMap.newKeySet()

    open fun entityRun(entity: Entity, task: Runnable, delay: Long, period: Long): Any? {
        val taskId = if (period <= 0L) {
            plugin.taskScheduler.runAtEntityLater(entity, task, delay)
        } else {
            SchedulerUtil.entityRun(plugin, entity, task, delay, period)
        }
        if (taskId != null) {
            tasks.add(taskId)
        }
        return taskId
    }

    open fun cancel(taskId: Any?) {
        if (taskId == null) {
            return
        }
        tasks.remove(taskId)
        SchedulerUtil.cancelTask(taskId)
    }

    fun cancelAll() {
        for (taskId in tasks) {
            SchedulerUtil.cancelTask(taskId)
        }
        tasks.clear()
    }
}
