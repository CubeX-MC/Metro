package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager

class StopSettingsView(private val plugin: Metro) {

    fun open(player: Player, stopId: String?, fromLineId: String?, previousView: GuiHolder.GuiView?) {
        val stop = if (stopId == null) null else plugin.stopManager.getStop(stopId)
        if (stop == null) {
            return
        }

        val holder = GuiHolder(GuiHolder.GuiType.STOP_SETTINGS)
        holder.setPreviousView(previousView)
        holder.setData("stopId", stopId)
        if (fromLineId != null) {
            holder.setData("fromLineId", fromLineId)
        }

        val inv =
            Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                ChatColor.translateAlternateColorCodes('&', msg("gui.stop_settings.title") + stop.name),
            )
        holder.setInventory(inv)

        val filler = ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()
        for (slot in 0 until INVENTORY_SIZE) {
            inv.setItem(slot, filler)
        }

        inv.setItem(
            SLOT_RENAME,
            ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.stop_settings.rename"))
                .lore(msg("gui.stop_settings.rename_lore"))
                .build(),
        )

        val stopPointText = formatLocation(stop.stopPointLocation)
        inv.setItem(
            SLOT_SET_POINT,
            ItemBuilder(Material.RAIL)
                .name(msg("gui.stop_settings.set_point"))
                .lore(
                    msg("gui.stop_settings.current_stoppoint", "stoppoint", stopPointText),
                    msg("gui.stop_settings.set_point_lore"),
                )
                .build(),
        )
        inv.setItem(
            SLOT_DELETE,
            ItemBuilder(Material.BARRIER)
                .name(msg("gui.stop_settings.delete"))
                .lore(msg("gui.stop_settings.delete_lore"))
                .build(),
        )
        inv.setItem(
            SLOT_BACK,
            ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.control.back_main")).build(),
        )

        player.openInventory(inv)
    }

    private fun formatLocation(location: Location?): String {
        val world = location?.world ?: return msg("gui.stop_settings.stoppoint_not_set")
        return world.name + " " + location.blockX + ", " + location.blockY + ", " + location.blockZ
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
        const val INVENTORY_SIZE = 27
        const val SLOT_RENAME = 11
        const val SLOT_SET_POINT = 13
        const val SLOT_DELETE = 15
        const val SLOT_BACK = 22
    }
}
