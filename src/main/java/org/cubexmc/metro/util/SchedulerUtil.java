package org.cubexmc.metro.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 调度器工具类，用于兼容Bukkit和Folia调度器
 */
public class SchedulerUtil {

    private static final boolean IS_FOLIA = checkFolia();
    
    /**
     * 检查服务器是否运行在Folia上
     * 
     * @return 是否为Folia服务器
     */
    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 判断服务器是否运行在Folia上
     * 
     * @return 是否为Folia服务器
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
    
    /**
     * 延迟执行任务（全局调度）
     * 
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @return 任务ID
     */
    @SuppressWarnings("unchecked")
    public static Object runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object globalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler").invoke(null);
                return globalRegionScheduler.getClass().getMethod("runDelayed", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class)
                        .invoke(globalRegionScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), delay);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
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
    public static Object runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object globalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler").invoke(null);
                return globalRegionScheduler.getClass().getMethod("runAtFixedRate", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class, long.class)
                        .invoke(globalRegionScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), delay, period);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
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
            if (IS_FOLIA) {
                try {
                    Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                    if (scheduledTaskClass.isInstance(task)) {
                        scheduledTaskClass.getMethod("cancel").invoke(task);
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            } else {
                if (task instanceof BukkitTask) {
                    ((BukkitTask) task).cancel();
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    /**
     * 在玩家所在区域执行任务
     * 
     * @param plugin 插件实例
     * @param player 玩家
     * @param task 任务
     */
    @SuppressWarnings("unchecked")
    public static void runTaskForPlayer(Plugin plugin, Player player, Consumer<Player> task) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                scheduler.getClass().getMethod("run", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), Object.class)
                        .invoke(scheduler, plugin, (Consumer<Object>) scheduledTask -> task.accept(player), null);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                Bukkit.getScheduler().runTask(plugin, () -> task.accept(player));
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> task.accept(player));
        }
    }
    
    /**
     * 在实体所在区域执行任务
     * 
     * @param plugin 插件实例
     * @param entity 实体
     * @param task 任务
     */
    @SuppressWarnings("unchecked")
    public static void runTaskForEntity(Plugin plugin, Entity entity, Consumer<Entity> task) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                scheduler.getClass().getMethod("run", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), Object.class)
                        .invoke(scheduler, plugin, (Consumer<Object>) scheduledTask -> task.accept(entity), null);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                Bukkit.getScheduler().runTask(plugin, () -> task.accept(entity));
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> task.accept(entity));
        }
    }
    
    /**
     * 在指定位置区域执行任务
     * 
     * @param plugin 插件实例
     * @param location 位置
     * @param task 任务
     */
    public static void runTaskAtLocation(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object regionScheduler = bukkitClass.getMethod("getRegionScheduler").invoke(null);
                regionScheduler.getClass().getMethod("execute", 
                        Plugin.class, Location.class, Runnable.class)
                        .invoke(regionScheduler, plugin, location, task);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
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
    @SuppressWarnings("unchecked")
    public static Object runTaskLaterAtLocation(Plugin plugin, Location location, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object regionScheduler = bukkitClass.getMethod("getRegionScheduler").invoke(null);
                return regionScheduler.getClass().getMethod("runDelayed", 
                        Plugin.class, Location.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class)
                        .invoke(regionScheduler, plugin, location, (Consumer<Object>) scheduledTask -> task.run(), delay);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
    
    /**
     * 在指定位置区域重复执行任务
     * 
     * @param plugin 插件实例
     * @param location 位置
     * @param task 任务
     * @param delay 延迟时间，单位为tick
     * @param period 周期时间，单位为tick
     * @return 任务ID
     */
    @SuppressWarnings("unchecked")
    public static Object runTaskTimerAtLocation(Plugin plugin, Location location, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object regionScheduler = bukkitClass.getMethod("getRegionScheduler").invoke(null);
                return regionScheduler.getClass().getMethod("runAtFixedRate", 
                        Plugin.class, Location.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class, long.class)
                        .invoke(regionScheduler, plugin, location, (Consumer<Object>) scheduledTask -> task.run(), delay, period);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }
    
    /**
     * 在异步线程执行任务
     * 
     * @param plugin 插件实例
     * @param task 任务
     */
    @SuppressWarnings("unchecked")
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass().getMethod("runNow", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"))
                        .invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * 在异步线程延迟执行任务
     * 
     * @param plugin 插件实例
     * @param task 任务
     * @param delay 延迟时间，单位为毫秒
     */
    @SuppressWarnings("unchecked")
    public static void runTaskLaterAsync(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                // 使用反射调用Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass().getMethod("runDelayed", 
                        Plugin.class, Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask$Consumer"), long.class, TimeUnit.class)
                        .invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), delay * 50, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // 出现异常时回退到Bukkit调度器
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }
} 