package org.cubexmc.metro.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class MetroEventTest {

    @Test
    void shouldBuildArrivalEventWithAllFields() {
        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);
        Line line = new Line("l1", "Line1");
        Stop stop = new Stop("A", "Alpha");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(cart, player, line, stop, true,
                MetroTrainArrivalEvent.ArrivalType.DOCKED);

        assertEquals(cart, event.getMinecart());
        assertEquals(player, event.getPassenger());
        assertEquals(line, event.getLine());
        assertEquals(stop, event.getCurrentStop());
        assertFalse(event.isTerminus() == false);
        assertEquals(MetroTrainArrivalEvent.ArrivalType.DOCKED, event.getArrivalType());
        assertNotNull(event.getHandlers());
        assertNotNull(MetroTrainArrivalEvent.getHandlerList());
    }

    @Test
    void shouldBuildArrivalEventAsEntering() {
        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                mock(Minecart.class), mock(Player.class), new Line("l1", "L"),
                new Stop("A", "A"), false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        assertEquals(MetroTrainArrivalEvent.ArrivalType.ENTERING, event.getArrivalType());
        assertFalse(event.isTerminus());
    }

    @Test
    void shouldBuildDepartureEventWithAllFields() {
        Minecart cart = mock(Minecart.class);
        Player player = mock(Player.class);
        Line line = new Line("l1", "Line1");
        Stop current = new Stop("A", "Alpha");
        Stop next = new Stop("B", "Bravo");

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(cart, player, line, current, next);

        assertEquals(cart, event.getMinecart());
        assertEquals(player, event.getPassenger());
        assertEquals(line, event.getLine());
        assertEquals(current, event.getCurrentStop());
        assertEquals(next, event.getNextStop());
        assertNotNull(event.getHandlers());
        assertNotNull(MetroTrainDepartureEvent.getHandlerList());
    }

    @Test
    void shouldVerifyArrivalTypeEnumValues() {
        assertEquals(2, MetroTrainArrivalEvent.ArrivalType.values().length);
        assertEquals("ENTERING", MetroTrainArrivalEvent.ArrivalType.ENTERING.name());
        assertEquals("DOCKED", MetroTrainArrivalEvent.ArrivalType.DOCKED.name());
    }
}
