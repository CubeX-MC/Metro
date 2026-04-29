package org.cubexmc.metro.train;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Controls one event-driven train ride from a stop to the next stop.
 */
public class TrainMovementTask implements Listener {

    public enum TrainState {
        STOPPED_AT_STATION,
        MOVING_IN_STATION,
        MOVING_BETWEEN_STATIONS
    }

    private static final Map<UUID, TrainMovementTask> activeTasks = new ConcurrentHashMap<>();

    private final TrainSession session;
    private final TrainStateMachine stateMachine;
    private final TrainScheduler trainScheduler;
    private final TrainPhysicsController physicsController;
    private Object movementAssistTaskId;

    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId) {
        this(plugin, minecart, passenger, lineId, fromStopId, TrainState.STOPPED_AT_STATION);
    }

    public TrainMovementTask(Metro plugin, Minecart minecart, Player passenger, String lineId, String fromStopId,
            TrainState initialState) {
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        this.session = new TrainSession(plugin, minecart, passenger, line, fromStopId, initialState);
        this.stateMachine = new TrainStateMachine(session);
        this.trainScheduler = new TrainScheduler(plugin);
        this.physicsController = new TrainPhysicsController();

        if (line != null && session.getTargetStopId() != null) {
            updateScoreboardBasedOnState();
        }
    }

    public void cancel() {
        Minecart minecart = session.getMinecart();
        if (minecart != null) {
            activeTasks.remove(minecart.getUniqueId());
        }
        stopMovementAssist();
        trainScheduler.cancelAll();
        HandlerList.unregisterAll(this);
        session.debug("Task cancelled for passenger=" + session.safePassengerName()
                + ", currentStop=" + session.getCurrentStopId()
                + ", targetStop=" + session.getTargetStopId());
    }

    public void transferMinecart(Minecart newCart) {
        Minecart previousCart = session.getMinecart();
        if (previousCart != null) {
            activeTasks.remove(previousCart.getUniqueId());
        }
        session.setMinecart(newCart);
        session.setTeleporting(false);
        activeTasks.put(newCart.getUniqueId(), this);
        if (session.getState() == TrainState.MOVING_BETWEEN_STATIONS) {
            startMovementAssist();
        }
        session.debug("Transferred movement task to new minecart UUID=" + newCart.getUniqueId());
    }

    public void setTeleporting(boolean teleporting) {
        session.setTeleporting(teleporting);
    }

    public static TrainMovementTask getTaskFor(Minecart cart) {
        return activeTasks.get(cart.getUniqueId());
    }

    TrainSession getSession() {
        return session;
    }

    private void updateScoreboardBasedOnState() {
        Player passenger = session.getPassenger();
        Line line = session.getLine();
        if (passenger == null || !passenger.isOnline() || line == null) {
            return;
        }

        switch (session.getState()) {
            case STOPPED_AT_STATION:
                if (session.getTargetStopId() == null) {
                    session.getPlugin().getScoreboardManager()
                            .updateTerminalScoreboard(passenger, line, session.getCurrentStopId());
                } else {
                    session.getPlugin().getScoreboardManager()
                            .updateEnteringStopScoreboard(passenger, line, session.getCurrentStopId());
                }
                break;
            case MOVING_IN_STATION:
                session.getPlugin().getScoreboardManager()
                        .updateTravelingScoreboard(passenger, line, session.getTargetStopId());
                break;
            case MOVING_BETWEEN_STATIONS:
                break;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTrainEnterStop(TrainEnterStopEvent event) {
        if (!event.getMinecart().equals(session.getMinecart())) {
            return;
        }

        if (!session.isTeleporting() && !session.isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }

        Stop enteredStop = event.getStop();
        if (enteredStop != null && session.getTargetStopId() != null
                && session.getTargetStopId().equals(enteredStop.getId())) {
            transitionToMovingInStation(enteredStop);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent event) {
        Minecart minecart = session.getMinecart();
        if (!event.getVehicle().equals(minecart)) {
            return;
        }

        Line line = session.getLine();
        if (line != null) {
            session.getPlugin().getRouteRecorder().sample(line.getId(), minecart, event.getTo());
        }
        updateLastTravelDirection(event.getFrom(), event.getTo());

        if (session.getState() != TrainState.MOVING_IN_STATION) {
            return;
        }

        Stop targetStop = session.getPlugin().getStopManager().getStop(session.getTargetStopId());
        if (targetStop == null || targetStop.getStopPointLocation() == null) {
            return;
        }

        Location currentLocation = minecart.getLocation();
        Location targetLocation = targetStop.getStopPointLocation();
        if (currentLocation.getWorld() == null || !currentLocation.getWorld().equals(targetLocation.getWorld())) {
            return;
        }

        double distance = currentLocation.distance(targetLocation);
        if (distance < 0.8) {
            transitionToStoppedAtStation(targetStop);
            return;
        }

        physicsController.applyApproachBraking(minecart, distance,
                session.getPlugin().getConfigFacade().getCartSpeed());
    }

    private void transitionToStoppedAtStation(Stop stop) {
        Minecart minecart = session.getMinecart();
        minecart.setVelocity(new Vector(0, 0, 0));
        minecart.setMaxSpeed(0);
        stopMovementAssist();

        Location snapLocation = stop.getStopPointLocation().clone();
        snapLocation.setX(snapLocation.getBlockX() + 0.5);
        snapLocation.setZ(snapLocation.getBlockZ() + 0.5);
        if (session.getLine() != null) {
            session.getPlugin().getRouteRecorder().sample(session.getLine().getId(), minecart, snapLocation);
        }
        SchedulerUtil.teleportEntity(minecart, snapLocation);

        TrainState previousState = stateMachine.transitionTo(TrainState.STOPPED_AT_STATION, null);
        if (previousState == TrainState.MOVING_IN_STATION) {
            handleArrivalAtStation();
        }

        updateScoreboardBasedOnState();
    }

    private void transitionToMovingInStation(Stop targetStop) {
        stopMovementAssist();

        TrainState previousState = stateMachine.transitionTo(TrainState.MOVING_IN_STATION,
                "enteredStop=" + targetStop.getId());
        if (previousState == TrainState.MOVING_BETWEEN_STATIONS) {
            handleEnteringStation(targetStop);
        }

        updateScoreboardBasedOnState();
    }

    private void transitionToMovingBetweenStations() {
        stateMachine.transitionTo(TrainState.MOVING_BETWEEN_STATIONS, null);

        Player passenger = session.getPassenger();
        if (passenger != null && passenger.isOnline()) {
            passenger.sendTitle("", "", 0, 0, 0);
        }
        updateScoreboardBasedOnState();
    }

    private void handleEnteringStation(Stop targetStop) {
        Player passenger = session.getPassenger();
        Line line = session.getLine();
        if (passenger == null || !passenger.isOnline() || line == null || targetStop == null) {
            return;
        }

        String nextStopId = line.getNextStopId(session.getTargetStopId());
        boolean isTerminus = nextStopId == null;
        Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(session.getMinecart(), passenger, line,
                targetStop, isTerminus, MetroTrainArrivalEvent.ArrivalType.ENTERING));
    }

    private void handleArrivalAtStation() {
        handleArrivalAtStation(false);
    }

    private void handleArrivalAtStation(boolean isNewlySpawned) {
        Line line = session.getLine();
        if (line == null) {
            cancel();
            return;
        }

        if (!isNewlySpawned) {
            session.setCurrentStopId(session.getTargetStopId());
        }

        Stop currentStop = session.getPlugin().getStopManager().getStop(session.getCurrentStopId());
        if (currentStop == null) {
            session.debug("Cancelling train because current stop is missing: " + session.getCurrentStopId());
            cancel();
            return;
        }

        if (line.getNextStopId(session.getCurrentStopId()) == null) {
            if (!handleTerminalStation()) {
                return;
            }
            session.setTargetStopId(null);
            Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(session.getMinecart(),
                    session.getPassenger(), line, currentStop, true, MetroTrainArrivalEvent.ArrivalType.DOCKED));
            return;
        }

        Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(session.getMinecart(),
                session.getPassenger(), line, currentStop, false, MetroTrainArrivalEvent.ArrivalType.DOCKED));
        updateScoreboardBasedOnState();
        scheduleNextDeparture();
    }

    private void handleDeparture() {
        Line line = session.getLine();
        if (line == null) {
            cancel();
            return;
        }

        session.refreshTargetFromCurrentStop();
        if (session.getTargetStopId() == null) {
            return;
        }

        StopManager stopManager = session.getPlugin().getStopManager();
        Stop currentStop = stopManager.getStop(session.getCurrentStopId());
        Stop nextStop = stopManager.getStop(session.getTargetStopId());
        if (currentStop == null || nextStop == null || currentStop.getStopPointLocation() == null) {
            session.debug("Cancelling train because departure stops are incomplete: current="
                    + session.getCurrentStopId() + ", target=" + session.getTargetStopId());
            cancel();
            return;
        }

        session.getPlugin().getRouteRecorder().sample(line.getId(), session.getMinecart(),
                currentStop.getStopPointLocation());
        Bukkit.getPluginManager().callEvent(new MetroTrainDepartureEvent(session.getMinecart(), session.getPassenger(),
                line, currentStop, nextStop));

        double maxSpeed = line.getMaxSpeed();
        if (maxSpeed == -1.0) {
            maxSpeed = session.getPlugin().getConfigFacade().getCartSpeed();
        }
        session.getMinecart().setMaxSpeed(maxSpeed);

        Vector launchDirection = physicsController.initMinecartVelocity(session.getMinecart(),
                currentStop.getLaunchYaw());
        if (launchDirection != null) {
            session.setLastTravelDirection(launchDirection);
        }

        transitionToMovingBetweenStations();
        startMovementAssist();
    }

    private void scheduleNextDeparture() {
        long delay = session.getPlugin().getConfigFacade().getCartDepartureDelay();
        session.debug("Schedule departure in " + delay + " ticks for passenger="
                + session.safePassengerName() + ", currentStop=" + session.getCurrentStopId());
        trainScheduler.entityRun(session.getMinecart(), () -> {
            if (session.isPassengerStillRiding()) {
                handleDeparture();
            } else {
                handlePassengerExit();
            }
        }, delay, -1);
    }

    private boolean handleTerminalStation() {
        Player passenger = session.getPassenger();
        Line line = session.getLine();
        if (passenger == null || !passenger.isOnline() || line == null) {
            cancel();
            return false;
        }
        session.debug("Terminal station reached for passenger=" + session.safePassengerName()
                + ", stop=" + session.getCurrentStopId());

        org.cubexmc.metro.manager.RouteRecorder.FinishResult routeResult =
                session.getPlugin().getRouteRecorder().finishIfRecording(line.getId(), session.getMinecart());
        if (routeResult.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS) {
            session.getPlugin().getLogger().warning("[RouteRecorder] Route recording for line " + line.getId()
                    + " reached the terminal but only collected " + routeResult.pointCount() + " point(s).");
        }

        trainScheduler.entityRun(session.getMinecart(), () -> {
            Minecart minecart = session.getMinecart();
            if (minecart != null && !minecart.isDead()) {
                minecart.eject();
                session.getPlugin().getScoreboardManager().clearPlayerDisplay(passenger);

                trainScheduler.entityRun(minecart, () -> {
                    if (minecart != null && !minecart.isDead()) {
                        minecart.remove();
                    }
                    cancel();
                }, 40L, -1);
            } else {
                cancel();
            }
        }, 60L, -1);
        return true;
    }

    private void handlePassengerExit() {
        cancel();
    }

    public static void startTrainTask(Metro plugin, Minecart minecart, Player passenger, String lineId,
            String currentStopId) {
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            return;
        }

        if (passenger == null || !passenger.isOnline() || passenger.getVehicle() != minecart) {
            if (minecart.isValid()) {
                minecart.remove();
            }
            return;
        }

        TrainMovementTask trainTask = new TrainMovementTask(plugin, minecart, passenger, lineId, currentStopId);
        Bukkit.getPluginManager().registerEvents(trainTask, plugin);
        activeTasks.put(minecart.getUniqueId(), trainTask);

        minecart.setMaxSpeed(0);
        minecart.setVelocity(new Vector(0, 0, 0));
        trainTask.handleArrivalAtStation(true);
    }

    private void startMovementAssist() {
        stopMovementAssist();
        if (!session.getPlugin().getConfigFacade().isSafeModeMovementAssist()
                || session.getMinecart() == null) {
            return;
        }
        long interval = Math.max(1L, session.getPlugin().getConfigFacade().getSafeModeStallRecoveryTicks());
        movementAssistTaskId = trainScheduler.entityRun(session.getMinecart(), this::recoverStalledMinecart,
                interval, interval);
    }

    private void stopMovementAssist() {
        trainScheduler.cancel(movementAssistTaskId);
        movementAssistTaskId = null;
    }

    private void recoverStalledMinecart() {
        if (!session.getPlugin().getConfigFacade().isSafeModeMovementAssist()) {
            stopMovementAssist();
            return;
        }
        Minecart minecart = session.getMinecart();
        if (minecart == null || minecart.isDead() || !minecart.isValid()) {
            cancel();
            return;
        }
        if (!physicsController.canRecoverStalledMinecart(session)) {
            return;
        }
        if (!session.isPassengerStillRiding()) {
            handlePassengerExit();
            return;
        }

        double minCruiseSpeed = Math.max(0.01,
                session.getPlugin().getConfigFacade().getSafeModeMinCruiseSpeed());
        if (!physicsController.isBelowCruiseSpeed(minecart, minCruiseSpeed)) {
            return;
        }

        double targetSpeed = physicsController.resolveAssistSpeed(minecart,
                session.getPlugin().getConfigFacade().getCartSpeed(), minCruiseSpeed);
        minecart.setVelocity(physicsController.buildAssistVelocity(session.getLastTravelDirection(), targetSpeed));
    }

    private void updateLastTravelDirection(Location from, Location to) {
        if (session.getState() == TrainState.STOPPED_AT_STATION || from == null || to == null) {
            return;
        }
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector direction = to.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        session.setLastTravelDirection(direction.normalize());
    }
}
