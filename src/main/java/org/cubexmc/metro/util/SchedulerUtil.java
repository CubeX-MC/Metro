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

    private static final boolean IS_FOLIA;

    static {
        boolean foliaDetected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException e) {
            // Folia not present
        }
        IS_FOLIA = foliaDetected;
    }

    /**
     * 判断服务器是否运行在Folia上
     *
     * @return 是否为Folia服务器
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    // --- Private Helper Methods for Task Execution ---

    private static Object executeTask(Plugin plugin, Runnable task, long delay, long period,
                                      FoliaScheduler foliaScheduler, BukkitScheduler bukkitScheduler) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Consumer<ScheduledTask> foliaTask = st -> task.run();
            return foliaScheduler.schedule(plugin, foliaTask, delay, period);
        } else {
            return bukkitScheduler.schedule(plugin, task, delay, period);
        }
    }

    @FunctionalInterface
    private interface FoliaScheduler {
        ScheduledTask schedule(Plugin plugin, Consumer<ScheduledTask> task, long delay, long period);
    }

    @FunctionalInterface
    private interface BukkitScheduler {
        BukkitTask schedule(Plugin plugin, Runnable task, long delay, long period);
    }

    // --- Public Scheduler Methods ---

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
        return executeTask(plugin, task, delay, period,
                (p, t, d, r) -> {
                    GlobalRegionScheduler scheduler = Bukkit.getServer().getGlobalRegionScheduler();
                    if (r < 0) return (d == 0) ? scheduler.run(p, t) : scheduler.runDelayed(p, t, d);
                    return scheduler.runAtFixedRate(p, t, d, r);
                },
                (p, t, d, r) -> {
                    if (r < 0) return (d == 0) ? Bukkit.getScheduler().runTask(p, t) : Bukkit.getScheduler().runTaskLater(p, t, d);
                    return Bukkit.getScheduler().runTaskTimer(p, t, d, r);
                }
        );
    }

    /**
     * 在玩家所在区域执行任务
     *
     * @param plugin 插件实例
     * @param entity 实体
     * @param task   任务
     * @param delay  延迟时间，单位为tick
     * @param period 周期时间，单位为tick，如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object entityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        // Entity retired callback - 当实体不存在时的回调
        Runnable retiredCallback = () -> plugin.getLogger().fine("Entity scheduler task cancelled: entity no longer exists");

        return executeTask(plugin, task, delay, period,
                (p, t, d, r) -> {
                    EntityScheduler scheduler = entity.getScheduler();
                    if (r < 0) return (d == 0) ? scheduler.run(p, t, retiredCallback) : scheduler.runDelayed(p, t, retiredCallback, d);
                    return scheduler.runAtFixedRate(p, t, retiredCallback, d, r);
                },
                (p, t, d, r) -> { // Bukkit doesn't have a retired callback for entity tasks in the same way
                    if (r < 0) return (d == 0) ? Bukkit.getScheduler().runTask(p, t) : Bukkit.getScheduler().runTaskLater(p, t, d);
                    return Bukkit.getScheduler().runTaskTimer(p, t, d, r);
                }
        );
    }

    /**
     * 在指定位置区域延迟执行任务
     *
     * @param plugin   插件实例
     * @param location 位置
     * @param task     任务
     * @param delay    延迟时间，单位为tick
     * @param period   周期时间，单位为tick, 如果为负数则表示只延迟一次
     * @return 任务ID
     */
    public static Object regionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        return executeTask(plugin, task, delay, period,
                (p, t, d, r) -> {
                    RegionScheduler scheduler = Bukkit.getServer().getRegionScheduler();
                    if (r < 0) return (d == 0) ? scheduler.run(p, location, t) : scheduler.runDelayed(p, location, t, d);
                    return scheduler.runAtFixedRate(p, location, t, d, r);
                },
                (p, t, d, r) -> {
                    if (r < 0) return (d == 0) ? Bukkit.getScheduler().runTask(p, t) : Bukkit.getScheduler().runTaskLater(p, t, d);
                    return Bukkit.getScheduler().runTaskTimer(p, t, d, r);
                }
        );
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
                if (task instanceof ScheduledTask) {
                    ((ScheduledTask) task).cancel();
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
     * 在异步线程延迟执行任务
     *
     * @param plugin 插件实例
     * @param task   任务
     * @param delayMs 延迟时间，单位为毫秒 (Folia) or Ticks (Bukkit)
     *                Note: Bukkit's runTaskLaterAsynchronously uses ticks for delay.
     *                Folia's asyncScheduler uses milliseconds.
     *                This method standardizes on Ticks for the delay parameter for Bukkit compatibility,
     *                and converts to MS for Folia. If you need MS precision for Bukkit, schedule with 0 delay
     *                and handle delay within your runnable.
     */
    public static void asyncRun(Plugin plugin, Runnable task, long delayTicks) {
        delayTicks = Math.max(0, delayTicks);
        if (isFolia()) {
            AsyncScheduler asyncScheduler = Bukkit.getServer().getAsyncScheduler();
            Consumer<ScheduledTask> foliaTask = st -> task.run();
            if (delayTicks <= 0) {
                asyncScheduler.runNow(plugin, foliaTask);
            } else {
                // Convert ticks to milliseconds for Folia (1 tick = 50 ms)
                asyncScheduler.runDelayed(plugin, foliaTask, delayTicks * 50, TimeUnit.MILLISECONDS);
            }
        } else {
            // Bukkit's runTaskLaterAsynchronously uses ticks for delay
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }
}