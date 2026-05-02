package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class TrainPhysicsControllerTest {

    private final TrainPhysicsController controller = new TrainPhysicsController();

    @Test
    void shouldApplyApproachBrakingWithoutIncreasingFrozenMinecartSpeed() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getMaxSpeed()).thenReturn(0.0);

        controller.applyApproachBraking(minecart, 5.0, 0.4);

        verify(minecart, never()).setMaxSpeed(org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void shouldClampApproachSpeedToCurrentMaxSpeed() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getMaxSpeed()).thenReturn(0.2);

        controller.applyApproachBraking(minecart, 15.0, 0.4);

        verify(minecart).setMaxSpeed(0.2);
    }

    @Test
    void shouldDetectHorizontalStallBelowCruiseSpeed() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getVelocity()).thenReturn(new Vector(0.01, 1.0, 0.01));

        assertTrue(controller.isBelowCruiseSpeed(minecart, 0.05));
        assertFalse(controller.isBelowCruiseSpeed(minecart, 0.01));
    }

    @Test
    void shouldResolveAssistSpeedWithinConfiguredAndMinecartLimits() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getMaxSpeed()).thenReturn(0.3);

        assertEquals(0.3, controller.resolveAssistSpeed(minecart, 0.5, 0.05));
        assertEquals(0.08, controller.resolveAssistSpeed(minecart, 0.02, 0.08));
    }
}
