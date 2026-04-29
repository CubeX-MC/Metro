package org.cubexmc.metro.train;

/**
 * Owns state transitions and transition diagnostics for a train session.
 */
public class TrainStateMachine {

    private final TrainSession session;

    public TrainStateMachine(TrainSession session) {
        this.session = session;
    }

    public TrainMovementTask.TrainState transitionTo(TrainMovementTask.TrainState nextState, String detail) {
        TrainMovementTask.TrainState previousState = session.getState();
        session.setState(nextState);
        session.debug("State transition " + previousState + " -> " + nextState
                + " for passenger=" + session.safePassengerName()
                + ", currentStop=" + session.getCurrentStopId()
                + ", targetStop=" + session.getTargetStopId()
                + (detail == null || detail.isBlank() ? "" : ", " + detail));
        return previousState;
    }
}
