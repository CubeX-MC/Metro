package org.cubexmc.metro.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

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
     * 检查给定位置是否为铁轨方块
     */
    public static boolean isRail(Location location) {
        if (location == null) {
            return false;
        }
        
        Block block = location.getBlock();
        return block.getType().name().contains("RAIL");
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
     * 获取两个位置之间的方向向量
     */
    public static org.bukkit.util.Vector getDirectionVector(Location from, Location to) {
        return to.toVector().subtract(from.toVector()).normalize();
    }
} 