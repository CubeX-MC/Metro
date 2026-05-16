package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrainMovementTaskTest {

    @AfterEach
    void tearDown() {
        TrainMovementTask.shutdownActiveTasks();
    }

    @Test
    void shouldFallbackToStoppedStateWhenLineNotFound() throws Exception {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(lineManager.getLine("missing")).thenReturn(null);

        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(false);
        TrainMovementTask task = new TrainMovementTask(
                plugin,
                mock(Minecart.class),
                passenger,
                "missing",
                "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
        );

        assertNull(task.getSession().getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION,
                task.getSession().getState());
    }

    @Test
    void shouldResolveNextStopWhenLineExists() throws Exception {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        when(lineManager.getLine("l1")).thenReturn(line);

        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(false);
        TrainMovementTask task = new TrainMovementTask(
                plugin,
                mock(Minecart.class),
                passenger,
                "l1",
                "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
        );

        assertEquals("B", task.getSession().getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS,
                task.getSession().getState());
    }

    @Test
    void shouldRemoveRegisteredMinecartWhenShuttingDownActiveTasks() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        when(lineManager.getLine("l1")).thenReturn(line);

        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(false);

        Minecart initialCart = mock(Minecart.class);
        when(initialCart.getUniqueId()).thenReturn(UUID.randomUUID());

        Minecart activeCart = mock(Minecart.class);
        when(activeCart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(activeCart.isDead()).thenReturn(false);

        TrainMovementTask task = new TrainMovementTask(plugin, initialCart, passenger, "l1", "A");
        task.transferMinecart(activeCart);

        assertEquals(1, TrainMovementTask.shutdownActiveTasks());
        assertNull(TrainMovementTask.getTaskFor(activeCart));
        verify(activeCart).eject();
        verify(activeCart).remove();
    }

    @Test
    void shouldCancelSessionWhenPassengerLeavesBeforeEnteringStop() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        when(lineManager.getLine("l1")).thenReturn(line);

        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(null);
        when(passenger.getName()).thenReturn("Angus");

        TrainMovementTask task = new TrainMovementTask(
                plugin,
                minecart,
                passenger,
                "l1",
                "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
        );

        task.onTrainEnterStop(new TrainEnterStopEvent(minecart, new Stop("B", "Bravo")));

        verify(plugin).debug(eq("train_state_transitions"), contains("Task cancelled for passenger=Angus"));
    }
}
