package org.cubexmc.metro.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.TextUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * 监听玩家移动事件，用于检测玩家进入停靠区
 */
public class PlayerMoveListener implements Listener {
    
    private final Metro plugin;
    private final Map<UUID, String> playerInStopMap = new HashMap<>(); // 记录玩家当前所在的停靠区ID
    private final Map<UUID, Object> continuousInfoTasks = new HashMap<>(); // 记录持续显示信息的任务ID
    private final Map<UUID, Object> actionBarTasks = new HashMap<>(); // 记录专门的ActionBar显示任务ID
    
    public PlayerMoveListener(Metro plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只有玩家从一个方块移动到另一个方块时才检查，优化性能
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!player.hasPermission("metro.use")) {
            return;
        }
        
        // 检查玩家是否在矿车内，如果在矿车内则不显示站台信息
        if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
            org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
            // 检查是否是Metro的矿车
            if ("MetroMinecart".equals(minecart.getCustomName())) {
                // 如果玩家在Metro矿车内，取消站台信息显示
                UUID playerId = player.getUniqueId();
                String currentStopId = playerInStopMap.remove(playerId);
                if (currentStopId != null) {
                    cancelContinuousInfoTask(playerId);
                    cancelActionBarTask(playerId);
                }
                return;
            }
        }
        
        // 玩家不在矿车内，正常处理站台信息
        Location location = player.getLocation();
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(location);
        
        UUID playerId = player.getUniqueId();
        String currentStopId = playerInStopMap.get(playerId);
        
        // 检查玩家是否进入了新的停靠区
        if (stop != null) {
            String stopId = stop.getId();
            if (!stopId.equals(currentStopId)) {
                // 玩家进入了新的停靠区
                playerInStopMap.put(playerId, stopId);
                
                // 取消原来的持续显示任务
                cancelContinuousInfoTask(playerId);
                
                // 启动新的持续显示任务
                if (plugin.getConfig().getBoolean("titles.stop_continuous.enabled", true)) {
                    startContinuousInfoTask(player, stop);
                }
            }
        } else if (currentStopId != null) {
            // 玩家离开了停靠区
            playerInStopMap.remove(playerId);
            cancelContinuousInfoTask(playerId);
            cancelActionBarTask(playerId);  // 取消ActionBar任务
            
            // 清除首次运行标记
            String lastStopId = currentStopId;
            if (lastStopId != null) {
                player.removeMetadata("metro_first_run_" + lastStopId, plugin);
            }
        }
    }
    
    /**
     * 检查两个位置是否在同一方块内
     */
    private boolean isSameBlock(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        
        return from.getBlockX() == to.getBlockX() 
                && from.getBlockY() == to.getBlockY() 
                && from.getBlockZ() == to.getBlockZ();
    }
    
    /**
     * 启动持续显示停靠区信息的任务
     */
    private void startContinuousInfoTask(Player player, Stop stop) {
        UUID playerId = player.getUniqueId();
        
        // 取消已有的ActionBar任务
        cancelActionBarTask(playerId);
        
        // 查找该停靠区所属的线路
        LineManager lineManager = plugin.getLineManager();
        final Line line = findLineForStop(stop);
        
        if (line == null) {
            return;
        }
        
        // 获取配置
        int interval = plugin.getConfig().getInt("titles.stop_continuous.interval", 60);
        boolean alwaysShow = plugin.getConfig().getBoolean("titles.stop_continuous.always", true);
        
        // 准备信息内容（在任务外提前准备，以便ActionBar任务可以使用）
        // 获取前一站和下一站信息
        String lastStopId = line.getPreviousStopId(stop.getId());
        String nextStopId = line.getNextStopId(stop.getId());
        
        StopManager stopManager = plugin.getStopManager();
        Stop lastStop = lastStopId != null ? stopManager.getStop(lastStopId) : null;
        Stop nextStop = nextStopId != null ? stopManager.getStop(nextStopId) : null;
        
        // 获取终点站信息
        List<String> stopIds = line.getOrderedStopIds();
        Stop terminalStop = null;
        if (!stopIds.isEmpty()) {
            String terminalStopId = stopIds.get(stopIds.size() - 1);
            terminalStop = stopManager.getStop(terminalStopId);
        }
        
        // 确定站点类型并获取对应配置
        String configPath = "titles.stop_continuous";
        boolean isStartStop = (lastStop == null); // 没有上一站，是始发站
        boolean isEndStop = (nextStop == null);   // 没有下一站，是终点站
        
        if (isStartStop) {
            configPath += ".start_stop";
        } else if (isEndStop) {
            configPath += ".end_stop";
        }
        
        // 获取配置
        String title = plugin.getConfig().getString(configPath + ".title", 
                plugin.getConfig().getString("titles.stop_continuous.title", "{line_color_code}{line}"));
        
        String subtitle = plugin.getConfig().getString(configPath + ".subtitle", 
                plugin.getConfig().getString("titles.stop_continuous.subtitle", 
                "开往 &d{terminus_name} &f方向 | 下一站: &e{next_stop_name}"));
        
        String actionbar = plugin.getConfig().getString(configPath + ".actionbar", 
                plugin.getConfig().getString("titles.stop_continuous.actionbar", "§f上一站: §7{last_stop_name} §f| 下一站: §a{next_stop_name} §f| §e可换乘: {transfer_lines}"));
        
        Map<String, String> customTitle = stop.getCustomTitle("stop_continuous");
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                title = customTitle.get("title");
            }
            if (customTitle.containsKey("subtitle")) {
                subtitle = customTitle.get("subtitle");
            }
            if (customTitle.containsKey("actionbar")) {
                actionbar = customTitle.get("actionbar");
            }
        }
        
        final String finalTitle = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop, lineManager, plugin.getStopManager());
        final String finalSubtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop, lineManager, plugin.getStopManager());
        final String finalActionbar = TextUtil.replacePlaceholders(actionbar, line, stop, lastStop, nextStop, terminalStop, lineManager, plugin.getStopManager());

        if (alwaysShow) {
            Object actionBarTaskId = SchedulerUtil.globalRun(plugin, new Runnable() {
                @Override
                public void run() {
                    // 检查任务是否仍然存在于Map中，如果不存在说明已被外部取消
                    if (!actionBarTasks.containsKey(playerId)) {
                        return;
                    }
                    
                    if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                        // 不要在这里取消任务，让外部的PlayerMoveEvent来处理
                        // cancelActionBarTask(playerId);
                        return;
                    }
                    if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                        org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                        if ("MetroMinecart".equals(minecart.getCustomName())) {
                            return; 
                        }
                    }
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', finalActionbar))
                    );
                }
            }, 0L, 20L); 
            actionBarTasks.put(playerId, actionBarTaskId);

            Object titleTaskId = SchedulerUtil.globalRun(plugin, new Runnable() {
                @Override
                public void run() {
                    // 检查任务是否仍然存在于Map中，如果不存在说明已被外部取消
                    if (!continuousInfoTasks.containsKey(playerId)) {
                        return;
                    }

                    if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                        // 不要在这里取消任务，让外部的PlayerMoveEvent来处理
                        // cancelContinuousInfoTask(playerId);
                        return;
                    }
                    if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                        org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                        if ("MetroMinecart".equals(minecart.getCustomName())) {
                            return;
                        }
                    }
                    player.sendTitle(
                         ChatColor.translateAlternateColorCodes('&', finalTitle),
                         ChatColor.translateAlternateColorCodes('&', finalSubtitle),
                         0, 40, 0
                    );
//                    Title title = new Title(ChatColor.translateAlternateColorCodes('&', finalTitle), ChatColor.translateAlternateColorCodes('&', finalSubtitle), 0, 40, interval);
//                    player.showTitle(title);
                }
            }, 0L, interval);
            continuousInfoTasks.put(playerId, titleTaskId);
        } else {
            String metaKey = "metro_first_run_" + stop.getId();
            List<MetadataValue> metaList = player.getMetadata(metaKey);
            
            if (metaList.isEmpty()) {
                player.setMetadata(metaKey, new FixedMetadataValue(plugin, true));
                
                boolean inMetroMinecart = false;
                if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                    org.bukkit.entity.Minecart mc_vehicle = (org.bukkit.entity.Minecart) player.getVehicle();
                    if ("MetroMinecart".equals(mc_vehicle.getCustomName())) {
                        inMetroMinecart = true;
                    }
                }

                if (!inMetroMinecart) {
                    int fadeIn = plugin.getConfig().getInt("titles.stop_continuous.fade_in", 10);
                    int stay = plugin.getConfig().getInt("titles.stop_continuous.stay", 40);
                    int fadeOut = plugin.getConfig().getInt("titles.stop_continuous.fade_out", 10);
                    
                    player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', finalTitle),
                        ChatColor.translateAlternateColorCodes('&', finalSubtitle),
                        fadeIn, stay, fadeOut
                    );
                    
                    final int totalDisplayTime = stay + fadeOut; 
                    Object actionBarTaskId = SchedulerUtil.globalRun(plugin, new Runnable() {
                        private int count = 0;
                        private final int maxCount = totalDisplayTime / 20 + 1; 
                        
                        @Override
                        public void run() {
                            if (!player.isOnline() || count >= maxCount || !stop.isInStop(player.getLocation())) {
                                cancelActionBarTask(playerId);
                                return;
                            }
                            if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                                org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                                if ("MetroMinecart".equals(minecart.getCustomName())) {
                                    count++; 
                                    return; 
                                }
                            }
                            player.spigot().sendMessage(
                                ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', finalActionbar))
                            );
                            count++;
                        }
                    }, 0L, 20L);
                    actionBarTasks.put(playerId, actionBarTaskId);
                }
            }
        }
    }
    
    /**
     * 查找包含指定停靠区的线路
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
     * 取消显示持续信息的任务
     */
    private void cancelContinuousInfoTask(UUID playerId) {
        Object taskId = continuousInfoTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }
    
    /**
     * 取消ActionBar显示任务
     */
    private void cancelActionBarTask(UUID playerId) {
        Object taskId = actionBarTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }
} 