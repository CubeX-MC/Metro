package org.cubexmc.metro;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.metro.command.MetroAdminCommand;
import org.cubexmc.metro.command.MetroAdminTabCompleter;
import org.cubexmc.metro.lang.LanguageManager;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.listener.PlayerMoveListener;
import org.cubexmc.metro.listener.VehicleListener;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.train.ScoreboardManager;

public final class Metro extends JavaPlugin {

    private LineManager lineManager;
    private StopManager stopManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        // 创建配置目录
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 初始化配置文件
        saveDefaultConfig();
        
        // 初始化默认配置文件
        createDefaultConfigFiles();
        
        // 初始化语言管理器
        this.languageManager = new LanguageManager(this);

        // 初始化管理器
        this.lineManager = new LineManager(this);
        this.stopManager = new StopManager(this);
        
        // 初始化计分板管理器
        ScoreboardManager.initialize(this);

        // 注册命令
        getCommand("m").setExecutor(new MetroAdminCommand(this));
        getCommand("m").setTabCompleter(new MetroAdminTabCompleter(this));

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new VehicleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // 注册bstats
        int pluginId = 25825; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        // getLogger().info(languageManager.getMessage("plugin.enabled"));
        Bukkit.getConsoleSender().sendMessage(languageManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        // getLogger().info(languageManager.getMessage("plugin.disabled"));
        Bukkit.getConsoleSender().sendMessage(languageManager.getMessage("plugin.disabled"));
    }
    
    /**
     * 重新创建默认配置文件（如果不存在）
     * 此方法用于reload命令，确保所有配置文件都能够被重新生成
     */
    public void ensureDefaultConfigs() {
        // 确保主配置文件存在
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            getLogger().info("重新生成默认主配置文件");
        }
        
        // 确保其他配置文件存在
        createDefaultConfigFiles();
    }
    
    /**
     * 创建默认配置文件
     */
    private void createDefaultConfigFiles() {
        // 确保这些文件存在于插件数据文件夹中
        saveDefaultConfigFiles("lines.yml");
        saveDefaultConfigFiles("stops.yml");
    }
    
    /**
     * 保存默认配置文件
     * 
     * @param fileName 文件名
     */
    private void saveDefaultConfigFiles(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }
    
    /**
     * 获取线路管理器
     * 
     * @return 线路管理器
     */
    public LineManager getLineManager() {
        return lineManager;
    }
    
    /**
     * 获取停靠区管理器
     * 
     * @return 停靠区管理器
     */
    public StopManager getStopManager() {
        return stopManager;
    }
    
    /**
     * 获取语言管理器
     * 
     * @return 语言管理器
     */
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    /**
     * 获取进入停靠区Title配置
     */
    public boolean isEnterStopTitleEnabled() {
        return getConfig().getBoolean("titles.enter_stop.enabled", true);
    }
    
    public String getEnterStopTitle() {
        String title = getConfig().getString("titles.enter_stop.title", "§6{line}");
        return ChatColor.translateAlternateColorCodes('&', title);
    }
    
    public String getEnterStopSubtitle() {
        String subtitle = getConfig().getString("titles.enter_stop.subtitle", "§a{stop_name}");
        return ChatColor.translateAlternateColorCodes('&', subtitle);
    }
    
    public int getEnterStopFadeIn() {
        return getConfig().getInt("titles.enter_stop.fade_in", 10);
    }
    
    public int getEnterStopStay() {
        return getConfig().getInt("titles.enter_stop.stay", 40);
    }
    
    public int getEnterStopFadeOut() {
        return getConfig().getInt("titles.enter_stop.fade_out", 10);
    }
    
    /**
     * 获取到站Title配置
     */
    public boolean isArriveStopTitleEnabled() {
        return getConfig().getBoolean("titles.arrive_stop.enabled", true);
    }
    
    public String getArriveStopTitle() {
        String title = getConfig().getString("titles.arrive_stop.title", "§a已到站");
        return ChatColor.translateAlternateColorCodes('&', title);
    }
    
    public String getArriveStopSubtitle() {
        String subtitle = getConfig().getString("titles.arrive_stop.subtitle", "§6{stop_name}");
        return ChatColor.translateAlternateColorCodes('&', subtitle);
    }
    
    public int getArriveStopFadeIn() {
        return getConfig().getInt("titles.arrive_stop.fade_in", 10);
    }
    
    public int getArriveStopStay() {
        return getConfig().getInt("titles.arrive_stop.stay", 40);
    }
    
    public int getArriveStopFadeOut() {
        return getConfig().getInt("titles.arrive_stop.fade_out", 10);
    }
    
    /**
     * 获取终点站Title配置
     */
    public boolean isTerminalStopTitleEnabled() {
        return getConfig().getBoolean("titles.terminal_stop.enabled", true);
    }
    
    public String getTerminalStopTitle() {
        String title = getConfig().getString("titles.terminal_stop.title", "§c终点站");
        return ChatColor.translateAlternateColorCodes('&', title);
    }
    
    public String getTerminalStopSubtitle() {
        String subtitle = getConfig().getString("titles.terminal_stop.subtitle", "§6请下车");
        return ChatColor.translateAlternateColorCodes('&', subtitle);
    }
    
    public int getTerminalStopFadeIn() {
        return getConfig().getInt("titles.terminal_stop.fade_in", 10);
    }
    
    public int getTerminalStopStay() {
        return getConfig().getInt("titles.terminal_stop.stay", 60);
    }
    
    public int getTerminalStopFadeOut() {
        return getConfig().getInt("titles.terminal_stop.fade_out", 10);
    }
    
    /**
     * 获取发车音乐配置
     */
    public boolean isDepartureSoundEnabled() {
        return getConfig().getBoolean("sounds.departure.enabled", true);
    }
    
    public List<String> getDepartureNotes() {
        return getConfig().getStringList("sounds.departure.notes");
    }
    
    public int getDepartureInitialDelay() {
        return getConfig().getInt("sounds.departure.initial_delay", 0);
    }
    
    /**
     * 获取到站音乐配置
     */
    public boolean isArrivalSoundEnabled() {
        return getConfig().getBoolean("sounds.arrival.enabled", true);
    }
    
    public List<String> getArrivalNotes() {
        return getConfig().getStringList("sounds.arrival.notes");
    }
    
    public int getArrivalInitialDelay() {
        return getConfig().getInt("sounds.arrival.initial_delay", 0);
    }
    
    /**
     * 获取车辆到站音乐配置
     */
    public boolean isStationArrivalSoundEnabled() {
        return getConfig().getBoolean("sounds.station_arrival.enabled", true);
    }
    
    public List<String> getStationArrivalNotes() {
        return getConfig().getStringList("sounds.station_arrival.notes");
    }
    
    public int getStationArrivalInitialDelay() {
        return getConfig().getInt("sounds.station_arrival.initial_delay", 0);
    }
    
    /**
     * 获取等待发车音乐配置
     */
    public boolean isWaitingSoundEnabled() {
        return getConfig().getBoolean("sounds.waiting.enabled", true);
    }
    
    public List<String> getWaitingNotes() {
        return getConfig().getStringList("sounds.waiting.notes");
    }
    
    public int getWaitingInitialDelay() {
        return getConfig().getInt("sounds.waiting.initial_delay", 0);
    }
    
    public int getWaitingSoundInterval() {
        return getConfig().getInt("sounds.waiting.interval", 60);
    }
    
    /**
     * 获取矿车速度
     */
    public double getCartSpeed() {
        return getConfig().getDouble("settings.cart_speed", 0.3);
    }
    
    /**
     * 获取矿车生成延迟
     */
    public long getCartSpawnDelay() {
        return getConfig().getLong("settings.cart_spawn_delay", 100L);
    }

    /**
     * 获取列车在站点停留的延迟时间（以游戏刻为单位）
     * 
     * @return 延迟时间，默认为100刻（5秒）
     */
    public long getCartDepartureDelay() {
        return getConfig().getLong("settings.cart_departure_delay", 100L);
    }
}
