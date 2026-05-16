package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainPhysicsControllerExtendedTest {

    private final TrainPhysicsController controller = new TrainPhysicsController();

    @Test
    void shouldBuildAssistVelocityFromDirection() {
        Vector direction = new Vector(1, 0, 0);
        Vector result = controller.buildAssistVelocity(direction, 0.4);
        assertEquals(0.4, result.length(), 0.001);
    }

    @Test
    void shouldNormalizeAndBuildAssistVelocity() {
        Vector direction = new Vector(3, 0, 4);
        Vector result = controller.buildAssistVelocity(direction, 0.5);
        assertEquals(0.5, result.length(), 0.001);
    }

    @Test
    void shouldNotRecoverWhenNotMovingBetweenStations() {
        Metro plugin = mock(Metro.class);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainPhysicsController ctrl = new TrainPhysicsController();
        assertFalseHelper(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldNotRecoverWhenDead() {
        Metro plugin = mock(Metro.class);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        TrainPhysicsController ctrl = new TrainPhysicsController();
        assertFalseHelper(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldNotRecoverWhenMaxSpeedIsZero() {
        Metro plugin = mock(Metro.class);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        when(minecart.getMaxSpeed()).thenReturn(0.0);

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        TrainPhysicsController ctrl = new TrainPhysicsController();
        assertFalseHelper(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldNotRecoverWhenNoTravelDirection() {
        Metro plugin = mock(Metro.class);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        when(minecart.getMaxSpeed()).thenReturn(0.4);
        when(minecart.getLocation()).thenReturn(mock(Location.class));

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        // lastTravelDirection is null by default

        TrainPhysicsController ctrl = new TrainPhysicsController();
        assertFalseHelper(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldApplyApproachBrakingGradually() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getMaxSpeed()).thenReturn(0.8);

        // distance=30, speedRatio = min(1.0, 30/15) = 1.0
        // targetSpeed = 0.1 + (0.4 - 0.1) * 1.0^0.7 = 0.4
        // clamped: min(0.8, 0.4) = 0.4
        controller.applyApproachBraking(minecart, 30.0, 0.4);
        org.mockito.Mockito.verify(minecart).setMaxSpeed(0.4);
    }

    @Test
    void shouldResolveAssistSpeedWithZeroConfiguredSpeed() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        double speed = controller.resolveAssistSpeed(minecart, 0.0, 0.05);
        assertEquals(0.4, speed, 0.001);
    }

    private void assertFalseHelper(boolean value) {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }

    // --- initMinecartVelocity tests ---

    @Test
    void shouldReturnNullWhenNotPoweredRail() {
        Minecart minecart = mock(Minecart.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        when(minecart.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.RAIL);
        when(block.getBlockData()).thenReturn(mock(BlockData.class));

        assertNull(controller.initMinecartVelocity(minecart, 90.0f));
    }

    @Test
    void shouldReturnNullWhenRailNotPowered() {
        Minecart minecart = mock(Minecart.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        Powerable powerable = mock(Powerable.class);
        when(minecart.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.POWERED_RAIL);
        when(block.getBlockData()).thenReturn(powerable);
        when(powerable.isPowered()).thenReturn(false);

        assertNull(controller.initMinecartVelocity(minecart, 90.0f));
    }

    @Test
    void shouldLaunchInYawDirectionWhenPowered() {
        Minecart minecart = mock(Minecart.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        Powerable powerable = mock(Powerable.class);
        when(minecart.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.POWERED_RAIL);
        when(block.getBlockData()).thenReturn(powerable);
        when(powerable.isPowered()).thenReturn(true);

        Vector result = controller.initMinecartVelocity(minecart, 0.0f);
        assertNotNull(result);
        assertEquals(1.0, result.length(), 0.001);
        org.mockito.Mockito.verify(minecart).setVelocity(org.mockito.ArgumentMatchers.any(Vector.class));
    }

    @Test
    void shouldLaunchSouthWhenYaw90() {
        Minecart minecart = mock(Minecart.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        Powerable powerable = mock(Powerable.class);
        when(minecart.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.POWERED_RAIL);
        when(block.getBlockData()).thenReturn(powerable);
        when(powerable.isPowered()).thenReturn(true);

        Vector result = controller.initMinecartVelocity(minecart, 90.0f);
        assertNotNull(result);
        // yaw=90: direction = (-sin(90), 0, cos(90)) = (-1, 0, 0), normalized
        assertEquals(-1.0, result.getX(), 0.001);
        assertEquals(0.0, result.getY(), 0.001);
        assertEquals(0.0, result.getZ(), 0.001);
    }

    @Test
    void shouldReturnNullWhenBlockDataIsNotPowerable() {
        Minecart minecart = mock(Minecart.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        BlockData nonPowerable = mock(BlockData.class);
        when(minecart.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.POWERED_RAIL);
        when(block.getBlockData()).thenReturn(nonPowerable);

        assertNull(controller.initMinecartVelocity(minecart, 0.0f));
    }

    // --- canRecoverStalledMinecart success path ---

    @Test
    void shouldRecoverWhenAllConditionsMet() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        TrainSession session = new TrainSession(plugin, minecart, player, line, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        // Set a travel direction
        session.setLastTravelDirection(new Vector(1, 0, 0));

        // Mock LocationUtil.isOnRail via the location
        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        org.bukkit.block.Block railBlock = mock(org.bukkit.block.Block.class);
        when(minecart.getLocation()).thenReturn(loc);
        when(loc.getBlock()).thenReturn(railBlock);
        when(railBlock.getType()).thenReturn(Material.POWERED_RAIL);

        TrainPhysicsController ctrl = new TrainPhysicsController();
        org.junit.jupiter.api.Assertions.assertTrue(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldNotRecoverWhenInvalidMinecart() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(false);

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        session.setLastTravelDirection(new Vector(1, 0, 0));

        TrainPhysicsController ctrl = new TrainPhysicsController();
        org.junit.jupiter.api.Assertions.assertFalse(ctrl.canRecoverStalledMinecart(session));
    }

    @Test
    void shouldNotRecoverWhenNotOnRail() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        TrainSession session = new TrainSession(plugin, minecart, mock(org.bukkit.entity.Player.class),
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        session.setLastTravelDirection(new Vector(1, 0, 0));

        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        org.bukkit.block.Block dirtBlock = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block belowBlock = mock(org.bukkit.block.Block.class);
        when(minecart.getLocation()).thenReturn(loc);
        when(loc.getBlock()).thenReturn(dirtBlock);
        when(dirtBlock.getType()).thenReturn(Material.DIRT);
        when(loc.clone()).thenReturn(loc);
        when(loc.subtract(0, 1, 0)).thenReturn(loc);
        when(loc.getBlock()).thenReturn(belowBlock);
        when(belowBlock.getType()).thenReturn(Material.STONE);

        TrainPhysicsController ctrl = new TrainPhysicsController();
        org.junit.jupiter.api.Assertions.assertFalse(ctrl.canRecoverStalledMinecart(session));
    }
}
