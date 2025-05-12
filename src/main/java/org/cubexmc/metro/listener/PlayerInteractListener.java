package org.cubexmc.metro.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

/**
 * 处理玩家交互事件
 */
public class PlayerInteractListener implements Listener {
    
    private final Metro plugin;
    
    public PlayerInteractListener(Metro plugin) {
        this.plugin = plugin;
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
        
        // 检查是否是停靠点
        checkAndHandleStopPoint(player, clickedBlock.getLocation());
    }
    
    /**
     * 检查并处理停靠点交互
     */
    private void checkAndHandleStopPoint(Player player, Location location) {
        if (!player.hasPermission("metro.use")) {
            return;
        }
        
        StopManager stopManager = plugin.getStopManager();
        
        // 将位置转换为字符串格式
        String locationStr = LocationUtil.locationToString(location);
        
        // 遍历所有停靠区，查找匹配的停靠点
        for (String stopId : stopManager.getAllStopIds()) {
            Stop stop = stopManager.getStop(stopId);
            if (stop != null && stop.getStopPointLocation() != null) {
                String stopPointStr = LocationUtil.locationToString(stop.getStopPointLocation());
                
                if (locationStr.equals(stopPointStr)) {
                    // 找到匹配的停靠点
                    handleStopPoint(player, stop);
                    return;
                }
            }
        }
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
            player.sendMessage(ChatColor.RED + "该停靠区不属于任何线路。");
            return;
        }
        
        // 显示线路信息
        showLineInfo(player, stop, line);
        
        // 生成矿车
        spawnMinecart(player, stop, line);
        
        // 播放发车音乐
        playDepartureSound(player);
    }
    
    /**
     * 播放发车音乐
     */
    private void playDepartureSound(Player player) {
        if (plugin.isDepartureSoundEnabled() && !plugin.getDepartureNotes().isEmpty()) {
            SoundUtil.playNoteSequence(plugin, player, plugin.getDepartureNotes());
        }
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
        }
        
        // 显示ActionBar信息
        String message = ChatColor.GOLD + line.getName() + ChatColor.WHITE + " - 下一站: " + 
                ChatColor.YELLOW + nextStopName;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * 生成矿车
     */
    private void spawnMinecart(Player player, Stop stop, Line line) {
        Location location = stop.getStopPointLocation();
        float yaw = stop.getLaunchYaw();
        
        if (location != null) {
            // 创建一个新位置，保留原来的坐标但使用停靠区的发车朝向
            Location spawnLocation = location.clone();
            spawnLocation.setYaw(yaw);
            
            // 生成矿车实体
            Minecart minecart = (Minecart) location.getWorld().spawnEntity(spawnLocation, EntityType.MINECART);
            
            // 设置矿车没有碰撞体积
            minecart.setCustomName("MetroMinecart");
            minecart.setCustomNameVisible(false);
            minecart.setPersistent(false);
            
            // 将玩家放入矿车
            minecart.addPassenger(player);
            
            // 启动矿车移动任务
            new TrainMovementTask(plugin, minecart, player, line.getId(), stop.getId())
                    .runTaskTimer(plugin, 20L, 1L); // 1秒后开始，每tick运行一次
        }
    }
} 