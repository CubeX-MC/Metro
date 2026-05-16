package org.cubexmc.metro.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.cubexmc.metro.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Normalizes raw minecart route recordings by snapping float-point positions
 * to nearest rail block centers and retaining direction / world / Y change points.
 */
public final class RouteNormalizer {

    private static final Logger LOGGER = Logger.getGlobal();
    private static final double MAX_SNAP_DISTANCE = 3.0;

    private final Material[] railMaterials;

    public RouteNormalizer() {
        this.railMaterials = new Material[] {
                Material.RAIL,
                Material.POWERED_RAIL,
                Material.DETECTOR_RAIL,
                Material.ACTIVATOR_RAIL
        };
    }

    /**
     * Snap each route point to the center of the nearest rail block,
     * then remove redundant collinear points from the result.
     *
     * @param points      raw route points from recording
     * @param simplifyEpsilon max distance from line before a point is kept (0 = no simplification)
     * @return cleaned list of route points
     */
    public List<RoutePoint> normalize(List<RoutePoint> points, double simplifyEpsilon) {
        if (points == null || points.isEmpty()) {
            return points == null ? List.of() : points;
        }

        List<RoutePoint> snapped = snapToRailCenters(points);
        if (simplifyEpsilon > 0 && snapped.size() >= 3) {
            snapped = simplifyCollinearPoints(snapped, simplifyEpsilon);
        }
        return snapped;
    }

    private List<RoutePoint> snapToRailCenters(List<RoutePoint> points) {
        List<RoutePoint> result = new ArrayList<>(points.size());
        int missed = 0;

        for (RoutePoint point : points) {
            RoutePoint snapped = snapPoint(point);
            if (snapped != null) {
                result.add(snapped);
            } else {
                missed++;
            }
        }

        if (missed > 0) {
            LOGGER.log(Level.INFO,
                    "[RouteNormalizer] {0}/{1} points could not be snapped to a rail block",
                    new Object[] { missed, points.size() });
        }
        return result;
    }

    private RoutePoint snapPoint(RoutePoint point) {
        if (point == null) return null;
        World world;
        try {
            world = Bukkit.getWorld(point.worldName());
        } catch (Exception e) {
            return point;
        }
        if (world == null) return point;

        try {
            return snapPointInWorld(point, world);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[RouteNormalizer] Failed to snap point", e);
            return point;
        }
    }

    private RoutePoint snapPointInWorld(RoutePoint point, World world) {

        int baseX = (int) Math.floor(point.x());
        int baseY = (int) Math.floor(point.y());
        int baseZ = (int) Math.floor(point.z());

        int bestX = 0, bestY = 0, bestZ = 0;
        boolean found = false;
        double bestDistance = MAX_SNAP_DISTANCE * MAX_SNAP_DISTANCE;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;
                    Block block = world.getBlockAt(x, y, z);
                    if (!isRail(block)) continue;
                    double dist = distanceSquaredToBlockCenter(point, x, y, z);
                    if (dist < bestDistance) {
                        bestDistance = dist;
                        found = true;
                        bestX = x;
                        bestY = y;
                        bestZ = z;
                    }
                }
            }
        }

        if (found) {
            return new RoutePoint(point.worldName(), bestX + 0.5, bestY + 0.5, bestZ + 0.5);
        }
        return point;
    }

    private boolean isRail(Block block) {
        Material type = block.getType();
        for (Material rail : railMaterials) {
            if (type == rail) return true;
        }
        return false;
    }

    private double distanceSquaredToBlockCenter(RoutePoint point, int x, int y, int z) {
        double dx = point.x() - (x + 0.5);
        double dy = point.y() - (y + 0.5);
        double dz = point.z() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private List<RoutePoint> simplifyCollinearPoints(List<RoutePoint> points, double epsilon) {
        List<RoutePoint> result = new ArrayList<>();
        result.add(points.get(0));
        double epsSq = epsilon * epsilon;

        for (int i = 1; i < points.size() - 1; i++) {
            RoutePoint prev = result.get(result.size() - 1);
            RoutePoint curr = points.get(i);
            RoutePoint next = points.get(i + 1);

            if (!isRedundantCollinear(prev, curr, next, epsSq)) {
                result.add(curr);
            }
        }
        result.add(points.get(points.size() - 1));
        return result;
    }

    private boolean isRedundantCollinear(RoutePoint a, RoutePoint b, RoutePoint c, double epsSq) {
        if (!a.worldName().equals(b.worldName()) || !b.worldName().equals(c.worldName())) {
            return false;
        }

        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double abz = b.z() - a.z();
        double acx = c.x() - a.x();
        double acy = c.y() - a.y();
        double acz = c.z() - a.z();

        double abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 1e-12) return true;

        double acLenSq = acx * acx + acy * acy + acz * acz;
        if (acLenSq < 1e-12) return false;

        double dot = abx * acx + aby * acy + abz * acz;
        if (dot < 0) return false;
        if (dot * dot > abLenSq * acLenSq) return false;

        double crossX = aby * acz - abz * acy;
        double crossY = abz * acx - abx * acz;
        double crossZ = abx * acy - aby * acx;
        double distSq = (crossX * crossX + crossY * crossY + crossZ * crossZ) / acLenSq;

        return distSq <= epsSq;
    }
}
