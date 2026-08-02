package org.cubexmc.metro.listener

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.ColorUtil
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.SchedulerUtil
import org.cubexmc.metro.util.TextUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 监听玩家移动事件，用于检测玩家进入停靠区
 */
class PlayerMoveListener(private val plugin: Metro) : Listener {

    /** 记录玩家当前所在的停靠区ID */
    private val playerInStopMap: MutableMap<UUID, String> = ConcurrentHashMap()

    /** 记录持续显示信息的任务ID */
    private val continuousInfoTasks: MutableMap<UUID, Any> = ConcurrentHashMap()

    /** 记录专门的ActionBar显示任务ID */
    private val actionBarTasks: MutableMap<UUID, Any> = ConcurrentHashMap()

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 只有玩家从一个方块移动到另一个方块时才检查，优化性能
        if (isSameBlock(event.from, event.to)) {
            return
        }

        val player = event.player
        if (!player.hasPermission("metro.use")) {
            return
        }

        // 检查玩家是否在矿车内，如果在矿车内则不显示站台信息
        if (isInMetroMinecart(player)) {
            val playerId = player.uniqueId
            if (playerInStopMap.remove(playerId) != null) {
                cancelContinuousInfoTask(playerId)
                cancelActionBarTask(playerId)
            }
            return
        }

        // 玩家不在矿车内，正常处理站台信息
        val stop = plugin.stopManager.getStopContainingLocation(player.location)

        val playerId = player.uniqueId
        val currentStopId = playerInStopMap[playerId]

        // 检查玩家是否进入了新的停靠区
        if (stop != null) {
            val stopId = stop.id
            if (stopId != currentStopId) {
                // 玩家进入了新的停靠区
                playerInStopMap[playerId] = stopId

                // 取消原来的持续显示任务
                cancelContinuousInfoTask(playerId)

                // 启动新的持续显示任务
                if (plugin.configFacade.isStopContinuousTitleEnabled()) {
                    startContinuousInfoTask(player, stop)
                }
            }
        } else if (currentStopId != null) {
            // 玩家离开了停靠区
            playerInStopMap.remove(playerId)
            cancelContinuousInfoTask(playerId)
            cancelActionBarTask(playerId) // 取消ActionBar任务

            // 立即清除title和actionbar显示
            player.resetTitle()
            // 发送空的ActionBar来清除显示
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(""))

            // 清除首次运行标记
            player.removeMetadata("metro_first_run_$currentStopId", plugin)
        }
    }

    /**
     * 检查两个位置是否在同一方块内
     */
    private fun isSameBlock(from: Location?, to: Location?): Boolean {
        if (from == null || to == null) {
            return true
        }

        return from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ
    }

    /**
     * 启动持续显示停靠区信息的任务
     */
    private fun startContinuousInfoTask(player: Player, stop: Stop) {
        val playerId = player.uniqueId

        // 取消已有的ActionBar任务
        cancelActionBarTask(playerId)

        val lineManager = plugin.lineManager
        val boardableLines = plugin.lineSelectionService.getBoardableLines(stop)

        val line: Line
        if (boardableLines.isEmpty()) {
            val terminalLines = plugin.lineSelectionService.getTerminalLines(stop)
            if (terminalLines.isEmpty()) {
                return
            }
            line = terminalLines[0]
        } else if (boardableLines.size > 1) {
            startMultiLineInfoTask(player, stop, boardableLines)
            return
        } else {
            line = boardableLines[0]
        }

        val config = plugin.configFacade
        val timings = TitleTimings.from(config)

        // 准备信息内容（在任务外提前准备，以便ActionBar任务可以使用）
        // 获取前一站和下一站信息
        val stopManager = plugin.stopManager
        val lastStopId = line.getPreviousStopId(stop.id)
        val nextStopId = line.getNextStopId(stop.id)
        val lastStop = if (lastStopId == null) null else stopManager.getStop(lastStopId)
        val nextStop = if (nextStopId == null) null else stopManager.getStop(nextStopId)

        // 获取终点站信息
        val stopIds = line.orderedStopIds
        val terminalStop = if (stopIds.isEmpty()) null else stopManager.getStop(stopIds[stopIds.size - 1])

        // 确定站点类型并获取对应配置
        val isStartStop = lastStop == null // 没有上一站，是始发站
        val isEndStop = nextStop == null // 没有下一站，是终点站

        var title = config.getStopContinuousTitle(isStartStop, isEndStop)
        var subtitle = config.getStopContinuousSubtitle(isStartStop, isEndStop)
        var actionbar = config.getStopContinuousActionbar(isStartStop, isEndStop)

        val customTitle = stop.getCustomTitle("stop_continuous")
        if (customTitle != null) {
            customTitle["title"]?.let { title = it }
            customTitle["subtitle"]?.let { subtitle = it }
            customTitle["actionbar"]?.let { actionbar = it }
        }

        val translatedTitle =
            ColorUtil.colorizeOrEmpty(
                TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop, lineManager),
            )
        val translatedSubtitle =
            ColorUtil.colorizeOrEmpty(
                TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop, lineManager),
            )
        val actionbarComponent: Array<BaseComponent> =
            TextComponent.fromLegacyText(
                ColorUtil.colorizeOrEmpty(
                    TextUtil.replacePlaceholders(actionbar, line, stop, lastStop, nextStop, terminalStop, lineManager),
                ),
            )

        showStopInfo(player, stop, timings, translatedTitle, translatedSubtitle, actionbarComponent)
    }

    private fun startMultiLineInfoTask(player: Player, stop: Stop, boardableLines: List<Line>) {
        val config = plugin.configFacade
        val timings = TitleTimings.from(config)

        val translatedTitle =
            ColorUtil.colorizeOrEmpty(
                plugin.languageManager.getMessage("interact.multi_line_title", mapOf("stop_name" to stop.name)),
            )
        val translatedSubtitle =
            ColorUtil.colorizeOrEmpty(
                plugin.languageManager.getMessage(
                    "interact.multi_line_subtitle",
                    mapOf("count" to boardableLines.size.toString()),
                ),
            )
        val actionbarComponent: Array<BaseComponent> =
            TextComponent.fromLegacyText(
                ColorUtil.colorizeOrEmpty(
                    plugin.languageManager.getMessage(
                        "interact.multi_line_actionbar",
                        mapOf("routes" to buildBoardableRouteSummary(stop, boardableLines)),
                    ),
                ),
            )

        showStopInfo(player, stop, timings, translatedTitle, translatedSubtitle, actionbarComponent)
    }

    private fun showStopInfo(
        player: Player,
        stop: Stop,
        timings: TitleTimings,
        translatedTitle: String,
        translatedSubtitle: String,
        actionbarComponent: Array<BaseComponent>,
    ) {
        if (timings.alwaysShow) {
            startAlwaysShowTasks(player, stop, timings, translatedTitle, translatedSubtitle, actionbarComponent)
            return
        }
        startFirstVisitTasks(player, stop, timings, translatedTitle, translatedSubtitle, actionbarComponent)
    }

    private fun startAlwaysShowTasks(
        player: Player,
        stop: Stop,
        timings: TitleTimings,
        translatedTitle: String,
        translatedSubtitle: String,
        actionbarComponent: Array<BaseComponent>,
    ) {
        val playerId = player.uniqueId

        val actionBarTaskId =
            SchedulerUtil.entityRun(
                plugin,
                player,
                {
                    // 检查任务是否仍然存在于Map中，如果不存在说明已被外部取消
                    // 不在这里取消任务，让外部的 PlayerMoveEvent 来处理
                    if (actionBarTasks.containsKey(playerId) && canDisplayAt(player, stop)) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *actionbarComponent)
                    }
                },
                0L,
                ACTION_BAR_INTERVAL_TICKS,
            )
        if (actionBarTaskId != null) {
            actionBarTasks[playerId] = actionBarTaskId
        }

        val titleTaskId =
            SchedulerUtil.entityRun(
                plugin,
                player,
                {
                    if (continuousInfoTasks.containsKey(playerId) && canDisplayAt(player, stop)) {
                        player.sendTitle(
                            translatedTitle,
                            translatedSubtitle,
                            timings.continuousFadeIn,
                            timings.continuousStay,
                            timings.continuousFadeOut,
                        )
                    }
                },
                0L,
                timings.interval.toLong(),
            )
        if (titleTaskId != null) {
            continuousInfoTasks[playerId] = titleTaskId
        }
    }

    private fun startFirstVisitTasks(
        player: Player,
        stop: Stop,
        timings: TitleTimings,
        translatedTitle: String,
        translatedSubtitle: String,
        actionbarComponent: Array<BaseComponent>,
    ) {
        val playerId = player.uniqueId
        val metaKey = "metro_first_run_" + stop.id
        if (player.getMetadata(metaKey).isNotEmpty()) {
            return
        }
        player.setMetadata(metaKey, FixedMetadataValue(plugin, true))

        if (isInMetroMinecart(player)) {
            return
        }

        player.sendTitle(
            translatedTitle,
            translatedSubtitle,
            timings.singleFadeIn,
            timings.singleStay,
            timings.singleFadeOut,
        )

        val totalDisplayTime = timings.singleStay + timings.singleFadeOut
        val actionBarTaskId =
            SchedulerUtil.entityRun(
                plugin,
                player,
                object : Runnable {
                    private var count = 0
                    private val maxCount = totalDisplayTime / TICKS_PER_SECOND + 1

                    override fun run() {
                        if (!player.isOnline || count >= maxCount || !stop.isInStop(player.location)) {
                            cancelActionBarTask(playerId)
                            return
                        }
                        if (!isInMetroMinecart(player)) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *actionbarComponent)
                        }
                        count++
                    }
                },
                0L,
                ACTION_BAR_INTERVAL_TICKS,
            )
        if (actionBarTaskId != null) {
            actionBarTasks[playerId] = actionBarTaskId
        }
    }

    private fun canDisplayAt(player: Player, stop: Stop): Boolean =
        player.isOnline && stop.isInStop(player.location) && !isInMetroMinecart(player)

    private fun buildBoardableRouteSummary(stop: Stop, boardableLines: List<Line>): String {
        val routes = ArrayList<String>()
        for (line in boardableLines) {
            val nextStopId = line.getNextStopId(stop.id)
            val nextStop = if (nextStopId == null) null else plugin.stopManager.getStop(nextStopId)
            val nextStopName = nextStop?.name ?: nextStopId.orEmpty()
            val lineName = line.color + line.name
            routes.add(
                plugin.languageManager.getMessage(
                    "interact.multi_line_route",
                    mapOf("line_name" to lineName, "next_stop_name" to nextStopName),
                ),
            )
        }
        return routes.joinToString("&7 | ")
    }

    private fun isInMetroMinecart(player: Player): Boolean {
        val minecart = player.vehicle as? Minecart ?: return false
        val minecartKey = MetroConstants.getMinecartKey() ?: return false
        return minecart.persistentDataContainer.has(minecartKey, PersistentDataType.BYTE)
    }

    /**
     * 取消显示持续信息的任务
     */
    private fun cancelContinuousInfoTask(playerId: UUID) {
        continuousInfoTasks.remove(playerId)?.let { SchedulerUtil.cancelTask(it) }
    }

    /**
     * 取消ActionBar显示任务
     */
    private fun cancelActionBarTask(playerId: UUID) {
        actionBarTasks.remove(playerId)?.let { SchedulerUtil.cancelTask(it) }
    }

    /**
     * 插件关闭时主动清理所有显示任务和缓存状态
     */
    fun shutdown() {
        for (taskId in continuousInfoTasks.values) {
            SchedulerUtil.cancelTask(taskId)
        }
        for (taskId in actionBarTasks.values) {
            SchedulerUtil.cancelTask(taskId)
        }
        continuousInfoTasks.clear()
        actionBarTasks.clear()
        playerInStopMap.clear()
    }

    /**
     * 标题显示时序。连续显示时禁用淡入淡出避免闪烁，并保证停留时间覆盖刷新间隔。
     */
    private class TitleTimings(
        val interval: Int,
        val alwaysShow: Boolean,
        val continuousFadeIn: Int,
        val continuousStay: Int,
        val continuousFadeOut: Int,
        val singleFadeIn: Int,
        val singleStay: Int,
        val singleFadeOut: Int,
    ) {
        companion object {
            fun from(config: org.cubexmc.metro.config.ConfigFacade): TitleTimings {
                val interval = config.getStopContinuousInterval()
                val alwaysShow = config.isStopContinuousAlways()
                val configuredFadeIn = config.getStopContinuousFadeIn()
                val configuredStay = config.getStopContinuousStay()
                val configuredFadeOut = config.getStopContinuousFadeOut()

                return TitleTimings(
                    interval = interval,
                    alwaysShow = alwaysShow,
                    continuousFadeIn = if (alwaysShow) 0 else configuredFadeIn,
                    continuousStay = if (alwaysShow) max(configuredStay, interval + 1) else configuredStay,
                    continuousFadeOut = if (alwaysShow) 0 else configuredFadeOut,
                    singleFadeIn = configuredFadeIn,
                    singleStay = configuredStay,
                    singleFadeOut = configuredFadeOut,
                )
            }
        }
    }

    private companion object {
        const val ACTION_BAR_INTERVAL_TICKS = 20L
        const val TICKS_PER_SECOND = 20
    }
}
