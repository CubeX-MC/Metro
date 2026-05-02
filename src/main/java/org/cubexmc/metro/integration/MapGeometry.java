package org.cubexmc.metro.integration;

import org.bukkit.Location;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class MapGeometry {

    private static final double SAME_COORDINATE_EPSILON = 0.0001D;

    private MapGeometry() {
    }

    static List<RoutePoint> orthogonalRoutePoints(List<RoutePoint> routePoints, String worldName) {
        List<RoutePoint> result = new ArrayList<>();
        if (routePoints == null || worldName == null || worldName.isBlank()) {
            return result;
        }

        RoutePoint previous = null;
        for (RoutePoint current : routePoints) {
            if (current == null || !worldName.equals(current.worldName())) {
                continue;
            }
            if (previous == null) {
                addIfDifferent(result, current);
                previous = current;
                continue;
            }

            double dx = Math.abs(current.x() - previous.x());
            double dz = Math.abs(current.z() - previous.z());
            if (dx > SAME_COORDINATE_EPSILON && dz > SAME_COORDINATE_EPSILON) {
                RoutePoint elbow = dx >= dz
                        ? new RoutePoint(worldName, current.x(), previous.y(), previous.z())
                        : new RoutePoint(worldName, previous.x(), previous.y(), current.z());
                addIfDifferent(result, elbow);
            }
            addIfDifferent(result, current);
            previous = current;
        }
        return result;
    }

    static Optional<StopBounds> stopBounds(Stop stop) {
        if (stop == null || stop.getCorner1() == null || stop.getCorner2() == null) {
            return Optional.empty();
        }

        Location corner1 = stop.getCorner1();
        Location corner2 = stop.getCorner2();
        if (corner1.getWorld() == null || corner2.getWorld() == null || !corner1.getWorld().equals(corner2.getWorld())) {
            return Optional.empty();
        }

        double minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        double maxX = Math.max(corner1.getBlockX(), corner2.getBlockX()) + 1.0D;
        double minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        double maxY = Math.max(corner1.getBlockY(), corner2.getBlockY()) + 1.0D;
        double minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        double maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ()) + 1.0D;
        return Optional.of(new StopBounds(corner1.getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static void addIfDifferent(List<RoutePoint> points, RoutePoint point) {
        if (points.isEmpty() || !samePosition(points.get(points.size() - 1), point)) {
            points.add(point);
        }
    }

    private static boolean samePosition(RoutePoint left, RoutePoint right) {
        return left.worldName().equals(right.worldName())
                && Math.abs(left.x() - right.x()) <= SAME_COORDINATE_EPSILON
                && Math.abs(left.y() - right.y()) <= SAME_COORDINATE_EPSILON
                && Math.abs(left.z() - right.z()) <= SAME_COORDINATE_EPSILON;
    }

    record StopBounds(String worldName, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double centerX() {
            return (minX + maxX) / 2.0D;
        }

        double centerY() {
            return (minY + maxY) / 2.0D;
        }

        double centerZ() {
            return (minZ + maxZ) / 2.0D;
        }
    }
}
