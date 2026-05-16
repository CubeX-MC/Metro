package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.cubexmc.metro.model.RoutePoint;
import org.junit.jupiter.api.Test;

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
}
