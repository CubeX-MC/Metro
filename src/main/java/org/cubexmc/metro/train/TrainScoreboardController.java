package org.cubexmc.metro.train;

import org.bukkit.entity.Player;
import org.cubexmc.metro.model.Line;

/**
 * Keeps ride scoreboard updates out of movement state transitions.
 */
public class TrainScoreboardController {

    public void updateBasedOnState(TrainSession session) {
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
}
