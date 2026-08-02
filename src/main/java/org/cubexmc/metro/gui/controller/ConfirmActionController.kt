package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.service.LineCommandService
import org.cubexmc.metro.service.StopCommandService

class ConfirmActionController(private val plugin: Metro) {

    private val permissionGuard = GuiPermissionGuard(plugin)
    private val lineService = LineCommandService(plugin.lineManager)
    private val stopService = StopCommandService(plugin.stopManager)

    fun handleClick(player: Player, holder: GuiHolder, slot: Int) {
        if (slot == SLOT_CANCEL || slot == SLOT_BACK) {
            plugin.guiManager.openPreviousView(player, holder) { reopenSource(player, holder) }
            return
        }
        if (slot != SLOT_CONFIRM) {
            return
        }

        when (holder.getData<String>("action")) {
            "DELETE_LINE" -> confirmDeleteLine(player, holder)
            "DELETE_STOP" -> confirmDeleteStop(player, holder)
            "REMOVE_STOP_FROM_LINE" -> confirmRemoveStopFromLine(player, holder)
            "CLEAR_ROUTE" -> confirmClearRoute(player, holder)
            else -> plugin.guiManager.openPreviousView(player, holder) { reopenSource(player, holder) }
        }
    }

    private fun confirmDeleteLine(player: Player, holder: GuiHolder) {
        val lineId = holder.getData<String>("targetId")
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (lineId == null || line == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
            plugin.guiManager.openLineList(player, 0, false)
            return
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        if (lineService.deleteLine(lineId) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        }
        plugin.guiManager.openLineList(player, 0, false)
    }

    private fun confirmDeleteStop(player: Player, holder: GuiHolder) {
        val stopId = holder.getData<String>("targetId")
        val fromLineId = holder.getData<String>("lineId")
        val stop = if (stopId == null) null else plugin.stopManager.getStop(stopId)
        if (stopId == null || stop == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
                ),
            )
            reopenAfterStopDelete(player, fromLineId)
            return
        }
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory()
            return
        }
        val messageKey =
            if (stopService.deleteStop(stopId) == StopCommandService.WriteStatus.SUCCESS) {
                "stop.delete_success"
            } else {
                "stop.delete_not_found"
            }
        player.sendMessage(
            plugin.languageManager.getMessage(
                messageKey,
                LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
            ),
        )
        reopenAfterStopDelete(player, fromLineId)
    }

    private fun reopenAfterStopDelete(player: Player, fromLineId: String?) {
        if (fromLineId != null) {
            plugin.guiManager.openLineDetail(player, fromLineId, 0)
        } else {
            plugin.guiManager.openStopList(player, 0, false)
        }
    }

    private fun confirmRemoveStopFromLine(player: Player, holder: GuiHolder) {
        val stopId = holder.getData<String>("targetId")
        val lineId = holder.getData<String>("lineId")
        val returnPage = holder.getData("returnPage", 0)
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
            player.closeInventory()
            return
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        if (stopId != null && lineService.removeStopFromLine(line, stopId) == LineCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "stop_id", stopId)
            LanguageManager.put(args, "line_id", lineId)
            player.sendMessage(plugin.languageManager.getMessage("line.delstop_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.delstop_fail"))
        }
        plugin.guiManager.openLineDetail(player, lineId, returnPage)
    }

    private fun confirmClearRoute(player: Player, holder: GuiHolder) {
        val lineId = holder.getData<String>("targetId")
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (lineId == null || line == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
            player.closeInventory()
            return
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        plugin.routeRecorder.clearActive(lineId)
        val result = lineService.clearRoutePoints(line)
        if (result.status() == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.clearroute_success",
                    args("line_id", lineId, "point_count", result.previousPointCount().toString()),
                ),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.clearroute_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        }
        if (!plugin.guiManager.openView(player, holder.getPreviousView())) {
            plugin.guiManager.openLineSettings(player, lineId)
        }
    }

    private fun reopenSource(player: Player, holder: GuiHolder) {
        val targetId = holder.getData<String>("targetId")
        val lineId = holder.getData<String>("lineId")
        val returnPage = holder.getData("returnPage", 0)
        when (holder.getData<String>("action")) {
            "DELETE_LINE" -> plugin.guiManager.openLineSettings(player, targetId)
            "DELETE_STOP" -> plugin.guiManager.openStopSettings(player, targetId, lineId)
            "REMOVE_STOP_FROM_LINE" -> plugin.guiManager.openLineDetail(player, lineId, returnPage)
            "CLEAR_ROUTE" -> plugin.guiManager.openLineSettings(player, targetId)
            else -> player.closeInventory()
        }
    }

    private fun args(vararg replacements: Any?): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        var index = 0
        while (index < replacements.size - 1) {
            LanguageManager.put(args, replacements[index].toString(), replacements[index + 1])
            index += 2
        }
        return args
    }

    private companion object {
        const val SLOT_CONFIRM = 11
        const val SLOT_CANCEL = 15
        const val SLOT_BACK = 22
    }
}
