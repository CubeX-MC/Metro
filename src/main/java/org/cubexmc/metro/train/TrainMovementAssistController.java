package org.cubexmc.metro.train;

import org.bukkit.entity.Minecart;

/**
 * Handles safe-mode movement assist for stalled minecarts.
 */
public class TrainMovementAssistController {

    private final TrainSession session;
    private final TrainScheduler trainScheduler;
    private final TrainPhysicsController physicsController;
    private final Runnable cancelHandler;
    private final Runnable passengerExitHandler;
    private Object movementAssistTaskId;

    public TrainMovementAssistController(TrainSession session, TrainScheduler trainScheduler,
            TrainPhysicsController physicsController, Runnable cancelHandler, Runnable passengerExitHandler) {
        this.session = session;
        this.trainScheduler = trainScheduler;
        this.physicsController = physicsController;
        this.cancelHandler = cancelHandler;
        this.passengerExitHandler = passengerExitHandler;
    }

    public void start() {
        stop();
        if (!session.getPlugin().getConfigFacade().isSafeModeMovementAssist()
                || session.getMinecart() == null) {
            return;
        }
        long interval = Math.max(1L, session.getPlugin().getConfigFacade().getSafeModeStallRecoveryTicks());
        movementAssistTaskId = trainScheduler.entityRun(session.getMinecart(), this::recoverStalledMinecart,
                interval, interval);
    }

    public void stop() {
        trainScheduler.cancel(movementAssistTaskId);
        movementAssistTaskId = null;
    }

    private void recoverStalledMinecart() {
        if (!session.getPlugin().getConfigFacade().isSafeModeMovementAssist()) {
            stop();
            return;
        }
        Minecart minecart = session.getMinecart();
        if (minecart == null || minecart.isDead() || !minecart.isValid()) {
            cancelHandler.run();
            return;
        }
        if (!physicsController.canRecoverStalledMinecart(session)) {
            return;
        }
        if (!session.isPassengerStillRiding()) {
            passengerExitHandler.run();
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
}
