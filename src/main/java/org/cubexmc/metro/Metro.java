package org.cubexmc.metro;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.incendo.cloud.annotations.AnnotationParser;

import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.gui.ChatInputManager;
import org.cubexmc.metro.gui.GuiListener;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.lifecycle.CommandRegistration;
import org.cubexmc.metro.lifecycle.ListenerRegistration;
import org.cubexmc.metro.lifecycle.MapIntegrationLifecycle;
import org.cubexmc.metro.lifecycle.ScheduledTaskLifecycle;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.listener.PlayerMoveListener;
import org.cubexmc.metro.listener.VehicleListener;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.RailProtectionManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.persistence.SaveCoordinator;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.train.TrainDisplayController;
import org.cubexmc.metro.update.ConfigUpdater;
import org.cubexmc.metro.update.DataFileUpdater;
import org.cubexmc.metro.util.MetroConstants;

public final class Metro extends JavaPlugin {

    private LineManager lineManager;
    private StopManager stopManager;
    private LanguageManager languageManager;
    private ScoreboardLibrary globalScoreboardLibrary;
    private org.cubexmc.metro.train.ScoreboardManager scoreboardManager;
    private SelectionManager selectionManager;
    private GuiManager guiManager;
    private ChatInputManager chatInputManager;
    private ConfigFacade configFacade;
    private PlayerInteractListener playerInteractListener;
    private VehicleListener vehicleListener;
    private PlayerMoveListener playerMoveListener;
    private GuiListener guiListener;
    private TrainDisplayController trainDisplayController;
    private org.incendo.cloud.CommandManager<CommandSender> commandManager;
    private AnnotationParser<CommandSender> annotationParser;
    private org.cubexmc.metro.manager.PortalManager portalManager;
    private org.cubexmc.metro.manager.RouteRecorder routeRecorder;
    private RailProtectionManager railProtectionManager;
    private org.cubexmc.metro.integration.VaultIntegration vaultIntegration;
    private org.cubexmc.metro.service.LineSelectionService lineSelectionService;
    private org.cubexmc.metro.service.TicketService ticketService;
    private SaveCoordinator saveCoordinator;
    private MapIntegrationLifecycle mapIntegrationLifecycle;
    private ScheduledTaskLifecycle scheduledTaskLifecycle;

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
        this.configFacade.reload();

        // 初始化默认配置文件
        createDefaultConfigFiles();
        DataFileUpdater.migrateAll(this);

        // 初始化语言管理器（内部会自动更新语言文件）
        this.languageManager = new LanguageManager(this);
        this.saveCoordinator = new SaveCoordinator(getLogger(),
                command -> org.cubexmc.metro.util.SchedulerUtil.asyncRun(this, command, 0L));

        // 初始化管理器
        this.lineManager = new LineManager(this);
        this.railProtectionManager = new RailProtectionManager(this);
        this.railProtectionManager.rebuildAll();
        this.stopManager = new StopManager(this);
        this.lineSelectionService = new org.cubexmc.metro.service.LineSelectionService(lineManager, stopManager);
        this.selectionManager = new SelectionManager();
        this.guiManager = new GuiManager(this);
        this.chatInputManager = new ChatInputManager(this);
        this.routeRecorder = new org.cubexmc.metro.manager.RouteRecorder(this);
        Bukkit.getPluginManager().registerEvents(this.chatInputManager, this);

        // 初始化传送门管理器
        this.portalManager = new org.cubexmc.metro.manager.PortalManager(this);

        // 初始化经济集成
        this.vaultIntegration = new org.cubexmc.metro.integration.VaultIntegration(this);
        if (this.vaultIntegration.isEnabled()) {
            getLogger().info("Vault economy integration enabled.");
        } else {
            getLogger().info("Vault economy not found or disabled.");
        }
        this.ticketService = new org.cubexmc.metro.service.TicketService(this::getVaultIntegration,
                () -> getConfig().getBoolean("economy.enabled", true));

        // 初始化计分板库
        try {
            this.globalScoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this);
        } catch (NoPacketAdapterAvailableException e) {
            getLogger().severe("无法加载 ScoreboardLibrary 数据包适配器，计分板功能将被禁用！");
        }

        // 初始化计分板管理器
        scoreboardManager = new ScoreboardManager(this);
        MetroConstants.initialize(this);

        CommandRegistration.Result commandRegistration =
                new CommandRegistration(this, lineManager, stopManager).register();
        if (commandRegistration == null) {
            return;
        }
        this.commandManager = commandRegistration.commandManager();
        this.annotationParser = commandRegistration.annotationParser();

        ListenerRegistration.Result listenerRegistration =
                new ListenerRegistration(this, railProtectionManager).register();
        this.playerInteractListener = listenerRegistration.playerInteractListener();
        this.vehicleListener = listenerRegistration.vehicleListener();
        this.playerMoveListener = listenerRegistration.playerMoveListener();
        this.guiListener = listenerRegistration.guiListener();
        this.trainDisplayController = listenerRegistration.trainDisplayController();

        // 注册bstats
        int pluginId = 25825; // <-- Replace with the id of your plugin!
        new Metrics(this, pluginId);

        this.scheduledTaskLifecycle = new ScheduledTaskLifecycle(this, lineManager, stopManager);
        this.scheduledTaskLifecycle.start();

        this.mapIntegrationLifecycle = new MapIntegrationLifecycle(this);
        this.mapIntegrationLifecycle.enable();

        getLogger().info("Metro(Modern) has been enabled!");
    }

    @Override
    public void onDisable() {
        if (this.mapIntegrationLifecycle != null) {
            this.mapIntegrationLifecycle.disable();
        }

        // 主动清理任务与显示，避免 reload 残留状态
        if (playerMoveListener != null) {
            playerMoveListener.shutdown();
        }
        if (playerInteractListener != null) {
            playerInteractListener.shutdown();
        }
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }
        if (globalScoreboardLibrary != null) {
            globalScoreboardLibrary.close();
        }

        // 清理在线玩家显示与地铁矿车
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (scoreboardManager != null) {
                scoreboardManager.clearPlayerDisplay(player);
            }
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart minecart
                        && minecart.getPersistentDataContainer().has(
                                org.cubexmc.metro.util.MetroConstants.getMinecartKey(),
                                org.bukkit.persistence.PersistentDataType.BYTE)) {
                    minecart.eject();
                    minecart.remove();
                }
            }
        }

        if (scheduledTaskLifecycle != null) {
            scheduledTaskLifecycle.shutdown();
        }
        if (routeRecorder != null) {
            routeRecorder.cancelAll();
        }
        flushPersistentData();

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

    public ScoreboardLibrary getGlobalScoreboardLibrary() {
        return globalScoreboardLibrary;
    }

    public org.cubexmc.metro.train.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
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

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public ConfigFacade getConfigFacade() {
        return configFacade;
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

    public void refreshMapIntegrations() {
        if (this.mapIntegrationLifecycle != null) {
            this.mapIntegrationLifecycle.refresh();
        }
    }

    public void requestMapIntegrationRefresh() {
        if (this.mapIntegrationLifecycle != null) {
            this.mapIntegrationLifecycle.requestRefresh();
        }
    }

    public org.cubexmc.metro.manager.PortalManager getPortalManager() {
        return portalManager;
    }

    public org.cubexmc.metro.manager.RouteRecorder getRouteRecorder() {
        return routeRecorder;
    }

    public RailProtectionManager getRailProtectionManager() {
        return railProtectionManager;
    }

    public org.cubexmc.metro.integration.VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }

    public org.cubexmc.metro.service.LineSelectionService getLineSelectionService() {
        return lineSelectionService;
    }

    public org.cubexmc.metro.service.TicketService getTicketService() {
        return ticketService;
    }

    public SaveCoordinator getSaveCoordinator() {
        return saveCoordinator;
    }

    public void flushPersistentData() {
        if (lineManager != null) {
            lineManager.forceSaveSync();
        }
        if (stopManager != null) {
            stopManager.forceSaveSync();
        }
        if (saveCoordinator != null) {
            saveCoordinator.flushAll();
        }
    }

    public PlayerInteractListener getPlayerInteractListener() {
        return playerInteractListener;
    }

}
