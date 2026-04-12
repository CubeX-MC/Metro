package org.cubexmc.metro.event;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.cubexmc.metro.model.Stop;

/**
 * 矿车进入地铁停靠区时触发的事件
 */
public class TrainEnterStopEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Minecart minecart;
    private final Stop stop;

    public TrainEnterStopEvent(Minecart minecart, Stop stop) {
        this.minecart = minecart;
        this.stop = stop;
    }

    public Minecart getMinecart() {
        return minecart;
    }

    public Stop getStop() {
        return stop;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
