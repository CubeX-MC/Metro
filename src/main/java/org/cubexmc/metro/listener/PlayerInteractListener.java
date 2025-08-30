package org.cubexmc.metro.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.SoundUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * 处理玩家交互事件
 */
public class PlayerInteractListener implements Listener {
    
    private final Metro plugin;
    private final SelectionManager selectionManager;
    
    // 用于防止短时间内多次点击触发多次调用
    private final Map<UUID, Long> lastInteractTime = new HashMap<>();
    private static final int INTERACT_COOLDOWN = 2000; // 点击冷却时间，单位毫秒
    
    // 用于跟踪站点的矿车生成状态，键为站点ID，值为时间戳
    private final Map<String, Long> pendingMinecarts = new HashMap<>();
    private static final int MINECART_PENDING_TIMEOUT = 60000; // 矿车最大等待时间，单位毫秒
    
    public PlayerInteractListener(Metro plugin) {
        this.plugin = plugin;
        this.selectionManager = plugin.getSelectionManager();
        
        // 定期清理过期的矿车等待记录
        SchedulerUtil.globalRun(plugin, () -> {
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

        // 处理金锄头选区
        // 别忘了添加语言文件selection.corner1_set和selection.corner2_set
        if (player.hasPermission("metro.admin") && player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_HOE) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                selectionManager.setCorner1(player, clickedBlock.getLocation());
                player.sendMessage(plugin.getLanguageManager().getMessage("selection.corner1_set",
                        LanguageManager.put(LanguageManager.args(), "location", clickedBlock.getLocation().getBlockX() + ", " + clickedBlock.getLocation().getBlockY() + ", " + clickedBlock.getLocation().getBlockZ())));
                event.setCancelled(true);
                return;
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                selectionManager.setCorner2(player, clickedBlock.getLocation());
                player.sendMessage(plugin.getLanguageManager().getMessage("selection.corner2_set",
                        LanguageManager.put(LanguageManager.args(), "location", clickedBlock.getLocation().getBlockX() + ", " + clickedBlock.getLocation().getBlockY() + ", " + clickedBlock.getLocation().getBlockZ())));
                event.setCancelled(true);
                return;
            }
        }
        
        // 如果不是右键点击方块，不处理
        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
            return;
        }
        
        // 检查点击的是否是铁轨
        if (!clickedBlock.getType().name().contains("RAIL")) {
            return;
        }
        
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
            lastInteractTime.put(playerId, currentTime);
            event.setCancelled(true);
            
            // 设置一个任务，在冷却时间后清除记录
            final UUID finalPlayerId = playerId;
            SchedulerUtil.asyncRun(plugin, () -> {
                lastInteractTime.remove(finalPlayerId);
            }, INTERACT_COOLDOWN / 50);
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
            // 反转Yaw值，使矿车外观朝向与移动方向一致
            spawnLocation.setYaw(yaw);
            
            // 获取矿车生成延迟
            long spawnDelay = plugin.getCartSpawnDelay();
            
            // 显示等待信息
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_coming"));
            
            // 延迟生成矿车，使用实体调度器以确保在正确的线程执行
            SchedulerUtil.regionRun(plugin, location, () -> {
                try {
                    // 生成矿车实体
                    Minecart minecart = (Minecart) location.getWorld().spawnEntity(spawnLocation, EntityType.MINECART);
                    
                    // 设置矿车属性
                    minecart.setCustomName("MetroMinecart");
                    minecart.setCustomNameVisible(false);
                    minecart.setPersistent(false);
                    minecart.setGravity(false); // 禁用重力temp 6-15
                    minecart.setSlowWhenEmpty(false); // 不因空车而减速

                    double max_speed = line.getMaxSpeed();
                    if (max_speed == -1.0)
                        max_speed = plugin.getCartSpeed();
                    
                    // 设置矿车的最大速度，只在创建时设置一次
                    minecart.setMaxSpeed(max_speed);
                    
                    // 将玩家放入矿车
                    if (!minecart.addPassenger(player)) {
                        // 如果上车失败，可能需要处理，例如取消任务或通知玩家
                        minecart.remove(); // 移除矿车
                        pendingMinecarts.remove(stopId); // 清除等待状态
                        player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_error"));
                        return;
                    }
                    
                    // 显示待乘车信息
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_spawned", 
                            LanguageManager.put(LanguageManager.args(), "departure_seconds", String.valueOf(plugin.getCartDepartureDelay() / 20))));

                    // 创建列车任务，使用TrainMovementTask处理等待发车和发车逻辑
                    // 这将触发handleArrivalAtStation方法，显示等待信息、播放等待音乐，然后延迟发车
                    TrainMovementTask.startTrainTask(plugin, minecart, player, line.getId(), stop.getId());
                    
                    // 清除该站点的矿车等待状态
                    pendingMinecarts.remove(stopId);
                } catch (Exception e) {
                    // 出现异常，清除该站点的矿车等待状态
                    pendingMinecarts.remove(stopId);
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_error"));
                    e.printStackTrace();
                }
            }, spawnDelay, -1);
        }
    }
} 