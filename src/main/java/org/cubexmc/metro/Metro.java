package org.cubexmc.metro;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import org.cubexmc.metro.command.newcmd.LineCommand;
import org.cubexmc.metro.command.newcmd.MetroMainCommand;
import org.cubexmc.metro.command.newcmd.StopCommand;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.gui.ChatInputManager;
import org.cubexmc.metro.gui.GuiListener;
import org.cubexmc.metro.gui.GuiManager;
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
    private Object autoSaveTaskId;

    private org.cubexmc.metro.integration.BlueMapIntegration blueMapIntegration;
    private org.cubexmc.metro.integration.DynmapIntegration dynmapIntegration;
    private org.cubexmc.metro.integration.SquaremapIntegration squaremapIntegration;
    private boolean mapRefreshQueued = false;

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

        // 注册命令 (Cloud Command Framework)
        try {
            try {
                // 尝试寻找 Paper 1.20.5+ 新引入的生命周期事件类
                Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager");
                final Class<?> cssClass = Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack");
                
                @SuppressWarnings({"unchecked", "rawtypes"})
                org.incendo.cloud.SenderMapper<?, CommandSender> mapper = org.incendo.cloud.SenderMapper.create(
                    source -> {
                        try {
                            return (CommandSender) source.getClass().getMethod("getSender").invoke(source);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to map CommandSourceStack to CommandSender", e);
                        }
                    },
                    sender -> java.lang.reflect.Proxy.newProxyInstance(
                        cssClass.getClassLoader(),
                        new Class<?>[]{cssClass},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if ("getSender".equals(name)) return sender;
                            if ("getLocation".equals(name)) return sender instanceof org.bukkit.entity.Entity ? ((org.bukkit.entity.Entity) sender).getLocation() : null;
                            if ("getExecutor".equals(name)) return sender instanceof org.bukkit.entity.Entity ? sender : null;
                            if ("toString".equals(name)) return "CommandSourceStackProxy[" + sender.getName() + "]";
                            if ("equals".equals(name)) return args != null && args.length == 1 && proxy == args[0];
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            return null;
                        }
                    )
                );
                
                commandManager = (org.incendo.cloud.CommandManager<CommandSender>) org.incendo.cloud.paper.PaperCommandManager.builder((org.incendo.cloud.SenderMapper) mapper)
                        .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                        .buildOnEnable(this);
                        
                getLogger().info("已加载新版 PaperCommandManager (1.20.5+)");
            } catch (ClassNotFoundException e) {
                // 如果抛出异常，说明找不到那个类，当前是老版本服务器 (1.18 - 1.20.4)
                // 回退使用 Legacy 版
                org.incendo.cloud.paper.LegacyPaperCommandManager<CommandSender> legacyManager = org.incendo.cloud.paper.LegacyPaperCommandManager.createNative(
                        this,
                        ExecutionCoordinator.simpleCoordinator());

                // 老版本通常需要手动注册 Brigadier 和 Asynchronous Completion
                if (legacyManager.hasCapability(org.incendo.cloud.bukkit.CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
                    legacyManager.registerBrigadier();
                }
                if (legacyManager.hasCapability(org.incendo.cloud.bukkit.CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                    legacyManager.registerAsynchronousCompletions();
                }
                
                commandManager = legacyManager;
                getLogger().info("已加载兼容版 LegacyPaperCommandManager (1.20.4 及以下)");
            }

            annotationParser = new AnnotationParser<>(commandManager, CommandSender.class);

            registerSuggestionProviders();

            // 解析并注册带有 @Command 注解的类
            annotationParser.parse(
                new MetroMainCommand(this, lineManager, stopManager),
                new LineCommand(this, lineManager, stopManager),
                new StopCommand(this, stopManager, lineManager),
                new org.cubexmc.metro.command.newcmd.PortalCommand(this)
            );

            getLogger().info("Cloud Command Framework initialized successfully.");

        } catch (Exception e) {
            getLogger().severe("Failed to initialize Cloud Command Framework:");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 注册事件监听器
        this.playerInteractListener = new PlayerInteractListener(this);
        this.vehicleListener = new VehicleListener(this);
        this.playerMoveListener = new PlayerMoveListener(this);
        this.guiListener = new GuiListener(this);
        this.trainDisplayController = new TrainDisplayController(this);
        Bukkit.getPluginManager().registerEvents(playerInteractListener, this);
        Bukkit.getPluginManager().registerEvents(vehicleListener, this);
        Bukkit.getPluginManager().registerEvents(playerMoveListener, this);
        Bukkit.getPluginManager().registerEvents(guiListener, this);
        Bukkit.getPluginManager().registerEvents(trainDisplayController, this);
        Bukkit.getPluginManager().registerEvents(railProtectionManager, this);

        // 注册bstats
        int pluginId = 25825; // <-- Replace with the id of your plugin!
        new Metrics(this, pluginId);

        // 自动保存任务（每60秒检查一次）
        this.autoSaveTaskId = org.cubexmc.metro.util.SchedulerUtil.globalRun(this, () -> {
            if (lineManager != null) {
                lineManager.processAsyncSave();
            }
            if (stopManager != null) {
                stopManager.processAsyncSave();
            }
        }, 1200L, 1200L);

        // ==== BACKWARD COMPATIBILITY ====
        // Sweep worlds for name-based minecarts missing the PDC tag and apply it.
        // Doing this delayed allows worlds to fully load.
        org.cubexmc.metro.util.SchedulerUtil.globalRun(this, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Minecart.class)) {
                    if (org.cubexmc.metro.util.MetroConstants.METRO_MINECART_NAME.equals(entity.getCustomName()) &&
                            !entity.getPersistentDataContainer().has(
                                    org.cubexmc.metro.util.MetroConstants.getMinecartKey(),
                                    org.bukkit.persistence.PersistentDataType.BYTE)) {
                        entity.getPersistentDataContainer().set(org.cubexmc.metro.util.MetroConstants.getMinecartKey(),
                                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                        getLogger().info("Migrated legacy Metro Minecart to PDC data: " + entity.getUniqueId());
                    }
                }
            }
        }, 100L, -1L); // 5 seconds after startup

        // 网页地图集成（BlueMap / Dynmap / Squaremap）
        // 集成类内部会自动检查 config 和 classpath，只有满足条件时才加载
        try {
            this.blueMapIntegration = new org.cubexmc.metro.integration.BlueMapIntegration(this);
            this.blueMapIntegration.enable();
        } catch (Throwable e) {
            getLogger().info("BlueMap API not found, skipping BlueMap integration.");
        }

        try {
            this.dynmapIntegration = new org.cubexmc.metro.integration.DynmapIntegration(this);
            this.dynmapIntegration.enable();
        } catch (Throwable e) {
            getLogger().info("Dynmap API not found, skipping Dynmap integration.");
        }

        try {
            this.squaremapIntegration = new org.cubexmc.metro.integration.SquaremapIntegration(this);
            this.squaremapIntegration.enable();
        } catch (Throwable e) {
            getLogger().info("Squaremap API not found, skipping Squaremap integration.");
        }

        getLogger().info("Metro(Modern) has been enabled!");
    }

    @Override
    public void onDisable() {
        // 关闭地图集成并清理标记
        if (this.blueMapIntegration != null) {
            this.blueMapIntegration.disable();
        }
        if (this.dynmapIntegration != null) {
            this.dynmapIntegration.disable();
        }
        if (this.squaremapIntegration != null) {
            this.squaremapIntegration.disable();
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

        if (autoSaveTaskId != null) {
            org.cubexmc.metro.util.SchedulerUtil.cancelTask(autoSaveTaskId);
        }
        if (routeRecorder != null) {
            routeRecorder.cancelAll();
        }
        if (lineManager != null) {
            lineManager.forceSaveSync();
        }
        if (stopManager != null) {
            stopManager.forceSaveSync();
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
        if (this.blueMapIntegration != null) {
            this.blueMapIntegration.refresh();
        }
        if (this.dynmapIntegration != null) {
            this.dynmapIntegration.refresh();
        }
        if (this.squaremapIntegration != null) {
            this.squaremapIntegration.refresh();
        }
    }

    public void requestMapIntegrationRefresh() {
        if (this.configFacade == null || !this.configFacade.isMapIntegrationEnabled() || mapRefreshQueued) {
            return;
        }
        mapRefreshQueued = true;
        org.cubexmc.metro.util.SchedulerUtil.globalRun(this, () -> {
            mapRefreshQueued = false;
            refreshMapIntegrations();
        }, 1L, -1L);
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

    public PlayerInteractListener getPlayerInteractListener() {
        return playerInteractListener;
    }

    private void registerSuggestionProviders() {
        commandManager.parserRegistry().registerSuggestionProvider("lineIds",
                (context, input) -> toSuggestionsFuture(lineIdSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("stopIds",
                (context, input) -> toSuggestionsFuture(stopIdSuggestions(context, input)));
    }

    private Iterable<String> lineIdSuggestions(final CommandContext<CommandSender> context, final CommandInput input) {
        return lineManager.getAllLines().stream().map(org.cubexmc.metro.model.Line::getId).toList();
    }

    private Iterable<String> stopIdSuggestions(final CommandContext<CommandSender> context, final CommandInput input) {
        return new java.util.ArrayList<>(stopManager.getAllStopIds());
    }

    private java.util.concurrent.CompletableFuture<? extends Iterable<? extends org.incendo.cloud.suggestion.Suggestion>> toSuggestionsFuture(
            final Iterable<String> values) {
        final java.util.List<org.incendo.cloud.suggestion.Suggestion> suggestions = new java.util.ArrayList<>();
        for (final String value : values) {
            suggestions.add(org.incendo.cloud.suggestion.Suggestion.suggestion(value));
        }
        return java.util.concurrent.CompletableFuture.completedFuture(suggestions);
    }
}
