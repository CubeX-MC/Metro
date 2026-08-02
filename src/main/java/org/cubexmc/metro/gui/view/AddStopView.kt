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
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class AddStopView(private val plugin: Metro) {

    fun openAddStopList(
        player: Player,
        lineId: String?,
        requestedPage: Int,
        showOnlyMine: Boolean,
        previousView: GuiHolder.GuiView?,
    ) {
        val holder = createHolder(GuiHolder.GuiType.ADD_STOP_LIST, previousView)
        holder.setData("lineId", lineId)
        holder.setData("page", requestedPage)
        holder.setData("showOnlyMine", showOnlyMine)

        val allStops = plugin.stopManager.getAllStopIds().mapNotNull { plugin.stopManager.getStop(it) }

        var filteredStops =
            if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
                allStops.filter { OwnershipUtil.canManageStop(player, it) }
            } else {
                allStops
            }

        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line != null) {
            filteredStops = filteredStops.filter { !line.containsStop(it.id) }
        }

        val groupedStops = filteredStops.groupBy { it.name }
        val sortedNames = groupedStops.keys.sorted()

        holder.setData("stopNames", sortedNames)
        holder.setData("groupedStops", groupedStops)

        val title = ChatColor.translateAlternateColorCodes('&', msg("gui.add_stop_list.title"))
        renderPaginatedList(
            player,
            holder,
            title,
            sortedNames,
            requestedPage,
            { inv, name, slot -> addStopNameItem(inv, name, slot, groupedStops) },
            { inv, page, totalPages ->
                addControlBar(inv, page, totalPages, showOnlyMine)
                inv.setItem(
                    GuiSlots.SLOT_BACK,
                    ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.control.back_line_list")).build(),
                )
            },
        )
    }

    private fun addStopNameItem(
        inv: Inventory,
        name: String,
        slot: Int,
        groupedStops: Map<String, List<Stop>>,
    ) {
        val variants = groupedStops[name] ?: return
        val representative = variants[0]

        val lore = ArrayList<String>()
        if (variants.size > 1) {
            lore.add(msg("gui.common.variants", "count", variants.size.toString()))
            lore.add("")
            lore.add(msg("gui.stop_list.click_view_variants"))
        } else {
            lore.add(msg("gui.common.id", "id", representative.id))
            lore.add("")
            lore.add(msg("gui.add_stop_list.click_add"))
        }

        inv.setItem(slot, ItemBuilder(Material.OAK_SIGN).name("&a$name").lore(lore).build())
    }

    fun openAddStopVariants(
        player: Player,
        lineId: String?,
        stopName: String?,
        requestedPage: Int,
        previousView: GuiHolder.GuiView?,
    ) {
        val holder = createHolder(GuiHolder.GuiType.ADD_STOP_VARIANTS, previousView)
        holder.setData("lineId", lineId)
        holder.setData("page", requestedPage)
        holder.setData("stopName", stopName)

        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            return
        }

        val variants =
            plugin.stopManager.getAllStopIds()
                .mapNotNull { plugin.stopManager.getStop(it) }
                .filter { it.name == stopName }
                .filter { !line.containsStop(it.id) }
                .sortedBy { it.id }
        holder.setData("stops", variants)

        val title =
            ChatColor.translateAlternateColorCodes('&', stopName + " - " + msg("gui.add_stop_list.title"))
        renderPaginatedList(
            player,
            holder,
            title,
            variants,
            requestedPage,
            { inv, stop, slot ->
                val lore = ArrayList<String>()
                lore.add(msg("gui.common.id", "id", stop.id))
                lore.add("")
                lore.add(msg("gui.add_stop_list.click_add"))

                inv.setItem(slot, ItemBuilder(Material.OAK_SIGN).name("&a" + stop.id).lore(lore).build())
            },
            { inv, page, totalPages -> addVariantNavigationControls(inv, page, totalPages) },
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
    }
}
