package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.ChatInputManager
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.StopCommandService

class StopSettingsController(private val plugin: Metro) {

    private val stopService = StopCommandService(plugin.stopManager)
    private val permissionGuard = GuiPermissionGuard(plugin)

    fun handleClick(player: Player, holder: GuiHolder, slot: Int) {
        val stopId = holder.getData<String>("stopId")
        val fromLineId = holder.getData<String>("fromLineId")
        val stop = if (stopId == null) null else plugin.stopManager.getStop(stopId)
        if (stop == null) {
            player.closeInventory()
            return
        }

        val previousView = holder.getPreviousView()
        when (slot) {
            SLOT_RENAME -> requestStopRename(player, stop, previousView, fromLineId)

            SLOT_SET_POINT -> handleSetStopPoint(player, stop, previousView, fromLineId)

            SLOT_DELETE -> {
                if (!permissionGuard.requireManageStop(player, stop)) {
                    player.closeInventory()
                    return
                }
                plugin.guiManager.openConfirmAction(
                    player,
                    "DELETE_STOP",
                    stopId,
                    stop.name,
                    fromLineId,
                    0,
                    holder.snapshot(),
                )
            }

            SLOT_BACK ->
                plugin.guiManager.openPreviousView(player, holder) {
                    if (fromLineId != null) {
                        plugin.guiManager.openLineDetail(player, fromLineId, 0)
                    } else {
                        plugin.guiManager.openStopList(player, 0, false)
                    }
                }

            else -> Unit
        }
    }

    private fun requestStopRename(
        player: Player,
        stop: Stop,
        previousView: GuiHolder.GuiView?,
        fromLineId: String?,
    ) {
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory()
            return
        }
        val stopId = stop.id
        val oldName = stop.name
        plugin.chatInputManager.requestInput(
            player,
            plugin.languageManager.getMessage("chat.enter_new_name"),
            object : ChatInputManager.ChatInputCallback {
                override fun onInput(input: String) {
                    if (requireCurrentStop(player, stopId) == null) {
                        return
                    }
                    if (stopService.renameStop(stopId, input) == StopCommandService.WriteStatus.SUCCESS) {
                        player.sendMessage(
                            plugin.languageManager.getMessage(
                                "stop.rename_success",
                                args("old_name", oldName, "new_name", input),
                            ),
                        )
                    } else {
                        player.sendMessage(plugin.languageManager.getMessage("stop.rename_fail"))
                    }
                    plugin.guiManager.openStopSettings(player, stopId, fromLineId, previousView)
                }

                override fun onCancel() {
                    plugin.guiManager.openStopSettings(player, stopId, fromLineId, previousView)
                }
            },
        )
    }

    private fun handleSetStopPoint(
        player: Player,
        stop: Stop,
        previousView: GuiHolder.GuiView?,
        fromLineId: String?,
    ) {
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory()
            return
        }
        val result = stopService.setPoint(stop.id, stop, player.location, null)
        when (result.status()) {
            StopCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.setpoint_success",
                        args("stop_id", stop.id, "yaw", String.format("%.1f", result.yaw())),
                    ),
                )

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
        plugin.guiManager.openStopSettings(player, stop.id, fromLineId, previousView)
    }

    private fun requireCurrentStop(player: Player, stopId: String): Stop? {
        val currentStop = plugin.stopManager.getStop(stopId)
        if (currentStop == null) {
            player.closeInventory()
            return null
        }
        if (!permissionGuard.requireManageStop(player, currentStop)) {
            player.closeInventory()
            return null
        }
        return currentStop
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
        const val SLOT_RENAME = 11
        const val SLOT_SET_POINT = 13
        const val SLOT_DELETE = 15
        const val SLOT_BACK = 22
    }
}
