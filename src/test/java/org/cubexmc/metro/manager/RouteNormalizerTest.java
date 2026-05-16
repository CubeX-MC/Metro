package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.cubexmc.metro.model.RoutePoint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RouteNormalizerTest {

    private final RouteNormalizer normalizer = new RouteNormalizer();

    @Test
    void shouldReturnEmptyForNullInput() {
        assertTrue(normalizer.normalize(null, 0).isEmpty());
    }

    @Test
    void shouldReturnSamePointsWhenNoSimplifyEpsilon() {
        List<RoutePoint> points = List.of(
                new RoutePoint("world", 0.0, 64.0, 0.0),
                new RoutePoint("world", 10.0, 64.0, 0.0),
                new RoutePoint("world", 20.0, 64.0, 0.0));
        List<RoutePoint> result = normalizer.normalize(points, 0);
        assertEquals(3, result.size());
    }

    @Test
    void shouldRemoveCollinearIntermediatePoints() {
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 5.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 10.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 15.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 20.0, 64.0, 0.0));

        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertEquals(2, result.size());
        assertEquals(0.0, result.get(0).x());
        assertEquals(20.0, result.get(1).x());
    }

    @Test
    void shouldKeepDirectionChangePoints() {
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 5.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 10.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 10.0, 64.0, 5.0));
        points.add(new RoutePoint("world", 10.0, 64.0, 10.0));

        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertEquals(3, result.size());
        assertEquals(0.0, result.get(0).x());
        assertEquals(0.0, result.get(0).z());
        assertEquals(10.0, result.get(1).x());
        assertEquals(10.0, result.get(2).z());
    }

    @Test
    void shouldKeepYChangePoints() {
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 5.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 10.0, 70.0, 0.0));
        points.add(new RoutePoint("world", 15.0, 70.0, 0.0));

        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertTrue(result.size() >= 3);
    }

    @Test
    void shouldKeepWorldChangePoints() {
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 5.0, 64.0, 0.0));
        points.add(new RoutePoint("world_nether", 5.0, 64.0, 0.0));
        points.add(new RoutePoint("world_nether", 10.0, 64.0, 0.0));

        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertTrue(result.size() >= 2);
    }

    @Test
    void shouldHandleDuplicatePoints() {
        List<RoutePoint> points = new ArrayList<>();
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 0.0, 64.0, 0.0));
        points.add(new RoutePoint("world", 10.0, 64.0, 0.0));

        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertEquals(2, result.size());
    }

    @Test
    void shouldHandleSinglePoint() {
        List<RoutePoint> points = List.of(new RoutePoint("world", 0.0, 64.0, 0.0));
        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertEquals(1, result.size());
    }

    @Test
    void shouldHandleTwoPoints() {
        List<RoutePoint> points = List.of(
                new RoutePoint("world", 0.0, 64.0, 0.0),
                new RoutePoint("world", 10.0, 64.0, 0.0));
        List<RoutePoint> result = normalizer.normalize(points, 0.2);
        assertEquals(2, result.size());
    }

    @Test
    void shouldSnapPointToNearestRailBlockCenter() {
        World world = mock(World.class);
        Block air = block(Material.AIR);
        Block rail = block(Material.POWERED_RAIL);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(4, 64, 4)).thenReturn(rail);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            List<RoutePoint> result = normalizer.normalize(
                    List.of(new RoutePoint("world", 4.7, 64.3, 4.6)),
                    0);

            assertEquals(1, result.size());
            assertEquals(4.5, result.get(0).x());
            assertEquals(64.5, result.get(0).y());
            assertEquals(4.5, result.get(0).z());
        }
    }

    @Test
    void shouldKeepOriginalPointWhenNoNearbyRailExists() {
        World world = mock(World.class);
        Block air = block(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        RoutePoint point = new RoutePoint("world", 4.7, 64.3, 4.6);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            List<RoutePoint> result = normalizer.normalize(List.of(point), 0);

            assertEquals(point, result.get(0));
        }
    }

    private Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }
}
