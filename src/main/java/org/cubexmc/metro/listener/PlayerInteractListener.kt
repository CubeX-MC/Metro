package org.cubexmc.metro.listener

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.LineStatus
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.TicketService
import org.cubexmc.metro.train.TrainMovementTask
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.OwnershipUtil
import org.cubexmc.metro.util.SchedulerUtil
import org.cubexmc.metro.util.SoundUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * 处理玩家交互事件
 */
class PlayerInteractListener(
    private val plugin: Metro,
    scheduleCleanupTask: Boolean,
) : Listener {

    constructor(plugin: Metro) : this(plugin, true)

    private val selectionManager = plugin.selectionManager

    /** 用于防止短时间内多次点击触发多次调用 */
    private val lastInteractTime: MutableMap<UUID, Long> = ConcurrentHashMap()

    /** 用于跟踪站点的矿车生成状态，键为站点ID，值为时间戳 */
    private val pendingMinecarts: MutableMap<String, Long> = ConcurrentHashMap()

    private val pendingMinecartCleanupTaskId: Any? =
        if (!scheduleCleanupTask) {
            null
        } else {
            // 定期清理过期的矿车等待记录，每分钟一次
            SchedulerUtil.globalRun(
                plugin,
                {
                    val currentTime = System.currentTimeMillis()
                    val timeout = plugin.configFacade.getMinecartPendingTimeout()
                    pendingMinecarts.entries.removeIf { currentTime - it.value > timeout }
                },
                CLEANUP_INTERVAL_TICKS,
                CLEANUP_INTERVAL_TICKS,
            )
        }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action
        val clickedBlock = event.clickedBlock

        // 处理选区工具（可在config中配置，默认金锄头）
        // 只处理主手事件，避免主手和副手各触发一次导致消息重复
        // 允许拥有 metro.admin 或 metro.stop.create 权限的玩家使用选区工具
        if (clickedBlock != null &&
            OwnershipUtil.canCreateStop(player) &&
            player.inventory.itemInMainHand.type == plugin.configFacade.getSelectionTool() &&
            event.hand == EquipmentSlot.HAND
        ) {
            if (handleSelectionTool(player, action, clickedBlock.location)) {
                event.isCancelled = true
                return
            }
        }

        // 如果不是右键点击方块，不处理
        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
            return
        }

        // 检查点击的是否是铁轨
        if (!clickedBlock.type.name.contains("RAIL")) {
            return
        }

        // 已经坐在地铁矿车里的玩家不能再次触发上车流程，否则会重复生成矿车
        if (isRidingMetroMinecart(player)) {
            event.isCancelled = true
            if (event.hand == EquipmentSlot.HAND) {
                player.sendMessage(plugin.languageManager.getMessage("interact.already_riding"))
            }
            return
        }

        // 防止短时间内多次点击
        val playerId = player.uniqueId
        val currentTime = System.currentTimeMillis()
        val cooldown = plugin.configFacade.getInteractCooldown()
        val lastTime = lastInteractTime[playerId]
        if (lastTime != null && currentTime - lastTime < cooldown) {
            // 如果冷却时间内再次点击，取消事件并返回
            event.isCancelled = true
            return
        }

        // 发现玩家可能会点到多个相互重叠的 stop (比如复线和双向站), 此时选择最优方向的 Stop
        val stop = plugin.stopManager.getBestStopContainingLocation(clickedBlock.location, player.location.yaw)

        // 如果找到了停靠区，检查是否有待处理的矿车
        if (stop != null && hasActivePendingMinecart(player, stop)) {
            event.isCancelled = true
            return
        }

        // 检查是否是停靠点并处理
        if (!checkAndHandleStopPoint(player, clickedBlock.location)) {
            return
        }

        // 成功处理了停靠点，更新点击时间并取消事件
        lastInteractTime[playerId] = currentTime
        event.isCancelled = true
        plugin.debug(
            "interaction_flow",
            "Handled stop interaction for player=" + player.name + ", location=" + clickedBlock.location,
        )

        // 设置一个任务，在冷却时间后清除记录
        SchedulerUtil.entityRun(plugin, player, { lastInteractTime.remove(playerId) }, cooldown / TICKS_TO_MILLIS, -1L)
    }

    /**
     * @return true 表示本次点击已作为选区操作处理
     */
    private fun handleSelectionTool(player: Player, action: Action, location: Location): Boolean {
        val messageKey =
            when (action) {
                Action.LEFT_CLICK_BLOCK -> {
                    selectionManager.setCorner1(player, location)
                    "selection.corner1_set"
                }

                Action.RIGHT_CLICK_BLOCK -> {
                    selectionManager.setCorner2(player, location)
                    "selection.corner2_set"
                }

                else -> return false
            }
        player.sendMessage(
            plugin.languageManager.getMessage(
                messageKey,
                LanguageManager.put(
                    LanguageManager.args(),
                    "location",
                    location.blockX.toString() + ", " + location.blockY + ", " + location.blockZ,
                ),
            ),
        )
        return true
    }

    /**
     * 检查并处理停靠点交互
     *
     * @return 是否成功处理了停靠点
     */
    private fun checkAndHandleStopPoint(player: Player, location: Location): Boolean {
        if (!player.hasPermission("metro.use")) {
            player.sendMessage(plugin.languageManager.getMessage("interact.no_permission"))
            return false
        }

        // 检查点击位置是否在任何停靠区内
        val stop = plugin.stopManager.getBestStopContainingLocation(location, player.location.yaw) ?: return false

        // 确保停靠区已配置停靠点
        if (stop.stopPointLocation == null) {
            player.sendMessage(plugin.languageManager.getMessage("interact.stop_no_point"))
            return false
        }

        // 找到停靠区并且是铁轨，处理上车逻辑
        handleStopPoint(player, stop)
        return true
    }

    /**
     * 处理停靠点交互
     */
    private fun handleStopPoint(player: Player, stop: Stop) {
        val boardableLines = plugin.lineSelectionService.getBoardableLines(stop)
        if (boardableLines.isEmpty()) {
            sendNoBoardableLineMessage(player, stop)
            return
        }

        if (boardableLines.size > 1 && plugin.lineSelectionService.requiresChoice(player, stop)) {
            plugin.guiManager.openLineBoardingChoice(player, stop, 0)
            return
        }

        val line = plugin.lineSelectionService.resolveDefaultLine(player, stop, stop.stopPointLocation)
        if (line == null) {
            sendNoBoardableLineMessage(player, stop)
            return
        }

        beginBoarding(player, stop, line)
    }

    /**
     * 从 GUI 选择线路后继续乘车流程。
     */
    fun boardSelectedLine(player: Player, stopId: String?, lineId: String?) {
        if (!player.hasPermission("metro.use")) {
            player.sendMessage(plugin.languageManager.getMessage("interact.no_permission"))
            return
        }

        val stop = if (stopId == null) null else plugin.stopManager.getStop(stopId)
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (stop == null || line == null) {
            player.sendMessage(plugin.languageManager.getMessage("interact.stop_no_line"))
            return
        }
        if (stop.stopPointLocation == null) {
            player.sendMessage(plugin.languageManager.getMessage("interact.stop_no_point"))
            return
        }
        if (plugin.lineSelectionService.getBoardableLines(stop).none { it.id == line.id }) {
            sendNoBoardableLineMessage(player, stop)
            return
        }
        if (isRidingMetroMinecart(player)) {
            player.sendMessage(plugin.languageManager.getMessage("interact.already_riding"))
            return
        }
        if (hasActivePendingMinecart(player, stop)) {
            return
        }

        beginBoarding(player, stop, line)
    }

    /**
     * 玩家是否已经是某辆地铁矿车的乘客
     */
    private fun isRidingMetroMinecart(player: Player): Boolean {
        val minecart = player.vehicle as? Minecart ?: return false
        val minecartKey = MetroConstants.getMinecartKey() ?: return false
        return minecart.persistentDataContainer.has(minecartKey, PersistentDataType.BYTE)
    }

    private fun beginBoarding(player: Player, stop: Stop, line: Line) {
        if (line.getLineStatus() == LineStatus.SUSPENDED) {
            sendSuspensionNotice(player, line)
            return
        }

        plugin.lineSelectionService.rememberChoice(player, stop.id, line.id)

        val ticketCheck = plugin.ticketService.checkCanBoard(player, line)
        if (!ticketCheck.canBoard()) {
            sendTicketCheckFailure(player, ticketCheck)
            return
        }
        val ticketTransaction = plugin.ticketService.createTransaction(player, line)

        // 记录该站点有矿车正在处理中
        pendingMinecarts[stop.id] = System.currentTimeMillis()
        plugin.debug(
            "interaction_flow",
            "Preparing minecart for player=" + player.name + ", line=" + line.id + ", stop=" + stop.id,
        )

        // 显示线路信息
        showLineInfo(player, stop, line)

        // 播放车辆到站音乐 - 在右键点击后立即播放
        playStationArrivalSound(player)

        // 生成矿车
        spawnMinecart(player, stop, line, ticketTransaction)
    }

    private fun sendSuspensionNotice(player: Player, line: Line) {
        val suspensionMsg = line.suspensionMessage
        if (!suspensionMsg.isNullOrEmpty()) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.line_suspended_msg",
                    LanguageManager.put(LanguageManager.args(), "message", suspensionMsg),
                ),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.line_suspended",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                ),
            )
        }

        val altRouteIds = line.alternativeRouteIds
        if (altRouteIds.isEmpty()) {
            return
        }
        player.sendMessage(plugin.languageManager.getMessage("line.suggest_alternatives"))
        for (altId in altRouteIds) {
            val altLine = plugin.lineManager.getLine(altId) ?: continue
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.alternative_format",
                    LanguageManager.put(LanguageManager.args(), "alt_line_name", altLine.name),
                ),
            )
        }
    }

    private fun sendNoBoardableLineMessage(player: Player, stop: Stop) {
        val servingLines = plugin.lineManager.getLinesForStop(stop.id)
        val onlyTerminalLines =
            servingLines.isNotEmpty() && servingLines.all { it.getNextStopId(stop.id) == null }
        if (onlyTerminalLines) {
            player.sendMessage(plugin.languageManager.getMessage("interact.terminal_stop"))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("interact.stop_no_line"))
        }
        plugin.debug("interaction_flow", "No line found for stop=" + stop.id)
    }

    private fun hasActivePendingMinecart(player: Player, stop: Stop): Boolean {
        val currentTime = System.currentTimeMillis()
        val pendingTime = pendingMinecarts[stop.id] ?: return false
        val timeout = plugin.configFacade.getMinecartPendingTimeout()
        if (currentTime - pendingTime < timeout) {
            player.sendMessage(plugin.languageManager.getMessage("interact.train_pending"))
            return true
        }
        pendingMinecarts.remove(stop.id)
        return false
    }

    /**
     * 显示线路信息
     */
    private fun showLineInfo(player: Player, stop: Stop, line: Line) {
        // 获取下一停靠区信息；如果当前站已经是终点站，不需要显示信息
        val nextStopId = line.getNextStopId(stop.id) ?: return

        val nextStop = plugin.stopManager.getStop(nextStopId)
        val nextStopName = nextStop?.name ?: nextStopId

        // 显示ActionBar信息
        val args = LanguageManager.args()
        LanguageManager.put(args, "line_name", line.name)
        LanguageManager.put(args, "next_stop_name", nextStopName)
        val message = plugin.languageManager.getMessage("interact.actionbar_line_info", args)
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(message))
    }

    /**
     * 播放车辆到站音乐
     */
    private fun playStationArrivalSound(player: Player) {
        if (plugin.configFacade.isStationArrivalSoundEnabled() &&
            plugin.configFacade.getStationArrivalNotes().isNotEmpty()
        ) {
            SoundUtil.playNoteSequence(
                plugin,
                player,
                plugin.configFacade.getStationArrivalNotes(),
                plugin.configFacade.getStationArrivalInitialDelay(),
            )
        }
    }

    /**
     * 生成矿车
     */
    private fun spawnMinecart(
        player: Player,
        stop: Stop,
        line: Line,
        ticketTransaction: TicketService.TicketTransaction,
    ) {
        val location = stop.stopPointLocation ?: return
        val stopId = stop.id

        // 创建一个新位置，保留原来的坐标但使用停靠区的发车朝向
        // 反转Yaw值，使矿车外观朝向与移动方向一致
        val spawnLocation = location.clone()
        spawnLocation.yaw = stop.launchYaw

        // 获取矿车生成延迟
        val spawnDelay = plugin.configFacade.getCartSpawnDelay()

        // 显示等待信息
        player.sendMessage(plugin.languageManager.getMessage("interact.train_coming"))

        // 延迟生成矿车，使用实体调度器以确保在正确的线程执行
        SchedulerUtil.regionRun(
            plugin,
            location,
            { spawnMinecartNow(player, stop, line, ticketTransaction, location, spawnLocation, stopId) },
            spawnDelay,
            -1L,
        )
    }

    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    private fun spawnMinecartNow(
        player: Player,
        stop: Stop,
        line: Line,
        ticketTransaction: TicketService.TicketTransaction,
        location: Location,
        spawnLocation: Location,
        stopId: String,
    ) {
        try {
            val world = location.world ?: throw IllegalStateException("Stop point world is not loaded")
            val minecartKey =
                MetroConstants.getMinecartKey() ?: throw IllegalStateException("Metro minecart key is unavailable")

            // 生成矿车实体
            val minecart = world.spawnEntity(spawnLocation, EntityType.MINECART) as Minecart

            // 设置矿车属性
            minecart.persistentDataContainer.set(minecartKey, PersistentDataType.BYTE, 1.toByte())
            minecart.customName = MetroConstants.METRO_MINECART_NAME
            minecart.isCustomNameVisible = false
            minecart.isPersistent = false
            minecart.setGravity(false) // 禁用重力
            minecart.isSlowWhenEmpty = false // 不因空车而减速

            // 设置矿车的最大速度，只在创建时设置一次
            val configuredMaxSpeed = line.getMaxSpeed() ?: -1.0
            minecart.maxSpeed =
                if (configuredMaxSpeed == -1.0) plugin.configFacade.getCartSpeed() else configuredMaxSpeed

            // 将玩家放入矿车
            if (!minecart.addPassenger(player)) {
                // 如果上车失败，移除矿车并清除等待状态
                minecart.remove()
                pendingMinecarts.remove(stopId)
                player.sendMessage(plugin.languageManager.getMessage("interact.train_error"))
                return
            }

            val chargeStatus = plugin.ticketService.charge(ticketTransaction)
            if (!isSuccessfulCharge(chargeStatus)) {
                minecart.remove()
                pendingMinecarts.remove(stopId)
                sendTicketChargeFailure(player, chargeStatus, ticketTransaction)
                return
            }
            if (chargeStatus == TicketService.TicketChargeStatus.CHARGED) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "economy.paid_boarding",
                        LanguageManager.put(
                            LanguageManager.args(),
                            "price",
                            plugin.ticketService.format(ticketTransaction.price),
                        ),
                    ),
                )
            }

            // 显示待乘车信息
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "interact.train_spawned",
                    LanguageManager.put(
                        LanguageManager.args(),
                        "departure_seconds",
                        (plugin.configFacade.getCartDepartureDelay() / TICKS_PER_SECOND).toString(),
                    ),
                ),
            )

            // 创建列车任务，使用 TrainMovementTask 处理等待发车和发车逻辑
            // 这将触发 handleArrivalAtStation 方法，显示等待信息、播放等待音乐，然后延迟发车
            TrainMovementTask.startTrainTask(plugin, minecart, player, line.id, stop.id)
            plugin.debug(
                "interaction_flow",
                "Minecart spawned and task started for player=" + player.name +
                    ", line=" + line.id + ", stop=" + stop.id,
            )

            // 清除该站点的矿车等待状态
            pendingMinecarts.remove(stopId)
        } catch (e: Exception) {
            // 出现异常，清除该站点的矿车等待状态
            pendingMinecarts.remove(stopId)
            player.sendMessage(plugin.languageManager.getMessage("interact.train_error"))
            plugin.logger.log(Level.SEVERE, "Failed to spawn metro minecart for stop $stopId", e)
        }
    }

    private fun isSuccessfulCharge(status: TicketService.TicketChargeStatus): Boolean =
        status == TicketService.TicketChargeStatus.CHARGED ||
            status == TicketService.TicketChargeStatus.FREE ||
            status == TicketService.TicketChargeStatus.ECONOMY_DISABLED

    private fun sendTicketCheckFailure(player: Player, ticketCheck: TicketService.TicketCheck) {
        if (ticketCheck.status == TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "economy.insufficient_funds",
                    LanguageManager.put(LanguageManager.args(), "price", ticketCheck.formattedPrice),
                ),
            )
            return
        }
        if (ticketCheck.status == TicketService.TicketCheckStatus.VAULT_UNAVAILABLE) {
            player.sendMessage(plugin.languageManager.getMessage("economy.vault_unavailable"))
            return
        }
        player.sendMessage(plugin.languageManager.getMessage("economy.transaction_failed"))
    }

    private fun sendTicketChargeFailure(
        player: Player,
        status: TicketService.TicketChargeStatus,
        transaction: TicketService.TicketTransaction,
    ) {
        if (status == TicketService.TicketChargeStatus.INSUFFICIENT_FUNDS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "economy.insufficient_funds",
                    LanguageManager.put(
                        LanguageManager.args(),
                        "price",
                        plugin.ticketService.format(transaction.price),
                    ),
                ),
            )
            return
        }
        if (status == TicketService.TicketChargeStatus.VAULT_UNAVAILABLE) {
            player.sendMessage(plugin.languageManager.getMessage("economy.vault_unavailable"))
            return
        }
        player.sendMessage(plugin.languageManager.getMessage("economy.transaction_failed"))
    }

    /**
     * 插件关闭时清理运行状态
     */
    fun shutdown() {
        SchedulerUtil.cancelTask(pendingMinecartCleanupTaskId)
        lastInteractTime.clear()
        pendingMinecarts.clear()
    }

    private companion object {
        const val CLEANUP_INTERVAL_TICKS = 1200L
        const val TICKS_TO_MILLIS = 50L
        const val TICKS_PER_SECOND = 20
    }
}
