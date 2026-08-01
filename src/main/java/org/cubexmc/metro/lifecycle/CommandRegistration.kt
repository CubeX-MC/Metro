package org.cubexmc.metro.lifecycle

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.cubexmc.metro.Metro
import org.cubexmc.metro.command.newcmd.LineCommand
import org.cubexmc.metro.command.newcmd.MetroMainCommand
import org.cubexmc.metro.command.newcmd.PortalCommand
import org.cubexmc.metro.command.newcmd.StopCommand
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.service.StopCommandService
import org.cubexmc.metro.util.VersionUtil
import org.incendo.cloud.CommandManager
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.suggestion.Suggestion
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

/**
 * Registers Cloud commands and suggestion providers.
 */
class CommandRegistration(
    private val plugin: Metro,
    private val lineManager: LineManager,
    private val stopManager: StopManager,
    private val portalManager: PortalManager,
) {

    fun register(): Result? {
        if (VersionUtil.isVersionAtLeast(FALLBACK_MAJOR, FALLBACK_MINOR, FALLBACK_PATCH)) {
            try {
                BukkitFallbackCommandRegistration(plugin, lineManager, stopManager, portalManager).register()
                plugin.logger.info("已为 Minecraft 26.1+ 启用 Bukkit 命令兼容层。")
                return Result(null, null)
            } catch (e: Exception) {
                plugin.logger.severe("Failed to initialize Bukkit command fallback:")
                e.printStackTrace()
                Bukkit.getPluginManager().disablePlugin(plugin)
                return null
            }
        }

        return try {
            val commandManager = createCommandManager()
            val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)

            registerSuggestionProviders(commandManager)
            annotationParser.parse(
                MetroMainCommand(plugin, lineManager, stopManager),
                LineCommand(plugin, lineManager, stopManager),
                StopCommand(plugin, stopManager, lineManager),
                PortalCommand(plugin),
            )

            plugin.logger.info("Cloud Command Framework initialized successfully.")
            Result(commandManager, annotationParser)
        } catch (e: Exception) {
            registerBukkitFallbackAfterCloudFailure(e)
        } catch (e: LinkageError) {
            registerBukkitFallbackAfterCloudFailure(e)
        }
    }

    private fun registerBukkitFallbackAfterCloudFailure(cause: Throwable): Result? {
        plugin.logger.warning(
            "Cloud Command Framework 初始化失败，将尝试 Bukkit fallback。原因: " +
                cause.javaClass.simpleName + ": " + cause.message,
        )
        return try {
            BukkitFallbackCommandRegistration(plugin, lineManager, stopManager, portalManager).register()
            Result(null, null)
        } catch (fallbackError: Exception) {
            plugin.logger.severe("Failed to initialize Cloud Command Framework and Bukkit fallback:")
            fallbackError.printStackTrace()
            Bukkit.getPluginManager().disablePlugin(plugin)
            null
        }
    }

    private fun createCommandManager(): CommandManager<CommandSender> {
        try {
            Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager")
            val commandSourceStackClass = Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack")

            val mapper: SenderMapper<Any, CommandSender> =
                SenderMapper.create(
                    { source ->
                        try {
                            source.javaClass.getMethod("getSender").invoke(source) as CommandSender
                        } catch (e: Exception) {
                            throw RuntimeException("Failed to map CommandSourceStack to CommandSender", e)
                        }
                    },
                    { sender ->
                        Proxy.newProxyInstance(
                            commandSourceStackClass.classLoader,
                            arrayOf<Class<*>>(commandSourceStackClass),
                        ) { proxy, method, args ->
                            when (method.name) {
                                "getSender" -> sender
                                "getLocation" -> (sender as? Entity)?.location
                                "getExecutor" -> if (sender is Entity) sender else null
                                "toString" -> "CommandSourceStackProxy[" + sender.name + "]"
                                "equals" -> args != null && args.size == 1 && proxy === args[0]
                                "hashCode" -> System.identityHashCode(proxy)
                                else -> null
                            }
                        }
                    },
                )

            return try {
                val manager = PaperCommandManagerBootstrap.buildOnEnable(plugin, mapper)
                plugin.logger.info("已加载新版 PaperCommandManager (1.20.5+)")
                manager
            } catch (e: RuntimeException) {
                warnModernManagerFailure(e)
                createLegacyCommandManager(false)
            } catch (e: LinkageError) {
                warnModernManagerFailure(e)
                createLegacyCommandManager(false)
            }
        } catch (_: ClassNotFoundException) {
            return createLegacyCommandManager(true)
        }
    }

    private fun warnModernManagerFailure(cause: Throwable) {
        plugin.logger.warning(
            "新版 PaperCommandManager 初始化失败，将降级为兼容命令注册。原因: " +
                cause.javaClass.simpleName + ": " + cause.message,
        )
    }

    private fun createLegacyCommandManager(enableNativeBrigadier: Boolean): LegacyPaperCommandManager<CommandSender> {
        val legacyManager =
            LegacyPaperCommandManager(
                plugin,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity<CommandSender>(),
            )

        if (enableNativeBrigadier && legacyManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                legacyManager.registerBrigadier()
            } catch (e: RuntimeException) {
                warnBrigadierFailure(e)
            } catch (e: LinkageError) {
                warnBrigadierFailure(e)
            }
        }
        if (legacyManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            try {
                legacyManager.registerAsynchronousCompletions()
            } catch (e: RuntimeException) {
                warnAsynchronousCompletionFailure(e)
            } catch (e: LinkageError) {
                warnAsynchronousCompletionFailure(e)
            }
        }

        plugin.logger.info(
            if (enableNativeBrigadier) {
                "已加载兼容版 LegacyPaperCommandManager (1.20.4 及以下)"
            } else {
                "已加载降级版 LegacyPaperCommandManager (Bukkit command fallback)"
            },
        )
        return legacyManager
    }

    private fun warnBrigadierFailure(cause: Throwable) {
        plugin.logger.warning(
            "Legacy Brigadier 注册失败，将继续使用 Bukkit 命令。原因: " +
                cause.javaClass.simpleName + ": " + cause.message,
        )
    }

    private fun warnAsynchronousCompletionFailure(cause: Throwable) {
        plugin.logger.warning(
            "异步命令补全注册失败，将继续使用同步补全。原因: " +
                cause.javaClass.simpleName + ": " + cause.message,
        )
    }

    private fun registerSuggestionProviders(commandManager: CommandManager<CommandSender>) {
        val registry = commandManager.parserRegistry()
        registry.registerSuggestionProvider("lineIds") { _, _ -> toSuggestionsFuture(lineIdSuggestions()) }
        registry.registerSuggestionProvider("stopIds") { _, _ -> toSuggestionsFuture(stopIdSuggestions()) }
        registry.registerSuggestionProvider("portalIds") { _, _ -> toSuggestionsFuture(portalIdSuggestions()) }
        registry.registerSuggestionProvider("playerNames") { _, _ -> toSuggestionsFuture(playerNameSuggestions()) }
        registry.registerSuggestionProvider("players") { _, _ -> toSuggestionsFuture(playerNameSuggestions()) }
        registry.registerSuggestionProvider("lineColors") { _, _ -> toSuggestionsFuture(LINE_COLORS) }
        registry.registerSuggestionProvider("protectModes") { _, _ -> toSuggestionsFuture(PROTECT_MODES) }
        registry.registerSuggestionProvider("titleTypes") { _, _ ->
            toSuggestionsFuture(StopCommandService.TITLE_TYPES.sorted())
        }
        registry.registerSuggestionProvider("titleKeys") { _, _ ->
            toSuggestionsFuture(StopCommandService.TITLE_KEYS.sorted())
        }
        registry.registerSuggestionProvider("linkActions") { _, _ -> toSuggestionsFuture(listOf("allow", "deny")) }
        registry.registerSuggestionProvider("pageNumbers") { _, _ -> toSuggestionsFuture(PAGE_NUMBERS) }
        registry.registerSuggestionProvider("stopIndexes") { _, _ -> toSuggestionsFuture(STOP_INDEXES) }
        registry.registerSuggestionProvider("yawValues") { _, _ -> toSuggestionsFuture(YAW_VALUES) }
        registry.registerSuggestionProvider("speedValues") { _, _ -> toSuggestionsFuture(SPEED_VALUES) }
        registry.registerSuggestionProvider("priceValues") { _, _ -> toSuggestionsFuture(PRICE_VALUES) }
        registry.registerSuggestionProvider("priceModes") { _, _ -> toSuggestionsFuture(PRICE_MODES) }
        registry.registerSuggestionProvider("lineStatusValues") { _, _ -> toSuggestionsFuture(LINE_STATUS_VALUES) }
    }

    private fun lineIdSuggestions(): List<String> = lineManager.getAllLines().map { it.id }

    private fun stopIdSuggestions(): List<String> = ArrayList(stopManager.getAllStopIds())

    private fun portalIdSuggestions(): List<String> = portalManager.getAllPortals().map { it.id }.sorted()

    private fun playerNameSuggestions(): List<String> = Bukkit.getOnlinePlayers().map { it.name }.sorted()

    private fun toSuggestionsFuture(values: Iterable<String>): CompletableFuture<List<Suggestion>> =
        CompletableFuture.completedFuture(values.map { Suggestion.suggestion(it) })

    data class Result(
        private val commandManager: CommandManager<CommandSender>?,
        private val annotationParser: AnnotationParser<CommandSender>?,
    ) {
        fun commandManager(): CommandManager<CommandSender>? = commandManager

        fun annotationParser(): AnnotationParser<CommandSender>? = annotationParser
    }

    private companion object {
        const val FALLBACK_MAJOR = 26
        const val FALLBACK_MINOR = 1
        const val FALLBACK_PATCH = 0

        val LINE_COLORS =
            listOf(
                "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
                "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f",
                "&#55AAFF",
            )
        val PROTECT_MODES =
            listOf("status", "on", "off", "enable", "disable", "enabled", "disabled", "true", "false")
        val PAGE_NUMBERS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val STOP_INDEXES = listOf("0", "1", "2", "3", "4", "5", "10")
        val YAW_VALUES = listOf("0", "90", "180", "-90")
        val SPEED_VALUES = listOf("0.4", "0.8", "1.0", "1.2")
        val PRICE_VALUES = listOf("0", "1", "2", "5", "10")
        val PRICE_MODES = listOf("flat", "distance", "interval")
        val LINE_STATUS_VALUES = listOf("normal", "suspended", "maintenance")
    }
}
