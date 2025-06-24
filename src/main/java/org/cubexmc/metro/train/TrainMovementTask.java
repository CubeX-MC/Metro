package org.cubexmc.metro.train;

import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * 负责控制单个矿车从一个停靠区直接移动到下一个停靠区
 * 同时负责所有与乘客显示相关的逻辑
 */
public class TrainMovementTask implements Runnable {
    
    // 列车状态枚举
    public enum TrainState {
        STOPPED_AT_STATION,    // 车停靠在stoppoint
        MOVING_IN_STATION,     // 车在行驶，在站台区域内
        MOVING_BETWEEN_STATIONS // 车在行驶，不在站台区域内
    }
    
    private final Metro plugin;
    private final Minecart minecart;
    private final Player passenger;
    private final Line line;
    private String currentStopId;
    private String targetStopId;
    private TrainState currentState;
    private Object taskId; // 保存任务ID，可以是BukkitTask或Folia的ScheduledTask
    
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
        this(plugin, minecart, passenger, lineId, fromStopId, TrainState.STOPPED_AT_STATION);
    }
    
    /**
     * 创建一个新的列车移动任务
     * 
     * @param plugin 插件实例
     * @param minecart 要控制的矿车实体
     * @param passenger 乘坐的玩家
     * @param lineId 线路ID
     * @param fromStopId 起始停靠区ID
     * @param initialState 初始状态
     */
    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId, TrainState initialState) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;
        
        LineManager lineManager = plugin.getLineManager();
        this.line = lineManager.getLine(lineId);
        this.currentStopId = fromStopId;
        this.targetStopId = line.getNextStopId(currentStopId);
        this.currentState = initialState;
        
        // 如果没有下一停靠区，默认为停止状态
        if (targetStopId == null) {
            this.currentState = TrainState.STOPPED_AT_STATION;
            return;
        }
        
        // 根据初始状态更新计分板
        updateScoreboardBasedOnState();
    }
    
    /**
     * 设置任务ID
     * 
     * @param taskId 任务ID对象
     */
    public void setTaskId(Object taskId) {
        this.taskId = taskId;
    }
    
    /**
     * 取消任务
     */
    public void cancel() {
        SchedulerUtil.cancelTask(taskId);
    }
    
    /**
     * 根据当前状态更新计分板
     */
    private void updateScoreboardBasedOnState() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        switch (currentState) {
            case STOPPED_AT_STATION:
                if (targetStopId == null) {
                    // 终点站
                    ScoreboardManager.updateTerminalScoreboard(passenger, line, currentStopId);
                } else {
                    // 普通站点
                    ScoreboardManager.updateEnteringStopScoreboard(passenger, line, currentStopId);
                }
                break;
            case MOVING_IN_STATION:
                ScoreboardManager.updateEnteringStopScoreboard(passenger, line, targetStopId);
                break;
            case MOVING_BETWEEN_STATIONS:
                ScoreboardManager.updateTravelingScoreboard(passenger, line, targetStopId);
                break;
        }
    }
    
    @Override
    public void run() {
        // 如果矿车不存在或没有目标，取消任务
        if (minecart == null || minecart.isDead() || targetStopId == null && currentState != TrainState.STOPPED_AT_STATION) {
            cancel();
            return;
        }
        
        // 检查玩家是否仍在矿车中
        if (!isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }
        
        // 维护矿车基本属性
        maintainMinecartProperties();
        
        // 获取目标停靠区
        Stop targetStop = plugin.getStopManager().getStop(targetStopId);
        if (targetStopId != null && (targetStop == null || targetStop.getStopPointLocation() == null)) {
            // 目标停靠区无效或没有停靠点，取消任务
            cancel();
            return;
        }
        
        // 根据当前状态执行不同的逻辑
        switch (currentState) {
            case STOPPED_AT_STATION:
//                handleStoppedAtStation();
                break;
            case MOVING_IN_STATION:
                handleMovingInStation(targetStop);
                break;
            case MOVING_BETWEEN_STATIONS:
                handleMovingBetweenStations(targetStop);
                break;
        }
    }
    
    /**
     * 维护矿车基本属性
     */
    private void maintainMinecartProperties() {
        // minecart.setCustomName("MetroMinecart");
        Component customName = Component.text("MetroMinecart");
        SchedulerUtil.entityRun(plugin, minecart, () -> {
            minecart.customName(customName);
            minecart.setGravity(false); // 禁用重力，防止下落
            // 如果矿车被阻挡，强制设置为穿透实体
            for (Entity entity : minecart.getNearbyEntities(1.0, 1.0, 1.0)) {
                if (entity != passenger && entity != minecart) {
                    // 如果不是乘客或矿车本身，允许穿透
                    minecart.teleport(minecart.getLocation()); // 刷新位置以避免碰撞
                    break;
                }
            }

            // 检查是否脱轨
            Location currentLocation = minecart.getLocation();
            if (!LocationUtil.isOnRail(currentLocation)) {
                handleDerailment(currentLocation);
            }
        }, 0L, -1); // 确保矿车存在时设置名称
    }
    
    /**
     * 处理脱轨情况
     */
    private void handleDerailment(Location currentLocation) {
        // 尝试将矿车放回最近的铁轨上
        Location nearbyRailLocation = findNearbyRail(currentLocation);
        if (nearbyRailLocation != null) {
            minecart.teleport(nearbyRailLocation);
        } else {
            // 如果无法找到附近的铁轨，执行脱轨逻辑

            // 检查是否启用了脱轨爆炸功能
            if (plugin.getConfig().getBoolean("derailment.explosion.enabled", false)) {
                // 从配置中获取爆炸参数
                float power = (float) plugin.getConfig().getDouble("derailment.explosion.power", 4.0);
                boolean setFire = plugin.getConfig().getBoolean("derailment.explosion.set_fire", true);
                boolean breakBlocks = plugin.getConfig().getBoolean("derailment.explosion.break_blocks", true);
                
                // 在矿车位置创建爆炸
                // 使用 region scheduler 来确保与 Folia 兼容并获得最佳性能，因为爆炸是基于位置的事件
                SchedulerUtil.regionRun(plugin, currentLocation, () -> {
                    currentLocation.getWorld().createExplosion(currentLocation, power, setFire, breakBlocks);
                }, 0L, -1L);
            }

            // 通知玩家
            if (passenger != null) {
                passenger.sendMessage(plugin.getLanguageManager().getMessage("passenger.train_derailed"));
            }
            // 强制下车并移除矿车
            minecart.eject();
            minecart.remove();
            cancel();
        }
    }
    
    /**
     * 处理在站内移动状态
     */
    private void handleMovingInStation(Stop targetStop) {
        Location currentLocation = minecart.getLocation();
        Location targetLocation = targetStop.getStopPointLocation();
        
        // 计算当前位置到目标点的距离
        double distance = currentLocation.distance(targetLocation);
        
        // 判断是否非常接近停车点
        if (distance < 0.8) {
            // 到达停车点，变更状态为停站状态
            transitionToStoppedAtStation();
            return;
        }
        
        // 检查是否仍在站台区域内
        boolean isInStopArea = targetStop.isInStop(currentLocation);
        if (!isInStopArea) {
            // 离开站台区域，变更状态为站间行驶
            transitionToMovingBetweenStations();
            return;
        }
    }
    
    /**
     * 处理站间行驶状态
     */
    private void handleMovingBetweenStations(Stop targetStop) {
        Location currentLocation = minecart.getLocation();
        // Location targetLocation = targetStop.getStopPointLocation();
        
        // 检查是否进入站台区域
        boolean isInStopArea = targetStop.isInStop(currentLocation);
        if (isInStopArea) {
            // 进入站台区域，变更状态为站内移动
            transitionToMovingInStation(targetStop);
            // return;
        }
    }
    
    /**
     * 转换到停站状态
     */
    private void transitionToStoppedAtStation() {
        // 停止矿车
        minecart.setVelocity(new Vector(0, 0, 0));
        
        // 更新状态
        TrainState previousState = currentState;
        currentState = TrainState.STOPPED_AT_STATION;
        
        // 如果是从站内移动状态转换过来的，处理停车事件
        if (previousState == TrainState.MOVING_IN_STATION) {
            handleArrivalAtStation();
        }
        
        // 更新计分板
        updateScoreboardBasedOnState();
    }
    
    /**
     * 转换到站内移动状态
     */
    private void transitionToMovingInStation(Stop targetStop) {
        // 更新状态
        TrainState previousState = currentState;
        currentState = TrainState.MOVING_IN_STATION;
        
        // 如果是从站间行驶状态转换过来的，处理进站事件
        if (previousState == TrainState.MOVING_BETWEEN_STATIONS) {
            handleEnteringStation(targetStop);
        }
        
        // 更新计分板
        updateScoreboardBasedOnState();
    }
    
    /**
     * 转换到站间行驶状态
     */
    private void transitionToMovingBetweenStations() {
        // 更新状态
        // TrainState previousState = currentState;
        currentState = TrainState.MOVING_BETWEEN_STATIONS;
        
        // 离开站台区域后提速到全速
        // updateMinecartVelocity(plugin.getCartSpeed());
        
        // 更新计分板
        updateScoreboardBasedOnState();
    }
    
    /**
     * 处理进站事件 (3->2)
     */
    private void handleEnteringStation(Stop targetStop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 播放到站音乐
        playArrivalSound();
        
        // 播放站台到站音乐（给站台上的玩家听）
        playStationArrivalSound(targetStop);
        
        // 检查是否为终点站
        String nextStopId = line.getNextStopId(targetStopId);
        if (nextStopId == null) {
            if (plugin.isTerminalStopTitleEnabled()) {
                showTerminalStopInfo(targetStop);
                
                // 更新终点站计分板 但在state中已经更新了
                // ScoreboardManager.updateTerminalScoreboard(passenger, line, targetStopId);
            }
        } else {
            // 显示普通到站提示
            showArriveStopInfo(targetStop);
        }
    }
    
    /**
     * 播放站台到站音乐（给站台上的玩家听）
     */
    private void playStationArrivalSound(Stop stop) {
        // 检查是否启用站台到站音乐
        if (!plugin.getConfig().getBoolean("station_arrival.enabled", true)) {
            return;
        }
        
        // 获取音符列表
        List<String> notes = plugin.getConfig().getStringList("station_arrival.notes");
        if (notes.isEmpty()) {
            return;
        }
        
        // 获取初始延迟
        int initialDelay = plugin.getConfig().getInt("station_arrival.initial_delay", 0);
        
        // 获取站台区域内的所有玩家
        Location stopLocation = stop.getStopPointLocation();
        if (stopLocation == null) {
            return;
        }
        
        // 获取所有在站台区域内的玩家
        for (Player player : stopLocation.getWorld().getPlayers()) {
            // 排除已经在车内的乘客
            if (player.equals(passenger)) {
                continue;
            }
            
            // 检查玩家是否在站台区域内
            if (stop.isInStop(player.getLocation())) {
                // 向站台上的玩家播放音乐
                SoundUtil.playNoteSequence(plugin, player, notes, initialDelay);
            }
        }
    }
    
    /**
     * 处理停车事件 (2->1)
     */
    private void handleArrivalAtStation() {
        handleArrivalAtStation(false);
    }
    
    /**
     * 处理停车事件 (2->1)
     * 
     * @param isNewlySpawned 矿车是否刚刚生成，如果是，则不会把目的地设置为当前站点
     */
    private void handleArrivalAtStation(boolean isNewlySpawned) {
        // 已到达停车点
        
        // 当矿车不是刚刚生成时，才更新当前站点ID
        if (!isNewlySpawned) {
            // 当前站点ID更新为目标站点ID
            currentStopId = targetStopId;
        }
        
        if (line.getNextStopId(currentStopId) == null) {
            // 终点站特殊处理
            handleTerminalStation();
            targetStopId = null;
        } else {
            // 非终点站，显示等待发车信息
            showWaitingInfo();
            
            // 播放等待发车音乐
            startWaitingSound();
            
            // 延迟发车
            scheduleNextDeparture();
        }
    }
    
    /**
     * 处理发车事件 (1->2)
     */
    private void handleDeparture() {
        // 获取下一站信息
        targetStopId = line.getNextStopId(currentStopId);
        if (targetStopId == null) {
            plugin.getLogger().warning("无法启动列车，没有下一站: 当前站=" + currentStopId);
            return;
        }
        
        // 获取站点信息
        StopManager stopManager = plugin.getStopManager();
        Stop currentStop = stopManager.getStop(currentStopId);
        Stop nextStop = stopManager.getStop(targetStopId);
        
        if (currentStop == null || nextStop == null) {
            plugin.getLogger().warning("无法启动列车，站点信息无效");
            return;
        }
        
        // 播放发车音乐
        playDepartureSound();
        
        // 显示行程信息
        if (plugin.getConfig().getBoolean("titles.departure.enabled", true)) {
            showDepartureInfo(currentStop, nextStop);
        }
        
        // 设置矿车初始速度
        initMinecartVelocity(currentStop.getLaunchYaw(), plugin.getCartSpeed() * 0.5);
        
        // 更新状态为站内移动
        currentState = TrainState.MOVING_IN_STATION;
        updateScoreboardBasedOnState();
    }    /**
     * 安排下一次发车
     */
    private void scheduleNextDeparture() {
        // 延迟发车 - 使用实体调度器以确保与矿车实体绑定
        SchedulerUtil.entityRun(plugin, minecart, (Runnable) () -> {
            // 确保玩家仍在矿车上
            if (isPassengerStillRiding()) {
                // 发车
                handleDeparture();
            } else {
                // 玩家已下车，移除矿车
                handlePassengerExit();
            }
        }, plugin.getCartDepartureDelay(), -1); // 转换为tick单位
    }    /**
     * 处理终点站逻辑
     */
    private void handleTerminalStation() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 3秒后自动下车，并移除矿车 - 使用实体调度器确保与矿车实体绑定
        SchedulerUtil.entityRun(plugin, minecart, (Runnable) () -> {
            if (minecart != null && !minecart.isDead()) {
                // 强制下车
                minecart.eject();
                
                // 清除玩家的地铁显示内容（包括计分板和title）
                ScoreboardManager.clearPlayerDisplay(passenger);
                
                // 延迟移除矿车 - 继续使用实体调度器
                final Minecart finalMinecart = minecart;
                SchedulerUtil.entityRun(plugin, finalMinecart, (Runnable) () -> {
                    if (finalMinecart != null && !finalMinecart.isDead()) {
                        finalMinecart.remove();
                    }
                }, 40L, -1); // 2秒后移除
            }
            // 取消当前任务
            cancel();
        }, 60L, -1); // 3秒后执行
    }
    
    /**
     * 查找附近最近的铁轨位置
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
        // 仅取消当前任务，其他工作（清除计分板、移除矿车）由VehicleListener完成
        cancel();
    }
    
    /**
     * 显示到站信息
     */
    private void showArriveStopInfo(Stop stop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 获取下一站信息
        String nextStopId = line.getNextStopId(stop.getId());
        Stop nextStop = null;
        if (nextStopId != null) {
            nextStop = plugin.getStopManager().getStop(nextStopId);
        }
        
        // 获取终点站
        List<String> stopIds = line.getOrderedStopIds();
        Stop terminusStop = null;
        if (!stopIds.isEmpty()) {
            String terminusId = stopIds.get(stopIds.size() - 1);
            terminusStop = plugin.getStopManager().getStop(terminusId);
        }
        
        // 设置淡入淡出时间
        int fadeIn = plugin.getArriveStopFadeIn();
        int stay = plugin.getArriveStopStay();
        int fadeOut = plugin.getArriveStopFadeOut();
        
        // 显示信息
        showStopInfo("arrive_stop", stop, null, nextStop, terminusStop, fadeIn, stay, fadeOut, false, null);
    }
    
    /**
     * 显示终点站信息
     */
    private void showTerminalStopInfo(Stop stop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 设置淡入淡出时间
        int fadeIn = plugin.getConfig().getInt("titles.terminal_stop.fade_in", 10);
        int stay = plugin.getConfig().getInt("titles.terminal_stop.stay", 60);
        int fadeOut = plugin.getConfig().getInt("titles.terminal_stop.fade_out", 10);
        
        // 显示信息
        showStopInfo("terminal_stop", stop, null, null, stop, fadeIn, stay, fadeOut, true, stop.getId());
    }
    
    /**
     * 显示行程信息
     */
    private void showDepartureInfo(Stop currentStop, Stop nextStop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 获取终点站信息
        List<String> stopIds = line.getOrderedStopIds();
        String terminusId = stopIds.isEmpty() ? null : stopIds.get(stopIds.size() - 1);
        Stop terminusStop = terminusId != null ? plugin.getStopManager().getStop(terminusId) : null;
        
        // 设置淡入淡出时间
        int fadeIn = plugin.getConfig().getInt("titles.departure.fade_in", 5);
        int stay = plugin.getConfig().getInt("titles.departure.stay", 40);
        int fadeOut = plugin.getConfig().getInt("titles.departure.fade_out", 5);
        
        // 检查是否包含倒计时标记
        String actionbarTemplateBase = plugin.getConfig().getString("titles.departure.actionbar", "列车已启动，请扶好站稳，注意安全");
        
        // 检查站点是否有自定义departure配置的actionbar
        Map<String, String> customTitle = currentStop.getCustomTitle("departure");
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplateBase = customTitle.get("actionbar");
        }
        
        // 取得最终的actionbar模板
        final String actionbarTemplate = actionbarTemplateBase;
        
        // 如果包含倒计时占位符，启动倒计时
        if (actionbarTemplate.contains("{countdown}")) {
            startCountdownActionbar("departure", currentStop, currentStop, nextStop, terminusStop);
        }
        
        // 显示标题信息
        showStopInfo("departure", currentStop, currentStop, nextStop, terminusStop, fadeIn, stay, fadeOut, false, null);
    }
    
    /**
     * 显示倒计时ActionBar
     * 
     * @param infoType 信息类型
     * @param mainStop 主要站点
     * @param prevStop 上一站
     * @param nextStop 下一站
     * @param terminusStop 终点站
     */
    private void startCountdownActionbar(String infoType, Stop mainStop, Stop prevStop, Stop nextStop, Stop terminusStop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 获取ActionBar模板
        String actionbarTemplateBase = plugin.getConfig().getString("titles." + infoType + ".actionbar", "");
        
        // 检查站点是否有自定义配置
        Map<String, String> customTitle = mainStop.getCustomTitle(infoType);
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplateBase = customTitle.get("actionbar");
        }
        
        // 获取最终的actionbar模板
        final String actionbarTemplate = actionbarTemplateBase;
        
        // 获取LineManager
        LineManager lineManager = plugin.getLineManager();
        
        // 获取总倒计时秒数（从tick转为秒）
        int totalSeconds = (int) Math.ceil(plugin.getCartDepartureDelay() / 20.0);
        
        // 创建倒计时任务
        for (int i = totalSeconds; i >= 0; i--) {
            final int secondsLeft = i;
            
            // 计算延迟时间（tick）
            long delayTicks = (totalSeconds - i) * 20L;
            
            SchedulerUtil.globalRun(plugin, () -> {
                // 确保玩家仍在矿车上
                if (passenger == null || !passenger.isOnline() || passenger.getVehicle() != minecart) {
                    return;
                }
                
                // 替换倒计时占位符
                String actionbarText = actionbarTemplate.replace("{countdown}", String.valueOf(secondsLeft));
                
                // 替换其他占位符
                actionbarText = TextUtil.replacePlaceholders(actionbarText, line, mainStop, prevStop, nextStop, terminusStop, lineManager);
                
                // 转换颜色代码
                actionbarText = ChatColor.translateAlternateColorCodes('&', actionbarText);
                
                // 显示ActionBar信息
                passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarText));
            }, delayTicks, -1); // -1表示不重复执行
        }
    }
    
    /**
     * 显示车站信息的通用方法
     * 
     * @param infoType 信息类型，可以是 "arrive_stop", "terminal_stop", "departure" 等
     * @param mainStop 主要站点（到站信息时是当前站，发车信息时是起始站）
     * @param prevStop 上一站，可为null
     * @param nextStop 下一站，可为null
     * @param terminusStop 终点站，可为null
     * @param fadeIn 淡入时间
     * @param stay 停留时间
     * @param fadeOut 淡出时间
     * @param updateTerminalScoreboard 是否更新终点站计分板
     * @param scoreboardStopId 计分板显示的站点ID，仅当updateTerminalScoreboard为true时使用
     */
    private void showStopInfo(String infoType, Stop mainStop, Stop prevStop, Stop nextStop, Stop terminusStop,
                            int fadeIn, int stay, int fadeOut, boolean updateTerminalScoreboard, String scoreboardStopId) {
        if (passenger == null || !passenger.isOnline() || mainStop == null) {
            return;
        }
        
        // 获取LineManager以便访问换乘信息
        LineManager lineManager = plugin.getLineManager();
        
        // 获取Title和Subtitle模板
        String titleTemplate = plugin.getConfig().getString("titles." + infoType + ".title", "");
        String subtitleTemplate = plugin.getConfig().getString("titles." + infoType + ".subtitle", "");
        String actionbarTemplate = plugin.getConfig().getString("titles." + infoType + ".actionbar", "");
        
        // 检查站点是否有自定义title配置
        Map<String, String> customTitle = mainStop.getCustomTitle(infoType);
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                titleTemplate = customTitle.get("title");
            }
            
            if (customTitle.containsKey("subtitle")) {
                subtitleTemplate = customTitle.get("subtitle");
            }
            
            if (customTitle.containsKey("actionbar")) {
                actionbarTemplate = customTitle.get("actionbar");
            }
        }
        
        // 使用TextUtil替换占位符
        String title = TextUtil.replacePlaceholders(titleTemplate, line, mainStop, prevStop, nextStop, terminusStop, lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, mainStop, prevStop, nextStop, terminusStop, lineManager);
        String actionbar = TextUtil.replacePlaceholders(actionbarTemplate, line, mainStop, prevStop, nextStop, terminusStop, lineManager);
        
        // 转换颜色代码
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        actionbar = ChatColor.translateAlternateColorCodes('&', actionbar);
        
        // 向玩家显示Title
        passenger.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        
        // 显示ActionBar信息
        passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbar));
        
        // 更新终点站计分板（如果需要）
        if (updateTerminalScoreboard) {
            ScoreboardManager.updateTerminalScoreboard(passenger, line, scoreboardStopId);
        }
    }
    
    /**
     * 播放到站音乐
     */
    private void playArrivalSound() {
        if (plugin.isArrivalSoundEnabled() && !plugin.getArrivalNotes().isEmpty() && passenger != null) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getArrivalNotes(), plugin.getArrivalInitialDelay());
        }
    }
    
    /**
     * 播放发车音乐
     */
    private void playDepartureSound() {
        if (plugin.isDepartureSoundEnabled() && !plugin.getDepartureNotes().isEmpty() && passenger != null) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getDepartureNotes(), plugin.getDepartureInitialDelay());
        }
    }
    
    /**
     * 显示等待发车信息
     */
    private void showWaitingInfo() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 如果waiting配置未启用，直接返回
        if (!plugin.getConfig().getBoolean("titles.waiting.enabled", true)) {
            return;
        }
        
        // 获取当前站点信息
        StopManager stopManager = plugin.getStopManager();
        Stop currentStop = stopManager.getStop(currentStopId);
        
        // 获取下一站信息
        String nextStopId = line.getNextStopId(currentStopId);
        Stop nextStop = null;
        if (nextStopId != null) {
            nextStop = stopManager.getStop(nextStopId);
        }
        
        // 获取终点站信息
        List<String> stopIds = line.getOrderedStopIds();
        String terminusId = stopIds.isEmpty() ? null : stopIds.get(stopIds.size() - 1);
        Stop terminusStop = terminusId != null ? stopManager.getStop(terminusId) : null;
        
        // 获取LineManager
        LineManager lineManager = plugin.getLineManager();
        
        // 获取标题模板
        String titleTemplate = plugin.getConfig().getString("titles.waiting.title", "列车即将发车");
        String subtitleTemplate = plugin.getConfig().getString("titles.waiting.subtitle", "当前站点: &a{stop_name} | 下一站: &e{next_stop_name}");
        
        // 检查站点是否有自定义waiting配置的标题
        Map<String, String> customTitle = currentStop.getCustomTitle("waiting");
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                titleTemplate = customTitle.get("title");
            }
            
            if (customTitle.containsKey("subtitle")) {
                subtitleTemplate = customTitle.get("subtitle");
            }
        }
        
        // 替换标题占位符
        String title = TextUtil.replacePlaceholders(titleTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
        
        // 转换颜色代码
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        
        // 显示标题（使用0作为淡入淡出时间，实现立即显示）
        passenger.sendTitle(title, subtitle, 0, 1000000, 0);
        
        // 获取ActionBar模板
        String actionbarTemplateBase = plugin.getConfig().getString("titles.waiting.actionbar", "列车将在 &c{countdown} &f秒后发车");
        
        // 检查站点是否有自定义waiting配置的actionbar
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplateBase = customTitle.get("actionbar");
        }
        
        // 取得最终的actionbar模板
        final String actionbarTemplate = actionbarTemplateBase;
        
        // 如果包含倒计时占位符，启动倒计时
        if (actionbarTemplate.contains("{countdown}")) {
            startCountdownActionbar("waiting", currentStop, null, nextStop, terminusStop);
        } else {
            // 如果没有倒计时，显示静态actionbar
            String actionbarText = TextUtil.replacePlaceholders(actionbarTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
            actionbarText = ChatColor.translateAlternateColorCodes('&', actionbarText);
            passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarText));
        }
    }
    
    /**
     * 开始循环播放等待音乐
     */
    private void startWaitingSound() {
        if (!plugin.isWaitingSoundEnabled() || plugin.getWaitingNotes().isEmpty() || passenger == null) {
            return;
        }
        
        // 初始延迟后播放第一次
        SchedulerUtil.globalRun(plugin, () -> {
            playWaitingSoundOnce();
        }, plugin.getWaitingInitialDelay(), -1); // -1表示不重复执行
        
        // 获取播放间隔
        int interval = plugin.getWaitingSoundInterval();
        
        // 计算总共需要播放的次数（根据发车延迟和播放间隔）
        int repeatTimes = (int) Math.ceil(plugin.getCartDepartureDelay() / interval);
        
        // 创建循环播放任务
        for (int i = 1; i <= repeatTimes; i++) {
            SchedulerUtil.globalRun(plugin, () -> {
                // 确保玩家仍在矿车上且还在等待发车
                if (passenger != null && passenger.isOnline() && passenger.getVehicle() == minecart 
                        && currentState == TrainState.STOPPED_AT_STATION) {
                    playWaitingSoundOnce();
                }
            }, plugin.getWaitingInitialDelay() + (interval * i), -1); // -1表示不重复执行
        }
    }
    
    /**
     * 播放一次等待音乐
     */
    private void playWaitingSoundOnce() {
        if (passenger != null && passenger.isOnline()) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getWaitingNotes(), 0);
        }
    }
    
    /**
     * 静态方法，用于创建一个新的列车等待任务
     * 该方法会创建一个任务并执行handleArrivalAtStation方法，使其在站点等待然后发车
     * 
     * @param plugin 插件实例
     * @param minecart 矿车实体
     * @param passenger 乘客
     * @param lineId 线路ID
     * @param currentStopId 当前站点ID
     */    public static void startTrainTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String currentStopId) {
        // 获取线路
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            plugin.getLogger().warning("无法启动列车，线路不存在: " + lineId);
            return;
        }
        
        // 确保玩家仍在矿车上
        if (passenger == null || !passenger.isOnline() || passenger.getVehicle() != minecart) {
            // 玩家不在矿车上，移除矿车
            if (minecart.isValid()) {
                minecart.remove();
            }
            return;
        }
        
        // 创建一个新的TrainMovementTask，初始状态为停站状态
        TrainMovementTask trainTask = new TrainMovementTask(plugin, minecart, passenger, lineId, currentStopId);
        // 使用实体调度器来支持 Folia
        Object taskId = SchedulerUtil.entityRun(plugin, minecart, trainTask, 1L, 1L); // 立即开始，每tick运行一次
        trainTask.setTaskId(taskId);
        
        // 设置任务的目标站点为下一站
        String nextStopId = line.getNextStopId(currentStopId);
        trainTask.targetStopId = nextStopId;
        
        // 执行到站处理，这会显示等待信息并设置延迟发车
        // 传递true表示矿车刚刚生成，避免把目的地设置为当前站
        trainTask.handleArrivalAtStation(true);
    }
    
    private void initMinecartVelocity(float yaw, double speed) {
        Location location = minecart.getLocation();
        Block block = location.getBlock();
        BlockData blockData = block.getBlockData();

        // 检查方块是否为动力铁轨并且可以被充能
        if (block.getType() == Material.POWERED_RAIL && blockData instanceof Powerable) {
            Powerable powerable = (Powerable) blockData;
            if (powerable.isPowered()) {
                // 在激活的动力铁轨上设置更高的最大速度
                // minecart.setMaxSpeed(speed);
                // 设置实际速度
                Vector direction = new Vector(
                    -Math.sin(Math.toRadians(yaw)), 
                    0, 
                    Math.cos(Math.toRadians(yaw))
                );
                // minecart.setVelocity(direction.multiply(speed));
                minecart.setVelocity(direction.multiply(0.4)); // 设置原版初始速度
            }
        }
    }
} 