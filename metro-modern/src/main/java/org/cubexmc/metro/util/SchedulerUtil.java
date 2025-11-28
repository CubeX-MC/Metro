package org.cubexmc.metro.util;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 调度器工具类，用于兼容 Bukkit 和 Folia 调度器
 * 通过反射实现，不需要编译时依赖 Folia API
 */
public class SchedulerUtil {

    private static final boolean IS_FOLIA = VersionUtil.isFolia();

    // Folia 反射缓存
    private static Method globalSchedulerMethod;
    private static Method regionSchedulerMethod;
    private static Method asyncSchedulerMethod;
    private static Method entitySchedulerMethod;
    private static Method globalRunMethod;
    private static Method globalRunDelayedMethod;
    private static Method globalRunAtFixedRateMethod;
    private static Method regionRunMethod;
    private static Method regionRunDelayedMethod;
    private static Method regionRunAtFixedRateMethod;
    private static Method asyncRunNowMethod;
    private static Method asyncRunDelayedMethod;
    private static Method entityRunMethod;
    private static Method entityRunDelayedMethod;
    private static Method entityRunAtFixedRateMethod;
    private static Method scheduledTaskCancelMethod;
    private static boolean reflectionInitialized = false;

    static {
        if (IS_FOLIA) {
            initFoliaReflection();
        }
    }

    /**
     * 初始化 Folia 反射
     */
    private static void initFoliaReflection() {
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();

            // 获取调度器方法
            globalSchedulerMethod = serverClass.getMethod("getGlobalRegionScheduler");
            regionSchedulerMethod = serverClass.getMethod("getRegionScheduler");
            asyncSchedulerMethod = serverClass.getMethod("getAsyncScheduler");

            // Entity scheduler
            entitySchedulerMethod = Entity.class.getMethod("getScheduler");

            // GlobalRegionScheduler 方法
            Class<?> globalSchedulerClass = globalSchedulerMethod.getReturnType();
            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            Class<?> consumerClass = Consumer.class;

            globalRunMethod = globalSchedulerClass.getMethod("run", Plugin.class, consumerClass);
            globalRunDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, consumerClass, long.class);
            globalRunAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, consumerClass, long.class, long.class);

            // RegionScheduler 方法
            Class<?> regionSchedulerClass = regionSchedulerMethod.getReturnType();
            regionRunMethod = regionSchedulerClass.getMethod("run", Plugin.class, Location.class, consumerClass);
            regionRunDelayedMethod = regionSchedulerClass.getMethod("runDelayed", Plugin.class, Location.class, consumerClass, long.class);
            regionRunAtFixedRateMethod = regionSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Location.class, consumerClass, long.class, long.class);

            // AsyncScheduler 方法
            Class<?> asyncSchedulerClass = asyncSchedulerMethod.getReturnType();
            asyncRunNowMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, consumerClass);
            asyncRunDelayedMethod = asyncSchedulerClass.getMethod("runDelayed", Plugin.class, consumerClass, long.class, TimeUnit.class);

            // EntityScheduler 方法
            Class<?> entitySchedulerClass = entitySchedulerMethod.getReturnType();
            entityRunMethod = entitySchedulerClass.getMethod("run", Plugin.class, consumerClass, Runnable.class);
            entityRunDelayedMethod = entitySchedulerClass.getMethod("runDelayed", Plugin.class, consumerClass, Runnable.class, long.class);
            entityRunAtFixedRateMethod = entitySchedulerClass.getMethod("runAtFixedRate", Plugin.class, consumerClass, Runnable.class, long.class, long.class);

            // ScheduledTask.cancel() 方法
            scheduledTaskCancelMethod = scheduledTaskClass.getMethod("cancel");

            reflectionInitialized = true;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Metro] Failed to initialize Folia reflection: " + e.getMessage());
            reflectionInitialized = false;
        }
    }

    /**
     * 延迟执行任务（全局调度）
     *
     * @param plugin 插件实例
     * @param task   任务
     * @param delay  延迟时间，单位为tick
     * @param period 周期时间，单位为tick, 如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object globalRun(Plugin plugin, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (IS_FOLIA && reflectionInitialized) {
            return foliaGlobalRun(plugin, task, delay, period);
        } else {
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    private static Object foliaGlobalRun(Plugin plugin, Runnable task, long delay, long period) {
        try {
            Object scheduler = globalSchedulerMethod.invoke(Bukkit.getServer());
            Consumer<Object> foliaTask = scheduledTask -> task.run();

            if (period <= 0) {
                if (delay == 0) {
                    return globalRunMethod.invoke(scheduler, plugin, foliaTask);
                } else {
                    return globalRunDelayedMethod.invoke(scheduler, plugin, foliaTask, delay);
                }
            } else {
                return globalRunAtFixedRateMethod.invoke(scheduler, plugin, foliaTask, Math.max(1, delay), period);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Folia scheduler error: " + e.getMessage());
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    private static BukkitTask bukkitGlobalRun(Plugin plugin, Runnable task, long delay, long period) {
        if (period < 0) {
            if (delay == 0) {
                return Bukkit.getScheduler().runTask(plugin, task);
            } else {
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
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
            if (IS_FOLIA && reflectionInitialized && scheduledTaskCancelMethod != null) {
                // Folia ScheduledTask
                if (task.getClass().getName().contains("ScheduledTask")) {
                    scheduledTaskCancelMethod.invoke(task);
                    return;
                }
            }
            // Bukkit task
            if (task instanceof BukkitTask) {
                ((BukkitTask) task).cancel();
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }

    /**
     * 在实体所在区域执行任务
     *
     * @param plugin 插件实例
     * @param entity 实体
     * @param task   任务
     * @param delay  延迟时间，单位为tick
     * @param period 周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object entityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (IS_FOLIA && reflectionInitialized) {
            return foliaEntityRun(plugin, entity, task, delay, period);
        } else {
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    private static Object foliaEntityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        try {
            Object scheduler = entitySchedulerMethod.invoke(entity);
            Consumer<Object> foliaTask = scheduledTask -> task.run();
            Runnable retiredCallback = () -> {
                // Entity no longer exists
            };

            if (period <= 0) {
                if (delay == 0) {
                    return entityRunMethod.invoke(scheduler, plugin, foliaTask, retiredCallback);
                } else {
                    return entityRunDelayedMethod.invoke(scheduler, plugin, foliaTask, retiredCallback, delay);
                }
            } else {
                return entityRunAtFixedRateMethod.invoke(scheduler, plugin, foliaTask, retiredCallback, Math.max(1, delay), period);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity scheduler error: " + e.getMessage());
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    /**
     * 在当前上下文安全地传送实体，兼容 Folia / Paper / Bukkit。
     *
     * @param entity   需要传送的实体
     * @param location 目标位置
     * @return CompletableFuture，在非 Folia 环境返回已完成的 Future
     */
    public static CompletableFuture<Boolean> teleportEntity(Entity entity, Location location) {
        if (location == null || entity == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        if (IS_FOLIA) {
            try {
                // Folia/Paper 1.19+ 支持 teleportAsync
                Method teleportAsyncMethod = Entity.class.getMethod("teleportAsync", Location.class);
                @SuppressWarnings("unchecked")
                CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) teleportAsyncMethod.invoke(entity, location);
                return future;
            } catch (Exception e) {
                // 回退到同步传送
            }
        }
        boolean success = entity.teleport(location);
        return CompletableFuture.completedFuture(success);
    }

    /**
     * 在指定位置区域延迟执行任务
     *
     * @param plugin   插件实例
     * @param location 位置
     * @param task     任务
     * @param delay    延迟时间，单位为tick
     * @param period   周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object regionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (IS_FOLIA && reflectionInitialized) {
            return foliaRegionRun(plugin, location, task, delay, period);
        } else {
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    private static Object foliaRegionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        try {
            Object scheduler = regionSchedulerMethod.invoke(Bukkit.getServer());
            Consumer<Object> foliaTask = scheduledTask -> task.run();

            if (period <= 0) {
                if (delay == 0) {
                    return regionRunMethod.invoke(scheduler, plugin, location, foliaTask);
                } else {
                    return regionRunDelayedMethod.invoke(scheduler, plugin, location, foliaTask, delay);
                }
            } else {
                return regionRunAtFixedRateMethod.invoke(scheduler, plugin, location, foliaTask, Math.max(1, delay), period);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Folia region scheduler error: " + e.getMessage());
            return bukkitGlobalRun(plugin, task, delay, period);
        }
    }

    /**
     * 在异步线程延迟执行任务
     *
     * @param plugin 插件实例
     * @param task   任务
     * @param delay  延迟时间，单位为tick
     */
    public static void asyncRun(Plugin plugin, Runnable task, long delay) {
        delay = Math.max(0, delay);
        if (IS_FOLIA && reflectionInitialized) {
            foliaAsyncRun(plugin, task, delay);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    private static void foliaAsyncRun(Plugin plugin, Runnable task, long delay) {
        try {
            Object scheduler = asyncSchedulerMethod.invoke(Bukkit.getServer());
            Consumer<Object> foliaTask = scheduledTask -> task.run();

            if (delay <= 0) {
                asyncRunNowMethod.invoke(scheduler, plugin, foliaTask);
            } else {
                asyncRunDelayedMethod.invoke(scheduler, plugin, foliaTask, delay * 50L, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Folia async scheduler error: " + e.getMessage());
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }
}
