package org.cubexmc.metro.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SchedulerUtil;
import org.bukkit.scheduler.BukkitTask;

/**
 * 处理矿车相关事件
 */
public class VehicleListener implements Listener {
    
    private final Metro plugin;
    private final Map<UUID, BukkitTask> trainMovementTasks = new HashMap<>(); // 记录乘客移动任务
    
    public VehicleListener(Metro plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 监听玩家进入矿车事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleEnter(VehicleEnterEvent event) {
        Vehicle vehicle = event.getVehicle();
        Entity passenger = event.getEntered();
        
        // 只处理玩家进入地铁矿车的情况
        if (!(vehicle instanceof Minecart) || !(passenger instanceof Player)) {
            return;
        }
        
        Player player = (Player) passenger;
        Minecart minecart = (Minecart) vehicle;
        
        // 检查是否是Metro的矿车
        if (!"MetroMinecart".equals(minecart.getCustomName())) {
            return;
        }
        
        // 获取当前位置所在的停靠区
        Location location = minecart.getLocation();
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(location);
        
        if (stop != null) {
            // 找出当前停靠区所在的线路
            Line line = findLineForStop(stop);
            
            if (line != null) {
                // 初始化列车移动任务
                startTrainMovementTask(player, minecart, line, stop.getId());
            }
        }
    }
    
    /**
     * 监听玩家离开矿车事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleExit(VehicleExitEvent event) {
        Vehicle vehicle = event.getVehicle();
        Entity passenger = event.getExited();
        
        // 只处理玩家离开地铁矿车的情况
        if (!(vehicle instanceof Minecart) || !(passenger instanceof Player)) {
            return;
        }
        
        Player player = (Player) passenger;
        Minecart minecart = (Minecart) vehicle;
        
        // 检查是否是Metro的矿车
        if (!"MetroMinecart".equals(minecart.getCustomName())) {
            return;
        }
        
        // 清除玩家的地铁显示内容（包括计分板和title）
        ScoreboardManager.clearPlayerDisplay(player);
        
        // 取消列车移动任务
        cancelTrainMovementTask(player.getUniqueId());
        
        // 获取当前位置
        Location location = minecart.getLocation();
        
        // 检查位置是否在停靠区上
        if (!isAtStop(location)) {
            // 如果不在停靠区上，立即移除矿车
            final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
            SchedulerUtil.regionRun(plugin, location, () -> {
                if (finalMinecart != null && !finalMinecart.isDead()) {
                    finalMinecart.remove();
                }
            }, 1L, -1); // 1 tick后移除
            return;
        }
        
        // 如果在停靠区上，根据配置延迟移除矿车
        int despawnDelay = plugin.getConfig().getInt("settings.cart_despawn_delay", 60);
        
        final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
        SchedulerUtil.regionRun(plugin, location, () -> {
            if (finalMinecart != null && !finalMinecart.isDead()) {
                finalMinecart.remove();
            }
        }, despawnDelay, -1); // 使用配置的延迟时间
    }
    
    /**
     * 监听矿车移动事件，检测脱轨
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        
        // 只处理地铁矿车
        if (!(vehicle instanceof Minecart)) {
            return;
        }
        
        Minecart minecart = (Minecart) vehicle;

        // 检查是否是Metro的矿车
        if (!"MetroMinecart".equals(minecart.getCustomName())) {
            return;
        }

        // 获取当前位置
        // Location location = minecart.getLocation();

        // // 检查矿车是否在铁轨上
        // if (!LocationUtil.isOnRail(location)) {
        //     // 矿车已脱轨，强制乘客下车并移除矿车
        //     minecart.eject();
        //     minecart.remove();
        // }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (LocationUtil.isOnRail(to)) {
            // 限制上坡速度为0.4
            if (to.getY() > from.getY()) {
                Vector direction = LocationUtil.getDirectionVector(from, to);
                minecart.setVelocity(direction.multiply(0.4));
            }
        } else {
            minecart.eject();
            minecart.remove();
        }
    }
    
    /**
     * 启动列车移动任务
     */
    private void startTrainMovementTask(Player player, Minecart minecart, Line line, String stopId) {
        UUID playerId = player.getUniqueId();
        
        // 如果已有任务，先取消
        cancelTrainMovementTask(playerId);
        
        // 创建新的列车移动任务
        TrainMovementTask task = new TrainMovementTask(plugin, minecart, player, line.getId(), stopId);
        
        // 如果当前站是终点站，不执行任务
        if (line.getNextStopId(stopId) == null) {
            return;
        }
        
        // 注册并启动任务
        BukkitTask taskId = SchedulerUtil.globalRun(plugin, task, 1L, 1L);
        task.setTaskId(taskId);
        
        // 保存任务ID
        trainMovementTasks.put(playerId, taskId);
    }
    
    /**
     * 取消列车移动任务
     */
    private void cancelTrainMovementTask(UUID playerId) {
        BukkitTask taskId = trainMovementTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }
    
    /**
     * 查找停靠区所属的线路
     */
    private Line findLineForStop(Stop stop) {
        if (stop == null) {
            return null;
        }
        
        LineManager lineManager = plugin.getLineManager();
        for (Line line : lineManager.getAllLines()) {
            if (line.containsStop(stop.getId())) {
                return line;
            }
        }
        
        return null;
    }
    
    /**
     * 检查位置是否在任何停靠区内
     */
    private boolean isAtStop(Location location) {
        StopManager stopManager = plugin.getStopManager();
        return stopManager.getStopContainingLocation(location) != null;
    }
} 