package org.cubexmc.metro.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.util.MetroConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 管理矿车传送门的加载、保存、查询和传送逻辑。
 */
public class PortalManager {

    private final Metro plugin;
    private final File portalFile;
    private YamlConfiguration portalConfig;
    private final Map<String, Portal> portals = new HashMap<>();

    public PortalManager(Metro plugin) {
        this.plugin = plugin;
        this.portalFile = new File(plugin.getDataFolder(), "portals.yml");
        load();
    }

    // =============== 加载 / 保存 ===============

    public void load() {
        portals.clear();
        if (!portalFile.exists()) {
            portalConfig = new YamlConfiguration();
            return;
        }
        portalConfig = YamlConfiguration.loadConfiguration(portalFile);
        ConfigurationSection section = portalConfig.getConfigurationSection("portals");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection portalSection = section.getConfigurationSection(id);
            if (portalSection != null) {
                portals.put(id, Portal.fromConfig(id, portalSection));
            }
        }
        plugin.getLogger().info("[Portal] Loaded " + portals.size() + " portals.");
    }

    public void save() {
        portalConfig = new YamlConfiguration();
        for (Portal portal : portals.values()) {
            ConfigurationSection section = portalConfig.createSection("portals." + portal.getId());
            portal.toConfig(section);
        }
        try {
            portalConfig.save(portalFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Portal] Failed to save portals.yml", e);
        }
    }

    // =============== CRUD ===============

    public Portal createPortal(String id, Location entrance) {
        Portal portal = new Portal(id);
        portal.setEntrance(entrance);
        portals.put(id, portal);
        save();
        return portal;
    }

    public boolean deletePortal(String id) {
        Portal removed = portals.remove(id);
        if (removed != null) {
            // 清除反向配对
            if (removed.getLinkedPortalId() != null) {
                Portal linked = portals.get(removed.getLinkedPortalId());
                if (linked != null) {
                    linked.setLinkedPortalId(null);
                }
            }
            save();
            return true;
        }
        return false;
    }

    public boolean setDestination(String id, Location destination) {
        Portal portal = portals.get(id);
        if (portal == null) return false;
        portal.setDestination(destination);
        save();
        return true;
    }

    public boolean linkPortals(String id1, String id2) {
        Portal p1 = portals.get(id1);
        Portal p2 = portals.get(id2);
        if (p1 == null || p2 == null) return false;
        p1.setLinkedPortalId(id2);
        p2.setLinkedPortalId(id1);
        save();
        return true;
    }

    public Portal getPortal(String id) {
        return portals.get(id);
    }

    public List<Portal> getAllPortals() {
        return new ArrayList<>(portals.values());
    }

    // =============== 位置查询 ===============

    /**
     * 根据铁轨方块坐标查找传送门。
     * 匹配的是铁轨所在位置的 blockX/Y/Z。
     */
    public Portal getPortalAt(Location railLocation) {
        for (Portal portal : portals.values()) {
            if (portal.matchesLocation(railLocation)) {
                return portal;
            }
        }
        return null;
    }

    // =============== 传送逻辑 ===============

    /**
     * 传送矿车和乘客到目标位置。
     * 流程：
     * 1. 获取乘客
     * 2. 在目标世界创建新矿车
     * 3. 复制 PDC 数据
     * 4. 移除原矿车
     * 5. 传送乘客并自动上车
     * 6. 播放特效
     */
    public void teleportMinecart(Minecart sourceCart, Portal portal) {
        Location destination = portal.getDestination();
        if (destination == null || destination.getWorld() == null) {
            plugin.getLogger().warning("[Portal] Invalid destination for portal: " + portal.getId());
            return;
        }

        // 获取乘客
        Player passenger = null;
        if (!sourceCart.getPassengers().isEmpty()) {
            Entity entity = sourceCart.getPassengers().get(0);
            if (entity instanceof Player) {
                passenger = (Player) entity;
            }
        }

        // 入口特效
        playEffects(sourceCart.getLocation());

        final Player finalPassenger = passenger;

        org.cubexmc.metro.train.TrainMovementTask oldTask = org.cubexmc.metro.train.TrainMovementTask.getTaskFor(sourceCart);
        if (oldTask != null) {
            oldTask.setTeleporting(true); // 通知任务即将传送，即使抛出乘客也不要自动 cancel
        }

        // 先弹出乘客再做传送
        if (passenger != null) {
            sourceCart.eject();
            // 注意：弹出后 TrainMovementTask 会检查 isPassengerStillRiding() 然后调用 cancel()
            // 如果不屏蔽该检查，整个任务在传送冷却 delay 期间就会被注销了
        }

        // 复制 PDC 数据
        PersistentDataContainer sourcePdc = sourceCart.getPersistentDataContainer();
        boolean isMetroCart = sourcePdc.has(MetroConstants.getMinecartKey(), PersistentDataType.BYTE);

        int teleportDelay = plugin.getConfigFacade().getPortalTeleportDelay();

        // 延迟移除原矿车
        org.cubexmc.metro.util.SchedulerUtil.entityRun(plugin, sourceCart, () -> {
            if (sourceCart.isValid()) {
                sourceCart.remove();
            }
        }, teleportDelay, -1L);

        // 延迟在目标位置生成新矿车并传送乘客
        org.cubexmc.metro.util.SchedulerUtil.regionRun(plugin, destination, () -> {
            World destWorld = destination.getWorld();
            if (destWorld == null) return;

            Minecart newCart = destWorld.spawn(destination, Minecart.class);

            // 复制 Metro 标记
            if (isMetroCart) {
                newCart.getPersistentDataContainer().set(
                        MetroConstants.getMinecartKey(),
                        PersistentDataType.BYTE,
                        (byte) 1
                );
                newCart.setCustomName(MetroConstants.METRO_MINECART_NAME);
                newCart.setCustomNameVisible(false);
            }

            // 传送乘客（如果有）
            if (finalPassenger != null && finalPassenger.isOnline()) {
                org.cubexmc.metro.util.SchedulerUtil.teleportEntity(finalPassenger, destination).thenAccept(success -> {
                    if (success && finalPassenger.isOnline() && newCart.isValid()) {
                        // 传送完成后，让乘客上车
                        org.cubexmc.metro.util.SchedulerUtil.entityRun(plugin, newCart, () -> {
                            if (finalPassenger.isOnline() && newCart.isValid()) {
                                newCart.addPassenger(finalPassenger);

                                // 转移 TrainMovementTask (接管新矿车)
                                if (oldTask != null) {
                                    oldTask.transferMinecart(newCart);
                                }

                                // 给矿车一个初始速度
                                float yaw = destination.getYaw();
                                double rad = Math.toRadians(yaw);
                                Vector direction = new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
                                newCart.setVelocity(direction.multiply(plugin.getConfigFacade().getCartSpeed()));
                            }
                        }, 2L, -1L);
                    }
                });
            } else {
                // 如果没有乘客，也要给空车一个初始速度
                // 转移 TrainMovementTask (接管新矿车)
                if (oldTask != null) {
                    oldTask.transferMinecart(newCart);
                }
                float yaw = destination.getYaw();
                double rad = Math.toRadians(yaw);
                Vector direction = new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
                newCart.setVelocity(direction.multiply(plugin.getConfigFacade().getCartSpeed()));
            }

            // 出口特效
            playEffects(destination);

        }, teleportDelay, -1L);

        // 冻结乘客（如果有延迟且有乘客）
        if (finalPassenger != null && teleportDelay > 0) {
            finalPassenger.sendTitle("§d✦ §b传送中... §d✦", "§7Teleporting...", 5, teleportDelay, 5);
        }
    }

    /**
     * 播放传送门特效（粒子 + 音效）
     */
    private void playEffects(Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        if (plugin.getConfigFacade().isPortalEffectParticles()) {
            loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.5);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
        }

        if (plugin.getConfigFacade().isPortalEffectSound()) {
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        }
    }
}
