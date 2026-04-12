package org.cubexmc.metro.spatial;

import org.bukkit.Location;

public class Point3D {
    public final double x, y, z;
    
    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public Point3D(Location loc) {
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
    }
}
