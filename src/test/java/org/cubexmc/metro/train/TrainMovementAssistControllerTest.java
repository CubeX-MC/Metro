package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainMovementAssistControllerTest {

    @Test
    void shouldNotStartWhenSafeModeDisabled() {
        Metro plugin = mockPlugin(false);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        TrainScheduler scheduler = new TrainScheduler(plugin);
        TrainPhysicsController physics = new TrainPhysicsController();

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, physics, () -> {}, () -> {});

        ctrl.start();
        // No task scheduled because safe mode is disabled
        // Verify by checking that no exception thrown
    }

    @Test
    void shouldNotStartWhenMinecartIsNull() {
        Metro plugin = mockPlugin(true);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);

        TrainSession session = new TrainSession(plugin, null, mock(Player.class),
                line, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);
        session.setState(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        TrainScheduler scheduler = new TrainScheduler(plugin);
        TrainPhysicsController physics = new TrainPhysicsController();

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, physics, () -> {}, () -> {});

        ctrl.start();
    }

    @Test
    void shouldStopAndCancelTask() {
        Metro plugin = mockPlugin(true);
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        TrainScheduler scheduler = new TrainScheduler(plugin);
        TrainPhysicsController physics = new TrainPhysicsController();

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, physics, () -> {}, () -> {});

        // stop with no active task should be safe
        ctrl.stop();
        // second stop should also be safe
        ctrl.stop();
    }

    @Test
    void shouldScheduleMovementAssistWithClampedInterval() {
        MockSetup setup = mockSetup(true, 0L, 0.4, 0.05);
        Minecart minecart = mock(Minecart.class);
        TrainSession session = movingSession(setup.plugin(), minecart, mock(Player.class));
        CapturingTrainScheduler scheduler = new CapturingTrainScheduler(setup.plugin());

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, mock(TrainPhysicsController.class), () -> {}, () -> {});

        ctrl.start();

        assertNotNull(scheduler.capturedTask);
        assertEquals(1L, scheduler.delay);
        assertEquals(1L, scheduler.period);
        ctrl.stop();
        assertEquals(scheduler.taskId, scheduler.cancelledTaskId);
    }

    @Test
    void shouldStopWhenSafeModeIsDisabledDuringRecovery() {
        MockSetup setup = mockSetup(true, 20L, 0.4, 0.05);
        Minecart minecart = mock(Minecart.class);
        TrainSession session = movingSession(setup.plugin(), minecart, mock(Player.class));
        CapturingTrainScheduler scheduler = new CapturingTrainScheduler(setup.plugin());

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, mock(TrainPhysicsController.class), () -> {}, () -> {});
        ctrl.start();
        when(setup.config().isSafeModeMovementAssist()).thenReturn(false);

        scheduler.capturedTask.run();

        assertEquals(scheduler.taskId, scheduler.cancelledTaskId);
    }

    @Test
    void shouldCancelSessionWhenMinecartIsInvalidDuringRecovery() {
        MockSetup setup = mockSetup(true, 20L, 0.4, 0.05);
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(true);
        AtomicInteger cancellations = new AtomicInteger();
        TrainSession session = movingSession(setup.plugin(), minecart, mock(Player.class));
        CapturingTrainScheduler scheduler = new CapturingTrainScheduler(setup.plugin());

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, mock(TrainPhysicsController.class), cancellations::incrementAndGet, () -> {});
        ctrl.start();

        scheduler.capturedTask.run();

        assertEquals(1, cancellations.get());
    }

    @Test
    void shouldCallPassengerExitHandlerWhenPassengerLeftCart() {
        MockSetup setup = mockSetup(true, 20L, 0.4, 0.05);
        Minecart minecart = validMinecart();
        TrainPhysicsController physics = mock(TrainPhysicsController.class);
        AtomicInteger passengerExits = new AtomicInteger();
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(null);
        TrainSession session = movingSession(setup.plugin(), minecart, passenger);
        when(physics.canRecoverStalledMinecart(session)).thenReturn(true);
        CapturingTrainScheduler scheduler = new CapturingTrainScheduler(setup.plugin());

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, physics, () -> {}, passengerExits::incrementAndGet);
        ctrl.start();

        scheduler.capturedTask.run();

        assertEquals(1, passengerExits.get());
        verify(minecart, never()).setVelocity(any(Vector.class));
    }

    @Test
    void shouldApplyAssistVelocityWhenMinecartIsRecoverableAndSlow() {
        MockSetup setup = mockSetup(true, 20L, 0.4, 0.05);
        Minecart minecart = validMinecart();
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(minecart);
        TrainSession session = movingSession(setup.plugin(), minecart, passenger);
        session.setLastTravelDirection(new Vector(1, 0, 0));
        TrainPhysicsController physics = mock(TrainPhysicsController.class);
        Vector assistVelocity = new Vector(0.3, 0, 0);
        when(physics.canRecoverStalledMinecart(session)).thenReturn(true);
        when(physics.isBelowCruiseSpeed(minecart, 0.05)).thenReturn(true);
        when(physics.resolveAssistSpeed(minecart, 0.4, 0.05)).thenReturn(0.3);
        when(physics.buildAssistVelocity(any(Vector.class), eq(0.3))).thenReturn(assistVelocity);
        CapturingTrainScheduler scheduler = new CapturingTrainScheduler(setup.plugin());

        TrainMovementAssistController ctrl = new TrainMovementAssistController(
                session, scheduler, physics, () -> {}, () -> {});
        ctrl.start();

        scheduler.capturedTask.run();

        verify(minecart).setVelocity(assistVelocity);
    }

    private Metro mockPlugin(boolean safeModeEnabled) {
        return mockSetup(safeModeEnabled, 20L, 0.4, 0.05).plugin();
    }

    private MockSetup mockSetup(boolean safeModeEnabled, long recoveryTicks, double cartSpeed, double minCruiseSpeed) {
        Metro plugin = mock(Metro.class);
        ConfigFacade config = mock(ConfigFacade.class);
        when(plugin.getConfigFacade()).thenReturn(config);
        when(config.isSafeModeMovementAssist()).thenReturn(safeModeEnabled);
        when(config.getSafeModeStallRecoveryTicks()).thenReturn(recoveryTicks);
        when(config.getCartSpeed()).thenReturn(cartSpeed);
        when(config.getSafeModeMinCruiseSpeed()).thenReturn(minCruiseSpeed);
        return new MockSetup(plugin, config);
    }

    private TrainSession movingSession(Metro plugin, Minecart minecart, Player passenger) {
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        line.addStop("B", -1);
        TrainSession session = new TrainSession(plugin, minecart, passenger,
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        session.setState(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        return session;
    }

    private Minecart validMinecart() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.isDead()).thenReturn(false);
        when(minecart.isValid()).thenReturn(true);
        return minecart;
    }

    private record MockSetup(Metro plugin, ConfigFacade config) {
    }

    private static class CapturingTrainScheduler extends TrainScheduler {
        private final Object taskId = new Object();
        private Runnable capturedTask;
        private Object cancelledTaskId;
        private long delay;
        private long period;

        CapturingTrainScheduler(Metro plugin) {
            super(plugin);
        }

        @Override
        public Object entityRun(Entity entity, Runnable task, long delay, long period) {
            this.capturedTask = task;
            this.delay = delay;
            this.period = period;
            return taskId;
        }

        @Override
        public void cancel(Object taskId) {
            this.cancelledTaskId = taskId;
        }
    }
}
