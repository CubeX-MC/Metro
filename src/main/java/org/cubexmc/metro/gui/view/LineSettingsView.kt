package org.cubexmc.metro.gui.view

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiColors
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.gui.ItemBuilder
import org.cubexmc.metro.manager.LanguageManager

class LineSettingsView(private val plugin: Metro) {

    fun open(player: Player, lineId: String?, previousView: GuiHolder.GuiView?) {
        if (lineId == null) {
            return
        }
        val line = plugin.lineManager.getLine(lineId) ?: return

        val holder = GuiHolder(GuiHolder.GuiType.LINE_SETTINGS)
        holder.setPreviousView(previousView)
        holder.setData("lineId", lineId)

        val inv =
            Bukkit.createInventory(
                holder,
                GuiSlots.LINE_SETTINGS_SIZE,
                ChatColor.translateAlternateColorCodes('&', msg("gui.line_settings.title") + line.name),
            )
        holder.setInventory(inv)

        val filler = ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()
        for (slot in 0 until GuiSlots.LINE_SETTINGS_SIZE) {
            inv.setItem(slot, filler)
        }

        val recording = plugin.routeRecorder.isRecording(lineId)
        inv.setItem(
            GuiSlots.LINE_SETTINGS_RENAME,
            ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.line_settings.rename"))
                .lore(msg("gui.line_settings.rename_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_COLOR,
            ItemBuilder(GuiColors.getWoolByColor(line.color))
                .name(msg("gui.line_settings.set_color"))
                .lore(
                    msg("gui.line_settings.current_color", "color", line.color),
                    msg("gui.line_settings.set_color_lore"),
                )
                .build(),
        )

        val terminusName =
            if (line.terminusName.isBlank()) msg("line.info_default") else line.terminusName
        inv.setItem(
            GuiSlots.LINE_SETTINGS_TERMINUS,
            ItemBuilder(Material.OAK_SIGN)
                .name(msg("gui.line_settings.set_terminus"))
                .lore(
                    msg("gui.line_settings.current_terminus", "terminus_name", terminusName),
                    msg("gui.line_settings.set_terminus_lore"),
                )
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_MAX_SPEED,
            ItemBuilder(Material.MINECART)
                .name(msg("gui.line_settings.set_speed"))
                .lore(msg("gui.line_settings.set_speed_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_TICKET_PRICE,
            ItemBuilder(Material.EMERALD)
                .name(msg("gui.line_settings.set_price"))
                .lore(msg("gui.line_settings.set_price_lore"))
                .build(),
        )

        inv.setItem(
            GuiSlots.LINE_SETTINGS_ROUTE_RECORDING,
            ItemBuilder(if (recording) Material.REDSTONE_TORCH else Material.MINECART)
                .name(msg(if (recording) "gui.line_settings.record_stop" else "gui.line_settings.record_start"))
                .lore(
                    msg(
                        if (recording) {
                            "gui.line_settings.record_stop_lore"
                        } else {
                            "gui.line_settings.record_start_lore"
                        },
                    ),
                )
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_ROUTE_INFO,
            ItemBuilder(Material.COMPASS)
                .name(msg("gui.line_settings.route_info"))
                .lore(msg("gui.line_settings.route_info_lore", "point_count", line.routePoints.size.toString()))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_CLEAR_ROUTE,
            ItemBuilder(Material.TNT)
                .name(msg("gui.line_settings.clear_route"))
                .lore(msg("gui.line_settings.clear_route_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_RAIL_PROTECTION,
            ItemBuilder(if (line.isRailProtected) Material.IRON_BARS else Material.RAIL)
                .name(
                    msg(
                        if (line.isRailProtected) {
                            "gui.line_settings.protection_on"
                        } else {
                            "gui.line_settings.protection_off"
                        },
                    ),
                )
                .lore(msg("gui.line_settings.protection_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_CLONE_REVERSE,
            ItemBuilder(Material.COMPARATOR)
                .name(msg("gui.line_settings.clone_reverse"))
                .lore(msg("gui.line_settings.clone_reverse_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_DELETE,
            ItemBuilder(Material.BARRIER)
                .name(msg("gui.line_settings.delete"))
                .lore(msg("gui.line_settings.delete_lore"))
                .build(),
        )
        inv.setItem(
            GuiSlots.LINE_SETTINGS_BACK,
            ItemBuilder(Material.DARK_OAK_DOOR).name(msg("gui.control.back_line_list")).build(),
        )

        player.openInventory(inv)
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
}
