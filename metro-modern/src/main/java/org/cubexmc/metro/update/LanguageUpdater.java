package org.cubexmc.metro.update;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 语言文件更新工具类
 * 用于在插件升级后自动合并新的语言键到现有语言文件中
 */
public final class LanguageUpdater {

    private LanguageUpdater() {
    }

    /**
     * 将默认语言文件中的新键合并到用户的语言文件中
     * 只添加缺失的键，不覆盖用户已有的翻译
     *
     * @param plugin 插件实例
     * @param targetFile 目标语言文件（用户的文件）
     * @param resourcePath 资源文件路径（如 "lang/zh_CN.yml"）
     */
    public static void merge(JavaPlugin plugin, File targetFile, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("Language resource not found: " + resourcePath);
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(targetFile);
            
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                // 只复制叶子节点（实际的值），不复制中间节点
                if (!defaults.isConfigurationSection(key) && !existing.contains(key)) {
                    existing.set(key, defaults.get(key));
                    changed = true;
                }
            }
            
            if (changed) {
                existing.save(targetFile);
                plugin.getLogger().info("Language file updated: " + targetFile.getName());
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to merge language defaults for " + targetFile.getName() + ": " + ex.getMessage());
        }
    }
}
