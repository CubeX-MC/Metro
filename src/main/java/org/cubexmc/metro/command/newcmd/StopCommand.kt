package org.cubexmc.metro.command.newcmd

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.CommandDisplayService
import org.cubexmc.metro.service.StopCommandService
import org.cubexmc.metro.util.OwnershipUtil
import org.incendo.cloud.annotation.specifier.Greedy
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription

class StopCommand(
    private val plugin: Metro,
    private val stopManager: StopManager,
    lineManager: LineManager,
) {

    private val guard = CommandGuard(plugin, lineManager, stopManager)
    private val stopService = StopCommandService(stopManager)
    private val view = StopCommandView(plugin, stopManager, lineManager, guard, CommandDisplayService())

    @Command("m|metro stop|s")
    @CommandDescription("Show Stop Help Menu")
    fun help(sender: CommandSender) {
        view.showHelp(sender, 1)
    }

    @Command("m|metro stop|s help [page]")
    @CommandDescription("Show Stop Help Menu Page")
    fun helpPage(sender: CommandSender, @Argument(value = "page", suggestions = "pageNumbers") page: Int?) {
        view.showHelp(sender, page)
    }

    @Command("m|metro stop|s list [page]")
    @CommandDescription("List all metro stops")
    fun list(player: Player, @Argument(value = "page", suggestions = "pageNumbers") page: Int?) {
        view.listStops(player, stopService.listStops(), page)
    }

    @Command("m|metro stop|s create <stopId> <name>")
    @CommandDescription("Create a new metro stop")
    fun create(player: Player, @Argument("stopId") id: String, @Greedy @Argument("name") name: String) {
        if (!OwnershipUtil.canCreateStop(player)) {
            player.sendMessage(plugin.languageManager.getMessage("stop.permission_create"))
            return
        }

        val selectionManager = plugin.selectionManager
        if (!selectionManager.isSelectionComplete(player)) {
            player.sendMessage(selectionIncompleteMessage())
            return
        }

        val corner1 = selectionManager.getCorner1(player)
        val corner2 = selectionManager.getCorner2(player)

        val result = stopService.createStop(id, name, corner1, corner2, player.uniqueId)
        when (result.status()) {
            StopCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.create_success",
                        LanguageManager.put(LanguageManager.args(), "stop_name", name),
                    ),
                )

            StopCommandService.WriteStatus.INVALID_ID ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.id_invalid",
                        LanguageManager.put(LanguageManager.args(), "stop_id", id),
                    ),
                )

            StopCommandService.WriteStatus.EXISTS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.stop_exists",
                        LanguageManager.put(LanguageManager.args(), "stop_id", id),
                    ),
                )

            else ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.create_fail",
                        LanguageManager.put(LanguageManager.args(), "stop_id", id),
                    ),
                )
        }
    }

    @Command("m|metro stop|s delete <stopId> [confirm]")
    @CommandDescription("Delete a metro stop")
    fun delete(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument("confirm") confirm: String?,
    ) {
        guard.requireManageableStop(player, id) ?: return
        if (!guard.requireConfirmation(player, confirm, "/m stop delete $id confirm")) {
            return
        }

        if (stopService.deleteStop(id) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.delete_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id),
                ),
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("stop.delete_fail"))
        }
    }

    @Command("m|metro stop|s tp <stopId>")
    @CommandDescription("Teleport to a metro stop")
    fun tp(player: Player, @Argument(value = "stopId", suggestions = "stopIds") id: String) {
        if (!guard.requirePermission(player, "metro.tp")) {
            return
        }
        val stop = guard.requireStop(player, id) ?: return
        val location = stop.stopPointLocation
        if (location == null) {
            player.sendMessage(plugin.languageManager.getMessage("stop.tp_no_point"))
            return
        }

        plugin.bedrockCompatibility.teleportPlayer(player, location).thenAccept { success ->
            if (success) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.tp_success",
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
                    ),
                )
            }
        }
    }

    @Command("m|metro stop|s setcorners <stopId>")
    @CommandDescription("Set stop corners from current selection")
    fun setCorners(player: Player, @Argument(value = "stopId", suggestions = "stopIds") id: String) {
        guard.requireManageableStop(player, id) ?: return
        val selectionManager = plugin.selectionManager
        if (!selectionManager.isSelectionComplete(player)) {
            player.sendMessage(selectionIncompleteMessage())
            return
        }
        val corner1 = selectionManager.getCorner1(player) ?: return
        val corner2 = selectionManager.getCorner2(player) ?: return
        if (stopService.setCorners(id, corner1, corner2) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.setcorners_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id),
                ),
            )
        }
    }

    @Command("m|metro stop|s setpoint [stopId] [yaw]")
    @CommandDescription("Set stop point at player position")
    fun setPoint(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") stopId: String?,
        @Argument(value = "yaw", suggestions = "yawValues") yaw: Float?,
    ) {
        var id = stopId
        if (id == null) {
            val containing = stopManager.getStopContainingLocation(player.location)
            if (containing == null) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.setpoint_not_in_area",
                        LanguageManager.put(LanguageManager.args(), "stop_name", "unknown"),
                    ),
                )
                return
            }
            id = containing.id
        } else if (guard.requireStop(player, id) == null) {
            return
        }

        val stop = guard.requireManageableStop(player, id) ?: return

        val result = stopService.setPoint(id, stop, player.location, yaw)
        when (result.status()) {
            StopCommandService.WriteStatus.SUCCESS -> {
                val args = LanguageManager.args()
                LanguageManager.put(args, "stop_id", id)
                LanguageManager.put(args, "yaw", String.format("%.1f", result.yaw()))
                player.sendMessage(plugin.languageManager.getMessage("stop.setpoint_success", args))
            }

            StopCommandService.WriteStatus.NOT_RAIL ->
                player.sendMessage(plugin.languageManager.getMessage("stop.setpoint_not_rail"))

            StopCommandService.WriteStatus.NOT_IN_STOP ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.setpoint_not_in_area",
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
                    ),
                )

            else -> player.sendMessage(plugin.languageManager.getMessage("stop.setpoint_fail"))
        }
    }

    @Command("m|metro stop|s addtransfer <stopId> <lineId>")
    @CommandDescription("Add transferable line to stop")
    fun addTransfer(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
    ) {
        val stop = guard.requireManageableStop(player, id)
        val line = guard.requireLine(player, lineId)
        if (stop == null || line == null) {
            return
        }
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_name", stop.name)
        LanguageManager.put(args, "transfer_line_name", line.name)
        if (stopService.addTransferLine(id, lineId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.languageManager.getMessage("stop.addtransfer_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("stop.addtransfer_exists", args))
        }
    }

    @Command("m|metro stop|s deltransfer <stopId> <lineId>")
    @CommandDescription("Remove transferable line from stop")
    fun delTransfer(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
    ) {
        val stop = guard.requireManageableStop(player, id)
        val line = guard.requireLine(player, lineId)
        if (stop == null || line == null) {
            return
        }
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_name", stop.name)
        LanguageManager.put(args, "transfer_line_name", line.name)
        if (stopService.removeTransferLine(id, lineId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.languageManager.getMessage("stop.deltransfer_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("stop.deltransfer_not_exists", args))
        }
    }

    @Command("m|metro stop|s listtransfers <stopId>")
    @CommandDescription("List transferable lines for stop")
    fun listTransfers(player: Player, @Argument(value = "stopId", suggestions = "stopIds") id: String) {
        val stop = guard.requireStop(player, id) ?: return
        view.listTransfers(player, stop)
    }

    @Command("m|metro stop|s settitle <stopId> <titleType> <titleKey> <titleValue>")
    @CommandDescription("Set custom title entry for stop")
    fun setTitle(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "titleType", suggestions = "titleTypes") titleType: String,
        @Argument(value = "titleKey", suggestions = "titleKeys") titleKey: String,
        @Greedy @Argument("titleValue") titleValue: String,
    ) {
        val stop = guard.requireManageableStop(player, id) ?: return
        when (stopService.setCustomTitle(stop, titleType, titleKey, titleValue)) {
            StopCommandService.WriteStatus.INVALID_TITLE_TYPE -> sendInvalidTitleType(player, titleType)

            StopCommandService.WriteStatus.INVALID_TITLE_KEY -> sendInvalidTitleKey(player, titleKey)

            StopCommandService.WriteStatus.SUCCESS -> {
                val args = LanguageManager.args()
                LanguageManager.put(args, "stop_name", stop.name)
                LanguageManager.put(args, "title_type", titleType)
                LanguageManager.put(args, "title_key", titleKey)
                LanguageManager.put(args, "title_value", titleValue)
                player.sendMessage(plugin.languageManager.getMessage("stop.settitle_success", args))
            }

            else -> Unit
        }
    }

    @Command("m|metro stop|s deltitle <stopId> <titleType> [titleKey]")
    @CommandDescription("Delete custom title entry for stop")
    fun delTitle(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "titleType", suggestions = "titleTypes") titleType: String,
        @Argument(value = "titleKey", suggestions = "titleKeys") titleKey: String?,
    ) {
        val stop = guard.requireManageableStop(player, id) ?: return
        if (!StopCommandService.TITLE_TYPES.contains(titleType)) {
            sendInvalidTitleType(player, titleType)
            return
        }

        if (titleKey == null) {
            removeTitleType(player, stop, titleType)
            return
        }

        if (!StopCommandService.TITLE_KEYS.contains(titleKey)) {
            sendInvalidTitleKey(player, titleKey)
            return
        }
        val status = stopService.removeCustomTitleKey(stop, titleType, titleKey)
        if (status == StopCommandService.WriteStatus.NOT_FOUND) {
            player.sendMessage(
                plugin.languageManager.getMessage("stop.deltitle_not_found", titleArgs(stop, titleType, titleKey)),
            )
            return
        }
        if (status == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage("stop.deltitle_success", titleArgs(stop, titleType, titleKey)),
            )
        }
    }

    private fun removeTitleType(player: Player, stop: Stop, titleType: String) {
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_name", stop.name)
        LanguageManager.put(args, "title_type", titleType)
        if (stopService.removeCustomTitleType(stop, titleType) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.languageManager.getMessage("stop.deltitle_type_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("stop.deltitle_type_not_found", args))
        }
    }

    @Command("m|metro stop|s listtitles <stopId>")
    @CommandDescription("List custom title config")
    fun listTitles(player: Player, @Argument(value = "stopId", suggestions = "stopIds") id: String) {
        val stop = guard.requireStop(player, id) ?: return
        view.listTitles(player, stop)
    }

    @Command("m|metro stop|s rename <stopId> <name>")
    @CommandDescription("Rename stop display name")
    fun rename(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Greedy @Argument("name") name: String,
    ) {
        val stop = guard.requireManageableStop(player, id) ?: return
        val oldName = stop.name
        if (stopService.renameStop(id, name) == StopCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "old_name", oldName)
            LanguageManager.put(args, "new_name", name)
            player.sendMessage(plugin.languageManager.getMessage("stop.rename_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("stop.rename_fail"))
        }
    }

    @Command("m|metro stop|s info <stopId>")
    @CommandDescription("Show stop details")
    fun info(player: Player, @Argument(value = "stopId", suggestions = "stopIds") id: String) {
        val stop = guard.requireStop(player, id) ?: return
        view.sendInfo(player, stop)
    }

    @Command("m|metro stop|s trust <stopId> <playerName>")
    @CommandDescription("Grant stop admin")
    fun trust(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val stop = guard.requireManageableStop(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        when (stopService.addAdmin(stop, target.uniqueId)) {
            StopCommandService.WriteStatus.EXISTS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.trust_exists",
                        LanguageManager.put(LanguageManager.args(), "player", playerName),
                    ),
                )

            StopCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage("stop.trust_success", stopPlayerArgs(id, playerName)),
                )

            else -> Unit
        }
    }

    @Command("m|metro stop|s untrust <stopId> <playerName>")
    @CommandDescription("Revoke stop admin")
    fun untrust(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val stop = guard.requireManageableStop(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (stopService.removeAdmin(stop, target.uniqueId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage("stop.untrust_success", stopPlayerArgs(id, playerName)),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.untrust_fail",
                    LanguageManager.put(LanguageManager.args(), "player", playerName),
                ),
            )
        }
    }

    @Command("m|metro stop|s owner <stopId> <playerName>")
    @CommandDescription("Transfer stop ownership")
    fun owner(
        player: Player,
        @Argument(value = "stopId", suggestions = "stopIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val stop = guard.requireStop(player, id) ?: return
        if (!guard.requireStopOwner(player, stop)) {
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (stopService.setOwner(stop, target.uniqueId) == StopCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "stop_id", id)
            LanguageManager.put(args, "owner", playerName)
            player.sendMessage(plugin.languageManager.getMessage("stop.owner_success", args))
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id),
                ),
            )
        }
    }

    @Command("m|metro stop|s link <action> <stopId> <lineId>")
    @CommandDescription("Allow or deny linking a line to a stop")
    fun link(
        player: Player,
        @Argument(value = "action", suggestions = "linkActions") action: String,
        @Argument(value = "stopId", suggestions = "stopIds") stopId: String,
        @Argument(value = "lineId", suggestions = "lineIds") lineId: String,
    ) {
        guard.requireManageableStop(player, stopId) ?: return
        val status = stopService.updateLineLink(action, stopId, lineId)
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_id", stopId)
        LanguageManager.put(args, "line_id", lineId)
        if ("allow".equals(action, ignoreCase = true)) {
            if (status == StopCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(plugin.languageManager.getMessage("stop.link_allow_success", args))
            } else {
                player.sendMessage(plugin.languageManager.getMessage("stop.link_allow_exists", args))
            }
            return
        }
        if ("deny".equals(action, ignoreCase = true)) {
            if (status == StopCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(plugin.languageManager.getMessage("stop.link_deny_success", args))
            } else {
                player.sendMessage(plugin.languageManager.getMessage("stop.link_deny_missing", args))
            }
            return
        }
        player.sendMessage(plugin.languageManager.getMessage("stop.usage_link"))
    }

    private fun selectionIncompleteMessage(): String =
        plugin.languageManager.getMessage(
            "stop.selection_not_complete",
            LanguageManager.put(LanguageManager.args(), "tool", plugin.configFacade.getSelectionToolName()),
        )

    private fun playerNotFoundMessage(playerName: String): String =
        plugin.languageManager.getMessage(
            "command.player_not_found",
            LanguageManager.put(LanguageManager.args(), "player", playerName),
        )

    private fun stopPlayerArgs(stopId: String, playerName: String): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_id", stopId)
        LanguageManager.put(args, "player", playerName)
        return args
    }

    private fun titleArgs(stop: Stop, titleType: String, titleKey: String): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_name", stop.name)
        LanguageManager.put(args, "title_type", titleType)
        LanguageManager.put(args, "title_key", titleKey)
        return args
    }

    private fun sendInvalidTitleType(player: Player, titleType: String) {
        player.sendMessage(
            plugin.languageManager.getMessage(
                "stop.title_type_invalid",
                LanguageManager.put(LanguageManager.args(), "title_type", titleType),
            ),
        )
        player.sendMessage(plugin.languageManager.getMessage("stop.title_types"))
    }

    private fun sendInvalidTitleKey(player: Player, titleKey: String) {
        player.sendMessage(
            plugin.languageManager.getMessage(
                "stop.title_key_invalid",
                LanguageManager.put(LanguageManager.args(), "title_key", titleKey),
            ),
        )
        player.sendMessage(plugin.languageManager.getMessage("stop.title_keys"))
    }
}
