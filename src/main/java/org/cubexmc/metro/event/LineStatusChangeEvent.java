package org.cubexmc.metro.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;

/**
 * Fired when a line's operational status changes.
 */
public class LineStatusChangeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Line line;
    private final LineStatus oldStatus;
    private final LineStatus newStatus;

    public LineStatusChangeEvent(Line line, LineStatus oldStatus, LineStatus newStatus) {
        this.line = line;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public Line getLine() {
        return line;
    }

    public LineStatus getOldStatus() {
        return oldStatus;
    }

    public LineStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
