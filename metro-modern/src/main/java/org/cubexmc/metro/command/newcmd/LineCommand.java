package org.cubexmc.metro.command.newcmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LineCommand {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;

    public LineCommand(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    @CommandMethod("m|metro line|l")
    @CommandDescription("Show Line Help Menu")
    public void help(CommandSender sender) {
        showHelp(sender, 1);
    }

    @CommandMethod("m|metro line|l help [page]")
    @CommandDescription("Show Line Help Menu Page")
    public void helpPage(CommandSender sender, @Argument("page") Integer page) {
        showHelp(sender, page == null ? 1 : page);
    }

    private void showHelp(CommandSender sender, int page) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        java.util.List<String> helpList = java.util.Arrays.asList(
                lang.getMessage("line.help_create"),
                lang.getMessage("line.help_delete"),
                lang.getMessage("line.help_list"),
                lang.getMessage("line.help_setcolor"),
                lang.getMessage("line.help_setterminus"),
                lang.getMessage("line.help_setmaxspeed"),
                lang.getMessage("line.help_addstop"),
                lang.getMessage("line.help_delstop"),
                lang.getMessage("line.help_stops"),
                lang.getMessage("line.help_rename"),
                lang.getMessage("line.help_info"),
                lang.getMessage("line.help_trust"),
                lang.getMessage("line.help_untrust"),
                lang.getMessage("line.help_owner"),
                lang.getMessage("line.help_clonereverse"),
                lang.getMessage("line.help_setprice")
        );

        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) helpList.size() / pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        sender.sendMessage(lang.getMessage("line.help_header") + " §e(" + page + "/" + totalPages + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, helpList.size());
        for (int i = start; i < end; i++) {
            sender.sendMessage(helpList.get(i));
        }
    }

    @CommandMethod("m|metro line|l list")
    @CommandDescription("List all metro lines")
    public void list(CommandSender sender) {
        Collection<Line> lines = lineManager.getAllLines();
        if (lines.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.list_empty"));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.list_header"));
            for (Line line : lines) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("line.list_item_format",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                "line_name", line.getName()), "line_id", line.getId())));
            }
        }
    }

    @CommandMethod("m|metro line|l create <id> <name>")
    @CommandDescription("Create a new metro line")
    public void create(Player player, @Argument("id") String id, @Greedy @Argument("name") String name) {
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
            return;
        }

        if (lineManager.createLine(id, name, player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.create_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.create_exists",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        }
    }

    @CommandMethod("m|metro line|l delete <id>")
    @CommandDescription("Delete a metro line")
    public void delete(Player player, @Argument(value = "id", suggestions = "lineIds") String id) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            String ownerName = line.getOwner() == null ? "Server" : line.getOwner().toString();
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", ownerName), "admins", "none")));
            return;
        }

        if (lineManager.deleteLine(id)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_fail"));
        }
    }

    @CommandMethod("m|metro line|l rename <id> <name>")
    @CommandDescription("Rename a metro line")
    public void rename(Player player, @Argument(value = "id", suggestions = "lineIds") String id, @Greedy @Argument("name") String name) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        if (lineManager.setLineName(id, name)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", id), "new_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
        }
    }

    @CommandMethod("m|metro line|l setcolor <id> <color>")
    @CommandDescription("Set the color of a metro line")
    public void setColor(Player player, @Argument(value = "id", suggestions = "lineIds") String id, @Argument("color") String color) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        if (lineManager.setLineColor(id, color)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "line_name", line.getName()), "color", color)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_fail"));
        }
    }

    @CommandMethod("m|metro line|l setterminus <id> <terminus>")
    @CommandDescription("Set terminus name for a line")
    public void setTerminus(Player player, @Argument(value = "id", suggestions = "lineIds") String id, @Greedy @Argument("terminus") String terminus) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        if (lineManager.setLineTerminusName(id, terminus)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "terminus_name", terminus)));
        }
    }

    @CommandMethod("m|metro line|l setmaxspeed <id> <speed>")
    @CommandDescription("Set max speed for a line")
    public void setMaxSpeed(Player player, @Argument(value = "id", suggestions = "lineIds") String id, @Argument("speed") double speed) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }
        if (speed <= 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
            return;
        }
        if (lineManager.setLineMaxSpeed(id, speed)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "max_speed", String.valueOf(speed))));
        }
    }

    @CommandMethod("m|metro line|l addstop <lineId> <stopId> [index]")
    @CommandDescription("Add a stop to a line")
    public void addStop(Player player,
                        @Argument(value = "lineId", suggestions = "lineIds") String lineId,
                        @Argument(value = "stopId", suggestions = "stopIds") String stopId,
                        @Argument("index") Integer index) {
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        Stop stop = stopManager.getStop(stopId);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            return;
        }

        if (!OwnershipUtil.canModifyLineStops(player, line, stop)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_link",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "owner", formatOwner(stop.getOwner())), "line_id", lineId)));
            return;
        }

        String stopWorld = stop.getWorldName();
        String lineWorld = line.getWorldName();
        if (stopWorld == null || stopWorld.isBlank()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_stop_no_world",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            return;
        }
        if (lineWorld != null && !lineWorld.equals(stopWorld)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_world_mismatch",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", lineId), "line_world", lineWorld), "stop_world", stopWorld)));
            return;
        }

        int targetIndex = index == null ? -1 : index;
        if (line.isCircular() && (targetIndex < 0 || targetIndex >= line.getOrderedStopIds().size())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_circular_invalid_index"));
            return;
        }

        if (lineManager.addStopToLine(lineId, stopId, targetIndex)) {
            if (lineWorld == null) {
                lineManager.setLineWorldName(lineId, stopWorld);
            }
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_id", line.getId())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
        }
    }

    @CommandMethod("m|metro line|l delstop <lineId> <stopId>")
    @CommandDescription("Remove a stop from a line")
    public void delStop(Player player, @Argument(value = "lineId", suggestions = "lineIds") String lineId, @Argument(value = "stopId", suggestions = "stopIds") String stopId) {
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        if (lineManager.delStopFromLine(lineId, stopId)) {
            if (line.getOrderedStopIds().isEmpty()) {
                lineManager.setLineWorldName(lineId, null);
            }
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_id", line.getId())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
        }
    }

    @CommandMethod("m|metro line|l stops <id>")
    @CommandDescription("List all stops in line")
    public void stops(Player player, @Argument(value = "id", suggestions = "lineIds") String id) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }

        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_empty",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
            return;
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_header"));
        for (int i = 0; i < stopIds.size(); i++) {
            String stopId = stopIds.get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_invalid_stop",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1)), "stop_id", stopId)));
                continue;
            }

            TextComponent row = new TextComponent(plugin.getLanguageManager().getMessage(
                    "line.stops_list_prefix",
                    LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1))));
            row.addExtra(createTeleportComponent(stop));

            String status = "";
            if (i == 0) {
                status = plugin.getLanguageManager().getMessage("line.stops_status_start");
            } else if (i == stopIds.size() - 1) {
                status = plugin.getLanguageManager().getMessage("line.stops_status_end");
            }
            String suffix = plugin.getLanguageManager().getMessage("line.stops_list_suffix",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId), "status", status));
            row.addExtra(new TextComponent(suffix));
            player.spigot().sendMessage(row);
        }
    }

    @CommandMethod("m|metro line|l info <id>")
    @CommandDescription("Show line details")
    public void info(Player player, @Argument(value = "id", suggestions = "lineIds") String id) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }

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
                        line.getTerminusName().isBlank() ? plugin.getLanguageManager().getMessage("line.info_default") : line.getTerminusName())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_max_speed",
                LanguageManager.put(LanguageManager.args(), "max_speed", String.valueOf(line.getMaxSpeed()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", formatOwner(line.getOwner()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins", formatAdmins(line.getAdmins()))));

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
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1)), "stop_id", stopId)));
                continue;
            }
            player.sendMessage(plugin.getLanguageManager().getMessage("line.info_stops_item",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "index", String.valueOf(i + 1)), "stop_id", stopId), "stop_name", stop.getName())));
        }
    }

    @CommandMethod("m|metro line|l trust <id> <playerName>")
    @CommandDescription("Grant line admin")
    public void trust(Player player,
                      @Argument(value = "id", suggestions = "lineIds") String id,
                      @Argument("playerName") String playerName) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (line.getAdmins().contains(target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_exists",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (lineManager.addLineAdmin(id, target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "player", playerName)));
        }
    }

    @CommandMethod("m|metro line|l untrust <id> <playerName>")
    @CommandDescription("Revoke line admin")
    public void untrust(Player player,
                        @Argument(value = "id", suggestions = "lineIds") String id,
                        @Argument("playerName") String playerName) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (lineManager.removeLineAdmin(id, target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "player", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_fail",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        }
    }

    @CommandMethod("m|metro line|l owner <id> <playerName>")
    @CommandDescription("Transfer line ownership")
    public void owner(Player player,
                      @Argument(value = "id", suggestions = "lineIds") String id,
                      @Argument("playerName") String playerName) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (line.getOwner() != null && !line.getOwner().equals(player.getUniqueId()) && !OwnershipUtil.hasAdminBypass(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_owner"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (lineManager.setLineOwner(id, target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "owner", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        }
    }

    @CommandMethod("m|metro line|l clonereverse <sourceId> <newId>")
    @CommandDescription("Clone a line and its stops in reverse order")
    public void cloneReverse(Player player, @Argument(value = "sourceId", suggestions = "lineIds") String sourceId, @Argument("newId") String newId) {
        cloneReverseWithSuffix(player, sourceId, newId, "_rev");
    }

    @CommandMethod("m|metro line|l clonereverse <sourceId> <newId> <stopIdSuffix>")
    @CommandDescription("Clone a line and its stops in reverse order with custom suffix")
    public void cloneReverseWithSuffix(Player player, @Argument(value = "sourceId", suggestions = "lineIds") String sourceId, @Argument("newId") String newId, @Argument("stopIdSuffix") String stopIdSuffix) {
        Line sourceLine = lineManager.getLine(sourceId);
        if (sourceLine == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", sourceId)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, sourceLine)) {
            String ownerName = sourceLine.getOwner() == null ? "Server" : sourceLine.getOwner().toString();
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", sourceLine.getId()), "owner", ownerName), "admins", "none")));
            return;
        }
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
            return;
        }

        if (lineManager.cloneReverseLine(sourceId, newId, stopIdSuffix, player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_success",
                    LanguageManager.put(LanguageManager.args(), "new_line_id", newId)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_fail"));
        }
    }

    @CommandMethod("m|metro line|l setprice <id> <price>")
    @CommandDescription("Set the ticket price for a metro line")
    public void setPrice(Player player, @Argument(value = "id", suggestions = "lineIds") String id, @Argument("price") double price) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())), "admins", formatAdmins(line.getAdmins()))));
            return;
        }

        if (price < 0) {
            player.sendMessage(ChatColor.RED + "Price cannot be negative.");
            return;
        }

        if (lineManager.setLineTicketPrice(id, price)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_name", line.getName()), "price", String.valueOf(price))));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
        }
    }

    private String formatOwner(UUID ownerId) {
        if (ownerId == null) {
            return plugin.getLanguageManager().getMessage("ownership.server");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        return owner.getName() == null ? ownerId.toString() : owner.getName();
    }

    private String formatAdmins(Set<UUID> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        String text = adminIds.stream().map(this::formatOwner).collect(Collectors.joining(", "));
        return text.isBlank() ? plugin.getLanguageManager().getMessage("ownership.none") : text;
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
