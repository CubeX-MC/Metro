package org.cubexmc.metro

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Minecart
import org.bukkit.persistence.PersistentDataType
import org.cubexmc.core.CubexPlugin
import org.cubexmc.metro.api.MetroAPI
import org.cubexmc.metro.bedrock.BedrockCompatibility
import org.cubexmc.metro.config.ConfigFacade
import org.cubexmc.metro.gui.ChatInputManager
import org.cubexmc.metro.gui.GuiListener
import org.cubexmc.metro.gui.GuiManager
import org.cubexmc.metro.integration.VaultIntegration
import org.cubexmc.metro.lifecycle.CommandRegistration
import org.cubexmc.metro.lifecycle.ListenerRegistration
import org.cubexmc.metro.lifecycle.MapIntegrationLifecycle
import org.cubexmc.metro.lifecycle.ScheduledTaskLifecycle
import org.cubexmc.metro.listener.PlayerInteractListener
import org.cubexmc.metro.listener.PlayerMoveListener
import org.cubexmc.metro.listener.VehicleListener
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.manager.RailProtectionManager
import org.cubexmc.metro.manager.RouteRecorder
import org.cubexmc.metro.manager.SelectionManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.persistence.SaveCoordinator
import org.cubexmc.metro.service.LineSelectionService
import org.cubexmc.metro.service.LineStatusService
import org.cubexmc.metro.service.PriceService
import org.cubexmc.metro.service.TicketService
import org.cubexmc.metro.train.ScoreboardManager
import org.cubexmc.metro.train.TrainDisplayController
import org.cubexmc.metro.train.TrainMovementTask
import org.cubexmc.metro.update.DataFileUpdater
import org.cubexmc.metro.update.MetroMigrations
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.MinecartEjector
import org.cubexmc.metro.util.SchedulerUtil
import org.cubexmc.metro.util.VersionUtil
import org.incendo.cloud.annotations.AnnotationParser
import java.io.File

class Metro : CubexPlugin() {

    lateinit var lineManager: LineManager
        private set

    lateinit var stopManager: StopManager
        private set

    lateinit var languageManager: LanguageManager
        private set

    var globalScoreboardLibrary: ScoreboardLibrary? = null
        private set

    lateinit var scoreboardManager: ScoreboardManager
        private set

    lateinit var selectionManager: SelectionManager
        private set

    lateinit var guiManager: GuiManager
        private set

    lateinit var chatInputManager: ChatInputManager
        private set

    lateinit var configFacade: ConfigFacade
        private set

    lateinit var playerInteractListener: PlayerInteractListener
        private set

    var vehicleListener: VehicleListener? = null
        private set

    var playerMoveListener: PlayerMoveListener? = null
        private set

    var guiListener: GuiListener? = null
        private set

    var trainDisplayController: TrainDisplayController? = null
        private set

    var commandManager: org.incendo.cloud.CommandManager<CommandSender>? = null
        private set

    var annotationParser: AnnotationParser<CommandSender>? = null
        private set

    var portalManager: PortalManager? = null
        private set

    lateinit var routeRecorder: RouteRecorder
        private set

    var railProtectionManager: RailProtectionManager? = null
        private set

    var vaultIntegration: VaultIntegration? = null
        private set

    lateinit var lineSelectionService: LineSelectionService
        private set

    lateinit var ticketService: TicketService
        private set

    lateinit var priceService: PriceService
        private set

    lateinit var lineStatusService: LineStatusService
        private set

    lateinit var saveCoordinator: SaveCoordinator
        private set

    lateinit var bedrockCompatibility: BedrockCompatibility
        private set

    private var mapIntegrationLifecycle: MapIntegrationLifecycle? = null
    private var scheduledTaskLifecycle: ScheduledTaskLifecycle? = null

    /**
     * 配置门面是否已就绪。生命周期组件可能在 enable 完成前被回调。
     */
    fun isConfigFacadeReady(): Boolean = ::configFacade.isInitialized

    @Throws(Exception::class)
    override fun enablePlugin() {
        bindShutdownActions()

        // 创建配置目录
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        // PDC 键要在任何管理器/GUI 构建物品之前准备好
        MetroConstants.initialize(this)

        // 初始化并迁移配置文件
        MetroMigrations.ensureConfigResources(this)
        MetroMigrations.migrateConfig(this)
        reloadConfig()
        configFacade = ConfigFacade(this)
        configFacade.reload()

        // 初始化默认配置文件
        createDefaultConfigFiles()
        DataFileUpdater.migrateAll(this)

        // 初始化 Bedrock 兼容 facade
        bedrockCompatibility = BedrockCompatibility(this) { configFacade.isBedrockArrivalSyncEnabled() }

        // 初始化并迁移语言文件
        MetroMigrations.ensureLanguageResources(this)
        MetroMigrations.migrateBundledLanguages(this)
        languageManager = LanguageManager(this)
        saveCoordinator = SaveCoordinator(logger) { command -> SchedulerUtil.asyncRun(this, command, 0L) }

        // 初始化管理器
        lineManager = LineManager(this)
        val protectionManager = RailProtectionManager(this)
        railProtectionManager = protectionManager
        protectionManager.rebuildAll()
        stopManager = StopManager(this)
        lineSelectionService = LineSelectionService(lineManager, stopManager)
        selectionManager = SelectionManager()
        guiManager = GuiManager(this)
        chatInputManager = ChatInputManager(this)
        routeRecorder = RouteRecorder(this)
        Bukkit.getPluginManager().registerEvents(chatInputManager, this)

        // 初始化传送门管理器
        val portals = PortalManager(this)
        portalManager = portals

        // 初始化经济集成
        val economy = VaultIntegration(this)
        vaultIntegration = economy
        Bukkit.getPluginManager().registerEvents(economy, this)
        if (economy.isEnabled) {
            logger.info("Vault economy integration enabled.")
        } else {
            logger.info("Vault economy not found or disabled.")
        }
        ticketService = TicketService({ vaultIntegration }, { configFacade.isEconomyEnabled() })

        priceService = PriceService()
        lineStatusService = LineStatusService(this, lineManager)

        // 初始化计分板库
        globalScoreboardLibrary =
            try {
                ScoreboardLibrary.loadScoreboardLibrary(this)
            } catch (_: NoPacketAdapterAvailableException) {
                logger.warning("当前服务端暂无可用 ScoreboardLibrary 数据包适配器，计分板显示将临时不可见。")
                NoopScoreboardLibrary()
            }

        // 初始化计分板管理器
        scoreboardManager = ScoreboardManager(this)

        val commandRegistration =
            CommandRegistration(this, lineManager, stopManager, portals).register() ?: return
        commandManager = commandRegistration.commandManager()
        annotationParser = commandRegistration.annotationParser()

        val listenerRegistration = ListenerRegistration(this, protectionManager).register()
        playerInteractListener = listenerRegistration.playerInteractListener()
        vehicleListener = listenerRegistration.vehicleListener()
        playerMoveListener = listenerRegistration.playerMoveListener()
        guiListener = listenerRegistration.guiListener()
        trainDisplayController = listenerRegistration.trainDisplayController()

        // 注册bstats
        Metrics(this, BSTATS_PLUGIN_ID)

        val scheduledTasks = ScheduledTaskLifecycle(this, lineManager, stopManager, portals)
        scheduledTaskLifecycle = scheduledTasks
        scheduledTasks.start()

        val mapIntegrations = MapIntegrationLifecycle(this)
        mapIntegrationLifecycle = mapIntegrations
        mapIntegrations.enable()

        MetroAPI.initialize(this)

        logger.info("Metro(Modern) has been enabled!")
    }

    override fun disablePlugin() {
        // 关闭动作全部通过 bindShutdownActions() 注册
    }

    private fun bindShutdownActions() {
        bind {
            if (::languageManager.isInitialized) {
                Bukkit.getConsoleSender().sendMessage(languageManager.getMessage("plugin.disabled"))
            } else {
                logger.info("Metro plugin disabled.")
            }
        }
        bind { flushPersistentData() }
        bind {
            if (::routeRecorder.isInitialized) {
                routeRecorder.cancelAll()
            }
        }
        bind { scheduledTaskLifecycle?.shutdown() }
        bind { removeFallbackMinecarts() }
        bind { shutdownActiveTrains() }
        bind { clearPlayerDisplays() }
        bind { globalScoreboardLibrary?.close() }
        bind {
            if (::scoreboardManager.isInitialized) {
                scoreboardManager.shutdown()
            }
        }
        bind {
            if (::playerInteractListener.isInitialized) {
                playerInteractListener.shutdown()
            }
        }
        bind { playerMoveListener?.shutdown() }
        bind { mapIntegrationLifecycle?.disable() }
    }

    private fun clearPlayerDisplays() {
        if (!::scoreboardManager.isInitialized) {
            return
        }
        for (player in Bukkit.getOnlinePlayers()) {
            scoreboardManager.clearPlayerDisplay(player)
        }
    }

    private fun shutdownActiveTrains() {
        val activeTrainCount = TrainMovementTask.shutdownActiveTasks(this, VersionUtil.isFolia())
        if (activeTrainCount > 0) {
            logger.info("Cleaned up $activeTrainCount active Metro train(s).")
        }
    }

    private fun removeFallbackMinecarts() {
        // Paper/Bukkit 兜底清理旧残留；Folia 不做全世界实体扫描，避免跨 region 访问风险。
        if (VersionUtil.isFolia()) {
            logger.info(
                "Skipped fallback world minecart scan during Folia shutdown; " +
                    "active trains were cleaned through the train registry.",
            )
            return
        }
        val minecartKey = MetroConstants.getMinecartKey() ?: return
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (entity is Minecart &&
                    entity.persistentDataContainer.has(minecartKey, PersistentDataType.BYTE)
                ) {
                    MinecartEjector.eject(entity)
                    entity.remove()
                }
            }
        }
    }

    /**
     * 重新创建默认配置文件（如果不存在）
     * 此方法用于reload命令，确保所有配置文件都能够被重新生成
     */
    fun ensureDefaultConfigs() {
        // 确保主配置文件存在
        if (!File(dataFolder, "config.yml").exists()) {
            saveDefaultConfig()
            logger.info("重新生成默认主配置文件")
        }

        // 确保其他配置文件存在
        createDefaultConfigFiles()
    }

    /**
     * 创建默认配置文件
     */
    private fun createDefaultConfigFiles() {
        // 确保这些文件存在于插件数据文件夹中
        saveDefaultConfigFiles("lines.yml")
        saveDefaultConfigFiles("stops.yml")
    }

    /**
     * 保存默认配置文件
     *
     * @param fileName 文件名
     */
    private fun saveDefaultConfigFiles(fileName: String) {
        if (!File(dataFolder, fileName).exists()) {
            saveResource(fileName, false)
        }
    }

    /**
     * 是否启用调试日志。
     */
    fun isDebugEnabled(): Boolean = configFacade.isDebugEnabled()

    /**
     * 是否启用某个调试分类。
     *
     * @param category 调试分类键，例如 train_state_transitions
     */
    fun isDebugCategoryEnabled(category: String): Boolean = configFacade.isDebugCategoryEnabled(category)

    /**
     * 输出分类调试日志。
     */
    fun debug(category: String, message: String) {
        if (!isDebugCategoryEnabled(category)) {
            return
        }
        logger.info("[DEBUG][$category] $message")
    }

    fun refreshMapIntegrations() {
        mapIntegrationLifecycle?.refresh()
    }

    fun requestMapIntegrationRefresh() {
        mapIntegrationLifecycle?.requestRefresh()
    }

    fun refreshVaultIntegration(): Boolean {
        val economy = vaultIntegration ?: return false
        val enabled = economy.refresh()
        logger.info(
            if (enabled) "Vault economy integration refreshed." else "Vault economy provider is currently unavailable.",
        )
        return enabled
    }

    fun flushPersistentData() {
        if (::lineManager.isInitialized) {
            lineManager.forceSaveSync()
        }
        if (::stopManager.isInitialized) {
            stopManager.forceSaveSync()
        }
        portalManager?.forceSaveSync()
        if (::saveCoordinator.isInitialized) {
            saveCoordinator.flushAll()
        }
    }

    private companion object {
        const val BSTATS_PLUGIN_ID = 25825
    }
}
