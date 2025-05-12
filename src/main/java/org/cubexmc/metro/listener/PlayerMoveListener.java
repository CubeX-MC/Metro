package org.cubexmc.metro.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.TextUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 监听玩家移动事件，用于检测玩家进入停靠区
 */
public class PlayerMoveListener implements Listener {
    
    private final Metro plugin;
    private final Map<UUID, String> playerInStopMap = new HashMap<>(); // 记录玩家当前所在的停靠区ID
    
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
                onPlayerEnterStop(player, stop);
            }
        } else if (currentStopId != null) {
            // 玩家离开了停靠区
            playerInStopMap.remove(playerId);
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
     * 处理玩家进入停靠区的事件
     */
    private void onPlayerEnterStop(Player player, Stop stop) {
        if (!plugin.isEnterStopTitleEnabled()) {
            return;
        }
        
        // 查找该停靠区所属的线路
        LineManager lineManager = plugin.getLineManager();
        Line line = null;
        
        for (Line l : lineManager.getAllLines()) {
            if (l.containsStop(stop.getId())) {
                line = l;
                break;
            }
        }
        
        if (line != null) {
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
            
            // 获取标题配置
            String title = plugin.getEnterStopTitle();
            String subtitle = plugin.getEnterStopSubtitle();
            
            // 替换占位符
            title = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop);
            subtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop);
            
            // 显示标题
            player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                plugin.getEnterStopFadeIn(),
                plugin.getEnterStopStay(),
                plugin.getEnterStopFadeOut()
            );
        }
    }
} 