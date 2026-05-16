package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainStateMachineTest {

    @Test
    void shouldTransitionStateAndLog() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainStateMachine sm = new TrainStateMachine(session);

        TrainMovementTask.TrainState previous = sm.transitionTo(
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, "departing");

        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, previous);
        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, session.getState());

        verify(plugin).debug(eq("train_state_transitions"),
                contains("STOPPED_AT_STATION -> MOVING_BETWEEN_STATIONS"));
    }

    @Test
    void shouldOmitBlankDetail() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainStateMachine sm = new TrainStateMachine(session);
        sm.transitionTo(TrainMovementTask.TrainState.STOPPED_AT_STATION, "");

        verify(plugin).debug(eq("train_state_transitions"),
                contains("STOPPED_AT_STATION -> STOPPED_AT_STATION for"));
    }

    @Test
    void shouldHandleNullDetail() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainStateMachine sm = new TrainStateMachine(session);
        TrainMovementTask.TrainState previous = sm.transitionTo(
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, null);

        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, previous);
    }
}
