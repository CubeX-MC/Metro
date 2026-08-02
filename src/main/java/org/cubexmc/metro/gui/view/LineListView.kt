package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiColors
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.util.OwnershipUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class LineListView(private val plugin: Metro) {

    fun openLineList(player: Player, requestedPage: Int, showOnlyMine: Boolean, previousView: GuiHolder.GuiView?) {
        val holder = createHolder(GuiHolder.GuiType.LINE_LIST, previousView)
        holder.setData("page", requestedPage)
        holder.setData("showOnlyMine", showOnlyMine)

        val allLines = plugin.lineManager.getAllLines()
        val filteredLines =
            if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
                allLines.filter { OwnershipUtil.canManageLine(player, it) }
            } else {
                allLines
            }

        val groupedLines = filteredLines.groupBy { it.name }
        val sortedNames = groupedLines.keys.sorted()

        holder.setData("lineNames", sortedNames)
        holder.setData("groupedLines", groupedLines)

        val titleKey = if (showOnlyMine) "gui.line_list.title_mine" else "gui.line_list.title_all"
        val title = ChatColor.translateAlternateColorCodes('&', msg(titleKey))
        renderPaginatedList(
            player,
            holder,
            title,
            sortedNames,
            requestedPage,
            { inv, name, slot -> addLineNameItem(player, inv, name, slot, groupedLines) },
            { inv, page, totalPages -> addControlBar(inv, page, totalPages, showOnlyMine) },
        )
    }

    private fun addLineNameItem(
        player: Player,
        inv: Inventory,
        name: String,
        slot: Int,
        groupedLines: Map<String, List<Line>>,
    ) {
        val variants = groupedLines[name] ?: return
        val representative = variants[0]

        val lore = ArrayList<String>()
        if (variants.size > 1) {
            lore.add(msg("gui.common.variants", "count", variants.size.toString()))
            lore.add("")
            lore.add(msg("gui.line_list.click_view_variants"))
        } else {
            lore.add(msg("gui.common.id", "id", representative.id))
            lore.add(msg("gui.line_list.stop_count", "count", representative.orderedStopIds.size.toString()))
            addLineSummaryLore(lore, representative)
            lore.add(msg("gui.line_list.color") + representative.color + COLOR_SWATCH)
            lore.add("")
            if (OwnershipUtil.canManageLine(player, representative)) {
                lore.add(msg("gui.line_list.can_manage"))
            } else {
                lore.add(msg("gui.line_list.view_only"))
            }
            lore.add("")
            lore.add(msg("gui.line_list.click_view"))
            if (OwnershipUtil.canManageLine(player, representative)) {
                lore.add(msg("gui.line_list.click_settings"))
            }
        }

        inv.setItem(
            slot,
            ItemBuilder(GuiColors.getWoolByColor(representative.color))
                .name(representative.color + name)
                .lore(lore)
                .build(),
        )
    }

    fun openLineVariants(player: Player, lineName: String?, requestedPage: Int, previousView: GuiHolder.GuiView?) {
        val holder = createHolder(GuiHolder.GuiType.LINE_VARIANTS, previousView)
        holder.setData("page", requestedPage)
        holder.setData("lineName", lineName)

        val variants =
            plugin.lineManager.getAllLines()
                .filter { it.name == lineName }
                .sortedBy { it.id }
        holder.setData("lines", variants)

        val title =
            ChatColor.translateAlternateColorCodes('&', lineName + " - " + msg("gui.common.variants_title"))
        renderPaginatedList(
            player,
            holder,
            title,
            variants,
            requestedPage,
            { inv, line, slot -> addLineVariantItem(player, inv, line, slot) },
            { inv, page, totalPages -> addVariantNavigationControls(inv, page, totalPages) },
        )
    }

    private fun addLineVariantItem(player: Player, inv: Inventory, line: Line, slot: Int) {
        val lore = ArrayList<String>()
        lore.add(msg("gui.common.id", "id", line.id))
        lore.add(msg("gui.line_list.stop_count", "count", line.orderedStopIds.size.toString()))
        addLineSummaryLore(lore, line)
        lore.add(msg("gui.line_list.color") + line.color + COLOR_SWATCH)
        lore.add("")
        if (OwnershipUtil.canManageLine(player, line)) {
            lore.add(msg("gui.line_list.can_manage"))
        } else {
            lore.add(msg("gui.line_list.view_only"))
        }
        lore.add("")
        lore.add(msg("gui.line_list.click_view"))

        inv.setItem(
            slot,
            ItemBuilder(GuiColors.getWoolByColor(line.color))
                .name(line.color + line.id)
                .lore(lore)
                .build(),
        )
    }

    private fun createHolder(type: GuiHolder.GuiType, previousView: GuiHolder.GuiView?): GuiHolder {
        val holder = GuiHolder(type)
        holder.setPreviousView(previousView)
        return holder
    }

    private fun <T> renderPaginatedList(
        player: Player,
        holder: GuiHolder,
        title: String,
        items: List<T>,
        requestedPage: Int,
        populator: ItemPopulator<T>,
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
            populator.populate(inv, items[index], index - start)
        }

        controlBar.populate(inv, page, totalPages)
        player.openInventory(inv)
    }

    private fun addControlBar(inv: Inventory, page: Int, totalPages: Int, showOnlyMine: Boolean) {
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
            GuiSlots.SLOT_BACK,
            ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.control.back_main")).build(),
        )
        inv.setItem(
            GuiSlots.SLOT_PAGE_INFO,
            ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", (page + 1).toString(), "total", totalPages.toString()))
                .build(),
        )

        val filterName = if (showOnlyMine) msg("gui.control.filter_mine") else msg("gui.control.filter_all")
        val filterLore = if (showOnlyMine) msg("gui.control.filter_lore_mine") else msg("gui.control.filter_lore_all")
        inv.setItem(
            GuiSlots.SLOT_FILTER,
            ItemBuilder(Material.HOPPER).name(filterName).lore(filterLore).build(),
        )

        if (page < totalPages - 1) {
            inv.setItem(
                GuiSlots.SLOT_NEXT_PAGE,
                ItemBuilder(Material.ARROW).name(msg("gui.control.next_page")).build(),
            )
        }
    }

    private fun addVariantNavigationControls(inv: Inventory, page: Int, totalPages: Int) {
        inv.setItem(GuiSlots.SLOT_BACK, ItemBuilder(Material.ARROW).name(msg("gui.common.back")).build())
        if (totalPages <= 1) {
            return
        }
        if (page > 0) {
            inv.setItem(
                GuiSlots.SLOT_PREV_PAGE,
                ItemBuilder(Material.ARROW).name(msg("gui.common.prev_page")).build(),
            )
        }
        inv.setItem(
            GuiSlots.SLOT_PAGE_INFO,
            ItemBuilder(Material.PAPER)
                .name(msg("gui.common.page_info", "page", (page + 1).toString(), "total", totalPages.toString()))
                .build(),
        )
        if (page < totalPages - 1) {
            inv.setItem(
                GuiSlots.SLOT_NEXT_PAGE,
                ItemBuilder(Material.ARROW).name(msg("gui.common.next_page")).build(),
            )
        }
    }

    private fun addLineSummaryLore(lore: MutableList<String>, line: Line) {
        lore.add(msg("gui.line_boarding.next_stop", "stop_name", getInitialNextStopName(line)))
        lore.add(msg("gui.line_boarding.terminus", "terminus_name", getTerminusDisplayName(line)))
        lore.add(msg("gui.line_boarding.price", "price", formatTicketPrice(line)))
    }

    private fun getInitialNextStopName(line: Line): String {
        val stopIds = line.orderedStopIds
        if (stopIds.size < 2) {
            return msg("gui.line_boarding.unknown_stop")
        }
        val nextStopId = line.getNextStopId(stopIds[0]) ?: return msg("gui.line_boarding.unknown_stop")
        val nextStop = plugin.stopManager.getStop(nextStopId)
        return nextStop?.name ?: msg("gui.line_boarding.unknown_stop")
    }

    private fun getTerminusDisplayName(line: Line): String {
        if (line.terminusName.isBlank()) {
            return msg("line.info_default")
        }
        return line.terminusName
    }

    private fun formatTicketPrice(line: Line): String {
        val price = line.ticketPrice
        if (price <= 0) {
            return msg("gui.line_boarding.free")
        }
        return plugin.ticketService.format(price) ?: ""
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

    private fun interface ItemPopulator<T> {
        fun populate(inv: Inventory, item: T, slot: Int)
    }

    private fun interface ControlBarPopulator {
        fun populate(inv: Inventory, page: Int, totalPages: Int)
    }

    private companion object {
        const val INVENTORY_SIZE = 54
        const val COLOR_SWATCH = "■■■■■"
    }
}
