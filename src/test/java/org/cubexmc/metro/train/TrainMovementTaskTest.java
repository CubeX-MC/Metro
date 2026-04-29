package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainMovementTaskTest {

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
}
