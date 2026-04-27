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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro. manager.LineManager;
import org.cubexmc.metro. manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * 负责控制单个矿车从一个停靠区直接移动到下一个停靠区
 * (Phase 2 重构：从 Runnable 轮询改为基于 Listener 事件驱动)
 */
public class TrainMovementTask implements Listener {

    // 列车状态枚举
    public enum TrainState {
        STOPPED_AT_STATION, // 车停靠在stoppoint
        MOVING_IN_STATION, // 车在行驶，在站台区域内
        MOVING_BETWEEN_STATIONS // 车在行驶，不在站台区域内
    }

    // 全局管理活动的任务，便于传送门等系统在替换矿车实体时进行交接
    private static final java.util.Map<java.util.UUID, TrainMovementTask> activeTasks = new java.util.concurrent.ConcurrentHashMap<>();

    private final Metro plugin;
    private Minecart minecart;
    private final Player passenger;
    private final Line line;
    private String currentStopId;
    private String targetStopId;
    private TrainState currentState;
    private boolean isTeleporting = false; // 用于告知任务当前正在经过传送门，切勿取消任务
    private Vector lastTravelDirection;
    private Object movementAssistTaskId;

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
     * 取消任务及注销监听器
     */
    public void cancel() {
        if (minecart != null) {
            activeTasks.remove(minecart.getUniqueId());
        }
        stopMovementAssist();
        HandlerList.unregisterAll(this);
        debugTrain("Task cancelled for passenger=" + safePassengerName() + ", currentStop=" + currentStopId
                + ", targetStop=" + targetStopId);
    }

    /**
     * 辅助方法：供 PortalManager 等系统在不得不销毁旧矿车并创建新矿车时，将该任务的所有权和监听转移给新矿车
     */
    public void transferMinecart(Minecart newCart) {
        if (this.minecart != null) {
            activeTasks.remove(this.minecart.getUniqueId());
        }
        this.minecart = newCart;
        this.isTeleporting = false; // 传送门交接完毕，解除传送标记
        activeTasks.put(newCart.getUniqueId(), this);
        if (currentState == TrainState.MOVING_BETWEEN_STATIONS) {
            startMovementAssist();
        }
        debugTrain("Transferred movement task to new minecart UUID=" + newCart.getUniqueId());
    }

    public void setTeleporting(boolean teleporting) {
        this.isTeleporting = teleporting;
    }

    public static TrainMovementTask getTaskFor(Minecart cart) {
        return activeTasks.get(cart.getUniqueId());
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTrainEnterStop(TrainEnterStopEvent event) {
        // 确保是我们在控制的矿车
        if (!event.getMinecart().equals(this.minecart)) {
            return;
        }

        // 检查玩家是否仍在矿车中 (除非正在被传送)
        if (!isTeleporting && !isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }

        Stop enteredStop = event.getStop();
        
        // 只有当进入的站点是我们的目标站点时，才处理进站和停车逻辑
        if (targetStopId != null && targetStopId.equals(enteredStop.getId())) {
             // 进站触发事件，变更状态为站内移动
             transitionToMovingInStation(enteredStop);
             // 我们在这个版本中移除了“立刻停稳”的硬代码，
             // 让 onVehicleMove 负责后续的减速和停车捕捉
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!event.getVehicle().equals(this.minecart)) {
            return;
        }
        if (line != null) {
            plugin.getRouteRecorder().sample(line.getId(), minecart, event.getTo());
        }
        updateLastTravelDirection(event.getFrom(), event.getTo());

        if (currentState == TrainState.MOVING_IN_STATION) {
            Stop targetStop = plugin.getStopManager().getStop(targetStopId);
            if (targetStop == null || targetStop.getStopPointLocation() == null) {
                return;
            }

            Location currentLocation = minecart.getLocation();
            Location targetLocation = targetStop.getStopPointLocation();

            if (!currentLocation.getWorld().equals(targetLocation.getWorld())) {
                return;
            }

            double distance = currentLocation.distance(targetLocation);

            // 到达停车点 (距离 < 0.8)
            if (distance < 0.8) {
                transitionToStoppedAtStation(targetStop);
                return;
            }

            // 计算减速曲线 (缩放距离，站台15格内非线性减速)
            double defaultMaxSpeed = plugin.getConfigFacade().getCartSpeed();
            double minSpeed = 0.1; // 最低速度，避免停在半路
            double speedRatio = Math.min(1.0, distance / 15.0);
            double targetSpeed = minSpeed + (defaultMaxSpeed - minSpeed) * Math.pow(speedRatio, 0.7);

            // 拦截并覆盖 VehicleListener(NORMAL) 计算出的速度
            // 如果 VehicleListener 把速度设为0(比如冻结中)，我们就不拉高它
            double currentMaxSpeed = minecart.getMaxSpeed();
            if (currentMaxSpeed > 0.0) {
                minecart.setMaxSpeed(Math.min(currentMaxSpeed, targetSpeed));
            }
        }
    }

    /**
     * 转换到停站状态
     */
    private void transitionToStoppedAtStation(Stop stop) {
        // Vector Snapping: 强制绝对居中并完全停止动能
        minecart.setVelocity(new Vector(0, 0, 0));
        minecart.setMaxSpeed(0); // 防止等待期间玩家手操移动
        stopMovementAssist();
        Location snapLocation = stop.getStopPointLocation().clone();
        snapLocation.setX(snapLocation.getBlockX() + 0.5);
        snapLocation.setZ(snapLocation.getBlockZ() + 0.5);
        if (line != null) {
            plugin.getRouteRecorder().sample(line.getId(), minecart, snapLocation);
        }
        SchedulerUtil.teleportEntity(minecart, snapLocation);

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
        stopMovementAssist();

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
        TrainState previousState = currentState;
        currentState = TrainState.MOVING_BETWEEN_STATIONS;
        debugTrain("State transition " + previousState + " -> " + currentState + " for passenger=" + safePassengerName()
                + ", currentStop=" + currentStopId + ", targetStop=" + targetStopId);

        if (passenger != null && passenger.isOnline()) {
            passenger.sendTitle("", "", 0, 0, 0); // 清除等待显示的Title和Actionbar
        }
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
            org.bukkit.Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(minecart, passenger, line, currentStop, true,
                    MetroTrainArrivalEvent.ArrivalType.DOCKED));
        } else {
            Stop currentStop = plugin.getStopManager().getStop(currentStopId);
            org.bukkit.Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(minecart, passenger, line, currentStop,
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
        if (line != null) {
            plugin.getRouteRecorder().sample(line.getId(), minecart, currentStop.getStopPointLocation());
        }

        // 触发发车事件
        org.bukkit.Bukkit.getPluginManager()
                .callEvent(new MetroTrainDepartureEvent(minecart, passenger, line, currentStop, nextStop));

        // 恢复默认最大速度（防止矿车卡在上一站设置的maxSpeed=0）
        double max_speed = line.getMaxSpeed();
        if (max_speed == -1.0)
            max_speed = plugin.getConfigFacade().getCartSpeed();
        minecart.setMaxSpeed(max_speed);

        // 设置矿车初始速度
        initMinecartVelocity(currentStop.getLaunchYaw(), plugin.getConfigFacade().getCartSpeed() * 0.5);

        // 更新状态为站间行驶 (取代站内移动)
        transitionToMovingBetweenStations();
        startMovementAssist();
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
        org.cubexmc.metro.manager.RouteRecorder.FinishResult routeResult =
                plugin.getRouteRecorder().finishIfRecording(line.getId(), minecart);
        if (routeResult.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS) {
            plugin.getLogger().warning("[RouteRecorder] Route recording for line " + line.getId()
                    + " reached the terminal but only collected " + routeResult.pointCount() + " point(s).");
        }

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
        // 确立为由事件驱动的架构，直接注册自己
        org.bukkit.Bukkit.getPluginManager().registerEvents(trainTask, plugin);
        activeTasks.put(minecart.getUniqueId(), trainTask);

        // 设置任务的目标站点为下一站
        String nextStopId = line.getNextStopId(currentStopId);
        trainTask.targetStopId = nextStopId;

        // 生成初始立刻锁定速度，防止在发车前被动力铁轨推出
        minecart.setMaxSpeed(0);
        minecart.setVelocity(new Vector(0, 0, 0));

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
                // Bukkit yaw: 0=south(+Z), 90=west(-X), 180=north(-Z), -90=east(+X)
                // 移除之前错误的 +180 修正，改回原版不修正的方向
                double launchRad = Math.toRadians(yaw);
                Vector direction = new Vector(
                        -Math.sin(launchRad),
                        0,
                        Math.cos(launchRad));
                lastTravelDirection = direction.clone().normalize();
                // minecart.setVelocity(direction.multiply(speed));
                minecart.setVelocity(direction.multiply(0.4)); // 设置原版初始速度
            }
        }
    }

    private void startMovementAssist() {
        stopMovementAssist();
        if (!plugin.getConfigFacade().isSafeModeMovementAssist() || minecart == null) {
            return;
        }
        long interval = Math.max(1L, plugin.getConfigFacade().getSafeModeStallRecoveryTicks());
        movementAssistTaskId = SchedulerUtil.entityRun(plugin, minecart, this::recoverStalledMinecart, interval, interval);
    }

    private void stopMovementAssist() {
        SchedulerUtil.cancelTask(movementAssistTaskId);
        movementAssistTaskId = null;
    }

    private void recoverStalledMinecart() {
        if (!plugin.getConfigFacade().isSafeModeMovementAssist()) {
            stopMovementAssist();
            return;
        }
        if (minecart == null || minecart.isDead() || !minecart.isValid()) {
            cancel();
            return;
        }
        if (currentState != TrainState.MOVING_BETWEEN_STATIONS || minecart.getMaxSpeed() <= 0.0) {
            return;
        }
        if (!isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }
        if (!LocationUtil.isOnRail(minecart.getLocation()) || lastTravelDirection == null) {
            return;
        }

        Vector velocity = minecart.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        double minCruiseSpeed = Math.max(0.01, plugin.getConfigFacade().getSafeModeMinCruiseSpeed());
        if (horizontalSpeed >= minCruiseSpeed) {
            return;
        }

        double targetSpeed = resolveAssistSpeed(minCruiseSpeed);
        Vector assistedVelocity = lastTravelDirection.clone().normalize().multiply(targetSpeed);
        minecart.setVelocity(assistedVelocity);
    }

    private double resolveAssistSpeed(double minCruiseSpeed) {
        double configuredSpeed = plugin.getConfigFacade().getCartSpeed();
        double maxSpeed = minecart.getMaxSpeed();
        double targetSpeed = configuredSpeed > 0.0 ? Math.min(configuredSpeed, maxSpeed) : maxSpeed;
        return Math.max(minCruiseSpeed, targetSpeed);
    }

    private void updateLastTravelDirection(Location from, Location to) {
        if (currentState == TrainState.STOPPED_AT_STATION || from == null || to == null) {
            return;
        }
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector direction = to.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        lastTravelDirection = direction.normalize();
    }
}
