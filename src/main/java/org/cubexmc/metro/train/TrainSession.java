package org.cubexmc.metro.train;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;

/**
 * Mutable runtime data for one active train ride.
 */
public class TrainSession {

    private final Metro plugin;
    private Minecart minecart;
    private final Player passenger;
    private final Line line;
    private String currentStopId;
    private String targetStopId;
    private TrainMovementTask.TrainState state;
    private boolean teleporting;
    private Vector lastTravelDirection;

    private String entryStopId;
    private double distanceTraveled;

    public TrainSession(Metro plugin, Minecart minecart, Player passenger, Line line, String currentStopId,
            TrainMovementTask.TrainState state) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;
        this.line = line;
        this.currentStopId = currentStopId;
        this.entryStopId = currentStopId;
        this.state = state;
        this.targetStopId = line == null ? null : line.getNextStopId(currentStopId);
        if (this.targetStopId == null) {
            this.state = TrainMovementTask.TrainState.STOPPED_AT_STATION;
        }
    }

    public Metro getPlugin() {
        return plugin;
    }

    public Minecart getMinecart() {
        return minecart;
    }

    public void setMinecart(Minecart minecart) {
        this.minecart = minecart;
    }

    public Player getPassenger() {
        return passenger;
    }

    public Line getLine() {
        return line;
    }

    public String getCurrentStopId() {
        return currentStopId;
    }

    public void setCurrentStopId(String currentStopId) {
        this.currentStopId = currentStopId;
    }

    public String getTargetStopId() {
        return targetStopId;
    }

    public void setTargetStopId(String targetStopId) {
        this.targetStopId = targetStopId;
    }

    public TrainMovementTask.TrainState getState() {
        return state;
    }

    public void setState(TrainMovementTask.TrainState state) {
        this.state = state;
    }

    public boolean isTeleporting() {
        return teleporting;
    }

    public void setTeleporting(boolean teleporting) {
        this.teleporting = teleporting;
    }

    public Vector getLastTravelDirection() {
        return lastTravelDirection;
    }

    public void setLastTravelDirection(Vector lastTravelDirection) {
        this.lastTravelDirection = lastTravelDirection;
    }

    public String getEntryStopId() {
        return entryStopId;
    }

    public void setEntryStopId(String entryStopId) {
        this.entryStopId = entryStopId;
    }

    public double getDistanceTraveled() {
        return distanceTraveled;
    }

    public double addDistance(double blocks) {
        this.distanceTraveled += blocks;
        return this.distanceTraveled;
    }

    public void refreshTargetFromCurrentStop() {
        this.targetStopId = line == null ? null : line.getNextStopId(currentStopId);
    }

    public boolean isPassengerStillRiding() {
        if (passenger == null || !passenger.isOnline()) {
            return false;
        }

        Entity vehicle = passenger.getVehicle();
        return vehicle != null && vehicle.equals(minecart);
    }

    public String safePassengerName() {
        return passenger == null ? "unknown" : passenger.getName();
    }

    public void debug(String message) {
        plugin.debug("train_state_transitions", message);
    }
}
