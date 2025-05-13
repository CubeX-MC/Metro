package org.cubexmc.metro.train;

import org.bukkit.Location;
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
import org.cubexmc.metro.train.ScoreboardManager;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.List;
import java.util.Map;

/**
 * 负责控制单个矿车从一个停靠区直接移动到下一个停靠区
 */
public class TrainMovementTask implements Runnable {
    
    private final Metro plugin;
    private final Minecart minecart;
    private final Player passenger;
    private final Line line;
    private final String currentStopId;
    protected String targetStopId;
    protected boolean isMoving;
    private boolean hasNotifiedArrival = false; // 添加标记，表示是否已通知到站
    private Object taskId; // 保存任务ID，可以是BukkitTask或Folia的ScheduledTask
    
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
        this(plugin, minecart, passenger, lineId, fromStopId, true);
    }
    
    /**
     * 创建一个新的列车移动任务
     * 
     * @param plugin 插件实例
     * @param minecart 要控制的矿车实体
     * @param passenger 乘坐的玩家
     * @param lineId 线路ID
     * @param fromStopId 起始停靠区ID
     * @param isAtStop 是否为停站状态
     */
    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId, boolean isAtStop) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;
        this.hasNotifiedArrival = false; // 重置通知标记
        
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
        
        // 初始化计分板
        if (isAtStop) {
            // 在站点处于停车状态
            ScoreboardManager.updateEnteringStopScoreboard(passenger, line, currentStopId);
        } else {
            // 行驶中状态
            ScoreboardManager.updateTravelingScoreboard(passenger, line, targetStopId);
            
            // 显示行程开始信息 - 从config.yml中读取
            showJourneyStartInfo();
        }
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
        
        // 获取目标停靠区
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
        
        // 获取当前位置是否在停靠区区域内
        boolean isInStopArea = targetStop.isInStop(currentLocation);
        
        // 如果进入停靠区区域且尚未通知到站，则触发到站提示
        if (isInStopArea && isMoving && !hasNotifiedArrival) {
            // 仅触发音效和提示，但继续移动直到精确到达停车点
            notifyArrival();
            hasNotifiedArrival = true; // 标记已通知到站
            
            // 当进入站点区域时，更新计分板显示
            ScoreboardManager.updateEnteringStopScoreboard(passenger, line, targetStopId);
        }
        
        // 仅当非常接近停车点的精确位置时才真正停车（距离小于0.3格）
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
                    passenger.sendMessage(plugin.getLanguageManager().getMessage("passenger.train_derailed"));
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
        
        // 如果不在站点区域内，更新行驶中的计分板
        if (!isInStopArea) {
            ScoreboardManager.updateTravelingScoreboard(passenger, line, targetStopId);
        }
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
        // 仅取消当前任务，其他工作（清除计分板、移除矿车）由VehicleListener完成
        cancel();
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
        
        // 仅当尚未通知到站时才触发通知
        if (!hasNotifiedArrival) {
            notifyArrival();
            hasNotifiedArrival = true;
        }
        
        // 更新计分板显示当前站为目标站
        ScoreboardManager.updateEnteringStopScoreboard(passenger, line, targetStopId);
        
        // 添加调试日志
        if (passenger != null) {
            plugin.getLogger().info("矿车到达停靠点: " + targetStopId + ", 玩家: " + passenger.getName() + ", 5秒后检查是否自动发车");
        }
        
        // 5秒后自动前往下一站
        SchedulerUtil.runTaskLater(plugin, () -> {
            // 确保玩家仍在矿车上
            if (isPassengerStillRiding()) {
                plugin.getLogger().info("5秒后自动前往下一站: " + nextStopId);
                
                // 添加调试日志，显示目标站和下一站信息
                plugin.getLogger().info("当前站点ID: " + targetStopId + ", 下一站ID: " + nextStopId);
                
                // 播放发车音乐
                playDepartureSound();
                
                // 创建新的移动任务（从当前站到下一站）
                minecart.setVelocity(new Vector(0, 0, 0)); // 先确保矿车速度为0
                
                // 重要修改：直接使用当前站点ID和下一站ID，而不是依赖构造函数去查找
                TrainMovementTask newTask = new TrainMovementTaskForNextStop(plugin, minecart, passenger, line.getId(), targetStopId, nextStopId);
                Object newTaskId = SchedulerUtil.runTaskTimer(plugin, newTask, 1L, 1L);
                newTask.setTaskId(newTaskId);
                
                // 取消当前任务
                cancel();
            } else {
                // 玩家已下车，延迟移除矿车
                handlePassengerExit();
            }
        }, plugin.getCartDepartureDelay()); // 使用Metro类中的getter方法获取延迟值
    }
    
    /**
     * 通知玩家已到站
     */
    private void notifyArrival() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 播放到站音乐
        playArrivalSound();
        
        // 显示到站提示
        Stop stop = plugin.getStopManager().getStop(targetStopId);
        if (stop != null) {
            showArriveStopTitle(stop);
        }
    }
    
    /**
     * 显示到站Title
     */
    private void showArriveStopTitle(Stop stop) {
        // 获取Title和Subtitle模板
        String titleTemplate = plugin.getConfig().getString("titles.arrive_stop.title", "§b{stop_name} §f到了");
        String subtitleTemplate = plugin.getConfig().getString("titles.arrive_stop.subtitle", "开往 &d{terminus_name} &f方向 | 下一站: &e{next_stop_name} | 当前可换乘: &a{stop_transfers} | 下一站可换乘: &6{next_stop_transfers}");
        
        // 检查站点是否有自定义title配置
        Map<String, String> customTitle = stop.getCustomTitle("arrive_stop");
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                titleTemplate = customTitle.get("title");
            }
            
            if (customTitle.containsKey("subtitle")) {
                subtitleTemplate = customTitle.get("subtitle");
            }
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
        
        // 获取LineManager
        LineManager lineManager = plugin.getLineManager();
        
        // 添加调试日志
        if (lineManager == null) {
            plugin.getLogger().warning("showArriveStopTitle: LineManager为null，无法获取换乘信息");
        } else {
            // 检查当前站点的换乘信息
            List<String> transfers = stop.getTransferableLines();
            if (transfers == null || transfers.isEmpty()) {
                plugin.getLogger().info("当前站点 " + stop.getName() + " 没有换乘线路");
            } else {
                plugin.getLogger().info("当前站点 " + stop.getName() + " 的换乘线路: " + String.join(", ", transfers));
            }
            
            // 检查下一站的换乘信息
            if (nextStop != null) {
                List<String> nextTransfers = nextStop.getTransferableLines();
                if (nextTransfers == null || nextTransfers.isEmpty()) {
                    plugin.getLogger().info("下一站 " + nextStop.getName() + " 没有换乘线路");
                } else {
                    plugin.getLogger().info("下一站 " + nextStop.getName() + " 的换乘线路: " + String.join(", ", nextTransfers));
                }
            }
        }
        
        // 使用TextUtil替换占位符，确保传递LineManager
        String title = TextUtil.replacePlaceholders(titleTemplate, line, stop, null, nextStop, terminusStop, lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, stop, null, nextStop, terminusStop, lineManager);
        
        // 检查占位符是否被替换
        if (subtitleTemplate.contains("{stop_transfers}") && subtitle.contains("{stop_transfers}")) {
            plugin.getLogger().warning("stop_transfers占位符未被替换，可能是换乘信息获取失败");
        }
        
        if (subtitleTemplate.contains("{next_stop_transfers}") && subtitle.contains("{next_stop_transfers}")) {
            plugin.getLogger().warning("next_stop_transfers占位符未被替换，可能是换乘信息获取失败");
        }
        
        // 设置淡入淡出时间
        int fadeIn = plugin.getArriveStopFadeIn();
        int stay = plugin.getArriveStopStay();
        int fadeOut = plugin.getArriveStopFadeOut();
        
        // 转换颜色代码
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        
        // 向玩家显示Title
        passenger.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
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
     * 处理终点站逻辑
     */
    private void handleTerminalStation() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 仅当尚未通知到站时才播放到站音乐
        if (!hasNotifiedArrival) {
            playArrivalSound();
            hasNotifiedArrival = true;
        }
        
        // 显示终点站Title
        if (plugin.isTerminalStopTitleEnabled()) {
            Stop stop = plugin.getStopManager().getStop(targetStopId);
            if (stop != null) {
                showTerminalStopTitle(stop);
                
                // 更新终点站计分板
                ScoreboardManager.updateTerminalScoreboard(passenger, line, targetStopId);
            }
        }
        
        // 3秒后自动下车，并移除矿车
        SchedulerUtil.runTaskLater(plugin, () -> {
            if (minecart != null && !minecart.isDead()) {
                // 强制下车
                minecart.eject();
                
                // 清除计分板
                ScoreboardManager.clearScoreboard(passenger);
                
                // 延迟移除矿车
                final Minecart finalMinecart = minecart;
                SchedulerUtil.runTaskLater(plugin, () -> {
                    if (finalMinecart != null && !finalMinecart.isDead()) {
                        finalMinecart.remove();
                    }
                }, 40L); // 2秒后移除
            }
            // 取消当前任务
            cancel();
        }, 60L); // 3秒后执行
    }
    
    /**
     * 显示终点站Title
     */
    private void showTerminalStopTitle(Stop stop) {
        // 获取Title和Subtitle模板
        String titleTemplate = plugin.getConfig().getString("titles.terminal_stop.title", "§c终点站");
        String subtitleTemplate = plugin.getConfig().getString("titles.terminal_stop.subtitle", "§6请下车 | 当前可换乘: &a{stop_transfers}");
        
        // 检查站点是否有自定义title配置
        Map<String, String> customTitle = stop.getCustomTitle("terminal_stop");
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                titleTemplate = customTitle.get("title");
            }
            
            if (customTitle.containsKey("subtitle")) {
                subtitleTemplate = customTitle.get("subtitle");
            }
        }
        
        // 使用TextUtil替换占位符
        LineManager lineManager = plugin.getLineManager();
        
        // 添加调试日志
        if (lineManager == null) {
            plugin.getLogger().warning("showTerminalStopTitle: LineManager为null，无法获取换乘信息");
        } else {
            // 检查当前站点的换乘信息
            List<String> transfers = stop.getTransferableLines();
            if (transfers == null || transfers.isEmpty()) {
                plugin.getLogger().info("终点站 " + stop.getName() + " 没有换乘线路");
            } else {
                plugin.getLogger().info("终点站 " + stop.getName() + " 的换乘线路: " + String.join(", ", transfers));
            }
        }
        
        String title = TextUtil.replacePlaceholders(titleTemplate, line, stop, null, null, stop, lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, stop, null, null, stop, lineManager);
        
        // 检查占位符是否被替换
        if (subtitleTemplate.contains("{stop_transfers}") && subtitle.contains("{stop_transfers}")) {
            plugin.getLogger().warning("终点站 stop_transfers 占位符未被替换，可能是换乘信息获取失败");
        }
        
        // 设置淡入淡出时间
        int fadeIn = plugin.getConfig().getInt("titles.terminal_stop.fade_in", 10);
        int stay = plugin.getConfig().getInt("titles.terminal_stop.stay", 60);
        int fadeOut = plugin.getConfig().getInt("titles.terminal_stop.fade_out", 10);
        
        // 转换颜色代码
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        
        // 向玩家显示Title
        passenger.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }
    
    /**
     * 显示行程开始信息
     */
    private void showJourneyStartInfo() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        
        // 检查配置中是否启用了乘客行程Title
        if (plugin.getConfig().getBoolean("titles.passenger_journey.enabled", true)) {
            // 获取必要的站点信息
            Stop currentStop = plugin.getStopManager().getStop(currentStopId);
            if (currentStop == null) {
                plugin.getLogger().warning("无法获取当前站信息: " + currentStopId);
                return;
            }
            
            Stop nextStop = plugin.getStopManager().getStop(targetStopId);
            if (nextStop == null) {
                plugin.getLogger().warning("无法获取下一站信息: " + targetStopId);
                return;
            }
            
            // 获取终点站信息
            List<String> stopIds = line.getOrderedStopIds();
            String terminusId = stopIds.isEmpty() ? null : stopIds.get(stopIds.size() - 1);
            Stop terminusStop = terminusId != null ? plugin.getStopManager().getStop(terminusId) : null;
            
            // 获取LineManager以便访问换乘信息
            LineManager lineManager = plugin.getLineManager();
            if (lineManager == null) {
                plugin.getLogger().warning("LineManager为null，无法获取可换乘线路信息");
                return;
            }
            
            // 获取Title模板
            String titleTemplate = plugin.getConfig().getString("titles.passenger_journey.title", "下一站 &e{next_stop_name}");
            String subtitleTemplate = plugin.getConfig().getString("titles.passenger_journey.subtitle", "开往 &d{terminus_name} &f方向 | 当前可换乘: &a{stop_transfers} | 下一站可换乘: &6{next_stop_transfers}");
            String actionbarTemplate = plugin.getConfig().getString("titles.passenger_journey.actionbar", "列车已启动，请扶好站稳，注意安全");
            
            // 检查当前站点是否有自定义passenger_journey配置
            Map<String, String> customTitle = currentStop.getCustomTitle("passenger_journey");
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
            
            // 添加调试日志，检查站点换乘信息
            List<String> currentTransfers = currentStop.getTransferableLines();
            if (currentTransfers == null || currentTransfers.isEmpty()) {
                plugin.getLogger().info("当前站 " + currentStop.getName() + " 没有换乘线路");
            } else {
                plugin.getLogger().info("当前站 " + currentStop.getName() + " 的换乘线路: " + String.join(", ", currentTransfers));
            }
            
            List<String> nextTransfers = nextStop.getTransferableLines();
            if (nextTransfers == null || nextTransfers.isEmpty()) {
                plugin.getLogger().info("下一站 " + nextStop.getName() + " 没有换乘线路");
            } else {
                plugin.getLogger().info("下一站 " + nextStop.getName() + " 的换乘线路: " + String.join(", ", nextTransfers));
            }
            
            // 替换标题占位符
            String title = TextUtil.replacePlaceholders(titleTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
            String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
            String actionbar = TextUtil.replacePlaceholders(actionbarTemplate, line, currentStop, null, nextStop, terminusStop, lineManager);
            
            // 检查占位符是否被替换
            if (subtitleTemplate.contains("{stop_transfers}") && subtitle.contains("{stop_transfers}")) {
                plugin.getLogger().warning("stop_transfers占位符未被替换，可能是换乘信息获取失败");
            }
            
            if (subtitleTemplate.contains("{next_stop_transfers}") && subtitle.contains("{next_stop_transfers}")) {
                plugin.getLogger().warning("next_stop_transfers占位符未被替换，可能是换乘信息获取失败");
            }
            
            // 设置淡入淡出时间
            int fadeIn = plugin.getConfig().getInt("titles.passenger_journey.fade_in", 5);
            int stay = plugin.getConfig().getInt("titles.passenger_journey.stay", 40);
            int fadeOut = plugin.getConfig().getInt("titles.passenger_journey.fade_out", 5);
            
            // 转换颜色代码
            title = ChatColor.translateAlternateColorCodes('&', title);
            subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
            actionbar = ChatColor.translateAlternateColorCodes('&', actionbar);
            
            // 向玩家显示Title
            passenger.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            
            // 显示ActionBar信息
            passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbar));
        }
    }
    
    /**
     * 专门用于发车到下一站的任务类，避免构造函数中的目标站点查找逻辑问题
     */
    private class TrainMovementTaskForNextStop extends TrainMovementTask {
        public TrainMovementTaskForNextStop(Metro plugin, Minecart minecart, Player passenger, 
                String lineId, String currentStopId, String nextStopId) {
            super(plugin, minecart, passenger, lineId, currentStopId);
            
            // 直接设置目标站为指定的下一站，而不是通过line.getNextStopId获取
            this.targetStopId = nextStopId;
            this.isMoving = true;
            
            // 更新计分板为行驶中状态
            ScoreboardManager.updateTravelingScoreboard(passenger, line, targetStopId);
            
            // 显示行程信息
            showJourneyStartInfo();
            
            // 记录调试信息
            plugin.getLogger().info("创建前往下一站的任务: 当前站=" + currentStopId + ", 目标站=" + nextStopId);
        }
    }
} 