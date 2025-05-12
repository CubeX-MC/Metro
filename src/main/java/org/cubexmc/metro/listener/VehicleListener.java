package org.cubexmc.metro.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.util.LocationUtil;

/**
 * 处理矿车相关事件
 */
public class VehicleListener implements Listener {
    
    private final Metro plugin;
    
    public VehicleListener(Metro plugin) {
        this.plugin = plugin;
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
        
        // 清除玩家的计分板
        ScoreboardManager.clearScoreboard(player);
        
        // 获取当前位置
        Location location = minecart.getLocation();
        
        // 检查位置是否在停靠区上
        if (!isAtStop(location)) {
            // 如果不在停靠区上，立即移除矿车
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minecart != null && !minecart.isDead()) {
                        minecart.remove();
                    }
                }
            }.runTaskLater(plugin, 1L); // 1 tick后移除
            return;
        }
        
        // 如果在停靠区上，延迟移除矿车
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minecart != null && !minecart.isDead()) {
                    minecart.remove();
                }
            }
        }.runTaskLater(plugin, 60L); // 3秒后移除
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
        Location location = minecart.getLocation();
        
        // 检查矿车是否在铁轨上
        if (!LocationUtil.isOnRail(location)) {
            // 矿车已脱轨，强制乘客下车并移除矿车
            minecart.eject();
            minecart.remove();
        }
    }
    
    /**
     * 检查位置是否在任何停靠区内
     */
    private boolean isAtStop(Location location) {
        // 使用StopManager查找包含该位置的停靠区
        StopManager stopManager = plugin.getStopManager();
        return stopManager.getStopContainingLocation(location) != null;
    }
} 