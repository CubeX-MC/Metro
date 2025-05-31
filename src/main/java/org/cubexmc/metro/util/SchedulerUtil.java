package org.cubexmc.metro.util;

import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
            if (delay == 0)
                return globbalScheduler.run(plugin, foliaTask);
            else if (period < 0)
                return globbalScheduler.runDelayed(plugin, foliaTask, delay);
            else
                return globbalScheduler.runAtFixedRate(plugin, foliaTask, delay, period);
        } else {
            if (delay == 0)
                return Bukkit.getScheduler().runTask(plugin, task);
            else if (period < 0)
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            else
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }
    
    /**
     * 重复执行任务（全局调度）
     * 
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick
     * @return 任务ID
     */
    @SuppressWarnings("unchecked")
//    public static Object runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
//        if (isFolia()) {
//            try {
//                // 使用反射调用Folia API
//                Class<?> bukkitClass = Bukkit.class;
//                Object globalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler").invoke(null);
//                return globalRegionScheduler.getClass().getMethod("runAtFixedRate",
//                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class, long.class)
//                        .invoke(globalRegionScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), delay, period);
//            } catch (Exception e) {
//                // 出现异常时回退到Bukkit调度器
//                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
//            }
//        } else {
//            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
//        }
//    }
    
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
            if (task instanceof Runnable) {
                foliaTask = scheduledTask -> ((Runnable) task).run();
            } else if (task instanceof Consumer) {
                foliaTask = (Consumer<ScheduledTask>) task;
            } else {
                throw new IllegalArgumentException("Task must be either Runnable or Consumer<ScheduledTask>");
            }
            
            // Entity retired callback - 当实体不存在时的回调
            Runnable retiredCallback = () -> {
                plugin.getLogger().fine("Entity scheduler task cancelled: entity no longer exists");
            };
            
            if (delay == 0) {
                return entityScheduler.run(plugin, foliaTask, retiredCallback);
            } else if (period < 0) {
                return entityScheduler.runDelayed(plugin, foliaTask, retiredCallback, delay);
            } else {
                return entityScheduler.runAtFixedRate(plugin, foliaTask, retiredCallback, delay, period);
            }
        } else {
            if (delay == 0) {
                return Bukkit.getScheduler().runTask(plugin, (Runnable) task);
            } else if (period < 0) {
                return Bukkit.getScheduler().runTaskLater(plugin, (Runnable) task, delay);
            } else {
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

            if (delay == 0) {
                return regionScheduler.run(plugin, location, foliaTask);
            } else if (period < 0) {
                return regionScheduler.runDelayed(plugin, location, foliaTask, delay);
            } else {
                return regionScheduler.runAtFixedRate(plugin, location, foliaTask, delay, period);
            }
        } else {
            if (delay == 0) {
                return Bukkit.getScheduler().runTask(plugin, task);
            } else if (period < 0) {
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            } else {
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