package org.cubexmc.metro.event;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

public class MetroTrainArrivalEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum ArrivalType {
        ENTERING,
        DOCKED
    }

    private final Minecart minecart;
    private final Player passenger;
    private final Line line;
    private final Stop currentStop;
    private final boolean isTerminus;
    private final ArrivalType arrivalType;

    public MetroTrainArrivalEvent(Minecart minecart, Player passenger, Line line, Stop currentStop, boolean isTerminus,
            ArrivalType arrivalType) {
        super(false); // Synchronous event - always fired from the main server thread
        this.minecart = minecart;
        this.passenger = passenger;
        this.line = line;
        this.currentStop = currentStop;
        this.isTerminus = isTerminus;
        this.arrivalType = arrivalType;
    }

    public Minecart getMinecart() {
        return minecart;
    }

    public Player getPassenger() {
        return passenger;
    }

    public Line getLine() {
        return line;
    }

    public Stop getCurrentStop() {
        return currentStop;
    }

    public boolean isTerminus() {
        return isTerminus;
    }

    public ArrivalType getArrivalType() {
        return arrivalType;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
