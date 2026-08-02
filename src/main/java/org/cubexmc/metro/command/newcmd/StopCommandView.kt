package org.cubexmc.metro.command.newcmd

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.CommandDisplayService
import org.cubexmc.metro.service.StopCommandService
import java.util.function.Function

/**
 * Rendering helper for stop commands.
 */
internal class StopCommandView(
    private val plugin: Metro,
    private val stopManager: StopManager,
    private val lineManager: LineManager,
    private val guard: CommandGuard,
    private val displayService: CommandDisplayService,
) {

    fun showHelp(sender: CommandSender, page: Int?) {
        val lang = plugin.languageManager
        val helpPage =
            displayService.helpPage(
                Function { key -> lang.getMessage(key) },
                "stop.help_header",
                HELP_KEYS,
                page,
            )
        sender.sendMessage(helpPage.header())
        for (helpLine in helpPage.lines()) {
            sender.sendMessage(helpLine)
        }
    }

    fun listStops(player: Player, stops: List<Stop>, page: Int?) {
        if (stops.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("stop.list_empty"))
            return
        }

        val stopPage = displayService.paginate(stops, page)
        player.sendMessage(
            displayService.pageHeader(plugin.languageManager.getMessage("stop.list_header"), stopPage),
        )

        val startIndex = (stopPage.page() - 1) * stopPage.pageSize()
        val items = stopPage.items()
        for (index in items.indices) {
            val stop = items[index]
            val displayIndex = startIndex + index + 1
            val message =
                TextComponent(
                    plugin.languageManager.getMessage(
                        "stop.list_prefix",
                        LanguageManager.put(LanguageManager.args(), "index", displayIndex.toString()),
                    ),
                )
            message.addExtra(createTeleportComponent(stop))
            val suffixText =
                plugin.languageManager.getMessage(
                    "stop.list_suffix",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stop.id),
                )
            message.addExtra(TextComponent(" $suffixText"))
            player.spigot().sendMessage(message)
        }
    }

    fun listTransfers(player: Player, stop: Stop) {
        val transferIds = stopManager.getTransferableLines(stop.id)
        if (transferIds.isEmpty()) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.transfers_empty",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
                ),
            )
            return
        }
        player.sendMessage(
            plugin.languageManager.getMessage(
                "stop.transfers_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
            ),
        )
        for (lineId in transferIds) {
            val line = lineManager.getLine(lineId)
            if (line == null) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.transfers_invalid",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                    ),
                )
            } else {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "stop.transfers_format",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                    ),
                )
            }
        }
    }

    fun listTitles(player: Player, stop: Stop) {
        var hasAny = false
        player.sendMessage(
            plugin.languageManager.getMessage(
                "stop.listtitles_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
            ),
        )
        for (type in StopCommandService.TITLE_TYPES) {
            val values = stop.getCustomTitle(type)
            if (values.isNullOrEmpty()) {
                continue
            }
            hasAny = true
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.listtitles_type",
                    LanguageManager.put(LanguageManager.args(), "title_type", type),
                ),
            )
            for ((titleKey, titleValue) in values) {
                val args = LanguageManager.args()
                LanguageManager.put(args, "title_key", titleKey)
                LanguageManager.put(args, "title_value", titleValue)
                player.sendMessage(plugin.languageManager.getMessage("stop.listtitles_item", args))
            }
        }
        if (!hasAny) {
            player.sendMessage(plugin.languageManager.getMessage("stop.listtitles_empty"))
        }
    }

    fun sendInfo(player: Player, stop: Stop) {
        val lang = plugin.languageManager
        player.sendMessage(
            lang.getMessage("stop.info_header", LanguageManager.put(LanguageManager.args(), "stop_name", stop.name)),
        )
        player.sendMessage(
            lang.getMessage("stop.info_name", LanguageManager.put(LanguageManager.args(), "stop_name", stop.name)),
        )
        player.sendMessage(
            lang.getMessage("stop.info_id", LanguageManager.put(LanguageManager.args(), "stop_id", stop.id)),
        )
        player.sendMessage(
            lang.getMessage(
                "stop.info_corner1",
                LanguageManager.put(LanguageManager.args(), "corner1", locationText(stop.corner1)),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "stop.info_corner2",
                LanguageManager.put(LanguageManager.args(), "corner2", locationText(stop.corner2)),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "stop.info_stoppoint",
                LanguageManager.put(LanguageManager.args(), "stoppoint", locationText(stop.stopPointLocation)),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "stop.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", guard.formatOwner(stop.owner)),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "stop.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins", guard.formatAdmins(stop.admins)),
            ),
        )

        val linkedLines =
            if (stop.linkedLineIds.isEmpty()) {
                lang.getMessage("ownership.none")
            } else {
                stop.linkedLineIds.joinToString(", ")
            }
        player.sendMessage(
            lang.getMessage(
                "stop.info_linked_lines",
                LanguageManager.put(LanguageManager.args(), "lines", linkedLines),
            ),
        )

        sendTransferInfo(player, stop)
        sendParentLineInfo(player, stop)
    }

    private fun sendTransferInfo(player: Player, stop: Stop) {
        val lang = plugin.languageManager
        val transferIds = stopManager.getTransferableLines(stop.id)
        if (transferIds.isEmpty()) {
            player.sendMessage(lang.getMessage("stop.info_no_transfers"))
            return
        }
        player.sendMessage(lang.getMessage("stop.info_transfers_header"))
        for (transferId in transferIds) {
            val transferLine = lineManager.getLine(transferId)
            if (transferLine == null) {
                player.sendMessage(
                    lang.getMessage(
                        "stop.info_transfer_item_invalid",
                        LanguageManager.put(LanguageManager.args(), "line_id", transferId),
                    ),
                )
            } else {
                val args = LanguageManager.args()
                LanguageManager.put(args, "line_id", transferLine.id)
                LanguageManager.put(args, "line_name", transferLine.name)
                player.sendMessage(lang.getMessage("stop.info_transfer_item", args))
            }
        }
    }

    private fun sendParentLineInfo(player: Player, stop: Stop) {
        val lang = plugin.languageManager
        val parentLines = lineManager.getLinesForStop(stop.id)
        if (parentLines.isEmpty()) {
            player.sendMessage(lang.getMessage("stop.info_no_parent_lines"))
            return
        }
        player.sendMessage(lang.getMessage("stop.info_parent_lines_header"))
        for (line in parentLines) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", line.id)
            LanguageManager.put(args, "line_name", line.name)
            player.sendMessage(lang.getMessage("stop.info_parent_line_item", args))
        }
    }

    private fun createTeleportComponent(stop: Stop): TextComponent {
        val stopComponent = TextComponent(stop.name)
        if (stop.stopPointLocation != null) {
            stopComponent.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/metro stop tp " + stop.id)
            val hoverText =
                plugin.languageManager.getMessage(
                    "command.teleport_to",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.name),
                )
            stopComponent.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hoverText))
        }
        return stopComponent
    }

    private fun locationText(location: Location?): String {
        val world = location?.world ?: return plugin.languageManager.getMessage("ownership.none")
        return world.name + " " + location.blockX + "," + location.blockY + "," + location.blockZ
    }

    private companion object {
        val HELP_KEYS =
            listOf(
                "stop.help_create",
                "stop.help_delete",
                "stop.help_list",
                "stop.help_setcorners",
                "stop.help_setpoint",
                "stop.help_addtransfer",
                "stop.help_deltransfer",
                "stop.help_listtransfers",
                "stop.help_settitle",
                "stop.help_deltitle",
                "stop.help_listtitles",
                "stop.help_rename",
                "stop.help_info",
                "stop.help_tp",
                "stop.help_trust",
                "stop.help_untrust",
                "stop.help_owner",
                "stop.help_link",
            )
    }
}
