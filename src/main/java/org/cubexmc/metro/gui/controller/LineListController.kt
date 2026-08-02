package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.util.OwnershipUtil

class LineListController(private val plugin: Metro) {

    fun handleLineListClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean) {
        val page = holder.getData("page", 0)
        val showOnlyMine = holder.getData("showOnlyMine", false)
        val totalPages = holder.getData("totalPages", 1)

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openLineList(player, page - 1, showOnlyMine, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openLineList(player, page + 1, showOnlyMine, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_FILTER -> {
                plugin.guiManager.openLineList(player, 0, !showOnlyMine, holder.getPreviousView())
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) { plugin.guiManager.openMainMenu(player) }
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val lineNames = holder.getData<List<String>>("lineNames") ?: return
        val groupedLines = holder.getData<Map<String, List<Line>>>("groupedLines") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= lineNames.size) {
            return
        }

        val name = lineNames[index]
        val variants = groupedLines[name]
        if (variants.isNullOrEmpty()) {
            return
        }

        if (variants.size > 1) {
            plugin.guiManager.openLineVariants(player, name, 0, holder.snapshot())
            return
        }

        openLine(player, holder, variants[0], isRightClick)
    }

    fun handleLineVariantsClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean) {
        val page = holder.getData("page", 0)
        val lineName = holder.getData<String>("lineName")
        val totalPages = holder.getData("totalPages", 1)

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openLineVariants(player, lineName, page - 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openLineVariants(player, lineName, page + 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openLineList(player, 0, false)
                }
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val lines = holder.getData<List<Line>>("lines") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= lines.size) {
            return
        }

        openLine(player, holder, lines[index], isRightClick)
    }

    private fun openLine(player: Player, holder: GuiHolder, line: Line, isRightClick: Boolean) {
        if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
            plugin.guiManager.openLineSettings(player, line.id, holder.snapshot())
        } else {
            plugin.guiManager.openLineDetail(player, line.id, 0, holder.snapshot())
        }
    }
}
