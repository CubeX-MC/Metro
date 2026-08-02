package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager

class ConfirmActionView(private val plugin: Metro) {

    fun open(
        player: Player,
        action: String,
        targetId: String?,
        targetName: String?,
        lineId: String?,
        returnPage: Int,
        previousView: GuiHolder.GuiView?,
    ) {
        val holder = GuiHolder(GuiHolder.GuiType.CONFIRM_ACTION)
        holder.setPreviousView(previousView)
        holder.setData("action", action)
        holder.setData("targetId", targetId)
        holder.setData("targetName", targetName)
        holder.setData("lineId", lineId)
        holder.setData("returnPage", returnPage)

        val inv =
            Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                ChatColor.translateAlternateColorCodes('&', msg("gui.confirm.title")),
            )
        holder.setInventory(inv)

        val filler = ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()
        for (slot in 0 until INVENTORY_SIZE) {
            inv.setItem(slot, filler)
        }

        val target =
            if (targetName.isNullOrBlank()) targetId ?: "" else "$targetName ($targetId)"
        inv.setItem(
            SLOT_CONFIRM,
            ItemBuilder(Material.LIME_CONCRETE)
                .name(msg("gui.confirm.confirm"))
                .lore(
                    msg(messageKey(action), "target", target),
                    msg("gui.confirm.warning"),
                    "",
                    msg("gui.confirm.confirm_lore"),
                )
                .build(),
        )
        inv.setItem(
            SLOT_CANCEL,
            ItemBuilder(Material.RED_CONCRETE)
                .name(msg("gui.confirm.cancel"))
                .lore(msg("gui.confirm.cancel_lore"))
                .build(),
        )
        inv.setItem(
            SLOT_BACK,
            ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.common.back")).build(),
        )

        player.openInventory(inv)
    }

    private fun messageKey(action: String): String =
        when (action) {
            "DELETE_LINE" -> "gui.confirm.delete_line"
            "DELETE_STOP" -> "gui.confirm.delete_stop"
            "REMOVE_STOP_FROM_LINE" -> "gui.confirm.remove_stop_from_line"
            "CLEAR_ROUTE" -> "gui.confirm.clear_route"
            else -> "gui.confirm.generic"
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
        const val SLOT_CONFIRM = 11
        const val SLOT_CANCEL = 15
        const val SLOT_BACK = 22
    }
}
