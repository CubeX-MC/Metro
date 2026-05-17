package org.cubexmc.metro.spatial;

import org.bukkit.Location;

/**
 * An immutable 3D point used for spatial queries in the octree index.
 * <p>
 * Used together with {@link Range3D} to test point-in-range containment.
 */
public class Point3D {
    public final double x, y, z;

    /**
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a point from a Bukkit {@link Location}.
     */
    public Point3D(Location loc) {
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
    }
}
