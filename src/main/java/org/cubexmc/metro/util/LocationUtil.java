package org.cubexmc.metro.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.EnumSet;
import java.util.Set;

/**
 * 位置工具类，用于位置数据的序列化和反序列化
 */
public class LocationUtil {
    
    /**
     * 将Location对象转换为字符串格式
     * 格式为: "世界名,x,y,z"
     */
    public static String locationToString(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        
        return String.format("%s,%d,%d,%d", 
                location.getWorld().getName(), 
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ());
    }
    
    /**
     * 将字符串格式的位置转换为Location对象
     * 格式应为: "世界名,x,y,z"
     */
    public static Location stringToLocation(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }
        
        String[] parts = locationStr.split(",");
        if (parts.length < 4) {
            return null;
        }
        
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 检查给定位置是否为铁轨方块
     */
    private static final Set<Material> RAIL_MATERIALS = EnumSet.of(
            Material.RAIL,
            Material.POWERED_RAIL,
            Material.DETECTOR_RAIL,
            Material.ACTIVATOR_RAIL
            // Add other rail types if necessary, e.g., from mods if this plugin supports them
    );

    public static boolean isRail(Location location) {
        if (location == null) {
            return false;
        }
        Block block = location.getBlock();
        return RAIL_MATERIALS.contains(block.getType());
    }

    /**
     * 检查给定位置是否在铁轨上（包括当前位置和下方一格）
     */
    public static boolean isOnRail(Location location) {
        if (location == null) {
            return false;
        }
        
        // 检查当前位置
        if (isRail(location)) {
            return true;
        }
        
        // 检查下方一格
        Location belowLocation = location.clone().subtract(0, 1, 0);
        return isRail(belowLocation);
    }
    
    /**
     * 检查给定位置是否为红石铁轨
     */
    public static boolean isPoweredRail(Location location) {
        if (location == null) {
            return false;
        }
        
        Block block = location.getBlock();
        return block.getType() == Material.POWERED_RAIL;
    }
    
    /**
     * 获取两个位置之间的方向向量
     */
    public static org.bukkit.util.Vector getDirectionVector(Location from, Location to) {
        return to.toVector().subtract(from.toVector()).normalize();
    }
} 