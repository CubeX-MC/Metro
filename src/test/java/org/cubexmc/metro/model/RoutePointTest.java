package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class RoutePointTest {

    @Test
    void shouldParseAndSerializeConfigString() {
        RoutePoint point = RoutePoint.fromConfigString("world,1.5,64.0,-2.25");

        assertEquals(new RoutePoint("world", 1.5, 64.0, -2.25), point);
        assertEquals("world,1.5,64.0,-2.25", point.toConfigString());
    }

    @Test
    void shouldRejectInvalidConfigString() {
        assertNull(RoutePoint.fromConfigString("world,1,2"));
        assertNull(RoutePoint.fromConfigString("world,x,2,3"));
    }

    @Test
    void shouldCreateFromLocationAndMeasureDistance() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        RoutePoint first = RoutePoint.fromLocation(new Location(world, 1.0, 2.0, 3.0));
        RoutePoint second = new RoutePoint("world", 4.0, 6.0, 3.0);

        assertEquals(new RoutePoint("world", 1.0, 2.0, 3.0), first);
        assertEquals(25.0, first.distanceSquared(second));
        assertTrue(first.distanceSquared(new RoutePoint("other", 1.0, 2.0, 3.0)) > 1_000_000.0);
    }
}
