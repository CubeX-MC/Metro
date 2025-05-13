package org.cubexmc.metro.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
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
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.TextUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 处理矿车相关事件
 */
public class VehicleListener implements Listener {
    
    private final Metro plugin;
    private final Map<UUID, Object> passengerInfoTasks = new HashMap<>(); // 记录乘客信息显示任务
    
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
            LineManager lineManager = plugin.getLineManager();
            Line line = findLineForStop(stop);
            
            if (line != null) {
                // 启动乘客行程信息显示
                if (plugin.getConfig().getBoolean("titles.passenger_journey.enabled", true)) {
                    startPassengerJourneyInfoTask(player, line, stop, minecart);
                }
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
        
        // 清除玩家的计分板
        ScoreboardManager.clearScoreboard(player);
        
        // 取消乘客信息显示任务
        cancelPassengerInfoTask(player.getUniqueId());
        
        // 获取当前位置
        Location location = minecart.getLocation();
        
        // 检查位置是否在停靠区上
        if (!isAtStop(location)) {
            // 如果不在停靠区上，立即移除矿车
            final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
            SchedulerUtil.runTaskLaterAtLocation(plugin, location, () -> {
                if (finalMinecart != null && !finalMinecart.isDead()) {
                    finalMinecart.remove();
                }
            }, 1L); // 1 tick后移除
            return;
        }
        
        // 如果在停靠区上，根据配置延迟移除矿车
        int despawnDelay = plugin.getConfig().getInt("settings.cart_despawn_delay", 60);
        
        final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
        SchedulerUtil.runTaskLaterAtLocation(plugin, location, () -> {
            if (finalMinecart != null && !finalMinecart.isDead()) {
                finalMinecart.remove();
            }
        }, despawnDelay); // 使用配置的延迟时间
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
     * 设置乘客计分板
     */
    private void setupPassengerScoreboard(Player player, Line line, Stop currentStop) {
        // 获取线路中的所有站点
        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            return;
        }
        
        // 更新计分板，显示当前站点
        ScoreboardManager.updateEnteringStopScoreboard(player, line, currentStop.getId());
    }
    
    /**
     * 启动乘客行程信息显示任务
     */
    private void startPassengerJourneyInfoTask(Player player, Line line, Stop currentStop, Minecart minecart) {
        UUID playerId = player.getUniqueId();
        
        // 如果已有任务，先取消
        cancelPassengerInfoTask(playerId);
        
        // 获取计分板刷新间隔
        int interval = plugin.getConfig().getInt("titles.passenger_journey.interval", 40);
        
        // 创建并保存任务ID
        Object taskId = SchedulerUtil.runTaskTimer(plugin, new Runnable() {
            private final Location minecartLastLocation = minecart.getLocation().clone();
            
            @Override
            public void run() {
                // 检查玩家是否仍在矿车上
                if (!minecart.isValid() || !player.isOnline() || !minecart.equals(player.getVehicle())) {
                    cancelPassengerInfoTask(playerId);
                    return;
                }
                
                // 获取当前位置
                Location currentLocation = minecart.getLocation();
                
                // 获取乘客行程信息
                StopManager stopManager = plugin.getStopManager();
                Stop currentStop = stopManager.getStopContainingLocation(currentLocation);
                
                // 如果当前不在任何站点区域内或者在移动中，显示乘客行程信息
                // 显示passenger_journey信息
                String title = plugin.getConfig().getString("titles.passenger_journey.title", "下一站 &e{next_stop_name}");
                String subtitle = plugin.getConfig().getString("titles.passenger_journey.subtitle", "开往 &d{terminus_name} &f方向 | 下一站可换乘: &6{next_stop_transfers}");
                String actionbar = plugin.getConfig().getString("titles.passenger_journey.actionbar", "列车已启动，请扶好站稳，注意安全");
                
                // 如果当前停在站点内，需要找出下一站
                Stop targetStop = null;
                if (currentStop != null) {
                    String nextStopId = line.getNextStopId(currentStop.getId());
                    if (nextStopId != null) {
                        targetStop = stopManager.getStop(nextStopId);
                    }
                } else {
                    // 不在站点内，尝试找出最近的下一站
                    String nextStopId = findNextStop(line, currentLocation);
                    if (nextStopId != null) {
                        targetStop = stopManager.getStop(nextStopId);
                    }
                }
                
                // 获取终点站信息
                List<String> stopIds = line.getOrderedStopIds();
                String terminalStopId = stopIds.isEmpty() ? null : stopIds.get(stopIds.size() - 1);
                Stop terminusStop = stopManager.getStop(terminalStopId);
                String terminusName = terminusStop != null ? terminusStop.getName() : "终点站";
                
                // 获取下一站名称
                String nextStopName = targetStop != null ? targetStop.getName() : "终点站";
                
                // 获取LineManager以获取线路信息
                LineManager lineManager = plugin.getLineManager();
                
                // 替换占位符
                String finalTitle = TextUtil.replacePlaceholders(title, line, currentStop, null, targetStop, terminusStop, lineManager);
                String finalSubtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, targetStop, terminusStop, lineManager);
                String finalActionbar = TextUtil.replacePlaceholders(actionbar, line, currentStop, null, targetStop, terminusStop, lineManager);
                
                // 转换颜色代码
                finalTitle = ChatColor.translateAlternateColorCodes('&', finalTitle);
                finalSubtitle = ChatColor.translateAlternateColorCodes('&', finalSubtitle);
                finalActionbar = ChatColor.translateAlternateColorCodes('&', finalActionbar);
                
                // 显示Title和ActionBar
                player.sendTitle(finalTitle, finalSubtitle, 0, 40, 10);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(finalActionbar));
            }
        }, 0L, interval);
        
        // 保存任务ID
        passengerInfoTasks.put(playerId, taskId);
    }
    
    /**
     * 取消乘客信息显示任务
     */
    private void cancelPassengerInfoTask(UUID playerId) {
        Object taskId = passengerInfoTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }
    
    /**
     * 查找下一个停靠区
     */
    private String findNextStop(Line line, Location currentLocation) {
        List<String> stopIds = line.getOrderedStopIds();
        
        // 找到距离当前位置最近的停靠区
        double minDistance = Double.MAX_VALUE;
        int nearestStopIndex = -1;
        StopManager stopManager = plugin.getStopManager();
        
        for (int i = 0; i < stopIds.size(); i++) {
            Stop stop = stopManager.getStop(stopIds.get(i));
            if (stop == null || stop.getStopPointLocation() == null) {
                continue;
            }
            
            Location stopLocation = stop.getStopPointLocation();
            if (stopLocation.getWorld() != currentLocation.getWorld()) {
                continue;
            }
            
            double distance = stopLocation.distance(currentLocation);
            if (distance < minDistance) {
                minDistance = distance;
                nearestStopIndex = i;
            }
        }
        
        // 如果找不到最近的停靠区，则返回第一个停靠区
        if (nearestStopIndex == -1) {
            return stopIds.isEmpty() ? null : stopIds.get(0);
        }
        
        // 如果当前位置就在一个停靠区内，则返回下一个停靠区
        StopManager manager = plugin.getStopManager();
        Stop stopAtLocation = manager.getStopContainingLocation(currentLocation);
        
        if (stopAtLocation != null) {
            int nextIndex = line.getStopIndex(stopAtLocation.getId()) + 1;
            if (nextIndex < stopIds.size()) {
                return stopIds.get(nextIndex);
            }
            return null; // 没有下一站，可能是终点站
        }
        
        // 否则，返回最近的停靠区
        return stopIds.get(nearestStopIndex);
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