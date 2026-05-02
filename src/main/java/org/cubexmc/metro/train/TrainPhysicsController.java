package org.cubexmc.metro.train;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.cubexmc.metro.util.LocationUtil;

/**
 * Minecart speed, launch and stall recovery calculations for train sessions.
 */
public class TrainPhysicsController {

    public void applyApproachBraking(Minecart minecart, double distance, double defaultMaxSpeed) {
        double minSpeed = 0.1;
        double speedRatio = Math.min(1.0, distance / 15.0);
        double targetSpeed = minSpeed + (defaultMaxSpeed - minSpeed) * Math.pow(speedRatio, 0.7);

        double currentMaxSpeed = minecart.getMaxSpeed();
        if (currentMaxSpeed > 0.0) {
            minecart.setMaxSpeed(Math.min(currentMaxSpeed, targetSpeed));
        }
    }

    public Vector initMinecartVelocity(Minecart minecart, float yaw) {
        Location location = minecart.getLocation();
        Block block = location.getBlock();
        BlockData blockData = block.getBlockData();

        if (block.getType() != Material.POWERED_RAIL || !(blockData instanceof Powerable powerable)
                || !powerable.isPowered()) {
            return null;
        }

        double launchRad = Math.toRadians(yaw);
        Vector direction = new Vector(
                -Math.sin(launchRad),
                0,
                Math.cos(launchRad));
        Vector normalizedDirection = direction.clone().normalize();
        minecart.setVelocity(direction.multiply(0.4));
        return normalizedDirection;
    }

    public boolean canRecoverStalledMinecart(TrainSession session) {
        Minecart minecart = session.getMinecart();
        return minecart != null
                && !minecart.isDead()
                && minecart.isValid()
                && session.getState() == TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
                && minecart.getMaxSpeed() > 0.0
                && LocationUtil.isOnRail(minecart.getLocation())
                && session.getLastTravelDirection() != null;
    }

    public boolean isBelowCruiseSpeed(Minecart minecart, double minCruiseSpeed) {
        Vector velocity = minecart.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        return horizontalSpeed < minCruiseSpeed;
    }

    public Vector buildAssistVelocity(Vector lastTravelDirection, double targetSpeed) {
        return lastTravelDirection.clone().normalize().multiply(targetSpeed);
    }

    public double resolveAssistSpeed(Minecart minecart, double configuredSpeed, double minCruiseSpeed) {
        double maxSpeed = minecart.getMaxSpeed();
        double targetSpeed = configuredSpeed > 0.0 ? Math.min(configuredSpeed, maxSpeed) : maxSpeed;
        return Math.max(minCruiseSpeed, targetSpeed);
    }
}
