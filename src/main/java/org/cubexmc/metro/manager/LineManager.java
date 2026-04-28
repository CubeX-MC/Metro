package org.cubexmc.metro.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.update.DataFileUpdater;

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
                    if (DataFileUpdater.SCHEMA_VERSION_KEY.equals(lineId)) {
                        continue;
                    }
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

                    List<String> routePointStrings = config.getStringList(lineId + ".route_points");
                    if (routePointStrings != null && !routePointStrings.isEmpty()) {
                        List<RoutePoint> routePoints = new ArrayList<>();
                        for (String routePointString : routePointStrings) {
                            RoutePoint routePoint = RoutePoint.fromConfigString(routePointString);
                            if (routePoint != null) {
                                routePoints.add(routePoint);
                            }
                        }
                        line.setRoutePoints(routePoints);
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

                    // 加载乘车价格
                    double ticketPrice = config.getDouble(lineId + ".ticket_price", 0.0);
                    line.setTicketPrice(ticketPrice);

                    line.setRailProtected(config.getBoolean(lineId + ".rail_protected", false));

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
        plugin.requestMapIntegrationRefresh();
    }

    public void processAsyncSave() {
        if (!isDirty) {
            return;
        }

        try {
            String yamlDataFinal = buildSnapshot();
            isDirty = false;
            plugin.getSaveCoordinator().submitSnapshot(configFile.toPath(), yamlDataFinal);
        } catch (Exception e) {
            isDirty = true;
            plugin.getLogger().log(Level.SEVERE, "处理线路配置时出错", e);
        }
    }

    public void forceSaveSync() {
        if (!isDirty) {
            return;
        }

        try {
            String yamlDataFinal = buildSnapshot();
            isDirty = false;
            plugin.getSaveCoordinator().saveNow(configFile.toPath(), yamlDataFinal);
        } catch (Exception e) {
            isDirty = true;
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
        rebuildRailProtection(lineId);
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
        rebuildRailProtection(lineId);
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
        if (plugin.getRailProtectionManager() != null) {
            plugin.getRailProtectionManager().rebuildAll();
        }
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

    /**
     * 设置线路乘车价格
     * 
     * @param lineId 线路ID
     * @param ticketPrice 新价格
     * @return 是否成功
     */
    public boolean setLineTicketPrice(String lineId, double ticketPrice) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setTicketPrice(ticketPrice);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    public boolean setLineRoutePoints(String lineId, List<RoutePoint> routePoints) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setRoutePoints(routePoints);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        rebuildRailProtection(lineId);
        return true;
    }

    public boolean clearLineRoutePoints(String lineId) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.clearRoutePoints();
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        rebuildRailProtection(lineId);
        return true;
    }

    public boolean setLineRailProtected(String lineId, boolean protectedRail) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setRailProtected(protectedRail);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        rebuildRailProtection(lineId);
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

    private List<String> routePointsToConfig(Line line) {
        List<RoutePoint> routePoints = line.getRoutePoints();
        if (routePoints.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (RoutePoint routePoint : routePoints) {
            values.add(routePoint.toConfigString());
        }
        return values;
    }

    private String buildSnapshot() {
        YamlConfiguration snapshot = new YamlConfiguration();
        lock.readLock().lock();
        try {
            if (!lines.isEmpty() || config.getInt(DataFileUpdater.SCHEMA_VERSION_KEY, 0) > 0) {
                snapshot.set(DataFileUpdater.SCHEMA_VERSION_KEY, DataFileUpdater.CURRENT_SCHEMA_VERSION);
            }

            List<String> lineIds = new ArrayList<>(lines.keySet());
            Collections.sort(lineIds);
            for (String lineId : lineIds) {
                Line line = lines.get(lineId);
                if (line == null) {
                    continue;
                }
                snapshot.set(lineId + ".name", line.getName());
                snapshot.set(lineId + ".ordered_stop_ids", line.getOrderedStopIds());
                snapshot.set(lineId + ".route_points", routePointsToConfig(line));
                snapshot.set(lineId + ".color", line.getColor());
                snapshot.set(lineId + ".terminus_name", line.getTerminusName());
                snapshot.set(lineId + ".max_speed", line.getMaxSpeed() != null ? line.getMaxSpeed() : null);
                snapshot.set(lineId + ".ticket_price", line.getTicketPrice() > 0 ? line.getTicketPrice() : null);
                snapshot.set(lineId + ".rail_protected", line.isRailProtected() ? true : null);
                snapshot.set(lineId + ".owner", line.getOwner() != null ? line.getOwner().toString() : null);

                List<String> adminStrings = new ArrayList<>();
                for (UUID adminId : line.getAdmins()) {
                    if (line.getOwner() != null && line.getOwner().equals(adminId)) {
                        continue;
                    }
                    adminStrings.add(adminId.toString());
                }
                Collections.sort(adminStrings);
                snapshot.set(lineId + ".admins", adminStrings.isEmpty() ? null : adminStrings);
                snapshot.set(lineId + ".world", line.getWorldName());
            }
            return snapshot.saveToString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 反向克隆线路和站点
     * 
     * @param sourceLineId 要克隆的源线路ID
     * @param newLineId 新的对向线路ID
     * @param stopIdSuffix 站点ID后缀
     * @param ownerId 新线路及站点的所有者
     * @return 是否成功
     */
    public boolean cloneReverseLine(String sourceLineId, String newLineId, String stopIdSuffix, UUID ownerId) {
        lock.writeLock().lock();
        try {
            Line sourceLine = lines.get(sourceLineId);
            if (sourceLine == null) {
                return false;
            }
            if (lines.containsKey(newLineId)) {
                return false;
            }

            // 1. 创建新线路
            Line newLine = new Line(newLineId, sourceLine.getName());
            newLine.setOwner(ownerId);
            newLine.setColor(sourceLine.getColor());
            newLine.setMaxSpeed(sourceLine.getMaxSpeed());
            newLine.setWorldName(sourceLine.getWorldName());
            
            // 复制管理员
            if (sourceLine.getAdmins() != null) {
                Set<UUID> newAdmins = new HashSet<>(sourceLine.getAdmins());
                if (ownerId != null) {
                    newAdmins.add(ownerId);
                }
                newLine.setAdmins(newAdmins);
            }

            lines.put(newLineId, newLine);

            // 2. 倒序克隆站点
            StopManager stopManager = plugin.getStopManager();
            List<String> sourceStops = sourceLine.getOrderedStopIds();
            for (int i = sourceStops.size() - 1; i >= 0; i--) {
                String oldStopId = sourceStops.get(i);
                Stop oldStop = stopManager.getStop(oldStopId);
                if (oldStop == null) continue;

                String newStopId = oldStopId + stopIdSuffix;
                Stop newStop = stopManager.getStop(newStopId);
                
                if (newStop == null) {
                    // 创建新站点
                    newStop = stopManager.createStop(newStopId, oldStop.getName(), oldStop.getCorner1(), oldStop.getCorner2(), ownerId);
                    if (newStop != null) {
                        // 旋转发车朝向 180 度
                        float newYaw = (oldStop.getLaunchYaw() + 180.0f) % 360.0f;
                        if (newYaw > 180.0f) newYaw -= 360.0f;
                        if (newYaw < -180.0f) newYaw += 360.0f;
                        
                        stopManager.setStopPoint(newStopId, oldStop.getStopPointLocation(), newYaw);
                        
                        // 复制站点管理员
                        for (UUID adminId : oldStop.getAdmins()) {
                            if (adminId != null) {
                                stopManager.addStopAdmin(newStopId, adminId);
                            }
                        }
                    }
                }
                
                // 将站点添加到新线路
                newLine.addStop(newStopId, -1);
            }
            
            indexLineStops(newLine);
        } finally {
            lock.writeLock().unlock();
        }
        
        saveConfig();
        return true;
    }

    private void rebuildRailProtection(String lineId) {
        if (plugin.getRailProtectionManager() != null) {
            plugin.getRailProtectionManager().rebuildLine(lineId);
        }
    }
}
