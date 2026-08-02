package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.model.Stop

class LineBoardingChoiceController(private val plugin: Metro) {

    fun handleClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean) {
        val stopId = holder.getData<String>("stopId")
        val page = holder.getData("page", 0)
        val totalPages = holder.getData("totalPages", 1)

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                val stop = findStop(stopId)
                if (stop != null && page > 0) {
                    plugin.guiManager.openLineBoardingChoice(player, stop, page - 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                val stop = findStop(stopId)
                if (stop != null && page < totalPages - 1) {
                    plugin.guiManager.openLineBoardingChoice(player, stop, page + 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_BACK -> {
                player.closeInventory()
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val lineIds = holder.getData<List<String>>("lineIds") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= lineIds.size) {
            return
        }

        val lineId = lineIds[index]
        if (isRightClick) {
            plugin.guiManager.openLineDetail(player, lineId, 0, holder.snapshot())
            return
        }

        player.closeInventory()
        plugin.playerInteractListener.boardSelectedLine(player, stopId, lineId)
    }

    private fun findStop(stopId: String?): Stop? {
        if (stopId == null) {
            return null
        }
        return plugin.stopManager.getStop(stopId)
    }
}
