package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil

class AddStopController(private val plugin: Metro) {

    fun handleAddStopListClick(player: Player, holder: GuiHolder, slot: Int) {
        val lineId = holder.getData<String>("lineId")
        val page = holder.getData("page", 0)
        val showOnlyMine = holder.getData("showOnlyMine", false)
        val totalPages = holder.getData("totalPages", 1)

        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.closeInventory()
            return
        }

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openAddStopList(
                        player,
                        lineId,
                        page - 1,
                        showOnlyMine,
                        holder.getPreviousView(),
                    )
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openAddStopList(
                        player,
                        lineId,
                        page + 1,
                        showOnlyMine,
                        holder.getPreviousView(),
                    )
                }
                return
            }

            GuiSlots.SLOT_FILTER -> {
                plugin.guiManager.openAddStopList(player, lineId, 0, !showOnlyMine, holder.getPreviousView())
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openLineDetail(player, lineId, 0)
                }
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val stopNames = holder.getData<List<String>>("stopNames") ?: return
        val groupedStops = holder.getData<Map<String, List<Stop>>>("groupedStops") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= stopNames.size) {
            return
        }

        val name = stopNames[index]
        val variants = groupedStops[name]
        if (variants.isNullOrEmpty()) {
            return
        }

        if (variants.size > 1) {
            plugin.guiManager.openAddStopVariants(player, lineId, name, 0, holder.snapshot())
        } else {
            handleAddStopClick(player, line, variants[0], holder.getPreviousView())
        }
    }

    fun handleAddStopVariantsClick(player: Player, holder: GuiHolder, slot: Int) {
        val lineId = holder.getData<String>("lineId")
        val page = holder.getData("page", 0)
        val stopName = holder.getData<String>("stopName")
        val totalPages = holder.getData("totalPages", 1)

        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.closeInventory()
            return
        }

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openAddStopVariants(
                        player,
                        lineId,
                        stopName,
                        page - 1,
                        holder.getPreviousView(),
                    )
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openAddStopVariants(
                        player,
                        lineId,
                        stopName,
                        page + 1,
                        holder.getPreviousView(),
                    )
                }
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openAddStopList(player, lineId, 0, false)
                }
                return
            }

            else -> Unit
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return
        }

        val stops = holder.getData<List<Stop>>("stops") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index >= 0 && index < stops.size) {
            handleAddStopClick(player, line, stops[index], holder.getPreviousView())
        }
    }

    private fun handleAddStopClick(player: Player, line: Line, stop: Stop, returnView: GuiHolder.GuiView?) {
        if (!OwnershipUtil.canManageLine(player, line)) {
            return
        }

        if (plugin.lineManager.addStopToLine(line.id, stop.id, -1)) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "stop_id", stop.id)
            LanguageManager.put(args, "line_id", line.id)
            player.sendMessage(plugin.languageManager.getMessage("line.addstop_success", args))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("line.addstop_fail"))
        }
        reopenAfterAddStop(player, line, returnView)
    }

    private fun reopenAfterAddStop(player: Player, line: Line, returnView: GuiHolder.GuiView?) {
        var view = returnView
        while (view != null &&
            (
                view.getType() == GuiHolder.GuiType.ADD_STOP_LIST ||
                    view.getType() == GuiHolder.GuiType.ADD_STOP_VARIANTS
                )
        ) {
            view = view.getPreviousView()
        }
        if (!plugin.guiManager.openView(player, view)) {
            plugin.guiManager.openLineDetail(player, line.id, 0)
        }
    }
}
