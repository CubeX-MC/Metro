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
}
