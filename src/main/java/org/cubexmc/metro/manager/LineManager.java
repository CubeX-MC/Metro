package org.cubexmc.metro.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;

/**
 * 线路管理器，负责线路数据的加载、保存和操作
 */
public class LineManager {
    private final Metro plugin;
    private final File configFile;
    private FileConfiguration config;
    private final Map<String, Line> lines;

    public LineManager(Metro plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lines.yml");
        this.lines = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("lines.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadLines();
    }

    private void loadLines() {
        lines.clear();
        ConfigurationSection linesSection = config.getConfigurationSection("");
        
        if (linesSection != null) {
            for (String lineId : linesSection.getKeys(false)) {
                String name = config.getString(lineId + ".name");
                Line line = new Line(lineId, name);
                
                // 加载有序停靠区列表
                List<String> stopIds = config.getStringList(lineId + ".ordered_stop_ids");
                for (String stopId : stopIds) {
                    line.addStop(stopId, -1);
                }
                
                // 加载颜色和终点站方向
                String color = config.getString(lineId + ".color");
                if (color != null) {
                    line.setColor(color);
                }
                
                String terminusName = config.getString(lineId + ".terminus_name");
                if (terminusName != null) {
                    line.setTerminusName(terminusName);
                }
                
                // 加载最大速度
                Double maxSpeed = config.getDouble(lineId + ".max_speed", -1);
                if (maxSpeed >= 0) {
                    line.setMaxSpeed(maxSpeed);
                }
                
                lines.put(lineId, line);
            }
        }
    }

    public void saveConfig() {
        try {
            // 将所有线路数据写入配置
            for (Line line : lines.values()) {
                String lineId = line.getId();
                config.set(lineId + ".name", line.getName());
                config.set(lineId + ".ordered_stop_ids", line.getOrderedStopIds());
                config.set(lineId + ".color", line.getColor());
                config.set(lineId + ".terminus_name", line.getTerminusName());
                if (line.getMaxSpeed() != null) {
                    config.set(lineId + ".max_speed", line.getMaxSpeed());
                } else {
                    config.set(lineId + ".max_speed", null);
                }
            }
            
            // 保存配置到文件
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存线路配置", e);
        }
    }
    
    public Line getLine(String lineId) {
        return lines.get(lineId);
    }
    
    public boolean createLine(String lineId, String name) {
        if (lines.containsKey(lineId)) {
            return false;
        }
        Line line = new Line(lineId, name);
        lines.put(lineId, line);
        saveConfig();
        return true;
    }
    
    public boolean deleteLine(String lineId) {
        if (!lines.containsKey(lineId)) {
            return false;
        }
        lines.remove(lineId);
        // 从配置中移除该线路
        config.set(lineId, null);
        saveConfig();
        return true;
    }
    
    /**
     * 向线路添加停靠区
     * 
     * @param lineId 线路ID
     * @param stopId 停靠区ID
     * @param index 添加位置，-1表示添加到末尾
     * @return 是否成功添加
     */
    public boolean addStopToLine(String lineId, String stopId, int index) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.addStop(stopId, index);
        saveConfig();
        return true;
    }
    
    /**
     * 从线路中移除停靠区
     * 
     * @param lineId 线路ID
     * @param stopId 停靠区ID
     * @return 是否成功移除
     */
    public boolean delStopFromLine(String lineId, String stopId) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.delStop(stopId);
        saveConfig();
        return true;
    }
    
    /**
     * 从所有线路中移除指定停靠区
     * 
     * @param stopId 要移除的停靠区ID
     */
    public void delStopFromAllLines(String stopId) {
        for (Line line : lines.values()) {
            if (line.containsStop(stopId)) {
                line.delStop(stopId);
            }
        }
        saveConfig();
    }
    
    /**
     * 获取包含特定停靠区的所有线路
     * 
     * @param stopId 停靠区ID
     * @return 包含该停靠区的线路列表
     */
    public List<Line> getLinesContainingStop(String stopId) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines.values()) {
            if (line.containsStop(stopId)) {
                result.add(line);
            }
        }
        return result;
    }
    
    /**
     * 获取所有线路
     * 
     * @return 所有线路列表
     */
    public List<Line> getAllLines() {
        return new ArrayList<>(lines.values());
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * 设置线路颜色
     * 
     * @param lineId 线路ID
     * @param color 颜色
     * @return 是否成功
     */
    public boolean setLineColor(String lineId, String color) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.setColor(color);
        saveConfig();
        return true;
    }
    
    /**
     * 设置线路终点站方向
     * 
     * @param lineId 线路ID
     * @param terminusName 终点站方向名称
     * @return 是否成功
     */
    public boolean setLineTerminusName(String lineId, String terminusName) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.setTerminusName(terminusName);
        saveConfig();
        return true;
    }
    
    /**
     * 设置线路名称
     * 
     * @param lineId 线路ID
     * @param name 新名称
     * @return 是否成功
     */
    public boolean setLineName(String lineId, String name) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.setName(name);
        saveConfig();
        return true;
    }
    
    /**
     * 设置线路最大速度
     * 
     * @param lineId 线路ID
     * @param maxSpeed 最大速度
     * @return 是否成功
     */
    public boolean setLineMaxSpeed(String lineId, Double maxSpeed) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.setMaxSpeed(maxSpeed);
        saveConfig();
        return true;
    }
} 