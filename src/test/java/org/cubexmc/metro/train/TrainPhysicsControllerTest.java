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
    void experimentalBrakingRestoresCapOnAWindingApproachAndHonorsExternalChanges() {
        Minecart cart = mock(Minecart.class);
        java.util.concurrent.atomic.AtomicReference<Double> cap = new java.util.concurrent.atomic.AtomicReference<>(3.0);
        when(cart.getMaxSpeed()).thenAnswer(call -> cap.get());
        org.mockito.Mockito.doAnswer(call -> { cap.set(call.getArgument(0)); return null; })
                .when(cart).setMaxSpeed(org.mockito.ArgumentMatchers.anyDouble());
        controller.applyExperimentalApproachBraking(cart, 2.8);
        assertEquals(1.0, cap.get(), 0.000001);
        controller.applyExperimentalApproachBraking(cart, 20.0);
        assertEquals(3.0, cap.get());
        cap.set(0.4); // BLOCK_BASED or another explicit speed change.
        controller.applyExperimentalApproachBraking(cart, 20.0);
        assertEquals(0.4, cap.get());
        controller.resetExperimentalApproachBraking();
        cap.set(1.0);
        controller.applyExperimentalApproachBraking(cart, 20.0);
        assertEquals(1.0, cap.get());
    }
    @Test
    void experimentalBrakingLeavesRoomBeforeStationEvenAtHighSpeed() {
        Minecart cart = mock(Minecart.class);
        when(cart.getMaxSpeed()).thenReturn(10.0);
        controller.applyExperimentalApproachBraking(cart, 8.8);
        verify(cart).setMaxSpeed(4.0);
    }

    @Test
    void experimentalBrakingPreservesLowCapsAndDockedCarts() {
        Minecart cart = mock(Minecart.class);
        when(cart.getMaxSpeed()).thenReturn(0.2, 0.0);
        controller.applyExperimentalApproachBraking(cart, 4.0);
        controller.applyExperimentalApproachBraking(cart, 0.5);
        verify(cart, never()).setMaxSpeed(org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void experimentalFinalApproachCanStillReachStopRadius() {
        Minecart cart = mock(Minecart.class);
        when(cart.getMaxSpeed()).thenReturn(3.0);
        controller.applyExperimentalApproachBraking(cart, 0.85);
        verify(cart).setMaxSpeed(0.1);
    }

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
