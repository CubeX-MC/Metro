package org.cubexmc.metro.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * 处理矿车相关事件
 */
public class VehicleListener implements Listener {

    private final Metro plugin;

    public VehicleListener(Metro plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听玩家离开矿车事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleExit(VehicleExitEvent event) {
        Vehicle vehicle = event.getVehicle();
        Entity passenger = event.getExited();

        // 只处理玩家离开地铁矿车的情况
        if (!(vehicle instanceof Minecart) || !(passenger instanceof Player)) {
            return;
        }

        Player player = (Player) passenger;
        Minecart minecart = (Minecart) vehicle;

        // 检查是否是Metro的矿车
        if (!minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }

        // 玩家下车，清除其界面显示
        plugin.getScoreboardManager().clearPlayerDisplay(player);

        // 获取当前位置
        Location location = minecart.getLocation();

        // 检查位置是否在停靠区上
        if (!isAtStop(location)) {
            // 如果不在停靠区上，立即移除矿车
            final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
            SchedulerUtil.regionRun(plugin, location, () -> {
                if (finalMinecart != null && !finalMinecart.isDead()) {
                    finalMinecart.remove();
                }
            }, 1L, -1); // 1 tick后移除
            return;
        }

        // 如果在停靠区上，根据配置延迟移除矿车
        int despawnDelay = plugin.getConfig().getInt("settings.cart_despawn_delay", 0);

        final Minecart finalMinecart = minecart; // 创建final引用以便在Lambda中使用
        SchedulerUtil.regionRun(plugin, location, () -> {
            if (finalMinecart != null && !finalMinecart.isDead()) {
                finalMinecart.remove();
            }
        }, despawnDelay, -1); // 使用配置的延迟时间
    }

    /**
     * 监听矿车移动事件，检测脱轨和处理上坡速度
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();

        // 只处理地铁矿车
        if (!(vehicle instanceof Minecart)) {
            return;
        }

        Minecart minecart = (Minecart) vehicle;

        // 检查是否是Metro的矿车
        if (!minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (LocationUtil.isOnRail(to)) {
            // 限制上坡速度为0.4，防止到达坡顶后倒退
            if (to.getY() > from.getY()) {
                Vector direction = LocationUtil.getDirectionVector(from, to);
                minecart.setVelocity(direction.multiply(0.4));
            }
        } else {
            // 矿车已脱轨，强制乘客下车并移除矿车
            minecart.eject();
            minecart.remove();
        }
    }

    /**
     * safe_mode.damage_protection：阻止其他实体攻击/破坏地铁矿车
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMetroMinecartDamage(VehicleDamageEvent event) {
        if (!plugin.getConfigFacade().isSafeModeDamageProtection()) {
            return;
        }
        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Minecart minecart)) {
            return;
        }
        if (!minecart.getPersistentDataContainer().has(
                MetroConstants.getMinecartKey(), PersistentDataType.BYTE)) {
            return;
        }
        // 阻止任何来源对地铁矿车造成伤害（包括玩家攻击）
        event.setCancelled(true);
    }

    /**
     * safe_mode.entity_push_protection：
     * EntityDamageByEntityEvent 覆盖 VehicleDamageEvent 未捕获的远程/投射伤害场景
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityHitMetroMinecart(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigFacade().isSafeModeEntityPushProtection()) {
            return;
        }
        Entity damaged = event.getEntity();
        if (!(damaged instanceof Minecart minecart)) {
            return;
        }
        if (!minecart.getPersistentDataContainer().has(
                MetroConstants.getMinecartKey(), PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * 检查位置是否在任何停靠区内
     */
    private boolean isAtStop(Location location) {
        StopManager stopManager = plugin.getStopManager();
        return stopManager.getStopContainingLocation(location) != null;
    }
}