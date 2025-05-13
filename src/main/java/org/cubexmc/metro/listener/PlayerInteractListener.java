package org.cubexmc.metro.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 处理玩家交互事件
 */
public class PlayerInteractListener implements Listener {
    
    private final Metro plugin;
    
    // 用于防止短时间内多次点击触发多次调用
    private final Map<UUID, Long> lastInteractTime = new HashMap<>();
    private static final int INTERACT_COOLDOWN = 2000; // 点击冷却时间，单位毫秒
    
    // 用于跟踪站点的矿车生成状态，键为站点ID，值为时间戳
    private final Map<String, Long> pendingMinecarts = new HashMap<>();
    private static final int MINECART_PENDING_TIMEOUT = 60000; // 矿车最大等待时间，单位毫秒
    
    public PlayerInteractListener(Metro plugin) {
        this.plugin = plugin;
        
        // 定期清理过期的矿车等待记录
        SchedulerUtil.runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            pendingMinecarts.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > MINECART_PENDING_TIMEOUT);
        }, 1200L, 1200L); // 每分钟清理一次
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        
        // 如果不是右键点击方块，不处理
        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
            return;
        }
        
        // 检查点击的是否是铁轨
        if (!clickedBlock.getType().name().contains("RAIL")) {
            return;
        }
        
        // 输出调试信息
        plugin.getLogger().info("玩家 " + player.getName() + " 右键点击了铁轨，位置: " + 
                LocationUtil.locationToString(clickedBlock.getLocation()));
        
        // 防止短时间内多次点击
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastInteractTime.containsKey(playerId)) {
            long lastTime = lastInteractTime.get(playerId);
            if (currentTime - lastTime < INTERACT_COOLDOWN) {
                // 如果冷却时间内再次点击，取消事件并返回
                event.setCancelled(true);
                return;
            }
        }
        
        // 在处理之前先检查点击的站点
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(clickedBlock.getLocation());
        
        // 如果找到了停靠区，检查是否有待处理的矿车
        if (stop != null) {
            String stopId = stop.getId();
            if (pendingMinecarts.containsKey(stopId)) {
                // 该站点已有矿车在等待玩家上车
                long pendingTime = pendingMinecarts.get(stopId);
                if (currentTime - pendingTime < MINECART_PENDING_TIMEOUT) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_pending"));
                    event.setCancelled(true);
                    return;
                } else {
                    // 超时，清除记录
                    pendingMinecarts.remove(stopId);
                }
            }
        }
        
        // 检查是否是停靠点并处理
        boolean handled = checkAndHandleStopPoint(player, clickedBlock.getLocation());
        
        // 如果成功处理了停靠点，更新点击时间并取消事件
        if (handled) {
            plugin.getLogger().info("玩家 " + player.getName() + " 成功在停靠区内上车");
            lastInteractTime.put(playerId, currentTime);
            event.setCancelled(true);
            
            // 设置一个任务，在冷却时间后清除记录
            final UUID finalPlayerId = playerId;
            SchedulerUtil.runTaskLaterAsync(plugin, () -> {
                lastInteractTime.remove(finalPlayerId);
            }, INTERACT_COOLDOWN / 50);
        } else {
            plugin.getLogger().info("玩家 " + player.getName() + " 点击的铁轨不在任何停靠区内");
        }
    }
    
    /**
     * 检查并处理停靠点交互
     * @return 是否成功处理了停靠点
     */
    private boolean checkAndHandleStopPoint(Player player, Location location) {
        if (!player.hasPermission("metro.use")) {
            return false;
        }
        
        StopManager stopManager = plugin.getStopManager();
        
        // 检查点击位置是否在任何停靠区内
        Stop stop = stopManager.getStopContainingLocation(location);
        
        // 如果找到了包含点击位置的停靠区
        if (stop != null) {
            plugin.getLogger().info("玩家 " + player.getName() + " 点击位置在停靠区 " + stop.getName() + " (" + stop.getId() + ") 内");
            
            // 确保停靠区已配置停靠点
            if (stop.getStopPointLocation() == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_point"));
                return false;
            }
            
            // 找到停靠区并且是铁轨，处理上车逻辑
            handleStopPoint(player, stop);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理停靠点交互
     */
    private void handleStopPoint(Player player, Stop stop) {
        // 检查停靠区是否在任何线路上
        LineManager lineManager = plugin.getLineManager();
        
        Line line = null;
        for (Line l : lineManager.getAllLines()) {
            if (l.containsStop(stop.getId())) {
                line = l;
                break;
            }
        }
        
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_line"));
            return;
        }
        
        // 检查该站点是否为终点站
        String nextStopId = line.getNextStopId(stop.getId());
        if (nextStopId == null) {
            // 是终点站，不生成列车
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.terminal_stop"));
            return;
        }
        
        // 记录该站点有矿车正在处理中
        pendingMinecarts.put(stop.getId(), System.currentTimeMillis());
        
        // 显示线路信息
        showLineInfo(player, stop, line);
        
        // 播放车辆到站音乐 - 在右键点击后立即播放
        playStationArrivalSound(player);
        
        // 生成矿车
        spawnMinecart(player, stop, line);
    }
    
    /**
     * 显示线路信息
     */
    private void showLineInfo(Player player, Stop stop, Line line) {
        // 获取下一停靠区信息
        String nextStopId = line.getNextStopId(stop.getId());
        String nextStopName = "终点站";
        
        if (nextStopId != null) {
            Stop nextStop = plugin.getStopManager().getStop(nextStopId);
            if (nextStop != null) {
                nextStopName = nextStop.getName();
            }
        } else {
            // 如果当前站已经是终点站，不需要显示信息
            return;
        }
        
        // 显示ActionBar信息
        String message = ChatColor.GOLD + line.getName() + ChatColor.WHITE + " - 下一站: " + 
                ChatColor.YELLOW + nextStopName;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * 播放车辆到站音乐
     */
    private void playStationArrivalSound(Player player) {
        if (plugin.isStationArrivalSoundEnabled() && !plugin.getStationArrivalNotes().isEmpty()) {
            SoundUtil.playNoteSequence(plugin, player, plugin.getStationArrivalNotes(), plugin.getStationArrivalInitialDelay());
        }
    }
    
    /**
     * 生成矿车
     */
    private void spawnMinecart(Player player, Stop stop, Line line) {
        Location location = stop.getStopPointLocation();
        float yaw = stop.getLaunchYaw();
        
        if (location != null) {
            final String stopId = stop.getId();
            
            // 创建一个新位置，保留原来的坐标但使用停靠区的发车朝向
            Location spawnLocation = location.clone();
            spawnLocation.setYaw(yaw);
            
            // 获取矿车生成延迟
            int spawnDelay = plugin.getConfig().getInt("settings.cart_spawn_delay", 20);
            
            // 显示等待信息
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_coming"));
            plugin.getLogger().info("为玩家 " + player.getName() + " 生成矿车，位置: " + LocationUtil.locationToString(location));
            
            // 延迟生成矿车，使用位置调度器以确保在正确的区域执行
            SchedulerUtil.runTaskLaterAtLocation(plugin, location, () -> {
                try {
                    // 生成矿车实体
                    Minecart minecart = (Minecart) location.getWorld().spawnEntity(spawnLocation, EntityType.MINECART);
                    
                    // 设置矿车没有碰撞体积
                    minecart.setCustomName("MetroMinecart");
                    minecart.setCustomNameVisible(false);
                    minecart.setPersistent(false);
                    
                    // 将玩家放入矿车
                    minecart.addPassenger(player);
                    
                    // 获取发车延迟
                    int departureDelay = plugin.getConfig().getInt("settings.cart_departure_delay", 60);
                    
                    // 显示等待发车的信息
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_spawned", departureDelay / 20));
                    
                    // 播放发车音乐 - 确保在玩家上车后播放
                    playDepartureSound(player);
                    
                    // 立即创建与发车后相同的计分板 - 设置为停站状态(true)，而不是行驶状态(false)
                    TrainMovementTask tempTask = new TrainMovementTask(plugin, minecart, player, line.getId(), stop.getId(), true);
                    
                    // 等待配置的发车延迟后启动列车移动任务
                    SchedulerUtil.runTaskLater(plugin, () -> {
                        // 检查玩家是否仍在矿车上
                        if (minecart.isValid() && minecart.getPassengers().contains(player)) {
                            // 启动矿车移动任务，明确指定为行驶状态
                            TrainMovementTask trainTask = new TrainMovementTask(plugin, minecart, player, line.getId(), stop.getId(), false);
                            Object taskId = SchedulerUtil.runTaskTimer(plugin, trainTask, 1L, 1L); // 立即开始，每tick运行一次
                            trainTask.setTaskId(taskId);
                            
                            // 清除该站点的矿车等待状态 - 玩家已经上车并发车
                            pendingMinecarts.remove(stopId);
                        } else {
                            // 玩家不在矿车上，移除矿车
                            if (minecart.isValid()) {
                                minecart.remove();
                            }
                            // 清除该站点的矿车等待状态 - 玩家没有上车
                            pendingMinecarts.remove(stopId);
                        }
                    }, departureDelay);
                } catch (Exception e) {
                    // 出现异常，清除该站点的矿车等待状态
                    pendingMinecarts.remove(stopId);
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_error"));
                    plugin.getLogger().warning("为玩家 " + player.getName() + " 生成矿车时出现异常: " + e.getMessage());
                }
            }, spawnDelay);
        }
    }
    
    /**
     * 播放发车音乐
     */
    private void playDepartureSound(Player player) {
        if (plugin.isDepartureSoundEnabled() && !plugin.getDepartureNotes().isEmpty()) {
            SoundUtil.playNoteSequence(plugin, player, plugin.getDepartureNotes(), plugin.getDepartureInitialDelay());
        }
    }
} 