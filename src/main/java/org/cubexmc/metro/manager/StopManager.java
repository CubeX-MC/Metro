package org.cubexmc.metro.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.spatial.Octree;
import org.cubexmc.metro.spatial.Point3D;
import org.cubexmc.metro.spatial.Range3D;

/**
 * 管理停靠区数据的加载、保存和访问
 */
public class StopManager {

    private final Metro plugin;
    private final File configFile;
    private FileConfiguration config;

    // 缓存数据
    private final Map<String, Stop> stops = new HashMap<>();
    // 将原先的按世界存放的List轻量级索引升级为八叉树空间索引 (Octree)，降低查询复杂度至 O(log N)
    private final Map<String, Octree<Stop>> worldStopIndex = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile boolean isDirty = false;

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
        lock.writeLock().lock();
        try {
            stops.clear();
            worldStopIndex.clear();

            // 加载所有停靠区
            ConfigurationSection stopsSection = config.getConfigurationSection("");
            if (stopsSection != null) {
                Set<String> stopIds = stopsSection.getKeys(false);
                for (String stopId : stopIds) {
                    ConfigurationSection stopSection = stopsSection.getConfigurationSection(stopId);
                    if (stopSection != null) {
                        try {
                            Stop stop = new Stop(stopId, stopSection);
                            stops.put(stopId, stop);
                            indexStop(stop);
                        } catch (RuntimeException ex) {
                            plugin.getLogger()
                                    .warning("Failed to load stop " + stopId + " from stops.yml: " + ex.getMessage());
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        plugin.getLogger().info("Loaded " + stops.size() + " stops");
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
            lock.readLock().lock();
            try {
                for (Map.Entry<String, Stop> entry : stops.entrySet()) {
                    String stopId = entry.getKey();
                    Stop stop = entry.getValue();

                    config.set(stopId, null);
                    ConfigurationSection section = config.createSection(stopId);
                    stop.saveToConfig(section);
                }

                yamlDataFinal = config.saveToString();
            } finally {
                lock.readLock().unlock();
            }

            org.cubexmc.metro.util.SchedulerUtil.asyncRun(plugin, () -> {
                try {
                    java.nio.file.Files.writeString(configFile.toPath(), yamlDataFinal);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not async save stops config: " + e.getMessage());
                }
            }, 0L);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "处理停靠区配置时出错", e);
        }
    }

    public void forceSaveSync() {
        if (!isDirty) {
            return;
        }
        isDirty = false;

        lock.readLock().lock();
        try {
            for (Map.Entry<String, Stop> entry : stops.entrySet()) {
                String stopId = entry.getKey();
                Stop stop = entry.getValue();

                config.set(stopId, null);
                ConfigurationSection section = config.createSection(stopId);
                stop.saveToConfig(section);
            }
        } finally {
            lock.readLock().unlock();
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
     * @param stopId      停靠区ID
     * @param displayName 停靠区显示名称
     * @return 创建的停靠区
     */
    public Stop createStop(String stopId, String displayName, Location corner1, Location corner2, UUID ownerId) {
        lock.writeLock().lock();
        try {
            if (stops.containsKey(stopId)) {
                return null; // 已存在
            }

            Stop stop = new Stop(stopId, displayName == null || displayName.isEmpty() ? stopId : displayName);
            stop.setOwner(ownerId);
            if (corner1 != null && corner2 != null) {
                stop.setCorner1(corner1);
                stop.setCorner2(corner2);
            }
            stops.put(stopId, stop);
            indexStop(stop);
            saveConfig();
            return stop;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 设置停靠区的新角点
     * 
     * @param stopId  停靠区ID
     * @param corner1 新的角点1
     * @param corner2 新的角点2
     * @return 如果成功更新则为true
     */
    public boolean setStopCorners(String stopId, Location corner1, Location corner2) {
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            deindexStop(stop);
            stop.setCorner1(corner1);
            stop.setCorner2(corner2);
            indexStop(stop);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    /**
     * 删除停靠区
     * 
     * @param stopId 要删除的停靠区ID
     * @return 是否成功删除
     */
    public boolean deleteStop(String stopId) {
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            // 从所有线路中移除
            LineManager lineManager = plugin.getLineManager();
            lineManager.delStopFromAllLines(stopId);

            // 移除位置映射
            deindexStop(stop);
            // 从内存和配置中移除
            stops.remove(stopId);
            config.set(stopId, null);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();

        return true;
    }

    /**
     * 设置停靠区的停靠点位置和发车朝向
     * 
     * @param stopId   停靠区ID
     * @param location 停靠点位置
     * @param yaw      发车朝向
     * @return 是否设置成功
     */
    public boolean setStopPoint(String stopId, Location location, float yaw) {
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            // 移除旧位置映射
            deindexStop(stop);
            // 更新停靠区
            stop.setStopPointLocation(location);
            stop.setLaunchYaw(yaw);
            indexStop(stop);
        } finally {
            lock.writeLock().unlock();
        }

        saveConfig();
        return true;
    }

    public boolean setStopOwner(String stopId, UUID ownerId) {
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }
            stop.setOwner(ownerId);
        } finally {
            lock.writeLock().unlock();
        }
        saveConfig();
        return true;
    }

    public boolean addStopAdmin(String stopId, UUID adminId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }
            changed = stop.addAdmin(adminId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public boolean removeStopAdmin(String stopId, UUID adminId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }
            changed = stop.removeAdmin(adminId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public boolean allowLineLink(String stopId, String lineId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }
            changed = stop.allowLine(lineId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    public boolean denyLineLink(String stopId, String lineId) {
        boolean changed;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }
            changed = stop.denyLine(lineId);
        } finally {
            lock.writeLock().unlock();
        }
        if (changed) {
            saveConfig();
        }
        return changed;
    }

    /**
     * 设置停靠区名称
     * 
     * @param stopId 停靠区ID
     * @param name   新名称
     * @return 是否设置成功
     */
    public boolean setStopName(String stopId, String name) {
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            stop.setName(name);
        } finally {
            lock.writeLock().unlock();
        }
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
        lock.readLock().lock();
        try {
            return stops.get(stopId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 查找包含指定位置的停靠区
     * 
     * @param location 要检查的位置
     * @return 包含该位置的停靠区，如果没有则返回null
     */
    public Stop getStopContainingLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        lock.readLock().lock();
        try {
            Octree<Stop> octree = worldStopIndex.get(location.getWorld().getName());
            if (octree == null) {
                return null;
            }
            return octree.firstRange(new Point3D(location));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 查找最匹配给定位置及偏航角的停靠区
     * 
     * @param location 要检查的位置
     * @param playerYaw 玩家偏航角
     * @return 包含该位置的停靠区中最符合方向的一个，如果没有则返回null
     */
    public Stop getBestStopContainingLocation(Location location, float playerYaw) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        lock.readLock().lock();
        try {
            Octree<Stop> octree = worldStopIndex.get(location.getWorld().getName());
            if (octree == null) {
                return null;
            }
            List<Stop> foundStops = octree.getAllRanges(new Point3D(location));
            if (foundStops.isEmpty()) {
                return null;
            }
            Stop bestStop = foundStops.get(0);
            double minDiff = Double.MAX_VALUE;
            for (Stop stop : foundStops) {
                double stopYaw = stop.getLaunchYaw();
                double diff = Math.abs((stopYaw - playerYaw + 360) % 360);
                diff = Math.min(diff, 360 - diff);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestStop = stop;
                }
            }
            return bestStop;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有停靠区ID
     * 
     * @return 所有停靠区ID的集合
     */
    public Set<String> getAllStopIds() {
        lock.readLock().lock();
        try {
            return new java.util.HashSet<>(stops.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加可换乘线路到停靠区
     * 
     * @param stopId 停靠区ID
     * @param lineId 可换乘的线路ID
     * @return 是否成功添加
     */
    public boolean addTransferLine(String stopId, String lineId) {
        boolean added;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            added = stop.addTransferableLine(lineId);
        } finally {
            lock.writeLock().unlock();
        }
        if (added) {
            saveConfig();
        }
        return added;
    }

    /**
     * 从停靠区移除可换乘线路
     * 
     * @param stopId 停靠区ID
     * @param lineId 要移除的可换乘线路ID
     * @return 是否成功移除
     */
    public boolean removeTransferLine(String stopId, String lineId) {
        boolean removed;
        lock.writeLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return false;
            }

            removed = stop.removeTransferableLine(lineId);
        } finally {
            lock.writeLock().unlock();
        }
        if (removed) {
            saveConfig();
        }
        return removed;
    }

    /**
     * 获取停靠区可换乘的线路ID列表
     * 
     * @param stopId 停靠区ID
     * @return 可换乘线路ID列表，如果停靠区不存在则返回空列表
     */
    public List<String> getTransferableLines(String stopId) {
        lock.readLock().lock();
        try {
            Stop stop = stops.get(stopId);
            if (stop == null) {
                return new ArrayList<>();
            }

            return stop.getTransferableLines();
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

    private void indexStop(Stop stop) {
        if (stop == null) {
            return;
        }
        String worldName = stop.getWorldName();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        Range3D range = stop.getRange3D();
        if (range == null) return;
        
        Octree<Stop> octree = worldStopIndex.computeIfAbsent(worldName, key -> 
            new Octree<>(new Range3D(-30000000, -64, -30000000, 30000000, 320, 30000000), 12, 8));
        octree.insert(range, stop);
    }

    private void deindexStop(Stop stop) {
        if (stop == null) {
            return;
        }
        String worldName = stop.getWorldName();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        Range3D range = stop.getRange3D();
        if (range == null) return;
        
        Octree<Stop> octree = worldStopIndex.get(worldName);
        if (octree != null) {
            octree.remove(range);
        }
    }
}