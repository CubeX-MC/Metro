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
import org.cubexmc.metro.model.FareRule;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;

import org.cubexmc.metro.update.DataFileUpdater;

/**
 * Line manager, responsible for loading, saving and operating line data.
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
        this.lines = new HashMap<String, Line>();
        this.stopToLinesIndex = new HashMap<String, Set<String>>();
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
                    if (name == null || name.trim().isEmpty()) {
                        plugin.getLogger().warning("Line " + lineId + " is missing name, using line id as fallback.");
                        name = lineId;
                    }
                    Line line = new Line(lineId, name);

                    List<String> stopIds = config.getStringList(lineId + ".ordered_stop_ids");
                    for (String stopId : stopIds) {
                        line.addStop(stopId, -1);
                    }

                    List<String> portalIds = config.getStringList(lineId + ".portal_ids");
                    for (String portalId : portalIds) {
                        line.addPortal(portalId);
                    }

                    List<String> routePointStrings = config.getStringList(lineId + ".route_points");
                    if (routePointStrings != null && !routePointStrings.isEmpty()) {
                        List<RoutePoint> routePoints = new ArrayList<RoutePoint>();
                        for (String routePointString : routePointStrings) {
                            RoutePoint routePoint = RoutePoint.fromConfigString(routePointString);
                            if (routePoint != null) {
                                routePoints.add(routePoint);
                            }
                        }
                        line.setRoutePoints(routePoints);
                    }
                    line.setRouteRecordedAtEpochMillis(config.getLong(lineId + ".route_recorded_at", 0L));
                    line.setRouteRecordedBy(readUuid(lineId, "route_recorded_by"));
                    line.setRouteRecordedCartId(readUuid(lineId, "route_recorded_cart"));

                    String color = config.getString(lineId + ".color");
                    if (color != null) {
                        line.setColor(color);
                    }

                    String terminusName = config.getString(lineId + ".terminus_name");
                    if (terminusName != null) {
                        line.setTerminusName(terminusName);
                    }

                    Double maxSpeed = config.getDouble(lineId + ".max_speed", -1);
                    if (maxSpeed >= 0) {
                        line.setMaxSpeed(maxSpeed);
                    }

                    double ticketPrice = config.getDouble(lineId + ".ticket_price", 0.0);
                    line.setTicketPrice(ticketPrice);

                    // Load FareRule
                    if (config.contains(lineId + ".fare_rule")) {
                        ConfigurationSection fareSection = config.getConfigurationSection(lineId + ".fare_rule");
                        if (fareSection != null) {
                            Map<String, Object> fareMap = fareSection.getValues(true);
                            // Flatten nested maps for proper deserialization
                            java.util.Map<String, Object> flattened = new java.util.HashMap<>();
                            for (java.util.Map.Entry<String, Object> entry : fareMap.entrySet()) {
                                String key = entry.getKey();
                                Object value = entry.getValue();
                                String topKey = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
                                if (!flattened.containsKey(topKey)) {
                                    if (value instanceof org.bukkit.configuration.MemorySection) {
                                        flattened.put(topKey, ((org.bukkit.configuration.MemorySection) value).getValues(false));
                                    } else {
                                        flattened.put(topKey, value);
                                    }
                                }
                            }
                            // Special handling for time_discounts list
                            if (fareMap.containsKey("time_discounts")) {
                                List<?> rawList = config.getList(lineId + ".fare_rule.time_discounts");
                                if (rawList != null) {
                                    List<Map<String, Object>> discountList = new ArrayList<>();
                                    for (Object item : rawList) {
                                        if (item instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> discountMap = (Map<String, Object>) item;
                                            discountList.add(discountMap);
                                        }
                                    }
                                    flattened.put("time_discounts", discountList);
                                }
                            }
                            line.setFareRule(FareRule.deserialize(flattened));
                        }
                    }

                    // Load LineStatus
                    String statusStr = config.getString(lineId + ".line_status");
                    if (statusStr != null) {
                        line.setLineStatus(LineStatus.fromConfig(statusStr));
                    }

                    // Load alternative routes
                    List<String> altRoutes = config.getStringList(lineId + ".alternative_routes");
                    if (altRoutes != null && !altRoutes.isEmpty()) {
                        line.setAlternativeRouteIds(altRoutes);
                    }

                    // Load suspension message
                    String suspensionMsg = config.getString(lineId + ".suspension_message");
                    if (suspensionMsg != null && !suspensionMsg.isEmpty()) {
                        line.setSuspensionMessage(suspensionMsg);
                    }

                    line.setRailProtected(config.getBoolean(lineId + ".rail_protected", false));


                    line.setOwner(readUuid(lineId, "owner"));

                    List<String> adminStrings = config.getStringList(lineId + ".admins");
                    if (adminStrings != null && !adminStrings.isEmpty()) {
                        Set<UUID> adminIds = new HashSet<UUID>();
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
            plugin.getLogger().log(Level.SEVERE, "Error processing line configuration", e);
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
            plugin.getLogger().log(Level.SEVERE, "Unable to sync save line configuration", e);
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
            config.set(lineId, null);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        rebuildRailProtection(lineId);
        return true;
    }

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

    public List<Line> getAllLines() {
        lock.readLock().lock();
        try {
            return new ArrayList<Line>(lines.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Line> getLinesForStop(String stopId) {
        lock.readLock().lock();
        try {
            Set<String> lineIds = stopToLinesIndex.get(stopId);
            if (lineIds == null || lineIds.isEmpty()) {
                return new ArrayList<Line>();
            }
            List<Line> result = new ArrayList<Line>();
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

    public void reload() {
        loadConfig();
        if (plugin.getRailProtectionManager() != null) {
            plugin.getRailProtectionManager().rebuildAll();
        }
    }

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

    public boolean addPortalToLine(String lineId, String portalId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            changed = line.addPortal(portalId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public boolean delPortalFromLine(String lineId, String portalId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            changed = line.delPortal(portalId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public void delPortalFromAllLines(String portalId) {
        boolean changed = false;
        lock.writeLock().lock();
        try {
            for (Line line : lines.values()) {
                if (line.delPortal(portalId)) {
                    changed = true;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
    }

    public boolean setLineRoutePoints(String lineId, List<RoutePoint> routePoints) {
        return setLineRoutePoints(lineId, routePoints, null, null, null);
    }

    public boolean setLineRoutePoints(String lineId, List<RoutePoint> routePoints,
                                      Long recordedAtEpochMillis, UUID recordedBy, UUID recordedCartId) {
        lock.writeLock().lock();
        try {
            Line line = lines.get(lineId);
            if (line == null) {
                return false;
            }
            line.setRoutePoints(routePoints);
            if (recordedAtEpochMillis != null || recordedBy != null || recordedCartId != null) {
                line.setRouteRecordingMetadata(recordedAtEpochMillis, recordedBy, recordedCartId);
            }
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
            stopToLinesIndex.computeIfAbsent(stopId, key -> new HashSet<String>()).add(line.getId());
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
        List<String> values = new ArrayList<String>();
        for (RoutePoint routePoint : routePoints) {
            values.add(routePoint.toConfigString());
        }
        return values;
    }

    private UUID readUuid(String lineId, String key) {
        String uuidString = config.getString(lineId + "." + key);
        if (uuidString == null || uuidString.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid " + key + " UUID in lines.yml for line " + lineId + ": " + uuidString);
            return null;
        }
    }

    private String buildSnapshot() {
        YamlConfiguration snapshot = new YamlConfiguration();
        lock.readLock().lock();
        try {
            if (!lines.isEmpty() || config.getInt(DataFileUpdater.SCHEMA_VERSION_KEY, 0) > 0) {
                snapshot.set(DataFileUpdater.SCHEMA_VERSION_KEY, DataFileUpdater.CURRENT_SCHEMA_VERSION);
            }

            List<String> lineIds = new ArrayList<String>(lines.keySet());
            Collections.sort(lineIds);
            for (String lineId : lineIds) {
                Line line = lines.get(lineId);
                if (line == null) {
                    continue;
                }
                snapshot.set(lineId + ".name", line.getName());
                snapshot.set(lineId + ".ordered_stop_ids", line.getOrderedStopIds());
                snapshot.set(lineId + ".portal_ids", line.getPortalIds().isEmpty() ? null : line.getPortalIds());
                snapshot.set(lineId + ".route_points", routePointsToConfig(line));
                snapshot.set(lineId + ".route_recorded_at", line.getRouteRecordedAtEpochMillis());
                snapshot.set(lineId + ".route_recorded_by",
                        line.getRouteRecordedBy() == null ? null : line.getRouteRecordedBy().toString());
                snapshot.set(lineId + ".route_recorded_cart",
                        line.getRouteRecordedCartId() == null ? null : line.getRouteRecordedCartId().toString());
                snapshot.set(lineId + ".color", line.getColor());
                snapshot.set(lineId + ".terminus_name", line.getTerminusName());
                snapshot.set(lineId + ".max_speed", line.getMaxSpeed() != null ? line.getMaxSpeed() : null);
                snapshot.set(lineId + ".ticket_price", line.getTicketPrice() > 0 ? line.getTicketPrice() : null);

                // Save FareRule
                FareRule fareRule = line.getFareRule();
                if (fareRule != null) {
                    Map<String, Object> fareMap = fareRule.serialize();
                    for (java.util.Map.Entry<String, Object> entry : fareMap.entrySet()) {
                        snapshot.set(lineId + ".fare_rule." + entry.getKey(), entry.getValue());
                    }
                }

                // Save LineStatus
                if (line.getLineStatus() != LineStatus.NORMAL) {
                    snapshot.set(lineId + ".line_status", line.getLineStatus().getConfigKey());
                }

                // Save alternative routes
                List<String> altRoutes = line.getAlternativeRouteIds();
                if (!altRoutes.isEmpty()) {
                    snapshot.set(lineId + ".alternative_routes", altRoutes);
                }

                // Save suspension message
                String suspensionMsg = line.getSuspensionMessage();
                if (suspensionMsg != null && !suspensionMsg.isEmpty()) {
                    snapshot.set(lineId + ".suspension_message", suspensionMsg);
                }

                snapshot.set(lineId + ".rail_protected", line.isRailProtected() ? true : null);

                snapshot.set(lineId + ".owner", line.getOwner() != null ? line.getOwner().toString() : null);

                List<String> adminStrings = new ArrayList<String>();
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

            Line newLine = new Line(newLineId, sourceLine.getName());
            newLine.setOwner(ownerId);
            newLine.setColor(sourceLine.getColor());
            newLine.setMaxSpeed(sourceLine.getMaxSpeed());
            newLine.setWorldName(sourceLine.getWorldName());

            if (sourceLine.getAdmins() != null) {
                Set<UUID> newAdmins = new HashSet<UUID>(sourceLine.getAdmins());
                if (ownerId != null) {
                    newAdmins.add(ownerId);
                }
                newLine.setAdmins(newAdmins);
            }

            lines.put(newLineId, newLine);

            StopManager stopManager = plugin.getStopManager();
            List<String> sourceStops = sourceLine.getOrderedStopIds();
            for (int i = sourceStops.size() - 1; i >= 0; i--) {
                String oldStopId = sourceStops.get(i);
                Stop oldStop = stopManager.getStop(oldStopId);
                if (oldStop == null) {
                    continue;
                }

                String newStopId = oldStopId + stopIdSuffix;
                Stop newStop = stopManager.getStop(newStopId);

                if (newStop == null) {
                    newStop = stopManager.createStop(newStopId, oldStop.getName(), oldStop.getCorner1(), oldStop.getCorner2(), ownerId);
                    if (newStop != null) {
                        float newYaw = (oldStop.getLaunchYaw() + 180.0f) % 360.0f;
                        if (newYaw > 180.0f) {
                            newYaw -= 360.0f;
                        }
                        if (newYaw < -180.0f) {
                            newYaw += 360.0f;
                        }

                        stopManager.setStopPoint(newStopId, oldStop.getStopPointLocation(), newYaw);

                        for (UUID adminId : oldStop.getAdmins()) {
                            if (adminId != null) {
                                stopManager.addStopAdmin(newStopId, adminId);
                            }
                        }
                    }
                }

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
