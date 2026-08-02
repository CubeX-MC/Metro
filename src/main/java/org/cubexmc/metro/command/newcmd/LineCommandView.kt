package org.cubexmc.metro.command.newcmd

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.RailProtectionManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.PriceRule
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.CommandDisplayService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.function.Function
import kotlin.math.roundToInt

/**
 * Rendering helper for line commands.
 */
internal class LineCommandView(
    private val plugin: Metro,
    private val stopManager: StopManager,
    private val guard: CommandGuard,
    private val displayService: CommandDisplayService,
) {

    fun showHelp(sender: CommandSender, page: Int?) {
        val lang = plugin.languageManager
        val helpPage =
            displayService.helpPage(
                Function { key -> lang.getMessage(key) },
                "line.help_header",
                HELP_KEYS,
                page,
            )
        sender.sendMessage(helpPage.header())
        for (helpLine in helpPage.lines()) {
            sender.sendMessage(helpLine)
        }
    }

    fun listLines(sender: CommandSender, lines: List<Line>, page: Int?) {
        if (lines.isEmpty()) {
            sender.sendMessage(plugin.languageManager.getMessage("line.list_empty"))
            return
        }

        val linePage = displayService.paginate(lines, page)
        sender.sendMessage(
            displayService.pageHeader(plugin.languageManager.getMessage("line.list_header"), linePage),
        )
        for (line in linePage.items()) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_name", line.name)
            LanguageManager.put(args, "line_id", line.id)
            sender.sendMessage(plugin.languageManager.getMessage("line.list_item_format", args))
        }
    }

    fun sendStops(player: Player, line: Line, page: Int?) {
        val stopIds = line.orderedStopIds
        if (stopIds.isEmpty()) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.stops_list_empty",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                ),
            )
            return
        }

        val stopPage = displayService.paginate(stopIds, page)
        player.sendMessage(
            displayService.pageHeader(plugin.languageManager.getMessage("line.stops_list_header"), stopPage),
        )
        val startIndex = (stopPage.page() - 1) * stopPage.pageSize()
        val items = stopPage.items()
        for (index in items.indices) {
            val displayIndex = startIndex + index + 1
            val stopId = items[index]
            val stop = stopManager.getStop(stopId)
            if (stop == null) {
                val args = LanguageManager.args()
                LanguageManager.put(args, "index", displayIndex.toString())
                LanguageManager.put(args, "stop_id", stopId)
                player.sendMessage(plugin.languageManager.getMessage("line.stops_list_invalid_stop", args))
                continue
            }

            val row =
                TextComponent(
                    plugin.languageManager.getMessage(
                        "line.stops_list_prefix",
                        LanguageManager.put(LanguageManager.args(), "index", displayIndex.toString()),
                    ),
                )
            row.addExtra(createTeleportComponent(stop))

            val status =
                when (displayIndex) {
                    1 -> plugin.languageManager.getMessage("line.stops_status_start")
                    stopIds.size -> plugin.languageManager.getMessage("line.stops_status_end")
                    else -> ""
                }
            val suffixArgs = LanguageManager.args()
            LanguageManager.put(suffixArgs, "stop_id", stopId)
            LanguageManager.put(suffixArgs, "status", status)
            row.addExtra(TextComponent(plugin.languageManager.getMessage("line.stops_list_suffix", suffixArgs)))
            player.spigot().sendMessage(row)
        }
    }

    fun sendPortals(player: Player, line: Line, page: Int?) {
        val portalIds = line.portalIds
        if (portalIds.isEmpty()) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.portals_list_empty",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.name),
                ),
            )
            return
        }

        val portalPage = displayService.paginate(portalIds, page)
        player.sendMessage(
            displayService.pageHeader(plugin.languageManager.getMessage("line.portals_list_header"), portalPage),
        )
        for (portalId in portalPage.items()) {
            val portal = plugin.portalManager?.getPortal(portalId)
            if (portal == null) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.portals_list_invalid_portal",
                        LanguageManager.put(LanguageManager.args(), "portal_id", portalId),
                    ),
                )
                continue
            }
            val args = LanguageManager.args()
            LanguageManager.put(args, "portal_id", portal.id)
            LanguageManager.put(args, "world", portal.worldName)
            LanguageManager.put(args, "x", portal.x.toString())
            LanguageManager.put(args, "y", portal.y.toString())
            LanguageManager.put(args, "z", portal.z.toString())
            player.sendMessage(plugin.languageManager.getMessage("line.portals_list_item", args))
        }
    }

    fun sendInfo(player: Player, line: Line) {
        val lang = plugin.languageManager
        player.sendMessage(
            lang.getMessage("line.info_header", LanguageManager.put(LanguageManager.args(), "line_name", line.name)),
        )
        player.sendMessage(
            lang.getMessage("line.info_id", LanguageManager.put(LanguageManager.args(), "line_id", line.id)),
        )
        player.sendMessage(
            lang.getMessage("line.info_name", LanguageManager.put(LanguageManager.args(), "line_name", line.name)),
        )
        player.sendMessage(
            lang.getMessage("line.info_color", LanguageManager.put(LanguageManager.args(), "color", line.color)),
        )
        val terminusName =
            if (line.terminusName.isBlank()) lang.getMessage("line.info_default") else line.terminusName
        player.sendMessage(
            lang.getMessage(
                "line.info_terminus",
                LanguageManager.put(LanguageManager.args(), "terminus_name", terminusName),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.info_max_speed",
                LanguageManager.put(LanguageManager.args(), "max_speed", line.getMaxSpeed().toString()),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", guard.formatOwner(line.owner)),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins", guard.formatAdmins(line.admins)),
            ),
        )

        val stopIds = line.orderedStopIds
        if (stopIds.isEmpty()) {
            player.sendMessage(lang.getMessage("line.info_no_stops"))
            return
        }

        player.sendMessage(lang.getMessage("line.info_stops_header"))
        for (index in stopIds.indices) {
            val stopId = stopIds[index]
            val stop = stopManager.getStop(stopId)
            val args = LanguageManager.args()
            LanguageManager.put(args, "index", (index + 1).toString())
            LanguageManager.put(args, "stop_id", stopId)
            if (stop == null) {
                player.sendMessage(lang.getMessage("line.info_stops_item_invalid", args))
                continue
            }
            LanguageManager.put(args, "stop_name", stop.name)
            player.sendMessage(lang.getMessage("line.info_stops_item", args))
        }
    }

    fun sendRouteInfo(player: Player, line: Line) {
        val recorder = plugin.routeRecorder
        player.sendMessage(msg("line.routeinfo_header", "line_name", line.name, "line_id", line.id))
        player.sendMessage(msg("line.routeinfo_saved_points", "point_count", line.routePoints.size))
        player.sendMessage(msg("line.routeinfo_last_recorded_at", "time", formatRouteRecordedAt(line)))
        player.sendMessage(msg("line.routeinfo_last_recorded_by", "player", formatUuidAsPlayer(line.routeRecordedBy)))
        player.sendMessage(msg("line.routeinfo_last_cart", "cart_id", formatUuid(line.routeRecordedCartId)))
        sendProtectionStatus(player, line)
        if (recorder.isRecording(line.id)) {
            val cartId = recorder.getRecordingCartId(line.id)
            player.sendMessage(msg("line.routeinfo_recording", "state", msg("line.routeinfo_recording_active")))
            player.sendMessage(
                msg("line.routeinfo_recording_by", "player", formatUuidAsPlayer(recorder.getRecordingPlayerId(line.id))),
            )
            player.sendMessage(
                msg("line.routeinfo_buffered_points", "point_count", recorder.getActivePointCount(line.id)),
            )
            player.sendMessage(
                msg("line.routeinfo_bound_cart", "cart_id", cartId?.toString() ?: msg("line.routeinfo_waiting_cart")),
            )
        } else {
            player.sendMessage(msg("line.routeinfo_recording", "state", msg("line.routeinfo_recording_inactive")))
        }
    }

    fun sendProtectionStatus(player: Player, line: Line) {
        val protectionManager = plugin.railProtectionManager
        val protectedBlocks = protectionManager?.getProtectedBlockCount(line.id) ?: 0
        val stats =
            protectionManager?.getProtectionIndexStats(line.id)
                ?: RailProtectionManager.ProtectionIndexStats.empty()
        val stateKey = if (line.isRailProtected) "line.protect_state_enabled" else "line.protect_state_disabled"
        player.sendMessage(msg("line.protect_status", "state", msg(stateKey)))
        player.sendMessage(msg("line.protect_blocks", "count", protectedBlocks))
        if (line.isRailProtected) {
            player.sendMessage(
                msg("line.protect_index_samples", "sampled", stats.sampledPoints(), "skipped", stats.skippedTotal()),
            )
            if (stats.skippedWorldMismatch() > 0) {
                player.sendMessage(msg("line.protect_skipped_world_mismatch", "count", stats.skippedWorldMismatch()))
            }
            if (stats.skippedMissingWorld() > 0) {
                player.sendMessage(msg("line.protect_skipped_missing_world", "count", stats.skippedMissingWorld()))
            }
            if (stats.skippedNoRail() > 0) {
                player.sendMessage(msg("line.protect_skipped_no_rail", "count", stats.skippedNoRail()))
            }
        }
        if (line.isRailProtected && protectedBlocks == 0) {
            player.sendMessage(msg("line.protect_no_blocks"))
        }
    }

    fun msg(key: String, vararg replacements: Any?): String {
        if (replacements.isEmpty()) {
            return plugin.languageManager.getMessage(key)
        }
        val args = LanguageManager.args()
        var index = 0
        while (index < replacements.size - 1) {
            LanguageManager.put(args, replacements[index].toString(), replacements[index + 1])
            index += 2
        }
        return plugin.languageManager.getMessage(key, args)
    }

    private fun formatRouteRecordedAt(line: Line): String {
        val recordedAt = line.routeRecordedAtEpochMillis ?: return msg("line.routeinfo_never_recorded")
        return ROUTE_TIME_FORMATTER.format(Instant.ofEpochMilli(recordedAt))
    }

    private fun formatUuidAsPlayer(playerId: UUID?): String {
        if (playerId == null) {
            return msg("line.routeinfo_unknown")
        }
        val name = Bukkit.getOfflinePlayer(playerId).name
        return if (name.isNullOrBlank()) playerId.toString() else name
    }

    private fun formatUuid(value: UUID?): String = value?.toString() ?: msg("line.routeinfo_unknown")

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

    fun sendPriceInfo(player: Player, line: Line) {
        val lang = plugin.languageManager
        player.sendMessage(
            lang.getMessage(
                "line.priceinfo_header",
                LanguageManager.put(LanguageManager.args(), "line_name", line.name),
            ),
        )

        val rule = line.priceRule
        if (rule == null) {
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_mode",
                    LanguageManager.put(LanguageManager.args(), "mode", lang.getMessage("line.priceinfo_mode_flat")),
                ),
            )
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_base",
                    LanguageManager.put(LanguageManager.args(), "base", line.ticketPrice.toString()),
                ),
            )
            return
        }

        player.sendMessage(
            lang.getMessage(
                "line.priceinfo_mode",
                LanguageManager.put(LanguageManager.args(), "mode", rule.getMode().name),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.priceinfo_base",
                LanguageManager.put(LanguageManager.args(), "base", rule.getBasePrice().toString()),
            ),
        )

        if (rule.getMode() == PriceRule.PricingMode.DISTANCE) {
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_per_block",
                    LanguageManager.put(LanguageManager.args(), "per_block", rule.getPerBlockRate().toString()),
                ),
            )
        } else if (rule.getMode() == PriceRule.PricingMode.INTERVAL) {
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_per_interval",
                    LanguageManager.put(LanguageManager.args(), "per_interval", rule.getPerIntervalRate().toString()),
                ),
            )
        }

        if (rule.getMaxPrice() > 0.0) {
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_max",
                    LanguageManager.put(LanguageManager.args(), "max", rule.getMaxPrice().toString()),
                ),
            )
        }

        sendDiscountInfo(player, rule)
    }

    private fun sendDiscountInfo(player: Player, rule: PriceRule) {
        val lang = plugin.languageManager
        val discounts = rule.getTimeDiscounts()
        if (discounts.isEmpty()) {
            return
        }

        player.sendMessage(lang.getMessage("line.priceinfo_discounts"))
        for (discount in discounts) {
            val percent = ((1.0 - discount.getDiscountMultiplier()) * PERCENT_SCALE).roundToInt()
            val args = LanguageManager.args()
            LanguageManager.put(args, "start_time", formatTicksToTime(discount.getStartTick()))
            LanguageManager.put(args, "end_time", formatTicksToTime(discount.getEndTick()))
            LanguageManager.put(args, "percent", percent.toString())
            player.sendMessage(lang.getMessage("line.priceinfo_discount_item", args))
        }

        val world = player.world
        val activeMultiplier = rule.getActiveDiscountMultiplier(world.time)
        if (activeMultiplier < 1.0) {
            val activePercent = ((1.0 - activeMultiplier) * PERCENT_SCALE).roundToInt()
            player.sendMessage(
                lang.getMessage(
                    "line.priceinfo_active_discount",
                    LanguageManager.put(LanguageManager.args(), "percent", activePercent.toString()),
                ),
            )
        }
    }

    private fun formatTicksToTime(ticks: Int): String {
        val totalMinutes = ((ticks / TICKS_PER_DAY) * HOURS_PER_DAY * MINUTES_PER_HOUR).roundToInt()
        val hours = (totalMinutes / MINUTES_PER_HOUR) % HOURS_PER_DAY
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return String.format("%02d:%02d", hours, minutes)
    }

    private companion object {
        val ROUTE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

        const val PERCENT_SCALE = 100
        const val TICKS_PER_DAY = 24000.0
        const val HOURS_PER_DAY = 24
        const val MINUTES_PER_HOUR = 60

        val HELP_KEYS =
            listOf(
                "line.help_create",
                "line.help_delete",
                "line.help_list",
                "line.help_setcolor",
                "line.help_setterminus",
                "line.help_setmaxspeed",
                "line.help_addstop",
                "line.help_delstop",
                "line.help_stops",
                "line.help_addportal",
                "line.help_delportal",
                "line.help_portals",
                "line.help_rename",
                "line.help_info",
                "line.help_trust",
                "line.help_untrust",
                "line.help_owner",
                "line.help_clonereverse",
                "line.help_setprice",
                "line.help_priceinfo",
                "line.help_setstatus",
                "line.help_recordroute",
                "line.help_clearroute",
                "line.help_routeinfo",
                "line.help_protect",
            )
    }
}
