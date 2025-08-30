package org.cubexmc.metro.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 调度器工具类，用于Bukkit调度器
 */
public class SchedulerUtil {
    /**
     * 延迟执行任务（全局调度）
     *
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick, 如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static BukkitTask globalRun(Plugin plugin, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (period < 0) {
            // 只执行一次的任务
            if (delay == 0)
                return Bukkit.getScheduler().runTask(plugin, task);
            else
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        } else {
            // 重复执行的任务
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    /**
     * 取消任务
     *
     * @param task 任务ID
     */
    public static void cancelTask(BukkitTask task) {
        if (task == null) return;
        try {
            task.cancel();
        } catch (Exception e) {
            // 忽略异常
        }
    }

    /**
     * 在玩家所在区域执行任务
     *
     * @param plugin 插件实例
     * @param entity 实体
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static BukkitTask entityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        // Bukkit aPI does not have entity based scheduler, so we just run it as a global task.
        // For 1.18, this should be fine.
        return globalRun(plugin, task, delay, period);
    }

    /**
     * 在指定位置区域延迟执行任务
     *
     * @param plugin 插件实例
     * @param location 位置
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static BukkitTask regionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        // Bukkit aPI does not have region based scheduler, so we just run it as a global task.
        // For 1.18, this should be fine.
        return globalRun(plugin, task, delay, period);
    }

    /**
     * 在异步线程延迟执行任务
     *
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     */
    public static void asyncRun(Plugin plugin, Runnable task, long delay) {
        delay = Math.max(0, delay);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
    }
} 