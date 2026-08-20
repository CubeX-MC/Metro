package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.gui.fillEmpty

class MainMenuView(private val plugin: Metro) {

    fun open(player: Player) {
        val holder = GuiHolder(GuiHolder.GuiType.MAIN_MENU)
        val inv =
            Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                ChatColor.DARK_GRAY.toString() + ChatColor.stripColor(msg("gui.main_menu.title")),
            )
        holder.setInventory(inv)

        inv.setItem(
            SLOT_LINE_MANAGE,
            ItemBuilder(Material.RAIL)
                .name(msg("gui.main_menu.line_manage"))
                .lore(msg("gui.main_menu.line_manage_lore1"), msg("gui.main_menu.line_manage_lore2"))
                .build(),
        )
        inv.setItem(
            SLOT_STOP_MANAGE,
            ItemBuilder(Material.MINECART)
                .name(msg("gui.main_menu.stop_manage"))
                .lore(msg("gui.main_menu.stop_manage_lore1"), msg("gui.main_menu.stop_manage_lore2"))
                .build(),
        )

        inv.fillEmpty(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build())

        player.openInventory(inv)
    }

    private fun msg(key: String): String = plugin.languageManager.getMessage(key)

    private companion object {
        const val INVENTORY_SIZE = 27
        const val SLOT_LINE_MANAGE = 11
        const val SLOT_STOP_MANAGE = 15
    }
}
