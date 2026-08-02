package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiColors
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.TicketService
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class LineBoardingChoiceView(private val plugin: Metro) {

    fun open(player: Player, stop: Stop?, requestedPage: Int, previousView: GuiHolder.GuiView?) {
        if (stop == null) {
            return
        }

        val lines = plugin.lineSelectionService.getBoardableLines(stop)
        if (lines.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("interact.stop_no_line"))
            return
        }

        val holder = GuiHolder(GuiHolder.GuiType.LINE_BOARDING_CHOICE)
        holder.setPreviousView(previousView)
        holder.setData("stopId", stop.id)
        holder.setData("lineIds", lines.map { it.id })

        val title =
            ChatColor.translateAlternateColorCodes(
                '&',
                msg("gui.line_boarding.title", "stop_name", stop.name),
            )
        render(player, holder, title, stop, lines, requestedPage)
    }

    private fun render(
        player: Player,
        holder: GuiHolder,
        title: String,
        stop: Stop,
        lines: List<Line>,
        requestedPage: Int,
    ) {
        val totalPages = max(1, ceil(lines.size.toDouble() / GuiSlots.ITEMS_PER_PAGE).toInt())
        val page = max(0, min(requestedPage, totalPages - 1))
        holder.setData("page", page)
        holder.setData("totalPages", totalPages)

        val inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title)
        holder.setInventory(inv)

        val start = page * GuiSlots.ITEMS_PER_PAGE
        val end = min(start + GuiSlots.ITEMS_PER_PAGE, lines.size)
        for (index in start until end) {
            inv.setItem(index - start, createLineItem(player, stop, lines[index]))
        }

        addControlBar(inv, page, totalPages)
        player.openInventory(inv)
    }

    private fun createLineItem(player: Player, stop: Stop, line: Line): ItemStack {
        val nextStop = getNextStop(line, stop)
        val nextStopName = nextStop?.name ?: msg("gui.line_boarding.unknown_stop")
        val terminusName =
            if (line.terminusName.isBlank()) msg("line.info_default") else line.terminusName
        val blockedReason = getBoardingBlockReason(player, line)

        val lore = ArrayList<String>()
        lore.add(msg("gui.common.id", "id", line.id))
        lore.add(msg("gui.line_boarding.next_stop", "stop_name", nextStopName))
        lore.add(msg("gui.line_boarding.terminus", "terminus_name", terminusName))
        lore.add(msg("gui.line_boarding.price", "price", formatTicketPrice(line)))
        lore.add("")
        if (blockedReason == null) {
            lore.add(msg("gui.line_boarding.click_board"))
        } else {
            lore.add(msg("gui.line_boarding.cannot_board", "reason", blockedReason))
        }
        lore.add(msg("gui.line_boarding.click_route"))

        val material = if (blockedReason == null) GuiColors.getWoolByColor(line.color) else Material.BARRIER
        return ItemBuilder(material)
            .name(line.color + line.name)
            .lore(lore)
            .build()
    }

    private fun addControlBar(inv: Inventory, page: Int, totalPages: Int) {
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
            ItemBuilder(Material.BARRIER).name(msg("gui.common.close")).build(),
        )
    }

    private fun getNextStop(line: Line, stop: Stop): Stop? {
        val nextStopId = line.getNextStopId(stop.id) ?: return null
        return plugin.stopManager.getStop(nextStopId)
    }

    private fun formatTicketPrice(line: Line): String {
        val price = line.ticketPrice
        if (price <= 0) {
            return msg("gui.line_boarding.free")
        }
        return plugin.ticketService.format(price) ?: ""
    }

    private fun getBoardingBlockReason(player: Player, line: Line): String? {
        if (!player.hasPermission("metro.use")) {
            return msg("gui.line_boarding.no_permission")
        }
        val check = plugin.ticketService.checkCanBoard(player, line)
        if (check.canBoard()) {
            return null
        }
        if (check.status == TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS) {
            return msg("economy.insufficient_funds", "price", check.formattedPrice ?: "")
        }
        if (check.status == TicketService.TicketCheckStatus.VAULT_UNAVAILABLE) {
            return msg("economy.vault_unavailable")
        }
        return msg("gui.line_boarding.no_permission")
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

    private companion object {
        const val INVENTORY_SIZE = 54
    }
}
