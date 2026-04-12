package org.cubexmc.metro.command.newcmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.specifier.Greedy;
import org.bukkit.ChatColor;
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
                    LanguageManager.put(LanguageManager.args(), "line_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_exists",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        }
    }

    @CommandMethod("m|metro line|l delete <id>")
    @CommandDescription("Delete a metro line")
    public void delete(Player player, @Argument("id") String id) {
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
    public void rename(Player player, @Argument("id") String id, @Greedy @Argument("name") String name) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage("No permission."); // Placeholder
            return;
        }

        String oldName = line.getName();
        if (lineManager.setLineName(id, name)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "old_name", oldName), "new_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
        }
    }

    @CommandMethod("m|metro line|l setcolor <id> <color>")
    @CommandDescription("Set the color of a metro line")
    public void setColor(Player player, @Argument("id") String id, @Argument("color") String color) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage("No permission.");
            return;
        }

        if (lineManager.setLineColor(id, color)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_name", line.getName()), "color", color)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_fail"));
        }
    }

    @CommandMethod("m|metro line|l addstop <lineId> <stopId>")
    @CommandDescription("Add a stop to a line")
    public void addStop(Player player, @Argument("lineId") String lineId, @Argument("stopId") String stopId) {
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage("No permission.");
            return;
        }

        Stop stop = stopManager.getStop(stopId);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            return;
        }

        if (lineManager.addStopToLine(lineId, stopId, -1)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_name", line.getName())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
        }
    }

    @CommandMethod("m|metro line|l delstop <lineId> <stopId>")
    @CommandDescription("Remove a stop from a line")
    public void delStop(Player player, @Argument("lineId") String lineId, @Argument("stopId") String stopId) {
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage("No permission.");
            return;
        }

        if (lineManager.delStopFromLine(lineId, stopId)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_name", line.getName())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
        }
    }

    @CommandMethod("m|metro line|l clonereverse <sourceId> <newId>")
    @CommandDescription("Clone a line and its stops in reverse order")
    public void cloneReverse(Player player, @Argument("sourceId") String sourceId, @Argument("newId") String newId) {
        cloneReverseWithSuffix(player, sourceId, newId, "_rev");
    }

    @CommandMethod("m|metro line|l clonereverse <sourceId> <newId> <stopIdSuffix>")
    @CommandDescription("Clone a line and its stops in reverse order with custom suffix")
    public void cloneReverseWithSuffix(Player player, @Argument("sourceId") String sourceId, @Argument("newId") String newId, @Argument("stopIdSuffix") String stopIdSuffix) {
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
    public void setPrice(Player player, @Argument("id") String id, @Argument("price") double price) {
        Line line = lineManager.getLine(id);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage("No permission.");
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
}
