package org.cubexmc.metro.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
    public boolean removeStopFromLine(String lineId, String stopId) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        line.removeStop(stopId);
        saveConfig();
        return true;
    }
    
    /**
     * 从所有线路中移除指定停靠区
     * 
     * @param stopId 要移除的停靠区ID
     */
    public void removeStopFromAllLines(String stopId) {
        for (Line line : lines.values()) {
            if (line.containsStop(stopId)) {
                line.removeStop(stopId);
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
} 