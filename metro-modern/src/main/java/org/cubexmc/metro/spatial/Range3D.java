package org.cubexmc.metro.spatial;

public class Range3D {
    public final double minX, minY, minZ, maxX, maxY, maxZ;

    public Range3D(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(Point3D p) {
        return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY && p.z >= minZ && p.z <= maxZ;
    }

    public boolean intersects(Range3D o) {
        return (minX <= o.maxX && maxX >= o.minX) &&
               (minY <= o.maxY && maxY >= o.minY) &&
               (minZ <= o.maxZ && maxZ >= o.minZ);
    }

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
