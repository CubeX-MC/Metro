package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil

class StopListController(private val plugin: Metro) {

    fun handleStopListClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean) {
        val page = holder.getData("page", 0)
        val showOnlyMine = holder.getData("showOnlyMine", false)
        val totalPages = holder.getData("totalPages", 1)

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openStopList(player, page - 1, showOnlyMine, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openStopList(player, page + 1, showOnlyMine, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_FILTER -> {
                plugin.guiManager.openStopList(player, 0, !showOnlyMine, holder.getPreviousView())
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

        val stopNames = holder.getData<List<String>>("stopNames") ?: return
        val groupedStops = holder.getData<Map<String, List<Stop>>>("groupedStops") ?: return

        val index = page * GuiSlots.ITEMS_PER_PAGE + slot
        if (index < 0 || index >= stopNames.size) {
            return
        }

        val variants = groupedStops[stopNames[index]]
        if (variants.isNullOrEmpty()) {
            return
        }

        if (variants.size > 1) {
            plugin.guiManager.openStopVariants(player, stopNames[index], 0, holder.snapshot())
            return
        }

        openStop(player, holder, variants[0], isRightClick)
    }

    fun handleStopVariantsClick(player: Player, holder: GuiHolder, slot: Int, isRightClick: Boolean) {
        val page = holder.getData("page", 0)
        val stopName = holder.getData<String>("stopName")
        val totalPages = holder.getData("totalPages", 1)

        when (slot) {
            GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.guiManager.openStopVariants(player, stopName, page - 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.guiManager.openStopVariants(player, stopName, page + 1, holder.getPreviousView())
                }
                return
            }

            GuiSlots.SLOT_BACK -> {
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openStopList(player, 0, false)
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
        if (index < 0 || index >= stops.size) {
            return
        }

        openStop(player, holder, stops[index], isRightClick)
    }

    private fun openStop(player: Player, holder: GuiHolder, stop: Stop, isRightClick: Boolean) {
        if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
            plugin.guiManager.openStopSettings(player, stop.id, null, holder.snapshot())
        } else {
            handleStopClick(player, stop)
        }
    }

    private fun handleStopClick(player: Player, stop: Stop) {
        if (!player.hasPermission("metro.tp")) {
            return
        }

        val stopPoint = stop.stopPointLocation
        if (stopPoint != null) {
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
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.stoppoint_not_set",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
                ),
            )
        }
    }
}
