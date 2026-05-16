package org.cubexmc.metro.train;

import java.util.Map;

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
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.PriceRule;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.TicketService;
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

    private final TrainSession session;
    private final TrainStateMachine stateMachine;
    private final TrainScheduler trainScheduler;
    private final TrainPhysicsController physicsController;
    private final TrainEventPublisher eventPublisher;
    private final TrainScoreboardController scoreboardController;
    private final TrainMovementAssistController movementAssistController;

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
        this.eventPublisher = new TrainEventPublisher(session);
        this.scoreboardController = new TrainScoreboardController();
        this.movementAssistController = new TrainMovementAssistController(session, trainScheduler,
                physicsController, this::cancel, this::handlePassengerExit);

        if (line != null && session.getTargetStopId() != null) {
            scoreboardController.updateBasedOnState(session);
        }
    }

    public void cancel() {
        Minecart minecart = session.getMinecart();
        TrainTaskRegistry.unregister(minecart);
        movementAssistController.stop();
        trainScheduler.cancelAll();
        HandlerList.unregisterAll(this);
        session.debug("Task cancelled for passenger=" + session.safePassengerName()
                + ", currentStop=" + session.getCurrentStopId()
                + ", targetStop=" + session.getTargetStopId());
    }

    public void removeMinecartAndCancel() {
        Minecart minecart = session.getMinecart();
        if (minecart != null && !minecart.isDead()) {
            minecart.eject();
            minecart.remove();
        }
        cancel();
    }

    void removeMinecartAndCancelOnEntityScheduler() {
        Minecart minecart = session.getMinecart();
        if (minecart == null) {
            cancel();
            return;
        }
        SchedulerUtil.entityRun(session.getPlugin(), minecart, this::removeMinecartAndCancel, 0L, -1L);
    }

    public void transferMinecart(Minecart newCart) {
        Minecart previousCart = session.getMinecart();
        session.setMinecart(newCart);
        session.setTeleporting(false);
        TrainTaskRegistry.transfer(previousCart, newCart, this);
        Line line = session.getLine();
        if (line != null && session.getPlugin().getRouteRecorder() != null) {
            session.getPlugin().getRouteRecorder().transferCart(line.getId(), previousCart, newCart);
        }
        if (session.getState() == TrainState.MOVING_BETWEEN_STATIONS) {
            movementAssistController.start();
        }
        session.debug("Transferred movement task to new minecart UUID=" + newCart.getUniqueId());
    }

    public void setTeleporting(boolean teleporting) {
        session.setTeleporting(teleporting);
    }

    public boolean canUsePortal(String portalId) {
        Line line = session.getLine();
        return line != null && line.containsPortal(portalId);
    }

    public static TrainMovementTask getTaskFor(Minecart cart) {
        return TrainTaskRegistry.get(cart);
    }

    public static int shutdownActiveTasks() {
        return TrainTaskRegistry.shutdownActiveTasks();
    }

    public static int shutdownActiveTasks(Metro plugin, boolean folia) {
        return TrainTaskRegistry.shutdownActiveTasks(plugin, folia);
    }

    Object scheduleSessionTask(Runnable task, long delay, long period) {
        return trainScheduler.entityRun(session.getMinecart(), task, delay, period);
    }

    TrainSession getSession() {
        return session;
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

        if (session.getState() == TrainState.MOVING_BETWEEN_STATIONS) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getWorld() != null && to.getWorld() != null && from.getWorld().equals(to.getWorld())) {
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.001) {
                    session.addDistance(dist);
                }
            }
        }

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
        movementAssistController.stop();

        Location snapLocation = stop.getStopPointLocation().clone();
        snapLocation.setX(snapLocation.getBlockX() + 0.5);
        snapLocation.setZ(snapLocation.getBlockZ() + 0.5);
        if (session.getLine() != null) {
            session.getPlugin().getRouteRecorder().sample(session.getLine().getId(), minecart, snapLocation);
        }
        SchedulerUtil.teleportEntity(minecart, snapLocation);

        settleDistanceFare(stop);

        TrainState previousState = stateMachine.transitionTo(TrainState.STOPPED_AT_STATION, null);
        if (previousState == TrainState.MOVING_IN_STATION) {
            handleArrivalAtStation();
        }

        scoreboardController.updateBasedOnState(session);
    }

    private void settleDistanceFare(Stop stop) {
        Line line = session.getLine();
        if (line == null) return;

        PriceRule rule = line.getPriceRule();
        if (rule == null) return;

        double distance = session.getDistanceTraveled();
        if (distance <= 0 && rule.getMode() != PriceRule.PricingMode.INTERVAL) return;

        Player passenger = session.getPassenger();
        if (passenger == null || !passenger.isOnline()) return;

        double variablePrice = 0;

        if (rule.getMode() == PriceRule.PricingMode.DISTANCE) {
            variablePrice = distance * rule.getPerBlockRate();
        } else if (rule.getMode() == PriceRule.PricingMode.INTERVAL) {
            int intervals = session.getPlugin().getPriceService()
                    .countStopIntervals(line, session.getEntryStopId(), stop.getId());
            variablePrice = intervals * rule.getPerIntervalRate();
        } else {
            return;
        }

        if (variablePrice > 0) {
            TicketService.TicketChargeStatus status = session.getPlugin().getTicketService()
                    .chargePrice(passenger, line, variablePrice);
            if (status == TicketService.TicketChargeStatus.CHARGED) {
                passenger.sendMessage(session.getPlugin().getLanguageManager().getMessage("economy.paid_distance",
                        LanguageManager.put(LanguageManager.args(), "price",
                                session.getPlugin().getTicketService().format(variablePrice))));
            }
        }

        session.addDistance(-distance);
    }

    private void transitionToMovingInStation(Stop targetStop) {
        movementAssistController.stop();

        TrainState previousState = stateMachine.transitionTo(TrainState.MOVING_IN_STATION,
                "enteredStop=" + targetStop.getId());
        if (previousState == TrainState.MOVING_BETWEEN_STATIONS) {
            eventPublisher.publishEnteringStop(targetStop);
        }

        scoreboardController.updateBasedOnState(session);
    }

    private void transitionToMovingBetweenStations() {
        stateMachine.transitionTo(TrainState.MOVING_BETWEEN_STATIONS, null);

        Player passenger = session.getPassenger();
        if (passenger != null && passenger.isOnline()) {
            passenger.sendTitle("", "", 0, 0, 0);
        }
        scoreboardController.updateBasedOnState(session);
    }

    private void handleArrivalAtStation() {
        handleArrivalAtStation(false);
    }

    void startAtStation() {
        handleArrivalAtStation(true);
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
            eventPublisher.publishDockedAtStop(currentStop, true);
            return;
        }

        eventPublisher.publishDockedAtStop(currentStop, false);
        scoreboardController.updateBasedOnState(session);
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
        eventPublisher.publishDeparture(currentStop, nextStop);

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
        movementAssistController.start();
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
        notifyRouteRecorder(routeResult);
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

    private void notifyRouteRecorder(org.cubexmc.metro.manager.RouteRecorder.FinishResult result) {
        if (result.recorderId() == null
                || result.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.NOT_RECORDING) {
            return;
        }
        Player recorder = Bukkit.getPlayer(result.recorderId());
        if (recorder == null || !recorder.isOnline()) {
            return;
        }

        Map<String, Object> args = LanguageManager.args();
        LanguageManager.put(args, "line_id", result.lineId());
        LanguageManager.put(args, "point_count", String.valueOf(result.pointCount()));

        String key = switch (result.status()) {
            case SAVED -> "line.record_saved";
            case TOO_FEW_POINTS -> "line.record_too_few";
            case FAILED -> "line.record_failed";
            case NOT_RECORDING -> null;
        };
        if (key != null) {
            recorder.sendMessage(session.getPlugin().getLanguageManager().getMessage(key, args));
        }
        if (result.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS) {
            recorder.sendMessage(session.getPlugin().getLanguageManager().getMessage("line.record_too_few_hint"));
        }
    }

    private void handlePassengerExit() {
        cancel();
    }

    public static void startTrainTask(Metro plugin, Minecart minecart, Player passenger, String lineId,
            String currentStopId) {
        TrainTaskStarter.start(plugin, minecart, passenger, lineId, currentStopId);
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
