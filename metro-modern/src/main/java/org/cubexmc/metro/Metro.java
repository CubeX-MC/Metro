package org.cubexmc.metro;

import java.io.File;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.metro.command.MetroAdminCommand;
import org.cubexmc.metro.command.MetroAdminTabCompleter;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.gui.GuiListener;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.listener.PlayerMoveListener;
import org.cubexmc.metro.listener.VehicleListener;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.update.ConfigUpdater;
import org.cubexmc.metro.util.MetroConstants;

public final class Metro extends JavaPlugin {

    private LineManager lineManager;
    private StopManager stopManager;
    private LanguageManager languageManager;
    private SelectionManager selectionManager;
    private GuiManager guiManager;
    private ConfigFacade configFacade;
    private PlayerInteractListener playerInteractListener;
    private VehicleListener vehicleListener;
    private PlayerMoveListener playerMoveListener;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        // 创建配置目录
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 初始化配置文件
        saveDefaultConfig();
        
        // 自动更新配置文件，添加新版本的配置项
        ConfigUpdater.applyDefaults(this, "config.yml");
        this.configFacade = new ConfigFacade(this);
        
        // 初始化默认配置文件
        createDefaultConfigFiles();
        
        // 初始化语言管理器（内部会自动更新语言文件）
        this.languageManager = new LanguageManager(this);

        // 初始化管理器
        this.lineManager = new LineManager(this);
        this.stopManager = new StopManager(this);
        this.selectionManager = new SelectionManager();
        this.guiManager = new GuiManager(this);
        
        // 初始化计分板管理器
        ScoreboardManager.initialize(this);

        // 注册命令
        PluginCommand metroCommand = getCommand("m");
        if (metroCommand == null) {
            getLogger().severe("Command 'm' is missing in plugin.yml, disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        metroCommand.setExecutor(new MetroAdminCommand(this));
        metroCommand.setTabCompleter(new MetroAdminTabCompleter(this));

        // 注册事件监听器
        this.playerInteractListener = new PlayerInteractListener(this);
        this.vehicleListener = new VehicleListener(this);
        this.playerMoveListener = new PlayerMoveListener(this);
        this.guiListener = new GuiListener(this);
        Bukkit.getPluginManager().registerEvents(playerInteractListener, this);
        Bukkit.getPluginManager().registerEvents(vehicleListener, this);
        Bukkit.getPluginManager().registerEvents(playerMoveListener, this);
        Bukkit.getPluginManager().registerEvents(guiListener, this);

        // 注册bstats
        int pluginId = 25825; // <-- Replace with the id of your plugin!
        new Metrics(this, pluginId);

        // getLogger().info(languageManager.getMessage("plugin.enabled"));
        Bukkit.getConsoleSender().sendMessage(languageManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        // 主动清理任务与显示，避免 reload 残留状态
        if (playerMoveListener != null) {
            playerMoveListener.shutdown();
        }
        if (playerInteractListener != null) {
            playerInteractListener.shutdown();
        }
        ScoreboardManager.shutdown();

        // 清理在线玩家显示与地铁矿车
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardManager.clearPlayerDisplay(player);
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart minecart && MetroConstants.METRO_MINECART_NAME.equals(minecart.getCustomName())) {
                    minecart.eject();
                    minecart.remove();
                }
            }
        }

        if (languageManager != null) {
            Bukkit.getConsoleSender().sendMessage(languageManager.getMessage("plugin.disabled"));
        } else {
            getLogger().info("Metro plugin disabled.");
        }
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
     * 获取选区管理器
     * 
     * @return 选区管理器
     */
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
    
    /**
     * 获取 GUI 管理器
     * 
     * @return GUI 管理器
     */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ConfigFacade getConfigFacade() {
        return configFacade;
    }
    
    /**
     * 获取进入停靠区Title配置
     */
    public boolean isEnterStopTitleEnabled() {
        return configFacade.isEnterStopTitleEnabled();
    }
    
    public String getEnterStopTitle() {
        return configFacade.getEnterStopTitle();
    }
    
    public String getEnterStopSubtitle() {
        return configFacade.getEnterStopSubtitle();
    }
    
    public int getEnterStopFadeIn() {
        return configFacade.getEnterStopFadeIn();
    }
    
    public int getEnterStopStay() {
        return configFacade.getEnterStopStay();
    }
    
    public int getEnterStopFadeOut() {
        return configFacade.getEnterStopFadeOut();
    }
    
    /**
     * 获取到站Title配置
     */
    public boolean isArriveStopTitleEnabled() {
        return configFacade.isArriveStopTitleEnabled();
    }
    
    public String getArriveStopTitle() {
        return configFacade.getArriveStopTitle();
    }
    
    public String getArriveStopSubtitle() {
        return configFacade.getArriveStopSubtitle();
    }
    
    public int getArriveStopFadeIn() {
        return configFacade.getArriveStopFadeIn();
    }
    
    public int getArriveStopStay() {
        return configFacade.getArriveStopStay();
    }
    
    public int getArriveStopFadeOut() {
        return configFacade.getArriveStopFadeOut();
    }
    
    /**
     * 获取终点站Title配置
     */
    public boolean isTerminalStopTitleEnabled() {
        return configFacade.isTerminalStopTitleEnabled();
    }
    
    public String getTerminalStopTitle() {
        return configFacade.getTerminalStopTitle();
    }
    
    public String getTerminalStopSubtitle() {
        return configFacade.getTerminalStopSubtitle();
    }
    
    public int getTerminalStopFadeIn() {
        return configFacade.getTerminalStopFadeIn();
    }
    
    public int getTerminalStopStay() {
        return configFacade.getTerminalStopStay();
    }
    
    public int getTerminalStopFadeOut() {
        return configFacade.getTerminalStopFadeOut();
    }
    
    /**
     * 获取发车音乐配置
     */
    public boolean isDepartureSoundEnabled() {
        return configFacade.isDepartureSoundEnabled();
    }
    
    public List<String> getDepartureNotes() {
        return configFacade.getDepartureNotes();
    }
    
    public int getDepartureInitialDelay() {
        return configFacade.getDepartureInitialDelay();
    }
    
    /**
     * 获取到站音乐配置
     */
    public boolean isArrivalSoundEnabled() {
        return configFacade.isArrivalSoundEnabled();
    }
    
    public List<String> getArrivalNotes() {
        return configFacade.getArrivalNotes();
    }
    
    public int getArrivalInitialDelay() {
        return configFacade.getArrivalInitialDelay();
    }
    
    /**
     * 获取车辆到站音乐配置
     */
    public boolean isStationArrivalSoundEnabled() {
        return configFacade.isStationArrivalSoundEnabled();
    }
    
    public List<String> getStationArrivalNotes() {
        return configFacade.getStationArrivalNotes();
    }
    
    public int getStationArrivalInitialDelay() {
        return configFacade.getStationArrivalInitialDelay();
    }
    
    /**
     * 获取等待发车音乐配置
     */
    public boolean isWaitingSoundEnabled() {
        return configFacade.isWaitingSoundEnabled();
    }
    
    public List<String> getWaitingNotes() {
        return configFacade.getWaitingNotes();
    }
    
    public int getWaitingInitialDelay() {
        return configFacade.getWaitingInitialDelay();
    }
    
    public int getWaitingSoundInterval() {
        return configFacade.getWaitingSoundInterval();
    }
    
    /**
     * 获取矿车速度
     */
    public double getCartSpeed() {
        return configFacade.getCartSpeed();
    }
    
    /**
     * 获取矿车生成延迟
     */
    public long getCartSpawnDelay() {
        return configFacade.getCartSpawnDelay();
    }

    /**
     * 获取列车在站点停留的延迟时间（以游戏刻为单位）
     * 
     * @return 延迟时间，默认为100刻（5秒）
     */
    public long getCartDepartureDelay() {
        return configFacade.getCartDepartureDelay();
    }

    /**
     * 是否启用调试日志。
     */
    public boolean isDebugEnabled() {
        return configFacade.isDebugEnabled();
    }

    /**
     * 是否启用某个调试分类。
     *
     * @param category 调试分类键，例如 train_state_transitions
     */
    public boolean isDebugCategoryEnabled(String category) {
        return configFacade.isDebugCategoryEnabled(category);
    }

    /**
     * 输出分类调试日志。
     */
    public void debug(String category, String message) {
        if (!isDebugCategoryEnabled(category)) {
            return;
        }
        getLogger().info("[DEBUG][" + category + "] " + message);
    }
    
    /**
     * 获取站台选区工具的Material类型
     * 
     * @return Material类型，默认为GOLDEN_SHOVEL
     */
    public Material getSelectionTool() {
        return configFacade.getSelectionTool();
    }

    /**
     * 获取选区工具的显示名称（用于语言消息）
     * 
     * @return 工具的显示名称
     */
    public String getSelectionToolName() {
        return configFacade.getSelectionToolName();
    }
}
