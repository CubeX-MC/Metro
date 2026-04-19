package org.cubexmc.metro.util;

import org.bukkit.Bukkit;

/**
 * 版本兼容工具类
 * 用于检测服务器版本和特性支持
 */
public final class VersionUtil {

    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final boolean IS_FOLIA;
    private static final boolean IS_PAPER;

    static {
        // 解析服务器版本，例如 "1.20.4-R0.1-SNAPSHOT" -> major=1, minor=20
        String version = Bukkit.getBukkitVersion();
        String[] parts = version.split("-")[0].split("\\.");
        MAJOR_VERSION = Integer.parseInt(parts[0]);
        MINOR_VERSION = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        // 检测是否为 Folia 服务器
        IS_FOLIA = checkClass("io.papermc.paper.threadedregions.RegionizedServer");

        // 检测是否为 Paper 服务器
        IS_PAPER = checkClass("com.destroystokyo.paper.PaperConfig") || 
                   checkClass("io.papermc.paper.configuration.Configuration");
    }

    private VersionUtil() {
    }

    /**
     * 检查类是否存在
     */
    private static boolean checkClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取主版本号
     * @return 主版本号，如 1
     */
    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * 获取次版本号
     * @return 次版本号，如 20
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * 检查服务器版本是否大于等于指定版本
     * @param major 主版本号
     * @param minor 次版本号
     * @return 是否大于等于指定版本
     */
    public static boolean isVersionAtLeast(int major, int minor) {
        if (MAJOR_VERSION > major) return true;
        if (MAJOR_VERSION < major) return false;
        return MINOR_VERSION >= minor;
    }

    /**
     * 检查是否为 1.20 或更高版本
     * @return 是否为 1.20+
     */
    public static boolean isModernVersion() {
        return isVersionAtLeast(1, 20);
    }

    /**
     * 检查是否为 Folia 服务器
     * @return 是否为 Folia
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * 检查是否为 Paper 服务器
     * @return 是否为 Paper
     */
    public static boolean isPaper() {
        return IS_PAPER;
    }

    /**
     * 获取版本字符串
     * @return 版本字符串，如 "1.20.4"
     */
    public static String getVersionString() {
        return MAJOR_VERSION + "." + MINOR_VERSION;
    }
}

