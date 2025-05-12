package org.cubexmc.metro.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 代表地铁系统中的停靠区
 */
public class Stop {
    private String id;
    private String name;
    private Location corner1; // 区域第一个角点
    private Location corner2; // 区域第二个角点
    private Location stopPointLocation; // 停靠点位置，用于矿车生成位置
    private float launchYaw;
    
    /**
     * 创建新停靠区
     * 
     * @param id 停靠区ID
     * @param name 停靠区名称
     */
    public Stop(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    /**
     * 从配置节加载停靠区
     * 
     * @param id 停靠区ID
     * @param section 配置节
     */
    public Stop(String id, ConfigurationSection section) {
        this.id = id;
        this.name = section.getString("display_name", "");
        
        String corner1String = section.getString("corner1_location");
        if (corner1String != null) {
            this.corner1 = locationFromString(corner1String);
        }
        
        String corner2String = section.getString("corner2_location");
        if (corner2String != null) {
            this.corner2 = locationFromString(corner2String);
        }
        
        String locString = section.getString("stoppoint_location");
        if (locString != null) {
            this.stopPointLocation = locationFromString(locString);
        }
        
        this.launchYaw = (float) section.getDouble("launch_yaw", 0.0);
    }
    
    /**
     * 将停靠区保存到配置节
     * 
     * @param section 目标配置节
     */
    public void saveToConfig(ConfigurationSection section) {
        section.set("display_name", name);
        
        if (corner1 != null) {
            section.set("corner1_location", locationToString(corner1));
        }
        
        if (corner2 != null) {
            section.set("corner2_location", locationToString(corner2));
        }
        
        if (stopPointLocation != null) {
            section.set("stoppoint_location", locationToString(stopPointLocation));
        }
        
        section.set("launch_yaw", launchYaw);
    }
    
    /**
     * 将位置转换为字符串表示
     */
    private String locationToString(Location location) {
        return String.format("%s,%d,%d,%d", 
                location.getWorld().getName(),
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ());
    }
    
    /**
     * 从字符串解析位置
     */
    private Location locationFromString(String locString) {
        String[] parts = locString.split(",");
        if (parts.length != 4) {
            return null;
        }
        
        try {
            return new Location(
                    org.bukkit.Bukkit.getWorld(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查指定位置是否在停靠区区域内
     * 
     * @param location 要检查的位置
     * @return 是否在区域内
     */
    public boolean isInStop(Location location) {
        if (corner1 == null || corner2 == null || location == null || 
                location.getWorld() == null || 
                !location.getWorld().equals(corner1.getWorld())) {
            return false;
        }
        
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Location getCorner1() {
        return corner1;
    }
    
    public void setCorner1(Location corner1) {
        this.corner1 = corner1;
    }
    
    public Location getCorner2() {
        return corner2;
    }
    
    public void setCorner2(Location corner2) {
        this.corner2 = corner2;
    }
    
    public Location getStopPointLocation() {
        return stopPointLocation;
    }
    
    public void setStopPointLocation(Location stopPointLocation) {
        this.stopPointLocation = stopPointLocation;
    }
    
    public float getLaunchYaw() {
        return launchYaw;
    }
    
    public void setLaunchYaw(float launchYaw) {
        this.launchYaw = launchYaw;
    }
} 