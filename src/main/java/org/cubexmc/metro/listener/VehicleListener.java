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
import org.bukkit.scheduler.BukkitRunnable;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.util.LocationUtil;
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
    private final Map<UUID, Integer> passengerInfoTasks = new HashMap<>(); // 记录乘客信息显示任务
    
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
                // 设置计分板显示
                if (plugin.getConfig().getBoolean("scoreboard.passenger.enabled", true)) {
                    setupPassengerScoreboard(player, line, stop);
                }
                
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
        
        // 如果在停靠区上，根据配置延迟移除矿车
        int despawnDelay = plugin.getConfig().getInt("settings.cart_despawn_delay", 60);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minecart != null && !minecart.isDead()) {
                    minecart.remove();
                }
            }
        }.runTaskLater(plugin, despawnDelay); // 使用配置的延迟时间
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
        
        // 获取终点站
        StopManager stopManager = plugin.getStopManager();
        String terminalStopId = stopIds.get(stopIds.size() - 1);
        Stop terminalStop = stopManager.getStop(terminalStopId);
        
        // 设置计分板标题
        String title = plugin.getConfig().getString("scoreboard.passenger.title", "{line_color_code}{line}");
        title = TextUtil.replacePlaceholders(title, line, currentStop, null, null, terminalStop, plugin.getLineManager());
        
        // 初始化计分板
        ScoreboardManager.createScoreboard(player, ChatColor.translateAlternateColorCodes('&', title));
        
        // 设置计分板内容
        List<String> lines = plugin.getConfig().getStringList("scoreboard.passenger.lines");
        if (lines.isEmpty()) {
            lines = List.of(
                "§f开往: §e{terminus_name}方向",
                "§f终点站: §e{destination_stop_name}",
                "",
                "§f当前站点:",
                "{stop_list}"
            );
        }
        
        // 添加计分板行
        int score = lines.size();
        for (String lineText : lines) {
            // 替换特殊占位符
            if (lineText.contains("{stop_list}")) {
                // 创建站点列表
                String stopList = buildStopList(line, currentStop, stopManager);
                ScoreboardManager.setLine(player, score, stopList);
            } else {
                // 替换其他占位符
                String processedLine = TextUtil.replacePlaceholders(lineText, line, currentStop, null, null, terminalStop, plugin.getLineManager());
                ScoreboardManager.setLine(player, score, ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            score--;
        }
    }
    
    /**
     * 创建线路站点列表字符串（简化版，使用省略号减少过多站点显示）
     */
    private String buildStopList(Line line, Stop currentStop, StopManager stopManager) {
        List<String> stopIds = line.getOrderedStopIds();
        StringBuilder stopList = new StringBuilder();
        
        // 获取站点样式
        String currentStopStyle = plugin.getConfig().getString("scoreboard.styles.current_stop", "§f● ");
        String nextStopStyle = plugin.getConfig().getString("scoreboard.styles.next_stop", "§a● ");
        String otherStopStyle = plugin.getConfig().getString("scoreboard.styles.other_stops", "§7● ");
        
        // 当前站点索引和终点站索引
        int currentIndex = line.getStopIndex(currentStop.getId());
        int lastIndex = stopIds.size() - 1;
        
        // 如果站点数量少于等于4个，直接全部显示
        if (stopIds.size() <= 4) {
            for (int i = 0; i < stopIds.size(); i++) {
                if (i > 0) {
                    stopList.append("\n");
                }
                
                String stopId = stopIds.get(i);
                Stop stop = stopManager.getStop(stopId);
                if (stop != null) {
                    // 根据站点位置使用不同样式
                    if (i == currentIndex) {
                        stopList.append(currentStopStyle);
                    } else if (i == currentIndex + 1) {
                        stopList.append(nextStopStyle);
                    } else {
                        stopList.append(otherStopStyle);
                    }
                    stopList.append(stop.getName());
                }
            }
        } else {
            // 显示部分站点，带省略号
            // 需要显示的站点索引：当前站点前一站、当前站点、当前站点后一站、终点站
            
            // 计算需要显示的站点范围
            int prevIndex = Math.max(0, currentIndex - 1);
            int nextIndex = Math.min(lastIndex, currentIndex + 1);
            
            // 添加前部分站点
            for (int i = prevIndex; i <= nextIndex; i++) {
                if (i > prevIndex) {
                    stopList.append("\n");
                }
                
                String stopId = stopIds.get(i);
                Stop stop = stopManager.getStop(stopId);
                if (stop != null) {
                    // 根据站点位置使用不同样式
                    if (i == currentIndex) {
                        stopList.append(currentStopStyle);
                    } else if (i == currentIndex + 1) {
                        stopList.append(nextStopStyle);
                    } else {
                        stopList.append(otherStopStyle);
                    }
                    stopList.append(stop.getName());
                }
            }
            
            // 如果下一站不是终点站，且当前显示的站点不包含终点站，添加省略号和终点站
            if (nextIndex != lastIndex) {
                stopList.append("\n").append(otherStopStyle).append("...");
                
                // 添加终点站
                stopList.append("\n");
                String lastStopId = stopIds.get(lastIndex);
                Stop lastStop = stopManager.getStop(lastStopId);
                if (lastStop != null) {
                    stopList.append(otherStopStyle).append(lastStop.getName());
                }
            }
        }
        
        return stopList.toString();
    }
    
    /**
     * 启动乘客行程信息显示任务
     */
    private void startPassengerJourneyInfoTask(Player player, Line line, Stop currentStop, Minecart minecart) {
        UUID playerId = player.getUniqueId();
        
        // 取消之前的任务
        cancelPassengerInfoTask(playerId);
        
        // 获取配置的显示间隔
        int interval = plugin.getConfig().getInt("titles.passenger_journey.interval", 40);
        
        // 创建并启动任务
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查玩家是否仍在线、是否仍在矿车中
                if (!player.isOnline() || player.getVehicle() != minecart || minecart.isDead()) {
                    cancel();
                    cancelPassengerInfoTask(playerId);
                    return;
                }
                
                // 获取当前矿车位置
                Location location = minecart.getLocation();
                StopManager stopManager = plugin.getStopManager();
                Stop currentStop = stopManager.getStopContainingLocation(location);
                
                // 如果不在任何停靠区，使用线路的下一站
                String nextStopId = null;
                if (currentStop != null) {
                    nextStopId = line.getNextStopId(currentStop.getId());
                } else {
                    // 找出最近的下一站
                    nextStopId = findNextStop(line, location);
                }
                
                Stop nextStop = nextStopId != null ? stopManager.getStop(nextStopId) : null;
                
                // 获取终点站
                List<String> stopIds = line.getOrderedStopIds();
                Stop terminalStop = null;
                if (!stopIds.isEmpty()) {
                    String terminalStopId = stopIds.get(stopIds.size() - 1);
                    terminalStop = stopManager.getStop(terminalStopId);
                }
                
                // 获取显示文本配置
                String title = plugin.getConfig().getString("titles.passenger_journey.title", "{line_color_code}{line}");
                
                // 是否有可换乘线路
                boolean hasTransferableLines = false;
                if (nextStop != null) {
                    List<String> transferLines = nextStop.getTransferableLines();
                    // 排除当前线路
                    transferLines.remove(line.getId());
                    hasTransferableLines = !transferLines.isEmpty();
                }
                
                // 根据是否有可换乘线路选择subtitle模板
                String subtitle;
                if (hasTransferableLines) {
                    // 有换乘线路，使用包含换乘信息的模板
                    subtitle = plugin.getConfig().getString("titles.passenger_journey.subtitle_with_transfers", 
                        "开往 &d{terminus_name} &f方向 | 可换乘: &a{transfer_lines}");
                } else {
                    // 没有换乘线路，使用不含换乘信息的基本模板
                    subtitle = plugin.getConfig().getString("titles.passenger_journey.subtitle", 
                        "开往 &d{terminus_name} &f方向");
                }
                
                String actionbar = plugin.getConfig().getString("titles.passenger_journey.actionbar", "§f当前线路: {line_color_code}{line} §f| 开往: §e{terminus_name}方向 §f| §e可换乘: {transfer_lines}");
                
                // 替换占位符
                LineManager lineManager = plugin.getLineManager();
                title = TextUtil.replacePlaceholders(title, line, currentStop, null, nextStop, terminalStop, lineManager);
                subtitle = TextUtil.replacePlaceholders(subtitle, line, currentStop, null, nextStop, terminalStop, lineManager);
                actionbar = TextUtil.replacePlaceholders(actionbar, line, currentStop, null, nextStop, terminalStop, lineManager);
                
                // 显示信息
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    plugin.getConfig().getInt("titles.passenger_journey.fade_in", 5),
                    plugin.getConfig().getInt("titles.passenger_journey.stay", 40),
                    plugin.getConfig().getInt("titles.passenger_journey.fade_out", 5)
                );
                
                // 显示ActionBar信息
                player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', actionbar))
                );
            }
        };
        
        int taskId = task.runTaskTimer(plugin, 0, interval).getTaskId();
        passengerInfoTasks.put(playerId, taskId);
    }
    
    /**
     * 取消乘客信息显示任务
     */
    private void cancelPassengerInfoTask(UUID playerId) {
        Integer taskId = passengerInfoTasks.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }
    
    /**
     * 根据当前位置找出线路中的下一站
     */
    private String findNextStop(Line line, Location currentLocation) {
        if (currentLocation == null || line == null) {
            return null;
        }
        
        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            return null;
        }
        
        StopManager stopManager = plugin.getStopManager();
        double minDistance = Double.MAX_VALUE;
        String closestStopId = null;
        
        // 找出距离当前位置最近的站点
        for (String stopId : stopIds) {
            Stop stop = stopManager.getStop(stopId);
            if (stop != null && stop.getStopPointLocation() != null) {
                double distance = currentLocation.distance(stop.getStopPointLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestStopId = stopId;
                }
            }
        }
        
        // 如果找到最近的站点，返回它的下一站
        if (closestStopId != null) {
            return line.getNextStopId(closestStopId);
        }
        
        return null;
    }
    
    /**
     * 获取包含指定停靠区的线路
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
        // 使用StopManager查找包含该位置的停靠区
        StopManager stopManager = plugin.getStopManager();
        return stopManager.getStopContainingLocation(location) != null;
    }
} 