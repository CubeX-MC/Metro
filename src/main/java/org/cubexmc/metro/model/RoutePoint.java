package org.cubexmc.metro.model;

import org.bukkit.Location;

public record RoutePoint(String worldName, double x, double y, double z) {

    public static RoutePoint fromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new RoutePoint(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public static RoutePoint fromConfigString(String value) {
        if (value == null || value.isBlank()) {
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
}
