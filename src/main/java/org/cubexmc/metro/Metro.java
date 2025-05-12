package org.cubexmc.metro;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.metro.command.MetroAdminCommand;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.listener.PlayerMoveListener;
import org.cubexmc.metro.listener.VehicleListener;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.train.ScoreboardManager;

import java.io.File;
import java.util.List;

public final class Metro extends JavaPlugin {

    private LineManager lineManager;
    private StopManager stopManager;

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

        // 初始化管理器
        this.lineManager = new LineManager(this);
        this.stopManager = new StopManager(this);
        
        // 初始化计分板管理器
        ScoreboardManager.initialize(this);

        // 注册命令
        getCommand("m").setExecutor(new MetroAdminCommand(this));

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new VehicleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // 注册bstats
        int pluginId = 25825; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info("Metro插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Metro插件已禁用!");
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
     * 获取进入停靠区Title配置
     */
    public boolean isEnterStopTitleEnabled() {
        return getConfig().getBoolean("titles.enter_stop.enabled", true);
    }
    
    public String getEnterStopTitle() {
        return getConfig().getString("titles.enter_stop.title", "§6{line}");
    }
    
    public String getEnterStopSubtitle() {
        return getConfig().getString("titles.enter_stop.subtitle", "§a{stop_name}");
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
        return getConfig().getString("titles.arrive_stop.title", "§a已到站");
    }
    
    public String getArriveStopSubtitle() {
        return getConfig().getString("titles.arrive_stop.subtitle", "§6{stop_name}");
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
        return getConfig().getString("titles.terminal_stop.title", "§c终点站");
    }
    
    public String getTerminalStopSubtitle() {
        return getConfig().getString("titles.terminal_stop.subtitle", "§6请下车");
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
    
    /**
     * 获取到站音乐配置
     */
    public boolean isArrivalSoundEnabled() {
        return getConfig().getBoolean("sounds.arrival.enabled", true);
    }
    
    public List<String> getArrivalNotes() {
        return getConfig().getStringList("sounds.arrival.notes");
    }
    
    /**
     * 获取矿车速度
     */
    public double getCartSpeed() {
        return getConfig().getDouble("settings.cart_speed", 0.3);
    }
    
    /**
     * 获取停靠区范围
     * 
     * @deprecated 此方法不再被使用，因为停靠区现在由两个对角坐标点确定区域，而不是使用固定范围
     */
    @Deprecated
    public double getStopRange() {
        return getConfig().getDouble("settings.stop_range", 5.0);
    }
}
