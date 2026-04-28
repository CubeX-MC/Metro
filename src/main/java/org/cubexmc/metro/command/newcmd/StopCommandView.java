package org.cubexmc.metro.command.newcmd;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.CommandDisplayService;
import org.cubexmc.metro.service.StopCommandService;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * Rendering helper for stop commands.
 */
final class StopCommandView {

    private static final List<String> HELP_KEYS = List.of(
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
            "stop.help_link"
    );

    private final Metro plugin;
    private final StopManager stopManager;
    private final LineManager lineManager;
    private final CommandGuard guard;
    private final CommandDisplayService displayService;

    StopCommandView(Metro plugin, StopManager stopManager, LineManager lineManager, CommandGuard guard,
                    CommandDisplayService displayService) {
        this.plugin = plugin;
        this.stopManager = stopManager;
        this.lineManager = lineManager;
        this.guard = guard;
        this.displayService = displayService;
    }

    void showHelp(CommandSender sender, Integer page) {
        LanguageManager lang = plugin.getLanguageManager();
        CommandDisplayService.HelpPage helpPage = displayService.helpPage(lang::getMessage,
                "stop.help_header", HELP_KEYS, page);
        sender.sendMessage(helpPage.header());
        for (String helpLine : helpPage.lines()) {
            sender.sendMessage(helpLine);
        }
    }

    void listStops(Player player, List<Stop> stops, Integer page) {
        if (stops.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.list_empty"));
            return;
        }

        CommandDisplayService.Page<Stop> stopPage = displayService.paginate(stops, page);
        player.sendMessage(displayService.pageHeader(
                plugin.getLanguageManager().getMessage("stop.list_header"), stopPage));

        int startIndex = (stopPage.page() - 1) * stopPage.pageSize();
        for (int i = 0; i < stopPage.items().size(); i++) {
            Stop stop = stopPage.items().get(i);
            int displayIndex = startIndex + i + 1;
            TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage(
                    "stop.list_prefix",
                    LanguageManager.put(LanguageManager.args(), "index", String.valueOf(displayIndex))));
            message.addExtra(createTeleportComponent(stop));
            String suffixText = plugin.getLanguageManager().getMessage("stop.list_suffix",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId()));
            message.addExtra(new TextComponent(" " + suffixText));
            player.spigot().sendMessage(message);
        }
    }

    void listTransfers(Player player, Stop stop) {
        List<String> transferIds = stopManager.getTransferableLines(stop.getId());
        if (transferIds.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_empty",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        for (String lineId : transferIds) {
            Line line = lineManager.getLine(lineId);
            if (line == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_invalid",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_format",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
            }
        }
    }

    void listTitles(Player player, Stop stop) {
        boolean hasAny = false;
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        for (String type : StopCommandService.TITLE_TYPES) {
            Map<String, String> values = stop.getCustomTitle(type);
            if (values == null || values.isEmpty()) {
                continue;
            }
            hasAny = true;
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_type",
                    LanguageManager.put(LanguageManager.args(), "title_type", type)));
            for (Map.Entry<String, String> entry : values.entrySet()) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_item",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                "title_key", entry.getKey()), "title_value", entry.getValue())));
            }
        }
        if (!hasAny) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_empty"));
        }
    }

    void sendInfo(Player player, Stop stop) {
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_name",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_id",
                LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId())));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner1",
                LanguageManager.put(LanguageManager.args(), "corner1", locationText(stop.getCorner1()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner2",
                LanguageManager.put(LanguageManager.args(), "corner2", locationText(stop.getCorner2()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_stoppoint",
                LanguageManager.put(LanguageManager.args(), "stoppoint", locationText(stop.getStopPointLocation()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", guard.formatOwner(stop.getOwner()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins", guard.formatAdmins(stop.getAdmins()))));

        String linkedLines = stop.getLinkedLineIds().isEmpty()
                ? plugin.getLanguageManager().getMessage("ownership.none")
                : String.join(", ", stop.getLinkedLineIds());
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_linked_lines",
                LanguageManager.put(LanguageManager.args(), "lines", linkedLines)));

        List<String> transferIds = stopManager.getTransferableLines(stop.getId());
        if (transferIds.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_no_transfers"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_transfers_header"));
            for (String transferId : transferIds) {
                Line transferLine = lineManager.getLine(transferId);
                if (transferLine == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_transfer_item_invalid",
                            LanguageManager.put(LanguageManager.args(), "line_id", transferId)));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_transfer_item",
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "line_id", transferLine.getId()), "line_name", transferLine.getName())));
                }
            }
        }

        List<Line> parentLines = lineManager.getLinesForStop(stop.getId());
        if (parentLines.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_no_parent_lines"));
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_parent_lines_header"));
        for (Line line : parentLines) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_parent_line_item",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "line_name", line.getName())));
        }
    }

    private TextComponent createTeleportComponent(Stop stop) {
        TextComponent stopComponent = new TextComponent(stop.getName());
        if (stop.getStopPointLocation() != null) {
            stopComponent
                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/metro stop tp " + stop.getId()));
            String hoverText = plugin.getLanguageManager().getMessage(
                    "command.teleport_to",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()));
            stopComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
        }
        return stopComponent;
    }

    private String locationText(Location location) {
        if (location == null || location.getWorld() == null) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        return location.getWorld().getName() + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }
}
