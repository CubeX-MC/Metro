package org.cubexmc.metro.train;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrainTerminalTest {

    @AfterEach
    void tearDown() {
        TrainMovementTask.shutdownActiveTasks();
    }

    @Test
    void shouldTransitionToMovingInStationWhenEnteringTerminalStop() {
        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mockPlugin();
            Minecart minecart = mockMinecart();
            Player passenger = mockPassenger(minecart);

            TrainMovementTask task = new TrainMovementTask(
                    plugin, minecart, passenger, "l1", "A",
                    TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
            );

            Stop terminalStop = new Stop("B", "Bravo");
            task.onTrainEnterStop(new TrainEnterStopEvent(minecart, terminalStop));

            // At terminal, session transitions to MOVING_IN_STATION first
            assertEquals(TrainMovementTask.TrainState.MOVING_IN_STATION,
                    task.getSession().getState());

            // Should have published an entering event for terminal
            verify(pm).callEvent(any(org.cubexmc.metro.event.MetroTrainArrivalEvent.class));
        }
    }

    @Test
    void shouldHandleSessionStateAfterCreation() {
        Metro plugin = mockPlugin();

        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(minecart);
        when(passenger.getName()).thenReturn("Tester");

        TrainMovementTask task = new TrainMovementTask(
                plugin, minecart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS
        );

        TrainSession session = task.getSession();
        assertEquals("A", session.getCurrentStopId());
        assertEquals("B", session.getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, session.getState());
    }

    private Metro mockPlugin() {
        Metro plugin = mock(Metro.class);
        ConfigFacade config = mock(ConfigFacade.class);
        when(plugin.getConfigFacade()).thenReturn(config);
        when(config.getCartSpeed()).thenReturn(0.4);
        when(plugin.getScoreboardManager()).thenReturn(mock(org.cubexmc.metro.train.ScoreboardManager.class));

        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        when(lineManager.getLine("l1")).thenReturn(line);
        return plugin;
    }

    private Minecart mockMinecart() {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(minecart.getLocation()).thenReturn(mock(org.bukkit.Location.class));
        when(minecart.getVelocity()).thenReturn(new Vector(0, 0, 0));
        when(minecart.getMaxSpeed()).thenReturn(0.4);
        return minecart;
    }

    private Player mockPassenger(Minecart minecart) {
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(minecart);
        when(passenger.getName()).thenReturn("Tester");
        return passenger;
    }

    static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
