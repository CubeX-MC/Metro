package org.cubexmc.metro.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 代表一个矿车传送门。
 * 当 Metro 矿车经过传送门入口铁轨下方的触发方块时，
 * 矿车和乘客将被传送到目标位置。
 */
public class Portal {

    private final String id;

    // 入口坐标（方块级精度）
    private String worldName;
    private int x, y, z;

    // 目标坐标（精确级精度）
    private String destWorldName;
    private double destX, destY, destZ;
    private float destYaw;

    // 可选的双向配对
    private String linkedPortalId;

    public Portal(String id) {
        this.id = id;
    }

    // =============== 序列化 / 反序列化 ===============

    /**
     * 从 ConfigurationSection 反序列化
     */
    public static Portal fromConfig(String id, ConfigurationSection section) {
        Portal portal = new Portal(id);
        portal.worldName = section.getString("world", "world");
        portal.x = section.getInt("x");
        portal.y = section.getInt("y");
        portal.z = section.getInt("z");
        portal.destWorldName = section.getString("dest_world", "world");
        portal.destX = section.getDouble("dest_x");
        portal.destY = section.getDouble("dest_y");
        portal.destZ = section.getDouble("dest_z");
        portal.destYaw = (float) section.getDouble("dest_yaw", 0.0);
        portal.linkedPortalId = section.getString("linked", null);
        return portal;
    }

    /**
     * 序列化到 ConfigurationSection
     */
    public void toConfig(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("dest_world", destWorldName);
        section.set("dest_x", destX);
        section.set("dest_y", destY);
        section.set("dest_z", destZ);
        section.set("dest_yaw", (double) destYaw);
        if (linkedPortalId != null) {
            section.set("linked", linkedPortalId);
        }
    }

    // =============== 核心方法 ===============

    /**
     * 检查给定位置的方块坐标是否匹配此传送门入口
     */
    public boolean matchesLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getWorld().getName().equals(worldName)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    /**
     * 获取目标 Location 对象
     */
    public Location getDestination() {
        World world = Bukkit.getWorld(destWorldName);
        if (world == null) return null;
        Location dest = new Location(world, destX, destY, destZ);
        dest.setYaw(destYaw);
        return dest;
    }

    /**
     * 从玩家当前位置设置入口点（方块坐标）
     */
    public void setEntrance(Location loc) {
        this.worldName = loc.getWorld().getName();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    /**
     * 从玩家当前位置设置目标点（精确坐标）
     */
    public void setDestination(Location loc) {
        this.destWorldName = loc.getWorld().getName();
        this.destX = loc.getX();
        this.destY = loc.getY();
        this.destZ = loc.getZ();
        this.destYaw = loc.getYaw();
    }

    // =============== Getters / Setters ===============

    public String getId() { return id; }
    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getDestWorldName() { return destWorldName; }
    public double getDestX() { return destX; }
    public double getDestY() { return destY; }
    public double getDestZ() { return destZ; }
    public float getDestYaw() { return destYaw; }
    public String getLinkedPortalId() { return linkedPortalId; }
    public void setLinkedPortalId(String linkedPortalId) { this.linkedPortalId = linkedPortalId; }
}
