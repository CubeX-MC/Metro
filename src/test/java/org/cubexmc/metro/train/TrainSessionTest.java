package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainSessionTest {

    private Metro mockPlugin() {
        Metro plugin = mock(Metro.class);
        return plugin;
    }

    @Test
    void shouldResolveNextStopFromCurrentStop() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        assertEquals("B", session.getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, session.getState());
    }

    @Test
    void shouldFallbackToStoppedWhenNoNextStop() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        assertNull(session.getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, session.getState());
    }

    @Test
    void shouldFallbackToStoppedWhenLineIsNull() {
        Metro plugin = mockPlugin();
        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);

        TrainSession session = new TrainSession(plugin, cart, player, null, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        assertNull(session.getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, session.getState());
    }

    @Test
    void shouldDetectPassengerStillRiding() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getVehicle()).thenReturn(cart);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertTrue(session.isPassengerStillRiding());
    }

    @Test
    void shouldDetectPassengerNotRidingWhenOffline() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertFalse(session.isPassengerStillRiding());
    }

    @Test
    void shouldDetectPassengerNotRidingWhenLeftVehicle() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getVehicle()).thenReturn(null);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertFalse(session.isPassengerStillRiding());
    }

    @Test
    void shouldReturnUnknownWhenPassengerIsNull() {
        Metro plugin = mockPlugin();
        TrainSession session = new TrainSession(plugin, mock(Minecart.class), null,
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals("unknown", session.safePassengerName());
    }

    @Test
    void shouldReturnPlayerNameWhenPassengerExists() {
        Metro plugin = mockPlugin();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Angus");

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), player,
                new Line("l1", "L"), "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals("Angus", session.safePassengerName());
    }

    @Test
    void shouldRefreshTargetFromCurrentStop() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);

        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);

        TrainSession session = new TrainSession(plugin, cart, player, line, "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        assertEquals("B", session.getTargetStopId());

        session.setCurrentStopId("B");
        session.refreshTargetFromCurrentStop();

        assertEquals("C", session.getTargetStopId());
    }

    @Test
    void shouldSupportTeleportingFlag() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "L");
        line.addStop("A", -1);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                line, "A", TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        assertFalse(session.isTeleporting());
        session.setTeleporting(true);
        assertTrue(session.isTeleporting());
    }

    @Test
    void shouldDelegateDebugToPlugin() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "L");
        line.addStop("A", -1);

        TrainSession session = new TrainSession(plugin, mock(Minecart.class), mock(Player.class),
                line, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);

        session.debug("test message");
        verify(plugin).debug("train_state_transitions", "test message");
    }

    @Test
    void shouldAllowReplacingMinecart() {
        Metro plugin = mockPlugin();
        Line line = new Line("l1", "L");
        line.addStop("A", -1);

        Minecart original = mock(Minecart.class);
        Minecart replacement = mock(Minecart.class);

        TrainSession session = new TrainSession(plugin, original, mock(Player.class),
                line, "A", TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals(original, session.getMinecart());
        session.setMinecart(replacement);
        assertEquals(replacement, session.getMinecart());
    }
}
