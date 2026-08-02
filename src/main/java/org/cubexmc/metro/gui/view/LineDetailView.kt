package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.util.OwnershipUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class LineDetailView(private val plugin: Metro) {

    fun open(player: Player, lineId: String?, requestedPage: Int, previousView: GuiHolder.GuiView?) {
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
            return
        }

        val holder = GuiHolder(GuiHolder.GuiType.LINE_DETAIL)
        holder.setPreviousView(previousView)
        holder.setData("lineId", lineId)
        holder.setData("page", requestedPage)

        val stopIds = line.orderedStopIds
        val coloredName = line.color + line.name
        val title = ChatColor.translateAlternateColorCodes('&', msg("gui.line_detail.title") + coloredName)
        val canManage = OwnershipUtil.canManageLine(player, line)

        renderPaginatedList(
            player,
            holder,
            title,
            stopIds,
            requestedPage,
            { inv, stopId, slot, page -> addStopItem(player, inv, stopIds, stopId, slot, page, canManage) },
            { inv, page, totalPages -> addControlBar(inv, page, totalPages, stopIds.size, canManage) },
        )
    }

    private fun addStopItem(
        player: Player,
        inv: Inventory,
        stopIds: List<String>,
        stopId: String,
        slot: Int,
        page: Int,
        canManage: Boolean,
    ) {
        val stop = plugin.stopManager.getStop(stopId)
        val lore = ArrayList<String>()
        val index = slot + page * GuiSlots.ITEMS_PER_PAGE
        lore.add(msg("gui.line_detail.index", "index", (index + 1).toString()))

        if (stop != null) {
            lore.add(msg("gui.common.id", "id", stop.id))
            if (index == 0) {
                lore.add(msg("gui.line_detail.start_stop"))
            } else if (index == stopIds.size - 1) {
                lore.add(msg("gui.line_detail.end_stop"))
            }
            lore.add("")
            if (player.hasPermission("metro.tp") && stop.stopPointLocation != null) {
                lore.add(msg("gui.line_detail.click_tp"))
            }
            if (canManage) {
                lore.add(msg("gui.line_detail.click_settings"))
                lore.add(msg("gui.line_detail.click_remove"))
            }

            inv.setItem(slot, ItemBuilder(Material.OAK_SIGN).name("&a" + stop.name).lore(lore).build())
            return
        }

        lore.add(msg("gui.line_detail.stop_not_exist"))
        inv.setItem(slot, ItemBuilder(Material.BARRIER).name("&c$stopId").lore(lore).build())
    }

    private fun renderPaginatedList(
        player: Player,
        holder: GuiHolder,
        title: String,
        items: List<String>,
        requestedPage: Int,
        populator: ItemPopulator,
        controlBar: ControlBarPopulator,
    ) {
        val totalPages = max(1, ceil(items.size.toDouble() / GuiSlots.ITEMS_PER_PAGE).toInt())
        val page = max(0, min(requestedPage, totalPages - 1))
        holder.setData("page", page)
        holder.setData("totalPages", totalPages)

        val inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title)
        holder.setInventory(inv)

        val start = page * GuiSlots.ITEMS_PER_PAGE
        val end = min(start + GuiSlots.ITEMS_PER_PAGE, items.size)
        for (index in start until end) {
            populator.populate(inv, items[index], index - start, page)
        }

        controlBar.populate(inv, page, totalPages)
        player.openInventory(inv)
    }

    private fun addControlBar(inv: Inventory, page: Int, totalPages: Int, stopCount: Int, canManage: Boolean) {
        val filler = ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()
        for (slot in GuiSlots.ITEMS_PER_PAGE until INVENTORY_SIZE) {
            inv.setItem(slot, filler)
        }

        if (page > 0) {
            inv.setItem(
                GuiSlots.SLOT_PREV_PAGE,
                ItemBuilder(Material.ARROW).name(msg("gui.control.prev_page")).build(),
            )
        }
        inv.setItem(
            GuiSlots.SLOT_PAGE_INFO,
            ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", (page + 1).toString(), "total", totalPages.toString()))
                .lore(msg("gui.control.stop_count", "count", stopCount.toString()))
                .build(),
        )
        if (page < totalPages - 1) {
            inv.setItem(
                GuiSlots.SLOT_NEXT_PAGE,
                ItemBuilder(Material.ARROW).name(msg("gui.control.next_page")).build(),
            )
        }
        inv.setItem(
            GuiSlots.SLOT_BACK,
            ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.control.back_line_list")).build(),
        )

        if (canManage) {
            inv.setItem(
                GuiSlots.SLOT_FILTER,
                ItemBuilder(Material.EMERALD_BLOCK)
                    .name(msg("gui.line_detail.add_stop"))
                    .lore(msg("gui.line_detail.add_stop_lore"))
                    .build(),
            )
            inv.setItem(
                SLOT_SETTINGS,
                ItemBuilder(Material.ANVIL)
                    .name(msg("gui.line_detail.settings"))
                    .lore(msg("gui.line_detail.settings_lore"))
                    .build(),
            )
        }
    }

    private fun msg(key: String): String = plugin.languageManager.getMessage(key)

    private fun msg(key: String, vararg replacements: String): String {
        val args = LanguageManager.args()
        var index = 0
        while (index < replacements.size - 1) {
            LanguageManager.put(args, replacements[index], replacements[index + 1])
            index += 2
        }
        return plugin.languageManager.getMessage(key, args)
    }

    private fun interface ItemPopulator {
        fun populate(inv: Inventory, item: String, slot: Int, page: Int)
    }

    private fun interface ControlBarPopulator {
        fun populate(inv: Inventory, page: Int, totalPages: Int)
    }

    private companion object {
        const val INVENTORY_SIZE = 54
        const val SLOT_SETTINGS = 50
    }
}
