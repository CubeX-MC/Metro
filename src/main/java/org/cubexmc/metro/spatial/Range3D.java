package org.cubexmc.metro.spatial;

/**
 * An immutable, axis-aligned 3D bounding box (AABB).
 * <p>
 * Uses half-open intervals ({@code [min, max)}) in each dimension,
 * consistent with Minecraft's block boundary convention.
 * <p>
 * Used as both the spatial extent of octree nodes and as the key for
 * items stored in the {@link Octree}.
 */
public class Range3D {
    public final double minX, minY, minZ, maxX, maxY, maxZ;

    /**
     * Creates a Range3D from any two opposite corners.  The
     * constructor automatically sorts coordinates so that
     * {@code min ≤ max} in each dimension.
     *
     * @param x1 X of first corner
     * @param y1 Y of first corner
     * @param z1 Z of first corner
     * @param x2 X of opposite corner
     * @param y2 Y of opposite corner
     * @param z2 Z of opposite corner
     */
    public Range3D(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Tests whether a point lies inside this range (half-open intervals).
     *
     * @param p the point to test
     * @return {@code true} if the point is within the range
     */
    public boolean contains(Point3D p) {
        return p.x >= minX && p.x < maxX && p.y >= minY && p.y < maxY && p.z >= minZ && p.z < maxZ;
    }

    /**
     * Tests whether this range overlaps another range.
     *
     * @param o the other range to test
     * @return {@code true} if the two ranges intersect
     */
    public boolean intersects(Range3D o) {
        return (minX <= o.maxX && maxX >= o.minX) &&
               (minY <= o.maxY && maxY >= o.minY) &&
               (minZ <= o.maxZ && maxZ >= o.minZ);
    }

    /**
     * Subdivides this range into eight equal-octant children.
     * <p>
     * The splitting plane is at the midpoint of each dimension.
     *
     * @return an array of exactly 8 non-overlapping {@code Range3D}s
     *         that partition this range
     */
    public Range3D[] subdivide() {
        double mx = (minX + maxX) / 2;
        double my = (minY + maxY) / 2;
        double mz = (minZ + maxZ) / 2;
        return new Range3D[] {
            new Range3D(minX, minY, minZ, mx, my, mz),
            new Range3D(mx, minY, minZ, maxX, my, mz),
            new Range3D(minX, my, minZ, mx, maxY, mz),
            new Range3D(mx, my, minZ, maxX, maxY, mz),
            new Range3D(minX, minY, mz, mx, my, maxZ),
            new Range3D(mx, minY, mz, maxX, my, maxZ),
            new Range3D(minX, my, mz, mx, maxY, maxZ),
            new Range3D(mx, my, mz, maxX, maxY, maxZ)
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Range3D)) return false;
        Range3D o = (Range3D) obj;
        return Double.compare(o.minX, minX) == 0 && Double.compare(o.minY, minY) == 0 && Double.compare(o.minZ, minZ) == 0 &&
               Double.compare(o.maxX, maxX) == 0 && Double.compare(o.maxY, maxY) == 0 && Double.compare(o.maxZ, maxZ) == 0;
    }

    @Override
    public int hashCode() {
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(minX); result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minY); result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minZ); result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxX); result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxY); result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxZ); result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
