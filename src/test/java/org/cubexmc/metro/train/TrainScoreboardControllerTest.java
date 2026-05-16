package org.cubexmc.metro.train;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainScoreboardControllerTest {

    @Test
    void shouldUpdateTerminalScoreboardWhenStoppedAtTerminal() {
        Metro plugin = mock(Metro.class);
        ScoreboardManager sbManager = mock(ScoreboardManager.class);
        when(plugin.getScoreboardManager()).thenReturn(sbManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(sbManager).updateTerminalScoreboard(player, line, "A");
    }

    @Test
    void shouldUpdateEnteringScoreboardWhenStoppedAtNonTerminal() {
        Metro plugin = mock(Metro.class);
        ScoreboardManager sbManager = mock(ScoreboardManager.class);
        when(plugin.getScoreboardManager()).thenReturn(sbManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(sbManager).updateEnteringStopScoreboard(player, line, "A");
    }

    @Test
    void shouldUpdateTravelingScoreboardWhenMovingInStation() {
        Metro plugin = mock(Metro.class);
        ScoreboardManager sbManager = mock(ScoreboardManager.class);
        when(plugin.getScoreboardManager()).thenReturn(sbManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(sbManager).updateTravelingScoreboard(player, line, "B");
    }

    @Test
    void shouldSkipWhenPassengerOffline() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(plugin, never()).getScoreboardManager();
    }

    @Test
    void shouldSkipWhenLineIsNull() {
        Metro plugin = mock(Metro.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, null, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(plugin, never()).getScoreboardManager();
    }

    @Test
    void shouldDoNothingWhenMovingBetweenStations() {
        Metro plugin = mock(Metro.class);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player, line, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        TrainScoreboardController controller = new TrainScoreboardController();
        controller.updateBasedOnState(session);

        verify(plugin, never()).getScoreboardManager();
    }
}
