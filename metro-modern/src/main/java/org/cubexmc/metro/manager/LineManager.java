package org.cubexmc.metro.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private final Map<String, Set<String>> stopToLinesIndex;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean isDirty = false;

    public LineManager(Metro plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lines.yml");
        this.lines = new HashMap<>();
        this.stopToLinesIndex = new HashMap<>();
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
        lock.writeLock().lock();
        try {
            lines.clear();
            stopToLinesIndex.clear();
            ConfigurationSection linesSection = config.getConfigurationSection("");

            if (linesSection != null) {
                for (String lineId : linesSection.getKeys(false)) {
                    String name = config.getString(lineId + ".name");
                    if (name == null || name.isBlank()) {
                        plugin.getLogger().warning("Line " + lineId + " is missing name, using line id as fallback.");
                        name = lineId;
                    }
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

                    String ownerString = config.getString(lineId + ".owner");
                    if (ownerString != null && !ownerString.isEmpty()) {
                        try {
                            line.setOwner(UUID.fromString(ownerString));
                        } catch (IllegalArgumentException ignored) {
                            plugin.getLogger()
                                    .warning("Invalid owner UUID in lines.yml for line " + lineId + ": " + ownerString);
                            line.setOwner(null);
                        }
                    }

                    List<String> adminStrings = config.getStringList(lineId + ".admins");
                    if (adminStrings != null && !adminStrings.isEmpty()) {
                        Set<UUID> adminIds = new HashSet<>();
                        for (String adminString : adminStrings) {
                            try {
                                adminIds.add(UUID.fromString(adminString));
                            } catch (IllegalArgumentException ignored) {
                                plugin.getLogger()
                                        .warning("Invalid admin UUID in lines.yml for line " + lineId + ": "
                                                + adminString);
                            }
                        }
                        line.setAdmins(adminIds);
                    }

                    // 加载世界名称
                    String worldName = config.getString(lineId + ".world");
                    if (worldName != null && !worldName.isEmpty()) {
                        line.setWorldName(worldName);
                    }

                    lines.put(lineId, line);
                    indexLineStops(line);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void saveConfig() {
        this.isDirty = true;
    }

    public void processAsyncSave() {
        if (!isDirty) {
            return;
        }
        isDirty = false;

        try {
            String yamlDataFinal;
            // 获取读锁，并在内存中生成配置快照，这样避免在耗时序列化操作中一直持有锁或者发生CME
            lock.readLock().lock();
            try {
                // 将所有线路数据写入配置
                for (Line line : lines.values()) {
                    String lineId = line.getId();
                    config.set(lineId + ".name", line.getName());
                    config.set(lineId + ".ordered_stop_ids", line.getOrderedStopIds());
                    config.set(lineId + ".color", line.getColor());
                    config.set(lineId + ".terminus_name", line.getTerminusName());
                    config.set(lineId + ".max_speed", line.getMaxSpeed() != null ? line.getMaxSpeed() : null);
                    config.set(lineId + ".owner", line.getOwner() != null ? line.getOwner().toString() : null);

                    List<String> adminStrings = new ArrayList<>();
                    for (UUID adminId : line.getAdmins()) {
                        if (line.getOwner() != null && line.getOwner().equals(adminId)) {
                            continue;
                        }
                        adminStrings.add(adminId.toString());
                    }
                    config.set(lineId + ".admins", adminStrings.isEmpty() ? null : adminStrings);
                    config.set(lineId + ".world", line.getWorldName());
                }

                yamlDataFinal = config.saveToString();
            } finally {
                lock.readLock().unlock();
            }

            org.cubexmc.metro.util.SchedulerUtil.asyncRun(plugin, () -> {
                try {
                    java.nio.file.Files.writeString(configFile.toPath(), yamlDataFinal);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "无法异步保存线路配置", e);
                }
            }, 0L);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理线路配置时出错", e);
        }
    }

    public void forceSaveSync() {
        if (!isDirty) {
            return;
        }
        isDirty = false;

        try {
            lock.readLock().lock();
            try {
                for (Line line : lines.values()) {
                    String lineId = line.getId();
                    config.set(lineId + ".name", line.getName());
                    config.set(lineId + ".ordered_stop_ids", line.getOrderedStopIds());
                    config.set(lineId + ".color", line.getColor());
                    config.set(lineId + ".terminus_name", line.getTerminusName());
                    config.set(lineId + ".max_speed", line.getMaxSpeed() != null ? line.getMaxSpeed() : null);
                    config.set(lineId + ".owner", line.getOwner() != null ? line.getOwner().toString() : null);

                    List<String> adminStrings = new ArrayList<>();
                    for (UUID adminId : line.getAdmins()) {
                        if (line.getOwner() != null && line.getOwner().equals(adminId)) {
                            continue;
                        }
                        adminStrings.add(adminId.toString());
                    }
                    config.set(lineId + ".admins", adminStrings.isEmpty() ? null : adminStrings);
                    config.set(lineId + ".world", line.getWorldName());
                }
            } finally {
                lock.readLock().unlock();
            }

            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法同步保存线路配置", e);
        }
    }

    public Line getLine(String lineId) {
        lock.readLock().lock();
        try {
            return lines.get(lineId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean createLine(String lineId, String name, UUID ownerId) {
        lock.writeLock().lock();
        try {
            if (lines.containsKey(lineId)) {
                return false;
            }
            Line line = new Line(lineId, name);
            line.setOwner(ownerId);
            lines.put(lineId, line);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    public boolean deleteLine(String lineId) {
        lock.writeLock().lock();
        try {
            if (!lines.containsKey(lineId)) {
                return false;
            }
            Line removed = lines.remove(lineId);
            if (removed != null) {
                deindexLineStops(removed);
            }
            // 从配置中移除该线路
            config.set(lineId, null);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 向线路添加停靠区
     * 
     * @param lineId 线路ID
     * @param stopId 停靠区ID
     * @param index  添加位置，-1表示添加到末尾
     * @return 是否成功添加
     */
    public boolean addStopToLine(String lineId, String stopId, int index) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            deindexLineStops(line);
            line.addStop(stopId, index);
            indexLineStops(line);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 设置线路世界名称
     * 
     * @param lineId    线路ID
     * @param worldName 世界名称
     * @return 是否成功设置
     */
    public boolean setLineWorldName(String lineId, String worldName) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setWorldName(worldName);
        } finally {
            lock.writeLock().unlock();
        }
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
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            deindexLineStops(line);
            line.delStop(stopId);
            indexLineStops(line);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 从所有线路中移除指定停靠区
     * 
     * @param stopId 要移除的停靠区ID
     */
    public void delStopFromAllLines(String stopId) {
        lock.writeLock().lock();
        try {
            for (Line line : lines.values()) {
                if (line.containsStop(stopId)) {
                    deindexLineStops(line);
                    line.delStop(stopId);
                    indexLineStops(line);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
    }

    /**
     * 获取所有线路
     * 
     * @return 所有线路列表
     */
    public List<Line> getAllLines() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(lines.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 通过停靠区ID反向获取包含该站点的线路列表。
     */
    public List<Line> getLinesForStop(String stopId) {
        lock.readLock().lock();
        try {
            Set<String> lineIds = stopToLinesIndex.get(stopId);
            if (lineIds == null || lineIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<Line> result = new ArrayList<>();
            for (String lineId : lineIds) {
                Line line = lines.get(lineId);
                if (line != null) {
                    result.add(line);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
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
     * @param color  颜色
     * @return 是否成功
     */
    public boolean setLineColor(String lineId, String color) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setColor(color);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 设置线路终点站方向
     * 
     * @param lineId       线路ID
     * @param terminusName 终点站方向名称
     * @return 是否成功
     */
    public boolean setLineTerminusName(String lineId, String terminusName) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setTerminusName(terminusName);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 设置线路名称
     * 
     * @param lineId 线路ID
     * @param name   新名称
     * @return 是否成功
     */
    public boolean setLineName(String lineId, String name) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setName(name);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 设置线路最大速度
     * 
     * @param lineId   线路ID
     * @param maxSpeed 最大速度
     * @return 是否成功
     */
    public boolean setLineMaxSpeed(String lineId, Double maxSpeed) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setMaxSpeed(maxSpeed);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    public boolean setLineOwner(String lineId, UUID ownerId) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setOwner(ownerId);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    public boolean addLineAdmin(String lineId, UUID adminId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            changed = line.addAdmin(adminId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public boolean removeLineAdmin(String lineId, UUID adminId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            changed = line.removeAdmin(adminId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    private void indexLineStops(Line line) {
        if (line == null) {
            return;
        }
        for (String stopId : line.getOrderedStopIds()) {
            stopToLinesIndex.computeIfAbsent(stopId, key -> new HashSet<>()).add(line.getId());
        }
    }

    private void deindexLineStops(Line line) {
        if (line == null) {
            return;
        }
        for (String stopId : line.getOrderedStopIds()) {
            Set<String> lineIds = stopToLinesIndex.get(stopId);
            if (lineIds == null) {
                continue;
            }
            lineIds.remove(line.getId());
            if (lineIds.isEmpty()) {
                stopToLinesIndex.remove(stopId);
            }
        }
    }
}