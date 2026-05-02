package org.cubexmc.metro.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

/**
 * 版本兼容工具类
 * 用于检测服务器版本和特性支持
 */
public final class VersionUtil {

    private static final Pattern MC_VERSION_PATTERN =
            Pattern.compile("\\bMC:\\s*(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?!\\d)");
    private static final ServerVersion SERVER_VERSION;
    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final int PATCH_VERSION;
    private static final boolean IS_FOLIA;
    private static final boolean IS_PAPER;

    static {
        // 解析服务器版本，例如 "1.20.4-R0.1-SNAPSHOT" 或 "26.1.2-R0.1-SNAPSHOT"
        SERVER_VERSION = parseBukkitVersion(readBukkitVersion());
        MAJOR_VERSION = SERVER_VERSION.major();
        MINOR_VERSION = SERVER_VERSION.minor();
        PATCH_VERSION = SERVER_VERSION.patch();

        // 检测是否为 Folia 服务器
        IS_FOLIA = checkClass("io.papermc.paper.threadedregions.RegionizedServer");

        // 检测是否为 Paper 服务器
        IS_PAPER = checkClass("com.destroystokyo.paper.PaperConfig") || 
                   checkClass("io.papermc.paper.configuration.Configuration");
    }

    private VersionUtil() {
    }

    private static String readBukkitVersion() {
        try {
            return Bukkit.getBukkitVersion();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    static ServerVersion parseBukkitVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return ServerVersion.UNKNOWN;
        }

        Matcher mcVersionMatcher = MC_VERSION_PATTERN.matcher(rawVersion);
        if (mcVersionMatcher.find()) {
            return toServerVersion(mcVersionMatcher);
        }

        Matcher versionMatcher = VERSION_PATTERN.matcher(rawVersion);
        if (versionMatcher.find()) {
            return toServerVersion(versionMatcher);
        }

        return ServerVersion.UNKNOWN;
    }

    private static ServerVersion toServerVersion(Matcher matcher) {
        return new ServerVersion(
                parseVersionPart(matcher.group(1)),
                parseVersionPart(matcher.group(2)),
                parseVersionPart(matcher.group(3))
        );
    }

    private static int parseVersionPart(String part) {
        if (part == null || part.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
     * 获取补丁版本号
     * @return 补丁版本号，如 4
     */
    public static int getPatchVersion() {
        return PATCH_VERSION;
    }

    /**
     * 检查服务器版本是否大于等于指定版本
     * @param major 主版本号
     * @param minor 次版本号
     * @return 是否大于等于指定版本
     */
    public static boolean isVersionAtLeast(int major, int minor) {
        return SERVER_VERSION.isAtLeast(major, minor, 0);
    }

    /**
     * 检查服务器版本是否大于等于指定版本
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 补丁版本号
     * @return 是否大于等于指定版本
     */
    public static boolean isVersionAtLeast(int major, int minor, int patch) {
        return SERVER_VERSION.isAtLeast(major, minor, patch);
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
     * @return 版本字符串，如 "1.20.4" 或 "26.1.2"
     */
    public static String getVersionString() {
        return SERVER_VERSION.toString();
    }

    record ServerVersion(int major, int minor, int patch) {

        static final ServerVersion UNKNOWN = new ServerVersion(0, 0, 0);

        boolean isAtLeast(int requiredMajor, int requiredMinor, int requiredPatch) {
            if (major > requiredMajor) return true;
            if (major < requiredMajor) return false;
            if (minor > requiredMinor) return true;
            if (minor < requiredMinor) return false;
            return patch >= requiredPatch;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}

