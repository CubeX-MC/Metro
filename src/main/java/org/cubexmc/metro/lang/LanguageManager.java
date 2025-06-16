package org.cubexmc.metro.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;

/**
 * 管理多语言消息的类
 */
public class LanguageManager {

    private final Metro plugin;
    private final Map<String, YamlConfiguration> languageFiles = new HashMap<>();
    private String defaultLanguage = "zh_CN";
    private String currentLanguage = "zh_CN";

    /**
     * 构造语言管理器
     *
     * @param plugin 插件实例
     */
    public LanguageManager(Metro plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    /**
     * 加载所有语言文件
     */
    public void loadLanguages() {
        // 清空已加载的语言
        languageFiles.clear();

        // 获取配置中的默认语言
        defaultLanguage = plugin.getConfig().getString("settings.default_language", "zh_CN");
        currentLanguage = defaultLanguage;

        // 确保语言目录存在
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // 保存默认语言文件
        saveDefaultLanguageFile("zh_CN");
        
        // 加载语言目录下的所有yml文件
        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File file : langFiles) {
                String langCode = file.getName().replace(".yml", "");
                try {
                    YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(file);
                    languageFiles.put(langCode, langConfig);
                    plugin.getLogger().info("已加载语言文件: " + langCode);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "加载语言文件失败: " + file.getName(), e);
                }
            }
        }

        // 如果没有找到默认语言，则尝试加载内置语言
        if (!languageFiles.containsKey(defaultLanguage)) {
            try {
                InputStream inputStream = plugin.getResource("lang/" + defaultLanguage + ".yml");
                if (inputStream != null) {
                    YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    languageFiles.put(defaultLanguage, defaultConfig);
                    plugin.getLogger().info("Loaded default language: " + defaultLanguage);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load default language: " + defaultLanguage, e);
            }
        }
    }

    /**
     * 保存默认语言文件
     *
     * @param langCode 语言代码
     */
    private void saveDefaultLanguageFile(String langCode) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + langCode + ".yml", false);
        }
    }

    /**
     * 设置当前使用的语言
     *
     * @param langCode 语言代码
     * @return 是否成功设置
     */
    public boolean setLanguage(String langCode) {
        if (languageFiles.containsKey(langCode)) {
            currentLanguage = langCode;
            return true;
        }
        return false;
    }

    /**
     * 获取语言消息
     *
     * @param key 消息键
     * @return 格式化后的消息
     */
    public String getMessage(String key) {
        return getMessage(key, currentLanguage);
    }

    /**
     * 获取语言消息
     *
     * @param key 消息键
     * @param langCode 语言代码
     * @return 格式化后的消息
     */
    public String getMessage(String key, String langCode) {
        // 尝试从指定语言获取消息
        YamlConfiguration langConfig = languageFiles.get(langCode);
        
        // 如果找不到指定语言或该语言中没有这个键，则尝试从默认语言获取
        if (langConfig == null || !langConfig.contains(key)) {
            langConfig = languageFiles.get(defaultLanguage);
        }
        
        // 如果默认语言也没有，则返回键名作为后备
        if (langConfig == null || !langConfig.contains(key)) {
            return "Missing message: " + key;
        }
        
        // 获取消息并替换颜色代码
        String message = langConfig.getString(key);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * 使用参数获取格式化的语言消息（数字占位符）
     *
     * @param key 消息键
     * @param args 替换参数，格式为 %1, %2 等
     * @return 格式化后的消息
     */
    public String getMessage(String key, Object... args) {
        String message = getMessage(key);
        for (int i = 0; i < args.length; i++) {
            message = message.replace("%" + (i + 1), String.valueOf(args[i]));
        }
        return message;
    }

    /**
     * 使用命名参数获取格式化的语言消息
     *
     * @param key 消息键
     * @param namedArgs 命名参数映射，格式为 {key} = value
     * @return 格式化后的消息
     */
    public String getMessage(String key, Map<String, Object> namedArgs) {
        String message = getMessage(key);
        if (namedArgs != null) {
            for (Map.Entry<String, Object> entry : namedArgs.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return message;
    }

    /**
     * 创建命名参数映射的便捷方法
     *
     * @return 新的参数映射
     */
    public static Map<String, Object> args() {
        return new HashMap<>();
    }

    /**
     * 向参数映射添加参数的便捷方法
     *
     * @param args 参数映射
     * @param key 参数名
     * @param value 参数值
     * @return 参数映射（链式调用）
     */
    public static Map<String, Object> put(Map<String, Object> args, String key, Object value) {
        args.put(key, value);
        return args;
    }
} 