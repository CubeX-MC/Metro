package org.cubexmc.metro.update;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 配置文件更新工具类
 * 用于在插件升级后自动合并新的配置项到现有配置文件中
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * 将默认配置值合并到现有配置中
     * 只添加缺失的键，不覆盖用户已有的设置
     *
     * @param plugin 插件实例
     * @param resourcePath 资源文件路径（如 "config.yml"）
     */
    public static void applyDefaults(JavaPlugin plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("Default resource not found: " + resourcePath);
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            plugin.getConfig().addDefaults(defaults);
            plugin.getConfig().options().copyDefaults(true);
            plugin.saveConfig();
            plugin.getLogger().info("Config updated with new default values from " + resourcePath);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to apply default config values from " + resourcePath + ": " + ex.getMessage());
        }
    }
}
