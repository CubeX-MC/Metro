package org.cubexmc.metro.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

import java.util.List;

class MapGeometryTest {

    @Test
    void shouldConvertDiagonalSegmentsToOrthogonalElbows() {
        List<RoutePoint> points = MapGeometry.orthogonalRoutePoints(List.of(
                new RoutePoint("world", 0.0, 64.0, 0.0),
                new RoutePoint("world", 10.0, 65.0, 4.0),
                new RoutePoint("world", 10.0, 65.0, 12.0)
        ), "world");

        assertEquals(4, points.size());
        assertEquals(new RoutePoint("world", 0.0, 64.0, 0.0), points.get(0));
        assertEquals(new RoutePoint("world", 10.0, 64.0, 0.0), points.get(1));
        assertEquals(new RoutePoint("world", 10.0, 65.0, 4.0), points.get(2));
        assertEquals(new RoutePoint("world", 10.0, 65.0, 12.0), points.get(3));
    }

    @Test
    void shouldCreateInclusiveStopBoundsFromCorners() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Stop stop = new Stop("central", "Central");
        stop.setCorner1(new Location(world, 10.0, 70.0, 12.0));
        stop.setCorner2(new Location(world, 5.0, 64.0, 6.0));

        MapGeometry.StopBounds bounds = MapGeometry.stopBounds(stop).orElseThrow();

        assertEquals("world", bounds.worldName());
        assertEquals(5.0, bounds.minX());
        assertEquals(11.0, bounds.maxX());
        assertEquals(64.0, bounds.minY());
        assertEquals(71.0, bounds.maxY());
        assertEquals(6.0, bounds.minZ());
        assertEquals(13.0, bounds.maxZ());
    }

    @Test
    void shouldSkipStopBoundsWhenCornersAreInDifferentWorlds() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        Stop stop = new Stop("central", "Central");
        stop.setCorner1(new Location(world, 0.0, 64.0, 0.0));
        stop.setCorner2(new Location(otherWorld, 5.0, 70.0, 5.0));

        assertTrue(MapGeometry.stopBounds(stop).isEmpty());
    }
}
