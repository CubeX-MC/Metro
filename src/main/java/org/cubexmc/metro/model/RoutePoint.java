package org.cubexmc.metro.model;

import org.bukkit.Location;

public final class RoutePoint {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;

    public RoutePoint(String worldName, double x, double y, double z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public static RoutePoint fromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new RoutePoint(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public static RoutePoint fromConfigString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }

        try {
            return new RoutePoint(
                    parts[0],
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String toConfigString() {
        return worldName + "," + x + "," + y + "," + z;
    }

    public double distanceSquared(RoutePoint other) {
        if (other == null || !worldName.equals(other.worldName())) {
            return Double.MAX_VALUE;
        }
        double dx = x - other.x();
        double dy = y - other.y();
        double dz = z - other.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoutePoint)) return false;
        RoutePoint that = (RoutePoint) o;
        return Double.compare(that.x, x) == 0
                && Double.compare(that.y, y) == 0
                && Double.compare(that.z, z) == 0
                && worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = worldName.hashCode();
        temp = Double.doubleToLongBits(x);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
