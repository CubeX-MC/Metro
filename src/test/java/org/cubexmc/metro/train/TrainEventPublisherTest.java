package org.cubexmc.metro.train;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class TrainEventPublisherTest {

    @Test
    void shouldPublishEnteringStop() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);
            line.addStop("B", -1);

            Minecart cart = mock(Minecart.class);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);

            TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                    TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            Stop target = new Stop("B", "Bravo");
            publisher.publishEnteringStop(target);

            verify(pm).callEvent(any(MetroTrainArrivalEvent.class));
        }
    }

    @Test
    void shouldSkipEnteringStopWhenPassengerOffline() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);

            Minecart cart = mock(Minecart.class);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(false);

            TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                    TrainMovementTask.TrainState.STOPPED_AT_STATION);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishEnteringStop(new Stop("B", "Bravo"));

            verify(pm, never()).callEvent(any());
        }
    }

    @Test
    void shouldSkipEnteringStopWhenTargetStopIsNull() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);

            Minecart cart = mock(Minecart.class);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);

            TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                    TrainMovementTask.TrainState.STOPPED_AT_STATION);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishEnteringStop(null);

            verify(pm, never()).callEvent(any());
        }
    }

    @Test
    void shouldPublishDockedAtStop() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);

            TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                    line, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishDockedAtStop(new Stop("A", "Alpha"), false);

            verify(pm).callEvent(any(MetroTrainArrivalEvent.class));
        }
    }

    @Test
    void shouldSkipDockedAtStopWhenLineIsNull() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                    null, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishDockedAtStop(new Stop("A", "Alpha"), false);

            verify(pm, never()).callEvent(any());
        }
    }

    @Test
    void shouldPublishDeparture() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);
            line.addStop("B", -1);

            TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                    line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishDeparture(new Stop("A", "Alpha"), new Stop("B", "Bravo"));

            verify(pm).callEvent(any(MetroTrainDepartureEvent.class));
        }
    }

    @Test
    void shouldSkipDepartureWhenNextStopIsNull() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);

            Metro plugin = mock(Metro.class);
            Line line = new Line("l1", "Line1");
            line.addStop("A", -1);

            TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                    line, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);

            TrainEventPublisher publisher = new TrainEventPublisher(session);
            publisher.publishDeparture(new Stop("A", "Alpha"), null);

            verify(pm, never()).callEvent(any());
        }
    }
}
