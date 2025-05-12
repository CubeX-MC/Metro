package org.cubexmc.metro.train;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import java.util.ArrayList;
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
     * 创建新的计分板
     * 
     * @param player 玩家
     * @param title 计分板标题
     */
    public static void createScoreboard(Player player, String title) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        
        // 创建新的计分板
        Scoreboard scoreboard = manager.getNewScoreboard();
        
        // 创建主要目标
        Objective objective = scoreboard.registerNewObjective("metro", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // 应用计分板
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * 设置计分板行
     * 
     * @param player 玩家
     * @param score 行的分数（用于排序，越高越靠上）
     * @param text 行文本
     */
    public static void setLine(Player player, int score, String text) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            return;
        }
        
        Objective objective = scoreboard.getObjective("metro");
        if (objective == null) {
            return;
        }
        
        Score scoreObj = objective.getScore(text);
        scoreObj.setScore(score);
    }
    
    /**
     * 为乘客更新旅行信息计分板
     * 
     * @param player 玩家
     * @param line 当前乘坐的线路
     * @param targetStopId 目标停靠区ID（当前前往或已到达的站点）
     * @param isAtStop 是否停靠在站点（true=停靠在站点，false=行驶中）
     */
    public static void updateTravelScoreboard(Player player, Line line, String targetStopId, boolean isAtStop) {
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
        LineManager lineManager = plugin.getLineManager();
        
        // 从配置文件中获取样式设置
        String currentStopStyle = plugin.getConfig().getString("scoreboard.styles.current_stop", "§f● ");
        String nextStopStyle = plugin.getConfig().getString("scoreboard.styles.next_stop", "§a● ");
        String otherStopsStyle = plugin.getConfig().getString("scoreboard.styles.other_stops", "§7● ");
        
        // 从配置文件中获取统一的线路标识符
        String lineSymbol = plugin.getConfig().getString("scoreboard.line_symbol", "■");
        
        // 在计分板上显示所有停靠区
        int scoreValue = stopIds.size();
        Map<String, String> displayedStops = new HashMap<>(); // 用于跟踪已显示的停靠区
        
        // 找到目标站点的索引
        int targetIndex = stopIds.indexOf(targetStopId);
        
        // 计算实际显示的当前站和下一站索引
        int currentStopIndex = -1;
        int nextStopIndex = -1;
        
        if (isAtStop) {
            // 在站点停靠时：当前站=目标站，下一站=目标站的下一站
            currentStopIndex = targetIndex;
            nextStopIndex = targetIndex + 1 < stopIds.size() ? targetIndex + 1 : -1;
        } else {
            // 在行驶中：当前站不显示，下一站=目标站（即正在前往的站台）
            currentStopIndex = -1;
            nextStopIndex = targetIndex;
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
                
                // 获取该站点的可换乘线路
                List<String> transferableLines = stop.getTransferableLines();
                StringBuilder transferInfo = new StringBuilder();
                
                // 添加换乘线路标识符
                if (!transferableLines.isEmpty()) {
                    // 排除当前乘坐的线路
                    List<String> filteredLines = new ArrayList<>(transferableLines);
                    filteredLines.remove(line.getId());
                    
                    if (!filteredLines.isEmpty()) {
                        for (String transferLineId : filteredLines) {
                            Line transferLine = lineManager.getLine(transferLineId);
                            if (transferLine != null) {
                                // 使用线路颜色 + 统一标识符
                                String coloredSymbol = ChatColor.translateAlternateColorCodes('&', transferLine.getColor()) + lineSymbol + " ";
                                transferInfo.append(coloredSymbol);
                            }
                        }
                    }
                }
                
                // 设置站点样式和名称
                String formattedName;
                if (i == currentStopIndex) {
                    // 当前站点
                    formattedName = currentStopStyle + displayName;
                } else if (i == nextStopIndex) {
                    // 下一站
                    formattedName = nextStopStyle + displayName;
                } else {
                    // 其他站点
                    formattedName = otherStopsStyle + displayName;
                }
                
                // 添加换乘信息
                if (transferInfo.length() > 0) {
                    formattedName += " " + transferInfo;
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
     * 为乘客更新旅行信息计分板 - 保持向后兼容的重载方法
     * 
     * @param player 玩家
     * @param line 当前乘坐的线路
     * @param targetStopId 目标停靠区ID
     */
    public static void updateTravelScoreboard(Player player, Line line, String targetStopId) {
        // 默认不在站点，只在路上行驶
        updateTravelScoreboard(player, line, targetStopId, false);
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