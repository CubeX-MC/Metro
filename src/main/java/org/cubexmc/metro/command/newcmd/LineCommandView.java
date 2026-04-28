package org.cubexmc.metro.command.newcmd;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.RouteRecorder;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.CommandDisplayService;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * Rendering helper for line commands.
 */
final class LineCommandView {

    private static final List<String> HELP_KEYS = List.of(
            "line.help_create",
            "line.help_delete",
            "line.help_list",
            "line.help_setcolor",
            "line.help_setterminus",
            "line.help_setmaxspeed",
            "line.help_addstop",
            "line.help_delstop",
            "line.help_stops",
            "line.help_rename",
            "line.help_info",
            "line.help_trust",
            "line.help_untrust",
            "line.help_owner",
            "line.help_clonereverse",
            "line.help_setprice",
            "line.help_recordroute",
            "line.help_clearroute",
            "line.help_routeinfo",
            "line.help_protect"
    );

    private final Metro plugin;
    private final StopManager stopManager;
    private final CommandGuard guard;
    private final CommandDisplayService displayService;

    LineCommandView(Metro plugin, StopManager stopManager, CommandGuard guard,
                    CommandDisplayService displayService) {
        this.plugin = plugin;
        this.stopManager = stopManager;
        this.guard = guard;
        this.displayService = displayService;
    }

    void showHelp(CommandSender sender, Integer page) {
        LanguageManager lang = plugin.getLanguageManager();
        CommandDisplayService.HelpPage helpPage = displayService.helpPage(lang::getMessage,
                "line.help_header", HELP_KEYS, page);
        sender.sendMessage(helpPage.header());
        for (String helpLine : helpPage.lines()) {
            sender.sendMessage(helpLine);
        }
    }

    void listLines(CommandSender sender, List<Line> lines, Integer page) {
        if (lines.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.list_empty"));
            return;
        }

        CommandDisplayService.Page<Line> linePage = displayService.paginate(lines, page);
        sender.sendMessage(displayService.pageHeader(
                plugin.getLanguageManager().getMessage("line.list_header"), linePage));
        for (Line line : linePage.items()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.list_item_format",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_name", line.getName()), "line_id", line.getId())));
        }
    }

    void sendStops(Player player, Line line, Integer page) {
        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_empty",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
            return;
        }

        CommandDisplayService.Page<String> stopPage = displayService.paginate(stopIds, page);
        player.sendMessage(displayService.pageHeader(
                plugin.getLanguageManager().getMessage("line.stops_list_header"), stopPage));
        int startIndex = (stopPage.page() - 1) * stopPage.pageSize();
        for (int i = 0; i < stopPage.items().size(); i++) {
            int displayIndex = startIndex + i + 1;
            String stopId = stopPage.items().get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_invalid_stop",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                "index", String.valueOf(displayIndex)), "stop_id", stopId)));
                continue;
            }

            TextComponent row = new TextComponent(plugin.getLanguageManager().getMessage(
                    "line.stops_list_prefix",
                    LanguageManager.put(LanguageManager.args(), "index", String.valueOf(displayIndex))));
            row.addExtra(createTeleportComponent(stop));

            String status = "";
            if (displayIndex == 1) {
                status = plugin.getLanguageManager().getMessage("line.stops_status_start");
            } else if (displayIndex == stopIds.size()) {
                status = plugin.getLanguageManager().getMessage("line.stops_status_end");
            }
            String suffix = plugin.getLanguageManager().getMessage("line.stops_list_suffix",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
                            "status", status));
            row.addExtra(new TextComponent(suffix));
            player.spigot().sendMessage(row);
        }
    }

    void sendInfo(Player player, Line line) {
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_header",
                LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_id",
                LanguageManager.put(LanguageManager.args(), "line_id", line.getId())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_name",
                LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_color",
                LanguageManager.put(LanguageManager.args(), "color", line.getColor())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_terminus",
                LanguageManager.put(LanguageManager.args(), "terminus_name",
                        line.getTerminusName().isBlank()
                                ? plugin.getLanguageManager().getMessage("line.info_default")
                                : line.getTerminusName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_max_speed",
                LanguageManager.put(LanguageManager.args(), "max_speed", String.valueOf(line.getMaxSpeed()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", guard.formatOwner(line.getOwner()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins", guard.formatAdmins(line.getAdmins()))));

        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.info_no_stops"));
            return;
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_stops_header"));
        for (int i = 0; i < stopIds.size(); i++) {
            String stopId = stopIds.get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("line.info_stops_item_invalid",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                "index", String.valueOf(i + 1)), "stop_id", stopId)));
                continue;
            }
            player.sendMessage(plugin.getLanguageManager().getMessage("line.info_stops_item",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "index", String.valueOf(i + 1)), "stop_id", stopId),
                            "stop_name", stop.getName())));
        }
    }

    void sendRouteInfo(Player player, Line line) {
        RouteRecorder recorder = plugin.getRouteRecorder();
        player.sendMessage(msg("line.routeinfo_header",
                "line_name", line.getName(),
                "line_id", line.getId()));
        player.sendMessage(msg("line.routeinfo_saved_points",
                "point_count", line.getRoutePoints().size()));
        sendProtectionStatus(player, line);
        if (recorder.isRecording(line.getId())) {
            UUID cartId = recorder.getRecordingCartId(line.getId());
            player.sendMessage(msg("line.routeinfo_recording",
                    "state", msg("line.routeinfo_recording_active")));
            player.sendMessage(msg("line.routeinfo_buffered_points",
                    "point_count", recorder.getActivePointCount(line.getId())));
            player.sendMessage(msg("line.routeinfo_bound_cart",
                    "cart_id", cartId == null ? msg("line.routeinfo_waiting_cart") : cartId.toString()));
        } else {
            player.sendMessage(msg("line.routeinfo_recording",
                    "state", msg("line.routeinfo_recording_inactive")));
        }
    }

    void sendProtectionStatus(Player player, Line line) {
        int protectedBlocks = plugin.getRailProtectionManager() == null
                ? 0
                : plugin.getRailProtectionManager().getProtectedBlockCount(line.getId());
        player.sendMessage(msg("line.protect_status",
                "state", msg(line.isRailProtected() ? "line.protect_state_enabled" : "line.protect_state_disabled")));
        player.sendMessage(msg("line.protect_blocks",
                "count", protectedBlocks));
        if (line.isRailProtected() && protectedBlocks == 0) {
            player.sendMessage(msg("line.protect_no_blocks"));
        }
    }

    String msg(String key, Object... replacements) {
        if (replacements.length == 0) {
            return plugin.getLanguageManager().getMessage(key);
        }
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, String.valueOf(replacements[i]), replacements[i + 1]);
        }
        return plugin.getLanguageManager().getMessage(key, args);
    }

    private TextComponent createTeleportComponent(Stop stop) {
        TextComponent stopComponent = new TextComponent(stop.getName());
        if (stop.getStopPointLocation() != null) {
            stopComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/metro stop tp " + stop.getId()));
            String hoverText = plugin.getLanguageManager().getMessage(
                    "command.teleport_to",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()));
            stopComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
        }
        return stopComponent;
    }
}
