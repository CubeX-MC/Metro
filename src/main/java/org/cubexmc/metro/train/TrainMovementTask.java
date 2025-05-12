package org.cubexmc.metro.train;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

import java.util.List;

/**
 * 负责控制单个矿车从一个停靠区直接移动到下一个停靠区
 */
public class TrainMovementTask extends BukkitRunnable {
    
    private final Metro plugin;
    private final Minecart minecart;
    private final Player passenger;
    private final Line line;
    private final String currentStopId;
    private final String targetStopId;
    private boolean isMoving;
    
    // 移动速度参数
    private static final double DEFAULT_SPEED = 0.3; // 矿车默认速度
    
    /**
     * 创建一个新的列车移动任务
     * 
     * @param plugin 插件实例
     * @param minecart 要控制的矿车实体
     * @param passenger 乘坐的玩家
     * @param lineId 线路ID
     * @param fromStopId 起始停靠区ID
     */
    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;
        
        LineManager lineManager = plugin.getLineManager();
        this.line = lineManager.getLine(lineId);
        this.currentStopId = fromStopId;
        
        // 确定下一停靠区作为目标
        this.targetStopId = line.getNextStopId(currentStopId);
        
        // 如果没有下一停靠区，无法启动
        if (targetStopId == null) {
            this.isMoving = false;
            return;
        }
        
        this.isMoving = true;
        
        // 更新乘客的计分板
        updateScoreboard();
    }
    
    @Override
    public void run() {
        // 如果矿车不存在或没有目标，取消任务
        if (minecart == null || minecart.isDead() || !isMoving || targetStopId == null) {
            cancel();
            return;
        }
        
        // 检查玩家是否仍在矿车中
        if (!isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }
        
        // 确保矿车仍然没有碰撞体积
        minecart.setCustomName("MetroMinecart");
        minecart.setGravity(false); // 禁用重力，防止下落
        
        // 如果矿车被阻挡，强制设置为穿透实体
        for (Entity entity : minecart.getNearbyEntities(1.0, 1.0, 1.0)) {
            if (entity != passenger && entity != minecart) {
                // 如果不是乘客或矿车本身，允许穿透
                minecart.teleport(minecart.getLocation()); // 刷新位置以避免碰撞
                break;
            }
        }
        
        // 直接前往目标停靠区
        Stop targetStop = plugin.getStopManager().getStop(targetStopId);
        if (targetStop == null || targetStop.getStopPointLocation() == null) {
            // 目标停靠区无效或没有停靠点，取消任务
            cancel();
            return;
        }
        
        Location targetLocation = targetStop.getStopPointLocation();
        Location currentLocation = minecart.getLocation();
        
        // 计算当前位置到目标点的距离
        double distance = currentLocation.distance(targetLocation);
        
        if (distance < 0.8) {
            // 已到达目标停靠区
            arrivedAtDestination();
            return;
        }
        
        // 检查是否脱轨
        if (!LocationUtil.isOnRail(currentLocation)) {
            // 如果脱轨，尝试将矿车放回最近的铁轨上
            Location nearbyRailLocation = findNearbyRail(currentLocation);
            if (nearbyRailLocation != null) {
                minecart.teleport(nearbyRailLocation);
            } else {
                // 如果无法找到附近的铁轨，通知玩家
                if (passenger != null) {
                    passenger.sendMessage("§c铁路已损坏，行程已终止！");
                }
                // 强制下车并移除矿车
                minecart.eject();
                minecart.remove();
                cancel();
                return;
            }
        }
        
        // 计算从当前位置到目标点的方向向量
        Vector direction = LocationUtil.getDirectionVector(currentLocation, targetLocation);
        
        // 设置矿车的速度
        minecart.setVelocity(direction.multiply(plugin.getCartSpeed()));
    }
    
    /**
     * 查找附近最近的铁轨位置
     * @param location 当前位置
     * @return 最近的铁轨位置，如果找不到则返回null
     */
    private Location findNearbyRail(Location location) {
        // 在5格半径内搜索铁轨
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Location checkLoc = location.clone().add(x, y, z);
                    if (LocationUtil.isRail(checkLoc)) {
                        return checkLoc.clone().add(0.5, 0, 0.5); // 中心化位置
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 检查乘客是否仍在矿车中
     */
    private boolean isPassengerStillRiding() {
        if (passenger == null || !passenger.isOnline()) {
            return false;
        }
        
        Entity vehicle = passenger.getVehicle();
        return vehicle != null && vehicle.equals(minecart);
    }
    
    /**
     * 处理乘客下车的情况
     */
    private void handlePassengerExit() {
        // 取消当前任务
        cancel();
        
        // 清除计分板
        if (passenger != null && passenger.isOnline()) {
            ScoreboardManager.clearScoreboard(passenger);
        }
        
        // 延迟移除矿车
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minecart != null && !minecart.isDead()) {
                    minecart.remove();
                }
            }
        }.runTaskLater(plugin, 40L); // 2秒后移除
    }
    
    /**
     * 处理到达目标停靠区的逻辑
     */
    private void arrivedAtDestination() {
        // 停止矿车
        minecart.setVelocity(new Vector(0, 0, 0));
        isMoving = false;
        
        // 检查是否为终点站
        String nextStopId = line.getNextStopId(targetStopId);
        if (nextStopId == null) {
            // 如果是终点站，直接处理终点站逻辑
            plugin.getLogger().info("已到达终点站: " + targetStopId + ", 玩家: " + (passenger != null ? passenger.getName() : "unknown"));
            handleTerminalStation();
            return;
        }
        
        // 普通站点，通知玩家已到站
        notifyArrival();
        
        // 更新计分板显示当前站为目标站
        updateScoreboard();
        
        // 添加调试日志
        if (passenger != null) {
            plugin.getLogger().info("矿车到达停靠点: " + targetStopId + ", 玩家: " + passenger.getName() + ", 5秒后检查是否自动发车");
        }
        
        // 设置等待时间，如果玩家未下车则继续前往下一站
        new BukkitRunnable() {
            @Override
            public void run() {
                // 检查玩家是否仍在矿车中
                if (isPassengerStillRiding()) {
                    // 获取下一停靠区 - 当前所在站点是之前的目标站点
                    String nextStopId = line.getNextStopId(targetStopId);
                    if (nextStopId != null) {
                        plugin.getLogger().info("自动发车: 从 " + targetStopId + " 前往 " + nextStopId + ", 玩家: " + passenger.getName());
                        
                        // 取消当前任务
                        TrainMovementTask.this.cancel();
                        
                        // 启动前往下一站的任务，注意：当前站点现在是targetStopId
                        new TrainMovementTask(plugin, minecart, passenger, line.getId(), targetStopId)
                                .runTaskTimer(plugin, 10L, 1L);
                    } else {
                        plugin.getLogger().info("已到达终点站: " + targetStopId + ", 玩家: " + passenger.getName());
                        // 已经是终点站，处理终点站逻辑
                        handleTerminalStation();
                    }
                } else {
                    plugin.getLogger().info("玩家已下车，不会自动发车");
                }
            }
        }.runTaskLater(plugin, 100L); // 5秒后检查
    }
    
    /**
     * 通知玩家已到达停靠区
     */
    private void notifyArrival() {
        if (passenger == null || !passenger.isOnline() || targetStopId == null) {
            return;
        }
        
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStop(targetStopId);
        
        if (stop != null) {
            // 显示自定义到站Title
            showArriveStopTitle(stop);
            
            // 播放到站音乐
            playArrivalSound();
        }
    }
    
    /**
     * 显示到站Title
     */
    private void showArriveStopTitle(Stop stop) {
        if (!plugin.isArriveStopTitleEnabled() || passenger == null || !passenger.isOnline()) {
            return;
        }
        
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
        
        String title = plugin.getArriveStopTitle();
        String subtitle = plugin.getArriveStopSubtitle();
        
        // 替换占位符
        title = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop);
        subtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop);
        
        passenger.sendTitle(
            title,
            subtitle,
            plugin.getArriveStopFadeIn(),
            plugin.getArriveStopStay(),
            plugin.getArriveStopFadeOut()
        );
    }
    
    /**
     * 播放到站音乐
     */
    private void playArrivalSound() {
        if (plugin.isArrivalSoundEnabled() && !plugin.getArrivalNotes().isEmpty() && passenger != null && passenger.isOnline()) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getArrivalNotes());
        }
    }
    
    /**
     * 处理终点站逻辑
     */
    private void handleTerminalStation() {
        if (passenger == null || !passenger.isOnline()) {
            plugin.getLogger().warning("终点站处理失败：玩家为null或不在线");
            return;
        }
        
        plugin.getLogger().info("处理终点站，玩家: " + passenger.getName() + ", 终点站: " + targetStopId);
        
        // 获取终点站信息
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStop(targetStopId);
        
        // 获取前一站信息（终点站没有下一站）
        String lastStopId = line.getPreviousStopId(targetStopId);
        Stop lastStop = lastStopId != null ? stopManager.getStop(lastStopId) : null;
        
        // 终点站信息 - 当前站就是终点站
        Stop terminalStop = stop;
        
        // 更新计分板显示当前站为终点站
        updateScoreboard();
        
        // 显示终点站Title
        if (plugin.isTerminalStopTitleEnabled()) {
            String title = plugin.getTerminalStopTitle();
            String subtitle = plugin.getTerminalStopSubtitle();
            
            // 替换占位符 - 终点站的nextStop传null
            title = TextUtil.replacePlaceholders(title, line, stop, lastStop, null, terminalStop);
            subtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, null, terminalStop);
            
            plugin.getLogger().info("显示终点站提示: title=" + title + ", subtitle=" + subtitle);
            
            passenger.sendTitle(
                title,
                subtitle,
                plugin.getTerminalStopFadeIn(),
                plugin.getTerminalStopStay(),
                plugin.getTerminalStopFadeOut()
            );
        } else {
            plugin.getLogger().warning("终点站提示未显示：配置中已禁用 (terminal_stop.enabled = false)");
        }
        
        // 播放到站音乐
        playArrivalSound();
        
        // 延迟强制玩家下车并移除矿车
        plugin.getLogger().info("计划4秒后强制玩家下车");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minecart != null && !minecart.isDead()) {
                    plugin.getLogger().info("执行强制下车操作，玩家: " + passenger.getName());
                    
                    // 清除计分板
                    ScoreboardManager.clearScoreboard(passenger);
                    
                    // 强制玩家下车
                    minecart.eject();
                    
                    // 延迟移除矿车
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (minecart != null && !minecart.isDead()) {
                                minecart.remove();
                                plugin.getLogger().info("终点站矿车已移除");
                            }
                        }
                    }.runTaskLater(plugin, 20L); // 1秒后移除
                } else {
                    plugin.getLogger().warning("无法强制下车：矿车已不存在");
                }
            }
        }.runTaskLater(plugin, 80L); // 4秒后强制下车
    }
    
    /**
     * 更新乘客的计分板
     */
    private void updateScoreboard() {
        if (passenger != null && passenger.isOnline() && line != null && targetStopId != null) {
            ScoreboardManager.updateTravelScoreboard(passenger, line, targetStopId);
        }
    }
} 