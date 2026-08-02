package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil

class LineDetailController(private val plugin: Metro) {

    fun handleClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean, isShiftClick: Boolean) {
        val lineId = holder.getData<String>("lineId")
        val page = holder.getData("page", 0)
        val totalPages = holder.getData("totalPages", 1)

        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.closeInventory()
            return
        }

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openLineDetail(player, lineId, page - 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openLineDetail(player, lineId, page + 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_FILTER -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.guiManager.openAddStopList(player, lineId, 0, false, holder.snapshot())
                }
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openLineList(player, 0, false)
                }
                return
            }

            SLOT_SETTINGS -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.guiManager.openLineSettings(player, lineId, holder.snapshot())
                }
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val stopIds = line.orderedStopIds
        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= stopIds.size) {
            return
        }

        val stopId = stopIds[index]
        val stop = plugin.stopManager.getStop(stopId) ?: return

        handleStopClick(player, holder, line, stop, stopId, lineId, page, isRightClick, isShiftClick)
    }

    @Suppress("LongParameterList")
    private fun handleStopClick(
        player: Player,
        holder: GuiHolder,
        line: Line,
        stop: Stop,
        stopId: String,
        lineId: String?,
        page: Int,
        isRightClick: Boolean,
        isShiftClick: Boolean,
    ) {
        if (isShiftClick) {
            if (OwnershipUtil.canManageLine(player, line)) {
                plugin.guiManager.openConfirmAction(
                    player,
                    "REMOVE_STOP_FROM_LINE",
                    stopId,
                    stop.name,
                    lineId,
                    page,
                    holder.snapshot(),
                )
            }
            return
        }
        if (isRightClick) {
            if (OwnershipUtil.canManageLine(player, line)) {
                plugin.guiManager.openStopSettings(player, stopId, lineId, holder.snapshot())
            }
            return
        }

        val stopPoint = stop.stopPointLocation
        if (player.hasPermission("metro.tp") && stopPoint != null) {
            player.closeInventory()
            plugin.bedrockCompatibility.teleportPlayer(player, stopPoint).thenAccept { success ->
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
    }

    private companion object {
        const val SLOT_SETTINGS = 50
    }
}
