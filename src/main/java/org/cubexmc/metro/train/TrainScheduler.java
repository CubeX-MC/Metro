package org.cubexmc.metro.train;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Tracks scheduled work owned by one train session so cancellation is centralized.
 */
public class TrainScheduler {

    private final Metro plugin;
    private final Set<Object> tasks = ConcurrentHashMap.newKeySet();

    public TrainScheduler(Metro plugin) {
        this.plugin = plugin;
    }

    public Object entityRun(Entity entity, Runnable task, long delay, long period) {
        Object taskId = SchedulerUtil.entityRun(plugin, entity, task, delay, period);
        if (taskId != null) {
            tasks.add(taskId);
        }
        return taskId;
    }

    public void cancel(Object taskId) {
        if (taskId == null) {
            return;
        }
        tasks.remove(taskId);
        SchedulerUtil.cancelTask(taskId);
    }

    public void cancelAll() {
        for (Object taskId : tasks) {
            SchedulerUtil.cancelTask(taskId);
        }
        tasks.clear();
    }
}
