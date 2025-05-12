package org.cubexmc.metro.train;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 计分板管理器，用于在玩家乘坐矿车时显示线路信息
 */
public class ScoreboardManager {
    
    private static final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private static Metro plugin;
    
    /**
     * 初始化计分板管理器
     * 
     * @param metroPlugin Metro插件实例
     */
    public static void initialize(Metro metroPlugin) {
        plugin = metroPlugin;
    }
    
    /**
     * 为乘客更新旅行信息计分板
     * 
     * @param player 玩家
     * @param line 当前乘坐的线路
     * @param targetStopId 目标停靠区ID
     */
    public static void updateTravelScoreboard(Player player, Line line, String targetStopId) {
        if (player == null || !player.isOnline() || line == null || plugin == null) {
            return;
        }
        
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        
        // 获取或创建玩家专属的计分板
        Scoreboard scoreboard = playerScoreboards.getOrDefault(player.getUniqueId(), manager.getNewScoreboard());
        
        // 清除旧的计分板内容
        if (scoreboard.getObjective("metro") != null) {
            scoreboard.getObjective("metro").unregister();
        }
        
        // 创建新的计分板
        Objective objective = scoreboard.registerNewObjective("metro", "dummy", 
                ChatColor.GOLD + "" + ChatColor.BOLD + line.getName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // 获取线路的所有停靠区信息
        List<String> stopIds = line.getOrderedStopIds();
        StopManager stopManager = plugin.getStopManager();
        
        // 从配置文件中获取样式设置
        String currentStopStyle = plugin.getConfig().getString("scoreboard.styles.current_stop", "§f● ");
        String nextStopStyle = plugin.getConfig().getString("scoreboard.styles.next_stop", "§a● ");
        String otherStopsStyle = plugin.getConfig().getString("scoreboard.styles.other_stops", "§7● ");
        
        // 在计分板上显示所有停靠区
        int scoreValue = stopIds.size();
        Map<String, String> displayedStops = new HashMap<>(); // 用于跟踪已显示的停靠区
        
        // 找到目标站点的索引，计算当前站点和下一站
        int targetIndex = stopIds.indexOf(targetStopId);
        int nextStopIndex = targetIndex + 1;
        if (nextStopIndex >= stopIds.size()) {
            nextStopIndex = -1; // 没有下一站
        }
        
        for (int i = 0; i < stopIds.size(); i++) {
            String stopId = stopIds.get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop != null) {
                String displayName = stop.getName();
                
                // 如果该停靠区已经显示过，则跳过（避免重复显示）
                if (displayedStops.containsKey(displayName)) {
                    continue;
                }
                
                displayedStops.put(displayName, displayName);
                
                // 设置站点样式
                String formattedName;
                if (i == targetIndex) {
                    // 当前站点
                    formattedName = currentStopStyle + displayName;
                } else if (i == nextStopIndex) {
                    // 下一站
                    formattedName = nextStopStyle + displayName;
                } else {
                    // 其他站点
                    formattedName = otherStopsStyle + displayName;
                }
                
                Score score = objective.getScore(formattedName);
                score.setScore(scoreValue--);
            }
        }
        
        // 应用计分板
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * 清除玩家的地铁计分板
     * 
     * @param player 要清除计分板的玩家
     */
    public static void clearScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getNewScoreboard());
            playerScoreboards.remove(player.getUniqueId());
        }
    }
} 