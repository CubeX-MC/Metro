package org.cubexmc.metro.train;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

/**
 * Publishes train lifecycle events consumed by display and integration layers.
 */
public class TrainEventPublisher {

    private final TrainSession session;

    public TrainEventPublisher(TrainSession session) {
        this.session = session;
    }

    public void publishEnteringStop(Stop targetStop) {
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

    public void publishDockedAtStop(Stop currentStop, boolean isTerminus) {
        Line line = session.getLine();
        if (line == null || currentStop == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new MetroTrainArrivalEvent(session.getMinecart(),
                session.getPassenger(), line, currentStop, isTerminus, MetroTrainArrivalEvent.ArrivalType.DOCKED));
    }

    public void publishDeparture(Stop currentStop, Stop nextStop) {
        Line line = session.getLine();
        if (line == null || currentStop == null || nextStop == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new MetroTrainDepartureEvent(session.getMinecart(),
                session.getPassenger(), line, currentStop, nextStop));
    }
}
