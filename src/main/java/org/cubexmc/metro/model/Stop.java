package org.cubexmc.metro.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox; // Paper API import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代表地铁系统中的停靠区
 */
public class Stop {
    private String id;
    private String name;
    private Location corner1; // 区域第一个角点
    private Location corner2; // 区域第二个角点
    private transient BoundingBox boundingBox; // Paper API BoundingBox
    private Location stopPointLocation; // 停靠点位置，用于矿车生成位置
    private float launchYaw;
    private List<String> transferableLines; // 可换乘的线路ID列表

    // 自定义titles配置
    private Map<String, Map<String, String>> customTitles;

    /**
     * 创建新停靠区
     * 
     * @param id 停靠区ID
     * @param name 停靠区名称
     */
    public Stop(String id, String name) {
        this.id = id;
        this.name = name;
        this.transferableLines = new ArrayList<>();
        this.customTitles = new HashMap<>();
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
        
        // 加载可换乘线路ID列表
        this.transferableLines = section.getStringList("transferable_lines");
        if (this.transferableLines == null) {
            this.transferableLines = new ArrayList<>();
        }
        
        // 加载自定义titles配置
        this.customTitles = new HashMap<>();
        ConfigurationSection customTitlesSection = section.getConfigurationSection("custom_titles");
        if (customTitlesSection != null) {
            for (String titleType : customTitlesSection.getKeys(false)) {
                ConfigurationSection titleTypeSection = customTitlesSection.getConfigurationSection(titleType);
                if (titleTypeSection != null) {
                    Map<String, String> titleConfig = new HashMap<>();
                    for (String key : titleTypeSection.getKeys(false)) {
                        titleConfig.put(key, titleTypeSection.getString(key));
                    }
                    customTitles.put(titleType, titleConfig);
                }
            }
        }
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
        
        // 保存可换乘线路ID列表
        section.set("transferable_lines", transferableLines);
        
        // 保存自定义titles配置
        if (!customTitles.isEmpty()) {
            ConfigurationSection customTitlesSection = section.createSection("custom_titles");
            for (Map.Entry<String, Map<String, String>> entry : customTitles.entrySet()) {
                String titleType = entry.getKey();
                Map<String, String> titleConfig = entry.getValue();
                
                if (!titleConfig.isEmpty()) {
                    ConfigurationSection titleTypeSection = customTitlesSection.createSection(titleType);
                    for (Map.Entry<String, String> configEntry : titleConfig.entrySet()) {
                        titleTypeSection.set(configEntry.getKey(), configEntry.getValue());
                    }
                }
            }
        }
    }
    
    /**
     * 获取站点自定义title配置
     * 
     * @param titleType title类型(stop_continuous, arrive_stop, terminal_stop, departure)
     * @return 配置Map，如果不存在则返回null
     */
    public Map<String, String> getCustomTitle(String titleType) {
        return customTitles.get(titleType);
    }
    
    /**
     * 设置站点自定义title配置
     * 
     * @param titleType title类型(stop_continuous, arrive_stop, terminal_stop, departure)
     * @param config title配置
     */
    public void setCustomTitle(String titleType, Map<String, String> config) {
        customTitles.put(titleType, config);
    }
    
    /**
     * 移除站点自定义title配置
     * 
     * @param titleType title类型(stop_continuous, arrive_stop, terminal_stop, departure)
     * @return 是否成功移除
     */
    public boolean removeCustomTitle(String titleType) {
        return customTitles.remove(titleType) != null;
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
        if (location == null || location.getWorld() == null) {
            return false;
        }

        BoundingBox currentBox = getBoundingBox();
        if (currentBox == null) {
            // If corners are not set, or in different worlds, it's not in the stop.
            // Also, ensure corner1's world is checked as getBoundingBox might return null if corner1's world is null
            if (corner1 == null || corner1.getWorld() == null) {
                return false;
            }
            // Fallback to old logic if bounding box couldn't be created but corners might exist
            // This case should ideally not be hit if getBoundingBox is robust
            if (corner1 == null || corner2 == null) return false;
            if (!location.getWorld().equals(corner1.getWorld())) return false;

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

        // Check world equality before using BoundingBox.contains, as BoundingBox is world-agnostic.
        // corner1's world is confirmed non-null if currentBox is non-null by getBoundingBox() logic.
        if (!location.getWorld().equals(this.corner1.getWorld())) {
            return false;
        }

        return currentBox.contains(location.toVector());
    }

    /**
     * 获取由此停靠区的corner1和corner2定义的边界框。
     * 如果任一角点为null，则返回null。
     *
     * @return BoundingBox实例，如果角点有效，否则为null。
     */
    public BoundingBox getBoundingBox() {
        if (this.boundingBox == null && this.corner1 != null && this.corner2 != null) {
            if (this.corner1.getWorld() == null || !this.corner1.getWorld().equals(this.corner2.getWorld())) {
                // Cannot create a bounding box if worlds are different or null
                return null;
            }
            this.boundingBox = BoundingBox.of(this.corner1, this.corner2);
        }
        return this.boundingBox;
    }

    /**
     * 获取可换乘线路ID列表
     * 
     * @return 可换乘线路ID列表
     */
    public List<String> getTransferableLines() {
        return new ArrayList<>(transferableLines);
    }
    
    /**
     * 添加可换乘线路
     * 
     * @param lineId 线路ID
     * @return 如果线路不存在于列表中并成功添加则返回true
     */
    public boolean addTransferableLine(String lineId) {
        if (!transferableLines.contains(lineId)) {
            return transferableLines.add(lineId);
        }
        return false;
    }
    
    /**
     * 移除可换乘线路
     * 
     * @param lineId 线路ID
     * @return 如果线路存在于列表中并成功移除则返回true
     */
    public boolean removeTransferableLine(String lineId) {
        return transferableLines.remove(lineId);
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
        this.boundingBox = null; // Invalidate cached bounding box
    }

    public Location getCorner2() {
        return corner2;
    }

    public void setCorner2(Location corner2) {
        this.corner2 = corner2;
        this.boundingBox = null; // Invalidate cached bounding box
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