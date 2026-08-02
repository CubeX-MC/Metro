package org.cubexmc.metro.command.newcmd

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.RouteRecorder
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.service.CommandDisplayService
import org.cubexmc.metro.service.LineCommandService
import org.cubexmc.metro.util.OwnershipUtil
import org.incendo.cloud.annotation.specifier.Greedy
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import java.util.Locale

class LineCommand(
    private val plugin: Metro,
    private val lineManager: LineManager,
    stopManager: StopManager,
) {

    private val guard = CommandGuard(plugin, lineManager, stopManager)
    private val lineService = LineCommandService(lineManager)
    private val view = LineCommandView(plugin, stopManager, guard, CommandDisplayService())

    @Command("m|metro line|l")
    @CommandDescription("Show Line Help Menu")
    fun help(sender: CommandSender) {
        view.showHelp(sender, 1)
    }

    @Command("m|metro line|l help [page]")
    @CommandDescription("Show Line Help Menu Page")
    fun helpPage(sender: CommandSender, @Argument(value = "page", suggestions = "pageNumbers") page: Int?) {
        view.showHelp(sender, page)
    }

    @Command("m|metro line|l list [page]")
    @CommandDescription("List all metro lines")
    fun list(sender: CommandSender, @Argument(value = "page", suggestions = "pageNumbers") page: Int?) {
        view.listLines(sender, lineService.listLines(), page)
    }

    @Command("m|metro line|l create <id> <name>")
    @CommandDescription("Create a new metro line")
    fun create(player: Player, @Argument("id") id: String, @Greedy @Argument("name") name: String) {
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.languageManager.getMessage("line.permission_create"))
            return
        }

        val messageKey =
            when (lineService.createLine(id, name, player.uniqueId)) {
                LineCommandService.WriteStatus.SUCCESS -> "line.create_success"
                LineCommandService.WriteStatus.INVALID_ID -> "line.id_invalid"
                LineCommandService.WriteStatus.EXISTS -> "line.create_exists"
                else -> "line.create_fail"
            }
        player.sendMessage(
            plugin.languageManager.getMessage(
                messageKey,
                LanguageManager.put(LanguageManager.args(), "line_id", id),
            ),
        )
    }

    @Command("m|metro line|l delete <lineId> [confirm]")
    @CommandDescription("Delete a metro line")
    fun delete(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument("confirm") confirm: String?,
    ) {
        guard.requireManageableLine(player, id) ?: return
        if (!guard.requireConfirmation(player, confirm, "/m line delete $id confirm")) {
            return
        }

        if (lineService.deleteLine(id) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", id),
                ),
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.delete_fail"))
        }
    }

    @Command("m|metro line|l rename <lineId> <name>")
    @CommandDescription("Rename a metro line")
    fun rename(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Greedy @Argument("name") name: String,
    ) {
        guard.requireManageableLine(player, id) ?: return

        if (lineService.renameLine(id, name) == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", id)
            LanguageManager.put(args, "new_name", name)
            player.sendMessage(plugin.languageManager.getMessage("line.rename_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.rename_fail"))
        }
    }

    @Command("m|metro line|l setcolor <lineId> <color>")
    @CommandDescription("Set the color of a metro line")
    fun setColor(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        // Greedy: Brigadier's单词参数不接受 & 和 #，会把 &#RRGGBB 判为语法错误
        @Greedy @Argument(value = "color", suggestions = "lineColors") color: String,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return

        val status = lineService.setColor(id, color)
        if (status == LineCommandService.WriteStatus.INVALID_COLOR) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.setcolor_invalid",
                    LanguageManager.put(LanguageManager.args(), "color", color),
                ),
            )
            return
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", line.id)
            LanguageManager.put(args, "line_name", line.name)
            LanguageManager.put(args, "color", color)
            player.sendMessage(plugin.languageManager.getMessage("line.setcolor_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.setcolor_fail"))
        }
    }

    @Command("m|metro line|l setterminus <lineId> <terminus>")
    @CommandDescription("Set terminus name for a line")
    fun setTerminus(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Greedy @Argument("terminus") terminus: String,
    ) {
        guard.requireManageableLine(player, id) ?: return

        if (lineService.setTerminusName(id, terminus) == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", id)
            LanguageManager.put(args, "terminus_name", terminus)
            player.sendMessage(plugin.languageManager.getMessage("line.setterminus_success", args))
        }
    }

    @Command("m|metro line|l setmaxspeed <lineId> <speed>")
    @CommandDescription("Set max speed for a line")
    fun setMaxSpeed(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "speed", suggestions = "speedValues") speed: Double,
    ) {
        guard.requireManageableLine(player, id) ?: return
        val status = lineService.setMaxSpeed(id, speed)
        if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
            player.sendMessage(plugin.languageManager.getMessage("line.setmaxspeed_invalid"))
            return
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", id)
            LanguageManager.put(args, "max_speed", speed.toString())
            player.sendMessage(plugin.languageManager.getMessage("line.setmaxspeed_success", args))
        }
    }

    @Command("m|metro line|l addstop <lineId> <stopId> [index]")
    @CommandDescription("Add a stop to a line")
    fun addStop(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
        @Argument(value = "stopId", suggestions = "stopIds") stopId: String,
        @Argument(value = "index", suggestions = "stopIndexes") index: Int?,
    ) {
        val line = guard.requireManageableLine(player, lineId) ?: return
        val stop = guard.requireStop(player, stopId) ?: return

        if (!guard.canModifyLineStops(player, line, stop)) {
            return
        }

        when (lineService.addStopToLine(line, stop, index).status()) {
            LineCommandService.WriteStatus.SUCCESS -> {
                val args = LanguageManager.args()
                LanguageManager.put(args, "stop_id", stopId)
                LanguageManager.put(args, "line_id", line.id)
                player.sendMessage(plugin.languageManager.getMessage("line.addstop_success", args))
            }

            LineCommandService.WriteStatus.STOP_NO_WORLD ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.addstop_stop_no_world",
                        LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
                    ),
                )

            LineCommandService.WriteStatus.CIRCULAR_INVALID_INDEX ->
                player.sendMessage(plugin.languageManager.getMessage("line.addstop_circular_invalid_index"))

            else -> player.sendMessage(plugin.languageManager.getMessage("line.addstop_fail"))
        }
    }

    @Command("m|metro line|l delstop <lineId> <stopId>")
    @CommandDescription("Remove a stop from a line")
    fun delStop(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
        @Argument(value = "stopId", suggestions = "stopIds") stopId: String,
    ) {
        val line = guard.requireManageableLine(player, lineId) ?: return

        if (lineService.removeStopFromLine(line, stopId) == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "stop_id", stopId)
            LanguageManager.put(args, "line_id", line.id)
            player.sendMessage(plugin.languageManager.getMessage("line.delstop_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.delstop_fail"))
        }
    }

    @Command("m|metro line|l addportal <lineId> <portalId>")
    @CommandDescription("Allow a line to use a portal")
    fun addPortal(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
        @Argument(value = "portalId", suggestions = "portalIds") portalId: String,
    ) {
        val line = guard.requireManageableLine(player, lineId) ?: return

        val portal = plugin.portalManager?.getPortal(portalId)
        if (portal == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", portalId),
                ),
            )
            return
        }

        val messageKey =
            when (lineService.addPortalToLine(line, portal)) {
                LineCommandService.WriteStatus.EXISTS -> "line.addportal_exists"
                LineCommandService.WriteStatus.SUCCESS -> "line.addportal_success"
                else -> "line.addportal_fail"
            }
        player.sendMessage(msg(messageKey, "portal_id", portalId, "line_id", line.id))
    }

    @Command("m|metro line|l delportal <lineId> <portalId>")
    @CommandDescription("Remove a portal from a line")
    fun delPortal(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
        @Argument(value = "portalId", suggestions = "portalIds") portalId: String,
    ) {
        val line = guard.requireManageableLine(player, lineId) ?: return

        val messageKey =
            when (lineService.removePortalFromLine(line, portalId)) {
                LineCommandService.WriteStatus.SUCCESS -> "line.delportal_success"
                LineCommandService.WriteStatus.NOT_FOUND -> "line.delportal_missing"
                else -> "line.delportal_fail"
            }
        player.sendMessage(msg(messageKey, "portal_id", portalId, "line_id", line.id))
    }

    @Command("m|metro line|l portals <lineId> [page]")
    @CommandDescription("List portals enabled for a line")
    fun portals(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "page", suggestions = "pageNumbers") page: Int?,
    ) {
        val line = guard.requireLine(player, id) ?: return
        view.sendPortals(player, line, page)
    }

    @Command("m|metro line|l stops <lineId> [page]")
    @CommandDescription("List all stops in line")
    fun stops(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "page", suggestions = "pageNumbers") page: Int?,
    ) {
        val line = guard.requireLine(player, id) ?: return
        view.sendStops(player, line, page)
    }

    @Command("m|metro line|l info <lineId>")
    @CommandDescription("Show line details")
    fun info(player: Player, @Argument(value = "lineId", suggestions = "lineIds") id: String) {
        val line = guard.requireLine(player, id) ?: return
        view.sendInfo(player, line)
    }

    @Command("m|metro line|l protect <lineId> <mode>")
    @CommandDescription("Enable, disable, or inspect rail protection for a line")
    fun protectRoute(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "mode", suggestions = "protectModes") mode: String,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return

        val normalizedMode = mode.lowercase(Locale.ROOT)
        if ("status" == normalizedMode) {
            view.sendProtectionStatus(player, line)
            return
        }

        val enabled = parseToggle(normalizedMode)
        if (enabled == null) {
            player.sendMessage(msg("line.usage_protect"))
            return
        }
        if (lineService.setRailProtected(id, enabled) != LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(msg("line.protect_update_fail", "line_id", id))
            return
        }

        val updatedLine = lineManager.getLine(id)
        val stateKey = if (enabled) "line.protect_state_enabled" else "line.protect_state_disabled"
        player.sendMessage(msg("line.protect_updated", "line_id", id, "state", msg(stateKey)))
        if (updatedLine != null) {
            view.sendProtectionStatus(player, updatedLine)
        }
    }

    @Command("m|metro line|l recordroute <lineId>")
    @CommandDescription("Start or finish recording route points for a line")
    fun recordRoute(player: Player, @Argument(value = "lineId", suggestions = "lineIds") id: String) {
        guard.requireManageableLine(player, id) ?: return

        val recorder = plugin.routeRecorder
        if (recorder.isRecording(id)) {
            finishRecording(player, id, recorder.stopAndSave(id))
            return
        }

        if (recorder.start(id, player.uniqueId)) {
            player.sendMessage(msg("line.record_started", "line_id", id))
            player.sendMessage(msg("line.record_hint"))
        } else {
            player.sendMessage(msg("line.record_already", "line_id", id))
        }
    }

    private fun finishRecording(player: Player, id: String, result: RouteRecorder.FinishResult) {
        when (result.status()) {
            RouteRecorder.FinishResult.Status.SAVED ->
                player.sendMessage(msg("line.record_saved", "line_id", id, "point_count", result.pointCount()))

            RouteRecorder.FinishResult.Status.TOO_FEW_POINTS -> sendRecordTooFew(player, result)

            RouteRecorder.FinishResult.Status.FAILED -> player.sendMessage(msg("line.record_failed", "line_id", id))

            RouteRecorder.FinishResult.Status.NOT_RECORDING ->
                player.sendMessage(msg("line.record_not_recording", "line_id", id))
        }
    }

    @Command("m|metro line|l clearroute <lineId> [confirm]")
    @CommandDescription("Clear recorded route points for a line")
    fun clearRoute(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument("confirm") confirm: String?,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return
        if (!guard.requireConfirmation(player, confirm, "/m line clearroute $id confirm")) {
            return
        }

        plugin.routeRecorder.clearActive(id)
        val result = lineService.clearRoutePoints(line)
        if (result.status() == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                msg("line.clearroute_success", "line_id", id, "point_count", result.previousPointCount()),
            )
        } else {
            player.sendMessage(msg("line.clearroute_fail", "line_id", id))
        }
    }

    @Command("m|metro line|l routeinfo <lineId>")
    @CommandDescription("Show recorded route point status for a line")
    fun routeInfo(player: Player, @Argument(value = "lineId", suggestions = "lineIds") id: String) {
        val line = guard.requireLine(player, id) ?: return
        view.sendRouteInfo(player, line)
    }

    private fun parseToggle(mode: String): Boolean? =
        when (mode) {
            "on", "true", "enable", "enabled" -> true
            "off", "false", "disable", "disabled" -> false
            else -> null
        }

    private fun msg(key: String, vararg replacements: Any?): String = view.msg(key, *replacements)

    private fun sendRecordTooFew(player: Player, result: RouteRecorder.FinishResult) {
        player.sendMessage(msg("line.record_too_few", "point_count", result.pointCount()))
        player.sendMessage(msg("line.record_too_few_hint"))
    }

    @Command("m|metro line|l trust <lineId> <playerName>")
    @CommandDescription("Grant line admin")
    fun trust(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        when (lineService.grantAdmin(line, target.uniqueId)) {
            LineCommandService.WriteStatus.EXISTS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.trust_exists",
                        LanguageManager.put(LanguageManager.args(), "player", playerName),
                    ),
                )

            LineCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage("line.trust_success", linePlayerArgs(id, playerName)),
                )

            else -> Unit
        }
    }

    @Command("m|metro line|l untrust <lineId> <playerName>")
    @CommandDescription("Revoke line admin")
    fun untrust(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (lineService.revokeAdmin(line, target.uniqueId) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage("line.untrust_success", linePlayerArgs(id, playerName)),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.untrust_fail",
                    LanguageManager.put(LanguageManager.args(), "player", playerName),
                ),
            )
        }
    }

    @Command("m|metro line|l owner <lineId> <playerName>")
    @CommandDescription("Transfer line ownership")
    fun owner(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val line = guard.requireLine(player, id) ?: return
        if (!guard.requireLineOwner(player, line)) {
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (lineService.transferOwner(line, target.uniqueId) == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", id)
            LanguageManager.put(args, "owner", playerName)
            player.sendMessage(plugin.languageManager.getMessage("line.owner_success", args))
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", id),
                ),
            )
        }
    }

    @Command("m|metro line|l clonereverse <sourceId> <newId>")
    @CommandDescription("Clone a line and its stops in reverse order")
    fun cloneReverse(
        player: Player,
        @Argument(value = "sourceId", suggestions = "lineIds") sourceId: String,
        @Argument("newId") newId: String,
    ) {
        cloneReverseWithSuffix(player, sourceId, newId, "_rev")
    }

    @Command("m|metro line|l clonereverse <sourceId> <newId> <stopIdSuffix>")
    @CommandDescription("Clone a line and its stops in reverse order with custom suffix")
    fun cloneReverseWithSuffix(
        player: Player,
        @Argument(value = "sourceId", suggestions = "lineIds") sourceId: String,
        @Argument("newId") newId: String,
        @Argument("stopIdSuffix") stopIdSuffix: String,
    ) {
        guard.requireManageableLine(player, sourceId) ?: return
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.languageManager.getMessage("line.permission_create"))
            return
        }

        val status = lineService.cloneReverseLine(sourceId, newId, stopIdSuffix, player.uniqueId)
        if (status == LineCommandService.WriteStatus.INVALID_ID) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.id_invalid",
                    LanguageManager.put(LanguageManager.args(), "line_id", newId),
                ),
            )
            return
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.clone_success",
                    LanguageManager.put(LanguageManager.args(), "new_line_id", newId),
                ),
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.clone_fail"))
        }
    }

    @Command("m|metro line|l setprice <lineId> <price>")
    @CommandDescription("Set the ticket price for a metro line (legacy flat)")
    fun setPrice(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "price", suggestions = "priceValues") price: Double,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return

        val status = lineService.setTicketPrice(id, price)
        if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
            player.sendMessage(plugin.languageManager.getMessage("line.setprice_invalid"))
            return
        }

        if (status == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_name", line.name)
            LanguageManager.put(args, "price", price.toString())
            player.sendMessage(plugin.languageManager.getMessage("line.setprice_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.setprice_fail"))
        }
    }

    @Command("m|metro line|l setprice <lineId> flat <base>")
    @CommandDescription("Set flat pricing for a line")
    fun setPriceFlat(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "base", suggestions = "priceValues") basePrice: Double,
    ) {
        setPriceRule(player, id, "flat", basePrice, null, null)
    }

    @Command("m|metro line|l setprice <lineId> distance <base> <perBlock> [max]")
    @CommandDescription("Set distance-based pricing for a line")
    fun setPriceDistance(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "base", suggestions = "priceValues") basePrice: Double,
        @Argument(value = "perBlock", suggestions = "priceValues") perBlock: Double,
        @Argument(value = "max", suggestions = "priceValues") maxPrice: Double?,
    ) {
        setPriceRule(player, id, "distance", basePrice, perBlock, maxPrice)
    }

    @Command("m|metro line|l setprice <lineId> interval <base> <perStop> [max]")
    @CommandDescription("Set interval-based pricing for a line")
    fun setPriceInterval(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "base", suggestions = "priceValues") basePrice: Double,
        @Argument(value = "perStop", suggestions = "priceValues") perStop: Double,
        @Argument(value = "max", suggestions = "priceValues") maxPrice: Double?,
    ) {
        setPriceRule(player, id, "interval", basePrice, perStop, maxPrice)
    }

    private fun setPriceRule(
        player: Player,
        id: String,
        mode: String,
        basePrice: Double,
        perUnit: Double?,
        maxPrice: Double?,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return

        when (lineService.setPriceRule(id, mode, basePrice, perUnit, maxPrice)) {
            LineCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.setprice_success",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                    ),
                )

            LineCommandService.WriteStatus.INVALID_VALUE ->
                player.sendMessage(plugin.languageManager.getMessage("line.setprice_invalid"))

            else -> player.sendMessage(plugin.languageManager.getMessage("line.setprice_fail"))
        }
    }

    @Command("m|metro line|l setprice reset <lineId>")
    @CommandDescription("Reset pricing rule to use legacy flat ticket price")
    fun resetPrice(player: Player, @Argument(value = "lineId", suggestions = "lineIds") id: String) {
        val line = guard.requireManageableLine(player, id) ?: return

        if (lineService.resetPriceRule(id)) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.setprice_reset",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                ),
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.setprice_fail"))
        }
    }

    @Command("m|metro line|l priceinfo <lineId>")
    @CommandDescription("View pricing details and active discounts for a line")
    fun priceInfo(player: Player, @Argument(value = "lineId", suggestions = "lineIds") id: String) {
        val line = guard.requireLine(player, id) ?: return
        view.sendPriceInfo(player, line)
    }

    @Command("m|metro line|l setstatus <lineId> <status>")
    @CommandDescription("Set line operational status (normal/suspended/maintenance)")
    fun setStatus(
        player: Player,
        @Argument(value = "lineId", suggestions = "lineIds") id: String,
        @Argument(value = "status", suggestions = "lineStatusValues") status: String,
    ) {
        val line = guard.requireManageableLine(player, id) ?: return

        when (lineService.setLineStatus(id, status)) {
            LineCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.setstatus_success",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                    ),
                )

            LineCommandService.WriteStatus.INVALID_VALUE ->
                player.sendMessage(plugin.languageManager.getMessage("line.setstatus_invalid"))

            else -> player.sendMessage(plugin.languageManager.getMessage("line.setstatus_fail"))
        }
    }

    private fun playerNotFoundMessage(playerName: String): String =
        plugin.languageManager.getMessage(
            "command.player_not_found",
            LanguageManager.put(LanguageManager.args(), "player", playerName),
        )

    private fun linePlayerArgs(lineId: String, playerName: String): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        LanguageManager.put(args, "line_id", lineId)
        LanguageManager.put(args, "player", playerName)
        return args
    }
}
