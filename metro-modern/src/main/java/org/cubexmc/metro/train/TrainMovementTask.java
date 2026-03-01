package org.cubexmc.metro.train;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * 负责控制单个矿车从一个停靠区直接移动到下一个停靠区
 * 同时负责所有与乘客显示相关的逻辑
 */
public class TrainMovementTask implements Runnable {

    // 列车状态枚举
    public enum TrainState {
        STOPPED_AT_STATION, // 车停靠在stoppoint
        MOVING_IN_STATION, // 车在行驶，在站台区域内
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
     * @param plugin     插件实例
     * @param minecart   要控制的矿车实体
     * @param passenger  乘坐的玩家
     * @param lineId     线路ID
     * @param fromStopId 起始停靠区ID
     */
    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId) {
        this(plugin, minecart, passenger, lineId, fromStopId, TrainState.STOPPED_AT_STATION);
    }

    /**
     * 创建一个新的列车移动任务
     * 
     * @param plugin       插件实例
     * @param minecart     要控制的矿车实体
     * @param passenger    乘坐的玩家
     * @param lineId       线路ID
     * @param fromStopId   起始停靠区ID
     * @param initialState 初始状态
     */
    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId,
            TrainState initialState) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;

        LineManager lineManager = plugin.getLineManager();
        this.line = lineManager.getLine(lineId);
        this.currentStopId = fromStopId;
        if (this.line == null) {
            this.targetStopId = null;
            this.currentState = TrainState.STOPPED_AT_STATION;
            return;
        }
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
        debugTrain("Task cancelled for passenger=" + safePassengerName() + ", currentStop=" + currentStopId
                + ", targetStop=" + targetStopId);
    }

    private void debugTrain(String message) {
        plugin.debug("train_state_transitions", message);
    }

    private String safePassengerName() {
        return passenger == null ? "unknown" : passenger.getName();
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
                    plugin.getScoreboardManager().updateTerminalScoreboard(passenger, line, currentStopId);
                } else {
                    // 普通站点
                    plugin.getScoreboardManager().updateEnteringStopScoreboard(passenger, line, currentStopId);
                }
                break;
            case MOVING_IN_STATION:
                plugin.getScoreboardManager().updateTravelingScoreboard(passenger, line, targetStopId);
                break;
            case MOVING_BETWEEN_STATIONS:
                // No scoreboard update for MOVING_BETWEEN_STATIONS in this method,
                // as it's handled by updateTravelingScoreboard in MOVING_IN_STATION
                // or when transitioning to MOVING_BETWEEN_STATIONS.
                break;
        }
    }

    @Override
    public void run() {
        // 如果矿车不存在或没有目标，取消任务
        if (minecart == null || minecart.isDead()
                || targetStopId == null && currentState != TrainState.STOPPED_AT_STATION) {
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
                minecart.setVelocity(new Vector(0, 0, 0));
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
     * 维护矿车基本属性（扩展点保留，当前不执行任何操作）
     */
    private void maintainMinecartProperties() {
        // 速度补偿已移除：弯道减速由玩家自行控制，无法统一处理
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
        debugTrain("State transition " + previousState + " -> " + currentState + " for passenger=" + safePassengerName()
                + ", currentStop=" + currentStopId + ", targetStop=" + targetStopId);

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
        debugTrain("State transition " + previousState + " -> " + currentState + " for passenger=" + safePassengerName()
                + ", targetStop=" + (targetStop == null ? "null" : targetStop.getId()));

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
        TrainState previousState = currentState;
        currentState = TrainState.MOVING_BETWEEN_STATIONS;
        debugTrain("State transition " + previousState + " -> " + currentState + " for passenger=" + safePassengerName()
                + ", currentStop=" + currentStopId + ", targetStop=" + targetStopId);

        // 离开站台区域后提速到全速
        // updateMinecartVelocity(plugin.getConfigFacade().getCartSpeed());

        // 更新计分板
        updateScoreboardBasedOnState();
    }

    private void handleEnteringStation(Stop targetStop) {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }

        // 检查是否为终点站
        String nextStopId = line.getNextStopId(targetStopId);
        boolean isTerminus = (nextStopId == null);

        // 触发进站事件
        Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(minecart, passenger, line, targetStop,
                isTerminus, MetroTrainArrivalEvent.ArrivalType.ENTERING));
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
        // 当矿车不是刚刚生成时，才更新当前站点ID
        if (!isNewlySpawned) {
            // 当前站点ID更新为目标站点ID
            currentStopId = targetStopId;
        }

        if (line.getNextStopId(currentStopId) == null) {
            // 终点站特殊处理
            handleTerminalStation();
            targetStopId = null;

            Stop currentStop = plugin.getStopManager().getStop(currentStopId);
            Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(minecart, passenger, line, currentStop, true,
                    MetroTrainArrivalEvent.ArrivalType.DOCKED));
        } else {
            Stop currentStop = plugin.getStopManager().getStop(currentStopId);
            Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(minecart, passenger, line, currentStop,
                    false, MetroTrainArrivalEvent.ArrivalType.DOCKED));

            // 更新计分板（上车后首次显示 / 到站后刷新）
            updateScoreboardBasedOnState();

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

        // 如果没有下一个停靠区，则为终点站
        if (targetStopId == null) {
            return;
        }

        // 获取站点信息
        StopManager stopManager = plugin.getStopManager();
        Stop currentStop = stopManager.getStop(currentStopId);
        Stop nextStop = stopManager.getStop(targetStopId);

        if (currentStop == null || nextStop == null) {
            return;
        }

        // 触发发车事件
        Bukkit.getPluginManager()
                .callEvent(new MetroTrainDepartureEvent(minecart, passenger, line, currentStop, nextStop));

        // 设置矿车初始速度
        initMinecartVelocity(currentStop.getLaunchYaw(), plugin.getConfigFacade().getCartSpeed() * 0.5);

        // 更新状态为站内移动
        currentState = TrainState.MOVING_IN_STATION;
        updateScoreboardBasedOnState();
    }

    /**
     * 安排下一次发车
     */
    private void scheduleNextDeparture() {
        // 延迟发车 - 使用实体调度器以确保与矿车实体绑定
        debugTrain("Schedule departure in " + plugin.getConfigFacade().getCartDepartureDelay() + " ticks for passenger="
                + safePassengerName() + ", currentStop=" + currentStopId);
        SchedulerUtil.entityRun(plugin, minecart, (Runnable) () -> {
            // 确保玩家仍在矿车上
            if (isPassengerStillRiding()) {
                // 发车
                handleDeparture();
            } else {
                // 玩家已下车，移除矿车
                handlePassengerExit();
            }
        }, plugin.getConfigFacade().getCartDepartureDelay(), -1); // 转换为tick单位
    }

    /**
     * 处理终点站逻辑
     */
    private void handleTerminalStation() {
        if (passenger == null || !passenger.isOnline()) {
            return;
        }
        debugTrain("Terminal station reached for passenger=" + safePassengerName() + ", stop=" + currentStopId);

        // 3秒后自动下车，并移除矿车 - 使用实体调度器确保与矿车实体绑定
        SchedulerUtil.entityRun(plugin, minecart, (Runnable) () -> {
            if (minecart != null && !minecart.isDead()) {
                // 强制下车
                minecart.eject();

                // 清除玩家的地铁显示内容（包括计分板和title）
                plugin.getScoreboardManager().clearPlayerDisplay(passenger);

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
     * 静态方法，用于创建一个新的列车等待任务
     * 该方法会创建一个任务并执行handleArrivalAtStation方法，使其在站点等待然后发车
     * 
     * @param plugin        插件实例
     * @param minecart      矿车实体
     * @param passenger     乘客
     * @param lineId        线路ID
     * @param currentStopId 当前站点ID
     */
    public static void startTrainTask(Metro plugin, Minecart minecart, Player passenger, String lineId,
            String currentStopId) {
        // 获取线路
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        if (line == null) {
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
                // launchYaw is the direction the train should travel.
                // Bukkit yaw: 0=south(+Z), 90=west(-X), 180=north(-Z), -90=east(+X)
                // +180° inverts the direction so the cart moves away from the stop point,
                // correcting the previously reversed travel direction.
                double launchRad = Math.toRadians(yaw);
                Vector direction = new Vector(
                        -Math.sin(launchRad),
                        0,
                        Math.cos(launchRad));
                // minecart.setVelocity(direction.multiply(speed));
                minecart.setVelocity(direction.multiply(0.4)); // 设置原版初始速度
            }
        }
    }
}