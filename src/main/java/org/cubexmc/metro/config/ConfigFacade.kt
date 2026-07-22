package org.cubexmc.metro.config

import java.util.Locale
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.MetroTextRenderer

/**
 * Centralized read access and memory cache for plugin configuration values.
 */
class ConfigFacade(private val plugin: Metro) {
    private var stopContinuousTitleEnabled = false
    private var stopContinuousInterval = 0
    private var stopContinuousAlways = false
    private var stopContinuousTitle = ""
    private var stopContinuousSubtitle = ""
    private var stopContinuousActionbar = ""
    private var stopContinuousStartTitle = ""
    private var stopContinuousStartSubtitle = ""
    private var stopContinuousStartActionbar = ""
    private var stopContinuousEndTitle = ""
    private var stopContinuousEndSubtitle = ""
    private var stopContinuousEndActionbar = ""
    private var stopContinuousFadeIn = 0
    private var stopContinuousStay = 0
    private var stopContinuousFadeOut = 0

    private var arriveStopTitleEnabled = false
    private var arriveStopTitle = ""
    private var arriveStopSubtitle = ""
    private var arriveStopFadeIn = 0
    private var arriveStopStay = 0
    private var arriveStopFadeOut = 0

    private var terminalStopTitleEnabled = false
    private var terminalStopTitle = ""
    private var terminalStopSubtitle = ""
    private var terminalStopFadeIn = 0
    private var terminalStopStay = 0
    private var terminalStopFadeOut = 0

    private var departureTitleEnabled = false
    private var departureTitle = ""
    private var departureSubtitle = ""
    private var departureActionbar = ""
    private var departureFadeIn = 0
    private var departureStay = 0
    private var departureFadeOut = 0

    private var waitingTitleEnabled = false
    private var waitingTitle = ""
    private var waitingSubtitle = ""
    private var waitingActionbar = ""

    private var departureSoundEnabled = false
    private var departureNotes: List<String> = emptyList()
    private var departureInitialDelay = 0

    private var arrivalSoundEnabled = false
    private var arrivalNotes: List<String> = emptyList()
    private var arrivalInitialDelay = 0
    private var enableParticles = false

    private var scoreboardEnabled = false
    private var sbStyleCurrent = ""
    private var sbStylePassed = ""
    private var sbStyleWaitingNext = ""
    private var sbStyleMovingNext = ""
    private var sbStyleTerminal = ""
    private var sbStyleNext = ""
    private var sbStyleOther = ""
    private var sbStyleFolding = ""
    private var lineSymbol = ""

    private var speedControlMode = ""
    private var blockSpeedMap: MutableMap<String, MutableMap<String, Double>> = HashMap()

    private var mapIntegrationEnabled = false
    private var mapProvider = ""
    private var mapMarkerSetLabel = ""
    private var mapDefaultVisible = false
    private var mapLineWidth = 0
    private var mapShowStopMarkers = false
    private var mapShowTransferInfo = false
    private var mapRefreshDelayTicks = 0L

    private var routeRecordingMinSampleDistanceBlocks = 0.0
    private var routeRecordingSimplifyCollinearPoints = false
    private var routeRecordingSimplifyEpsilonBlocks = 0.0

    private var portalsEnabled = false
    private var portalTriggerBlock = ""
    private var portalTeleportDelay = 0
    private var portalEffectParticles = false
    private var portalEffectSound = false

    private var stationArrivalSoundEnabled = false
    private var stationArrivalNotes: List<String> = emptyList()
    private var stationArrivalInitialDelay = 0

    private var waitingSoundEnabled = false
    private var waitingNotes: List<String> = emptyList()
    private var waitingInitialDelay = 0
    private var waitingSoundInterval = 0

    private var cartSpeed = 0.0
    private var cartSpawnDelay = 0L
    private var cartDepartureDelay = 0L
    private var interactCooldown = 0L
    private var minecartPendingTimeout = 0L
    private var debugEnabled = false
    private var safeModeEnabled = false
    private var safeModeEntityPushProtection = false
    private var safeModeDamageProtection = false
    private var safeModeMovementAssist = false
    private var safeModePassengerRailBreakProtection = false
    private var safeModeMinCruiseSpeed = 0.0
    private var safeModeStallRecoveryTicks = 0L
    private var economyEnabled = false

    private var selectionTool: Material = Material.GOLDEN_SHOVEL
    private var selectionToolName = ""

    fun reload() {
        stopContinuousTitleEnabled = getStopContinuousBoolean("enabled", true)
        stopContinuousInterval = getStopContinuousInt("interval", 40)
        stopContinuousAlways = getStopContinuousBoolean("always", true)
        stopContinuousTitle = colorize(getStopContinuousString("title", "&b{stop_name}"))
        stopContinuousSubtitle = colorize(
            getStopContinuousString(
                "subtitle",
                "&d➔ {terminus_name} &8| &e» {next_stop_name} &8| &a⇄ {stop_transfers}",
            ),
        )
        stopContinuousActionbar = colorize(
            getStopContinuousString(
                "actionbar",
                "&fRight-click rail to board &7[&r{line_color_code}{line}&7]",
            ),
        )
        stopContinuousStartTitle = colorize(getStopContinuousString("start_stop.title", stopContinuousTitle))
        stopContinuousStartSubtitle = colorize(
            getStopContinuousString(
                "start_stop.subtitle",
                "&d➔ {terminus_name} &8| &fOrigin &8| &a⇄ {stop_transfers}",
            ),
        )
        stopContinuousStartActionbar = colorize(getStopContinuousString("start_stop.actionbar", stopContinuousActionbar))
        stopContinuousEndTitle = colorize(getStopContinuousString("end_stop.title", stopContinuousTitle))
        stopContinuousEndSubtitle = colorize(
            getStopContinuousString(
                "end_stop.subtitle",
                "&c🛑 Terminal Station &8| &a⇄ {stop_transfers}",
            ),
        )
        stopContinuousEndActionbar = colorize(
            getStopContinuousString(
                "end_stop.actionbar",
                "&cEnd of line. Please allow passengers to exit.",
            ),
        )
        stopContinuousFadeIn = getStopContinuousInt("fade_in", 10)
        stopContinuousStay = getStopContinuousInt("stay", 40)
        stopContinuousFadeOut = getStopContinuousInt("fade_out", 10)

        arriveStopTitleEnabled = plugin.config.getBoolean("titles.arrive_stop.enabled", true)
        arriveStopTitle = colorize(plugin.config.getString("titles.arrive_stop.title", "&a已到站") ?: "&a已到站")
        arriveStopSubtitle = colorize(plugin.config.getString("titles.arrive_stop.subtitle", "&6{stop_name}") ?: "&6{stop_name}")
        arriveStopFadeIn = plugin.config.getInt("titles.arrive_stop.fade_in", 10)
        arriveStopStay = plugin.config.getInt("titles.arrive_stop.stay", 40)
        arriveStopFadeOut = plugin.config.getInt("titles.arrive_stop.fade_out", 10)

        terminalStopTitleEnabled = plugin.config.getBoolean("titles.terminal_stop.enabled", true)
        terminalStopTitle = colorize(plugin.config.getString("titles.terminal_stop.title", "&c终点站") ?: "&c终点站")
        terminalStopSubtitle = colorize(plugin.config.getString("titles.terminal_stop.subtitle", "&6请下车") ?: "&6请下车")
        terminalStopFadeIn = plugin.config.getInt("titles.terminal_stop.fade_in", 10)
        terminalStopStay = plugin.config.getInt("titles.terminal_stop.stay", 60)
        terminalStopFadeOut = plugin.config.getInt("titles.terminal_stop.fade_out", 10)

        departureTitleEnabled = plugin.config.getBoolean("titles.departure.enabled", true)
        departureTitle = colorize(plugin.config.getString("titles.departure.title", "") ?: "")
        departureSubtitle = colorize(plugin.config.getString("titles.departure.subtitle", "") ?: "")
        departureActionbar = colorize(
            plugin.config.getString("titles.departure.actionbar", "列车已启动，请扶好站稳，注意安全")
                ?: "列车已启动，请扶好站稳，注意安全",
        )
        departureFadeIn = plugin.config.getInt("titles.departure.fade_in", 5)
        departureStay = plugin.config.getInt("titles.departure.stay", 40)
        departureFadeOut = plugin.config.getInt("titles.departure.fade_out", 5)

        waitingTitleEnabled = plugin.config.getBoolean("titles.waiting.enabled", true)
        waitingTitle = colorize(plugin.config.getString("titles.waiting.title", "列车即将发车") ?: "列车即将发车")
        waitingSubtitle = colorize(
            plugin.config.getString(
                "titles.waiting.subtitle",
                "当前站点: &a{stop_name} | 下一站: &e{next_stop_name}",
            ) ?: "当前站点: &a{stop_name} | 下一站: &e{next_stop_name}",
        )
        waitingActionbar = colorize(
            plugin.config.getString("titles.waiting.actionbar", "列车将在 &c{countdown} &f秒后发车")
                ?: "列车将在 &c{countdown} &f秒后发车",
        )

        departureSoundEnabled = plugin.config.getBoolean("sounds.departure.enabled", true)
        departureNotes = plugin.config.getStringList("sounds.departure.notes")
        departureInitialDelay = plugin.config.getInt("sounds.departure.initial_delay", 0)

        arrivalSoundEnabled = plugin.config.getBoolean("sounds.arrival.enabled", true)
        arrivalNotes = plugin.config.getStringList("sounds.arrival.notes")
        arrivalInitialDelay = plugin.config.getInt("sounds.arrival.initial_delay", 0)
        enableParticles = plugin.config.getBoolean("particles.enabled", true)

        scoreboardEnabled = plugin.config.getBoolean("scoreboard.enabled", true)
        sbStyleCurrent = colorize(plugin.config.getString("scoreboard.styles.current_stop", "&6☛ &l") ?: "&6☛ &l")
        sbStylePassed = colorize(plugin.config.getString("scoreboard.styles.passed_stop", "&8▼ &7&o") ?: "&8▼ &7&o")
        sbStyleWaitingNext = colorize(plugin.config.getString("scoreboard.styles.waiting_next_stop", "&f▽ &l") ?: "&f▽ &l")
        sbStyleMovingNext = colorize(plugin.config.getString("scoreboard.styles.moving_next_stop", "&6☛ &l") ?: "&6☛ &l")
        sbStyleTerminal = colorize(plugin.config.getString("scoreboard.styles.terminal_stop", " ◇ &f&n") ?: " ◇ &f&n")
        sbStyleNext = colorize(plugin.config.getString("scoreboard.styles.next_stop", "&a○ ") ?: "&a○ ")
        sbStyleOther = colorize(plugin.config.getString("scoreboard.styles.other_stops", "&7· ") ?: "&7· ")
        sbStyleFolding = colorize(plugin.config.getString("scoreboard.styles.folding_symbol", "     &8...     ") ?: "     &8...     ")
        lineSymbol = plugin.config.getString("scoreboard.line_symbol", "❙") ?: "❙"

        speedControlMode = plugin.config.getString("speed_control.mode", "VANILLA_MOMENTUM") ?: "VANILLA_MOMENTUM"
        blockSpeedMap = HashMap()
        if (plugin.config.isConfigurationSection("speed_control.worlds")) {
            val worldsSection = plugin.config.getConfigurationSection("speed_control.worlds")
            if (worldsSection != null) {
                for (worldName in worldsSection.getKeys(false)) {
                    val worldMap: MutableMap<String, Double> = HashMap()
                    val blockSection = worldsSection.getConfigurationSection(worldName)
                    if (blockSection != null) {
                        for (blockName in blockSection.getKeys(false)) {
                            worldMap[blockName.uppercase(Locale.getDefault())] = blockSection.getDouble(blockName)
                        }
                    }
                    blockSpeedMap[worldName] = worldMap
                }
            }
        } else if (plugin.config.isConfigurationSection("speed_control.block_speed_map")) {
            val defaultMap: MutableMap<String, Double> = HashMap()
            val blockSpeedSection = plugin.config.getConfigurationSection("speed_control.block_speed_map")
            if (blockSpeedSection != null) {
                for (key in blockSpeedSection.getKeys(false)) {
                    defaultMap[key.uppercase(Locale.getDefault())] =
                        plugin.config.getDouble("speed_control.block_speed_map.$key")
                }
            }
            blockSpeedMap["default"] = defaultMap
        }

        mapIntegrationEnabled = plugin.config.getBoolean("map_integration.enabled", false)
        mapProvider = (plugin.config.getString("map_integration.provider", "AUTO") ?: "AUTO").uppercase(Locale.getDefault())
        mapMarkerSetLabel = plugin.config.getString("map_integration.marker_set_label", "Metro Network") ?: "Metro Network"
        mapDefaultVisible = plugin.config.getBoolean("map_integration.default_visible", true)
        mapLineWidth = plugin.config.getInt("map_integration.line_width", 3)
        mapShowStopMarkers = plugin.config.getBoolean("map_integration.show_stop_markers", true)
        mapShowTransferInfo = plugin.config.getBoolean("map_integration.show_transfer_info", true)
        mapRefreshDelayTicks = kotlin.math.max(1L, plugin.config.getLong("map_integration.refresh_delay_ticks", 20L))

        routeRecordingMinSampleDistanceBlocks = kotlin.math.max(
            0.1,
            plugin.config.getDouble("route_recording.min_sample_distance_blocks", 1.0),
        )
        routeRecordingSimplifyCollinearPoints = plugin.config.getBoolean("route_recording.simplify_collinear_points", true)
        routeRecordingSimplifyEpsilonBlocks = kotlin.math.max(
            0.0,
            plugin.config.getDouble("route_recording.simplify_epsilon_blocks", 0.15),
        )

        portalsEnabled = plugin.config.getBoolean("portals.enabled", true)
        portalTriggerBlock = (plugin.config.getString("portals.trigger_block", "CRYING_OBSIDIAN") ?: "CRYING_OBSIDIAN")
            .uppercase(Locale.getDefault())
        portalTeleportDelay = plugin.config.getInt("portals.teleport_delay", 5)
        portalEffectParticles = plugin.config.getBoolean("portals.effects.particles", true)
        portalEffectSound = plugin.config.getBoolean("portals.effects.sound", true)

        stationArrivalSoundEnabled = plugin.config.getBoolean("sounds.station_arrival.enabled", true)
        stationArrivalNotes = plugin.config.getStringList("sounds.station_arrival.notes")
        stationArrivalInitialDelay = plugin.config.getInt("sounds.station_arrival.initial_delay", 0)

        waitingSoundEnabled = plugin.config.getBoolean("sounds.waiting.enabled", true)
        waitingNotes = plugin.config.getStringList("sounds.waiting.notes")
        waitingInitialDelay = plugin.config.getInt("sounds.waiting.initial_delay", 0)
        waitingSoundInterval = plugin.config.getInt("sounds.waiting.interval", 20)

        cartSpeed = plugin.config.getDouble("settings.cart_speed", 0.3)
        cartSpawnDelay = plugin.config.getLong("settings.cart_spawn_delay", 60L)
        cartDepartureDelay = plugin.config.getLong("settings.cart_departure_delay", 100L)
        interactCooldown = plugin.config.getLong("settings.interact_cooldown", 2000L)
        minecartPendingTimeout = plugin.config.getLong("settings.minecart_pending_timeout", 60000L)
        debugEnabled = plugin.config.getBoolean("settings.debug.enabled", false)
        safeModeEnabled = plugin.config.getBoolean("settings.safe_mode.enabled", true)
        safeModeEntityPushProtection = plugin.config.getBoolean("settings.safe_mode.entity_push_protection", true)
        safeModeDamageProtection = plugin.config.getBoolean("settings.safe_mode.damage_protection", true)
        safeModeMovementAssist = plugin.config.getBoolean("settings.safe_mode.movement_assist", true)
        safeModePassengerRailBreakProtection =
            plugin.config.getBoolean("settings.safe_mode.passenger_rail_break_protection", true)
        safeModeMinCruiseSpeed = plugin.config.getDouble("settings.safe_mode.min_cruise_speed", 0.08)
        safeModeStallRecoveryTicks = plugin.config.getLong("settings.safe_mode.stall_recovery_ticks", 8L)
        economyEnabled = plugin.config.getBoolean("economy.enabled", true)

        val toolName = plugin.config.getString("settings.selection_tool", "GOLDEN_SHOVEL") ?: "GOLDEN_SHOVEL"
        selectionTool = try {
            Material.valueOf(toolName.uppercase(Locale.getDefault()))
        } catch (exception: IllegalArgumentException) {
            plugin.logger.warning("Invalid selection tool in config: $toolName, using default GOLDEN_SHOVEL")
            Material.GOLDEN_SHOVEL
        }

        val name = selectionTool.name.lowercase(Locale.getDefault()).replace('_', ' ')
        val result = StringBuilder()
        for (word in name.split(" ")) {
            if (word.isNotEmpty()) {
                result.append(word[0].uppercaseChar())
                    .append(word.substring(1))
                    .append(" ")
            }
        }
        selectionToolName = result.toString().trim()
    }

    fun isEnterStopTitleEnabled(): Boolean = isStopContinuousTitleEnabled()

    fun getEnterStopTitle(): String = getStopContinuousTitle(false, false)

    fun getEnterStopSubtitle(): String = getStopContinuousSubtitle(false, false)

    fun getEnterStopFadeIn(): Int = getStopContinuousFadeIn()

    fun getEnterStopStay(): Int = getStopContinuousStay()

    fun getEnterStopFadeOut(): Int = getStopContinuousFadeOut()

    fun isStopContinuousTitleEnabled(): Boolean = stopContinuousTitleEnabled

    fun getStopContinuousInterval(): Int = stopContinuousInterval

    fun isStopContinuousAlways(): Boolean = stopContinuousAlways

    fun getStopContinuousTitle(startStop: Boolean, endStop: Boolean): String =
        when {
            startStop -> stopContinuousStartTitle
            endStop -> stopContinuousEndTitle
            else -> stopContinuousTitle
        }

    fun getStopContinuousSubtitle(startStop: Boolean, endStop: Boolean): String =
        when {
            startStop -> stopContinuousStartSubtitle
            endStop -> stopContinuousEndSubtitle
            else -> stopContinuousSubtitle
        }

    fun getStopContinuousActionbar(startStop: Boolean, endStop: Boolean): String =
        when {
            startStop -> stopContinuousStartActionbar
            endStop -> stopContinuousEndActionbar
            else -> stopContinuousActionbar
        }

    fun getStopContinuousFadeIn(): Int = stopContinuousFadeIn

    fun getStopContinuousStay(): Int = stopContinuousStay

    fun getStopContinuousFadeOut(): Int = stopContinuousFadeOut

    fun isArriveStopTitleEnabled(): Boolean = arriveStopTitleEnabled

    fun getArriveStopTitle(): String = arriveStopTitle

    fun getArriveStopSubtitle(): String = arriveStopSubtitle

    fun getArriveStopFadeIn(): Int = arriveStopFadeIn

    fun getArriveStopStay(): Int = arriveStopStay

    fun getArriveStopFadeOut(): Int = arriveStopFadeOut

    fun isTerminalStopTitleEnabled(): Boolean = terminalStopTitleEnabled

    fun getTerminalStopTitle(): String = terminalStopTitle

    fun getTerminalStopSubtitle(): String = terminalStopSubtitle

    fun getTerminalStopFadeIn(): Int = terminalStopFadeIn

    fun getTerminalStopStay(): Int = terminalStopStay

    fun getTerminalStopFadeOut(): Int = terminalStopFadeOut

    fun isDepartureTitleEnabled(): Boolean = departureTitleEnabled

    fun getDepartureTitle(): String = departureTitle

    fun getDepartureSubtitle(): String = departureSubtitle

    fun getDepartureActionbar(): String = departureActionbar

    fun getDepartureFadeIn(): Int = departureFadeIn

    fun getDepartureStay(): Int = departureStay

    fun getDepartureFadeOut(): Int = departureFadeOut

    fun isWaitingTitleEnabled(): Boolean = waitingTitleEnabled

    fun getWaitingTitle(): String = waitingTitle

    fun getWaitingSubtitle(): String = waitingSubtitle

    fun getWaitingActionbar(): String = waitingActionbar

    fun isDepartureSoundEnabled(): Boolean = departureSoundEnabled

    fun getDepartureNotes(): List<String> = departureNotes

    fun getDepartureInitialDelay(): Int = departureInitialDelay

    fun isArrivalSoundEnabled(): Boolean = arrivalSoundEnabled

    fun getArrivalNotes(): List<String> = arrivalNotes

    fun getArrivalInitialDelay(): Int = arrivalInitialDelay

    fun isEnableParticles(): Boolean = enableParticles

    fun isScoreboardEnabled(): Boolean = scoreboardEnabled

    fun getSbStyleCurrent(): String = sbStyleCurrent

    fun getSbStylePassed(): String = sbStylePassed

    fun getSbStyleWaitingNext(): String = sbStyleWaitingNext

    fun getSbStyleMovingNext(): String = sbStyleMovingNext

    fun getSbStyleTerminal(): String = sbStyleTerminal

    fun getSbStyleNext(): String = sbStyleNext

    fun getSbStyleOther(): String = sbStyleOther

    fun getSbStyleFolding(): String = sbStyleFolding

    fun getLineSymbol(): String = lineSymbol

    fun getSpeedControlMode(): String = speedControlMode

    fun getBlockSpeedMap(): Map<String, Map<String, Double>> = blockSpeedMap

    fun isStationArrivalSoundEnabled(): Boolean = stationArrivalSoundEnabled

    fun getStationArrivalNotes(): List<String> = stationArrivalNotes

    fun getStationArrivalInitialDelay(): Int = stationArrivalInitialDelay

    fun isWaitingSoundEnabled(): Boolean = waitingSoundEnabled

    fun getWaitingNotes(): List<String> = waitingNotes

    fun getWaitingInitialDelay(): Int = waitingInitialDelay

    fun getWaitingSoundInterval(): Int = waitingSoundInterval

    fun getCartSpeed(): Double = cartSpeed

    fun getCartSpawnDelay(): Long = cartSpawnDelay

    fun getCartDepartureDelay(): Long = cartDepartureDelay

    fun getInteractCooldown(): Long = interactCooldown

    fun getMinecartPendingTimeout(): Long = minecartPendingTimeout

    fun isDebugEnabled(): Boolean = debugEnabled

    fun isSafeModeEnabled(): Boolean = safeModeEnabled

    fun isSafeModeEntityPushProtection(): Boolean = safeModeEnabled && safeModeEntityPushProtection

    fun isSafeModeDamageProtection(): Boolean = safeModeEnabled && safeModeDamageProtection

    fun isSafeModeMovementAssist(): Boolean = safeModeEnabled && safeModeMovementAssist

    fun isSafeModePassengerRailBreakProtection(): Boolean =
        safeModeEnabled && safeModePassengerRailBreakProtection

    fun getSafeModeMinCruiseSpeed(): Double = safeModeMinCruiseSpeed

    fun getSafeModeStallRecoveryTicks(): Long = safeModeStallRecoveryTicks

    fun isEconomyEnabled(): Boolean = economyEnabled

    fun isDebugCategoryEnabled(category: String?): Boolean {
        if (!isDebugEnabled() || category == null || category.isEmpty()) {
            return false
        }
        return plugin.config.getBoolean("settings.debug.$category", true)
    }

    fun getSelectionTool(): Material = selectionTool

    fun getSelectionToolName(): String = selectionToolName

    private fun colorize(text: String): String = MetroTextRenderer.renderPreservingPlaceholders(text)

    private fun getStopContinuousBoolean(key: String, defaultValue: Boolean): Boolean {
        val config: FileConfiguration = plugin.config
        val newPath = "$STOP_CONTINUOUS_PATH.$key"
        if (config.contains(newPath)) {
            return config.getBoolean(newPath, defaultValue)
        }
        return config.getBoolean("$LEGACY_ENTER_STOP_PATH.$key", defaultValue)
    }

    private fun getStopContinuousInt(key: String, defaultValue: Int): Int {
        val config: FileConfiguration = plugin.config
        val newPath = "$STOP_CONTINUOUS_PATH.$key"
        if (config.contains(newPath)) {
            return config.getInt(newPath, defaultValue)
        }
        return config.getInt("$LEGACY_ENTER_STOP_PATH.$key", defaultValue)
    }

    private fun getStopContinuousString(key: String, defaultValue: String): String {
        val config: FileConfiguration = plugin.config
        val newPath = "$STOP_CONTINUOUS_PATH.$key"
        if (config.contains(newPath)) {
            return config.getString(newPath, defaultValue) ?: defaultValue
        }
        return config.getString("$LEGACY_ENTER_STOP_PATH.$key", defaultValue) ?: defaultValue
    }

    fun isMapIntegrationEnabled(): Boolean = mapIntegrationEnabled

    fun getMapProvider(): String = mapProvider

    fun getMapMarkerSetLabel(): String = mapMarkerSetLabel

    fun isMapDefaultVisible(): Boolean = mapDefaultVisible

    fun getMapLineWidth(): Int = mapLineWidth

    fun isMapShowStopMarkers(): Boolean = mapShowStopMarkers

    fun isMapShowTransferInfo(): Boolean = mapShowTransferInfo

    fun getMapRefreshDelayTicks(): Long = mapRefreshDelayTicks

    fun getRouteRecordingMinSampleDistanceBlocks(): Double = routeRecordingMinSampleDistanceBlocks

    fun isRouteRecordingSimplifyCollinearPoints(): Boolean = routeRecordingSimplifyCollinearPoints

    fun getRouteRecordingSimplifyEpsilonBlocks(): Double = routeRecordingSimplifyEpsilonBlocks

    fun isPortalsEnabled(): Boolean = portalsEnabled

    fun getPortalTriggerBlock(): String = portalTriggerBlock

    fun getPortalTeleportDelay(): Int = portalTeleportDelay

    fun isPortalEffectParticles(): Boolean = portalEffectParticles

    fun isPortalEffectSound(): Boolean = portalEffectSound

    companion object {
        private const val STOP_CONTINUOUS_PATH = "titles.stop_continuous"
        private const val LEGACY_ENTER_STOP_PATH = "titles.enter_stop"
    }
}
