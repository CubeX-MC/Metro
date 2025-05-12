package org.cubexmc.metro.manager;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Stop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理停靠区数据的加载、保存和访问
 */
public class StopManager {
    
    private final Metro plugin;
    private final File configFile;
    private FileConfiguration config;
    
    // 缓存数据
    private final Map<String, Stop> stops = new HashMap<>();
    private final Map<Location, String> locationToStopId = new HashMap<>();
    
    /**
     * 创建停靠区管理器
     * 
     * @param plugin Metro插件实例
     */
    public StopManager(Metro plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "stops.yml");
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("stops.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        stops.clear();
        locationToStopId.clear();
        
        // 加载所有停靠区
        ConfigurationSection stopsSection = config.getConfigurationSection("");
        if (stopsSection != null) {
            Set<String> stopIds = stopsSection.getKeys(false);
            for (String stopId : stopIds) {
                ConfigurationSection stopSection = stopsSection.getConfigurationSection(stopId);
                if (stopSection != null) {
                    Stop stop = new Stop(stopId, stopSection);
                    stops.put(stopId, stop);
                    
                    // 缓存位置映射
                    if (stop.getStopPointLocation() != null) {
                        locationToStopId.put(stop.getStopPointLocation(), stopId);
                    }
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + stops.size() + " stops");
    }
    
    /**
     * 保存配置到文件
     */
    public void saveConfig() {
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            Stop stop = entry.getValue();
            
            ConfigurationSection section = config.createSection(stopId);
            stop.saveToConfig(section);
        }
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save stops config: " + e.getMessage());
        }
    }
    
    /**
     * 创建新停靠区
     * 
     * @param stopId 停靠区ID
     * @param displayName 停靠区显示名称
     * @return 创建的停靠区
     */
    public Stop createStop(String stopId, String displayName) {
        if (stops.containsKey(stopId)) {
            return null; // 已存在
        }
        
        Stop stop = new Stop(stopId, displayName);
        stops.put(stopId, stop);
        saveConfig();
        return stop;
    }
    
    /**
     * 删除停靠区
     * 
     * @param stopId 要删除的停靠区ID
     * @return 是否成功删除
     */
    public boolean deleteStop(String stopId) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            return false;
        }
        
        // 从所有线路中移除
        LineManager lineManager = plugin.getLineManager();
        lineManager.removeStopFromAllLines(stopId);
        
        // 移除位置映射
        if (stop.getStopPointLocation() != null) {
            locationToStopId.remove(stop.getStopPointLocation());
        }
        
        // 从内存和配置中移除
        stops.remove(stopId);
        config.set(stopId, null);
        saveConfig();
        
        return true;
    }
    
    /**
     * 设置停靠区的停靠点位置和发车朝向
     * 
     * @param stopId 停靠区ID
     * @param location 停靠点位置
     * @param yaw 发车朝向
     * @return 是否设置成功
     */
    public boolean setStopPoint(String stopId, Location location, float yaw) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            return false;
        }
        
        // 移除旧位置映射
        if (stop.getStopPointLocation() != null) {
            locationToStopId.remove(stop.getStopPointLocation());
        }
        
        // 更新停靠区
        stop.setStopPointLocation(location);
        stop.setLaunchYaw(yaw);
        
        // 添加新位置映射
        locationToStopId.put(location, stopId);
        
        saveConfig();
        return true;
    }
    
    /**
     * 设置停靠区区域的第一个角点
     * 
     * @param stopId 停靠区ID
     * @param location 角点位置
     * @return 是否设置成功
     */
    public boolean setStopCorner1(String stopId, Location location) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            return false;
        }
        
        stop.setCorner1(location);
        saveConfig();
        return true;
    }
    
    /**
     * 设置停靠区区域的第二个角点
     * 
     * @param stopId 停靠区ID
     * @param location 角点位置
     * @return 是否设置成功
     */
    public boolean setStopCorner2(String stopId, Location location) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            return false;
        }
        
        stop.setCorner2(location);
        saveConfig();
        return true;
    }
    
    /**
     * 通过ID获取停靠区
     * 
     * @param stopId 停靠区ID
     * @return 停靠区，若不存在则返回null
     */
    public Stop getStop(String stopId) {
        return stops.get(stopId);
    }
    
    /**
     * 通过位置获取停靠区
     * 
     * @param location 位置
     * @return 停靠区，若不存在则返回null
     */
    public Stop getStopByLocation(Location location) {
        String stopId = locationToStopId.get(location);
        if (stopId != null) {
            return stops.get(stopId);
        }
        return null;
    }
    
    /**
     * 查找包含指定位置的停靠区
     * 
     * @param location 要检查的位置
     * @return 包含该位置的停靠区，如果没有则返回null
     */
    public Stop getStopContainingLocation(Location location) {
        for (Stop stop : stops.values()) {
            if (stop.isInStop(location)) {
                return stop;
            }
        }
        return null;
    }
    
    /**
     * 获取所有停靠区ID
     * 
     * @return 所有停靠区ID的集合
     */
    public Set<String> getAllStopIds() {
        return stops.keySet();
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }
} 