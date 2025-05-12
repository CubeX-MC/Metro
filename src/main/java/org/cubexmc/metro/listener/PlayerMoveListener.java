package org.cubexmc.metro.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
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
    private final Map<UUID, Integer> continuousInfoTasks = new HashMap<>(); // 记录持续显示信息的任务ID
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>(); // 记录专门的ActionBar显示任务ID
    
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
        
        // 判断是否有可换乘线路
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
            subtitle = plugin.getConfig().getString(configPath + ".subtitle_with_transfers", 
                    plugin.getConfig().getString("titles.stop_continuous.subtitle_with_transfers", 
                    "开往 &d{terminus_name} &f方向 | 下一站: &e{next_stop_name} | 可换乘: &a{transfer_lines}"));
        } else {
            // 没有换乘线路，使用不含换乘信息的基本模板
            subtitle = plugin.getConfig().getString(configPath + ".subtitle", 
                    plugin.getConfig().getString("titles.stop_continuous.subtitle", 
                    "开往 &d{terminus_name} &f方向 | 下一站: &e{next_stop_name}"));
        }
        
        String actionbar = plugin.getConfig().getString(configPath + ".actionbar", 
                plugin.getConfig().getString("titles.stop_continuous.actionbar", "§f上一站: §7{last_stop_name} §f| 下一站: §a{next_stop_name} §f| §e可换乘: {transfer_lines}"));
        
        // 替换占位符
        final String finalTitle = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop, lineManager);
        final String finalSubtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop, lineManager);
        final String finalActionbar = TextUtil.replacePlaceholders(actionbar, line, stop, lastStop, nextStop, terminalStop, lineManager);
        
        // 根据always配置选择显示方式
        if (alwaysShow) {
            // 在always模式下，为ActionBar创建一个高频刷新的独立任务
            BukkitRunnable actionBarTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                        cancel();
                        cancelActionBarTask(playerId);
                        return;
                    }
                    
                    // 持续显示ActionBar信息（更高频率刷新以防闪烁）
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', finalActionbar))
                    );
                }
            };
            
            // 高频刷新ActionBar（每10 tick，约0.5秒刷新一次）
            int actionBarTaskId = actionBarTask.runTaskTimer(plugin, 0, 10).getTaskId();
            actionBarTasks.put(playerId, actionBarTaskId);
        }
        
        // 创建并启动主任务（负责Title显示）
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                    cancel();
                    cancelContinuousInfoTask(playerId);
                    cancelActionBarTask(playerId);
                    return;
                }
                
                // 根据always配置选择显示方式
                if (alwaysShow) {
                    // 持续显示模式：使用无淡入淡出效果的title显示（时间极短）
                    player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', finalTitle),
                        ChatColor.translateAlternateColorCodes('&', finalSubtitle),
                        0, 90, 0  // 无淡入淡出，持续时间增加到100 ticks (5秒)，比刷新间隔长
                    );
                    
                    // ActionBar由专门的任务处理，这里不再发送
                } else {
                    // 非持续显示模式：仅在任务首次运行时显示
                    // 获取任务首次运行的标记
                    boolean isFirstRun = true;
                    
                    if (player.hasMetadata("metro_first_run_" + stop.getId())) {
                        MetadataValue value = player.getMetadata("metro_first_run_" + stop.getId()).get(0);
                        if (value != null && value.asBoolean()) {
                            isFirstRun = false;
                        }
                    }
                    
                    if (isFirstRun) {
                        // 首次运行，显示title和actionbar
                        player.setMetadata("metro_first_run_" + stop.getId(), 
                                new FixedMetadataValue(plugin, true));
                        
                        // 同时显示Title和ActionBar
                        player.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', finalTitle),
                            ChatColor.translateAlternateColorCodes('&', finalSubtitle),
                            plugin.getConfig().getInt("titles.stop_continuous.fade_in", 10),
                            plugin.getConfig().getInt("titles.stop_continuous.stay", 40),
                            plugin.getConfig().getInt("titles.stop_continuous.fade_out", 10)
                        );
                        
                        // 显示ActionBar
                        player.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', finalActionbar))
                        );
                        
                        // 停止任务，因为非持续显示模式只需要显示一次
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // 清除标记，以便下次进入停靠区时能再次显示
                                player.removeMetadata("metro_first_run_" + stop.getId(), plugin);
                            }
                        }.runTaskLater(plugin, 200L); // 10秒后清除标记
                        
                        // 立即取消任务，不再持续运行
                        cancel();
                    }
                }
            }
        };
        
        int taskId = task.runTaskTimer(plugin, 0, interval).getTaskId();
        continuousInfoTasks.put(playerId, taskId);
    }
    
    /**
     * 获取包含指定停靠区的线路
     * 
     * @param stop 停靠区
     * @return 包含该停靠区的第一条线路，如果没有则返回null
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
     * 取消持续显示信息的任务
     */
    private void cancelContinuousInfoTask(UUID playerId) {
        Integer taskId = continuousInfoTasks.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }
    
    /**
     * 取消ActionBar显示任务
     */
    private void cancelActionBarTask(UUID playerId) {
        Integer taskId = actionBarTasks.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }
} 