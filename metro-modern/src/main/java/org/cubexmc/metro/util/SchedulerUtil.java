package org.cubexmc.metro.util;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * 调度器工具类，用于兼容Bukkit和Folia调度器
 */
public class SchedulerUtil {
    
    /**
     * 判断服务器是否运行在Folia上
     * 
     * @return 是否为Folia服务器
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
      /**
     * 延迟执行任务（全局调度）
     * 
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick, 如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object globalRun(Plugin plugin, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            GlobalRegionScheduler globbalScheduler = server.getGlobalRegionScheduler();
            // Convert Runnable to Consumer<ScheduledTask> for Folia API
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();
            if (period <= 0) {
                // 只执行一次的任务
                if (delay == 0)
                    return globbalScheduler.run(plugin, foliaTask);
                else
                    return globbalScheduler.runDelayed(plugin, foliaTask, delay);
            } else {
                // 立即执行一次，然后重复执行的任务
                return globbalScheduler.runAtFixedRate(plugin, foliaTask, Math.max(1, delay), period);
            }
        } else {
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
    }
    
    /**
     * 取消任务
     * 
     * @param task 任务ID
     */
    public static void cancelTask(Object task) {
        if (task == null) return;
        try {
            if (isFolia()) {
                if (task instanceof ScheduledTask)
                    ((ScheduledTask) task).cancel();
            } else {
                if (task instanceof BukkitTask)
                    ((BukkitTask) task).cancel();
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }    /**
     * 在玩家所在区域执行任务
     * 
     * @param plugin 插件实例
     * @param entity 实体
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    @SuppressWarnings("unchecked")
    public static Object entityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            EntityScheduler entityScheduler = entity.getScheduler();
            // Convert task to Consumer<ScheduledTask> for Folia API
            Consumer<ScheduledTask> foliaTask;
            if (task instanceof Runnable)
                foliaTask = scheduledTask -> ((Runnable) task).run();
            else if (task instanceof Consumer)
                foliaTask = (Consumer<ScheduledTask>) task;
            else
                throw new IllegalArgumentException("Task must be either Runnable or Consumer<ScheduledTask>");
            
            // Entity retired callback - 当实体不存在时的回调
            Runnable retiredCallback = () -> {
                plugin.getLogger().fine("Entity scheduler task cancelled: entity no longer exists");
            };
            
            if (period <= 0) {
                // 只执行一次的任务
                if (delay == 0)
                    return entityScheduler.run(plugin, foliaTask, retiredCallback);
                else
                    return entityScheduler.runDelayed(plugin, foliaTask, retiredCallback, delay);
            } else {
                // 重复执行的任务
                return entityScheduler.runAtFixedRate(plugin, foliaTask, retiredCallback, Math.max(1, delay), period);
            }
        } else {
            if (period <= 0) {
                // 只执行一次的任务
                if (delay == 0)
                    return Bukkit.getScheduler().runTask(plugin, (Runnable) task);
                else
                    return Bukkit.getScheduler().runTaskLater(plugin, (Runnable) task, delay);
            } else {
                // 重复执行的任务
                return Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) task, delay, period);
            }
        }
    }
      /**
     * 在指定位置区域延迟执行任务
     * 
     * @param plugin 插件实例
     * @param location 位置
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @return 任务ID
     */
    public static Object regionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            RegionScheduler regionScheduler = server.getRegionScheduler();
            // Convert Runnable to Consumer<ScheduledTask> for Folia API
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();

            if (period <= 0) {
                // 只执行一次的任务
                if (delay == 0)
                    return regionScheduler.run(plugin, location, foliaTask);
                else
                    return regionScheduler.runDelayed(plugin, location, foliaTask, delay);
            } else {
                // 重复执行的任务
                return regionScheduler.runAtFixedRate(plugin, location, foliaTask, Math.max(1, delay), period);
            }
        } else {
            if (period <= 0) {
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
    }
      /**
     * 在异步线程延迟执行任务
     * 
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为毫秒
     */
    public static void asyncRun(Plugin plugin, Runnable task, long delay) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            AsyncScheduler asyncScheduler = server.getAsyncScheduler();
            // Convert Runnable to Consumer<ScheduledTask> for Folia API
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();
            if (delay <= 0) {
                asyncScheduler.runNow(plugin, foliaTask);
            } else {
                asyncScheduler.runDelayed(plugin, foliaTask, delay * 50, TimeUnit.MILLISECONDS);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }
} 