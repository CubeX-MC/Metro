package org.cubexmc.metro.command.newcmd;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotation.specifier.Greedy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.StopCommandService;
import org.cubexmc.metro.util.OwnershipUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StopCommand {

    private final Metro plugin;
    private final StopManager stopManager;
    private final LineManager lineManager;
    private final CommandGuard guard;
    private final StopCommandService stopService;

    public StopCommand(Metro plugin, StopManager stopManager, LineManager lineManager) {
        this.plugin = plugin;
        this.stopManager = stopManager;
        this.lineManager = lineManager;
        this.guard = new CommandGuard(plugin, lineManager, stopManager);
        this.stopService = new StopCommandService(stopManager);
    }

    @Command("m|metro stop|s")
    @CommandDescription("Show Stop Help Menu")
    public void help(CommandSender sender) {
        showHelp(sender, 1);
    }

    @Command("m|metro stop|s help [page]")
    @CommandDescription("Show Stop Help Menu Page")
    public void helpPage(CommandSender sender, @Argument("page") Integer page) {
        showHelp(sender, page == null ? 1 : page);
    }

    private void showHelp(CommandSender sender, int page) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        java.util.List<String> helpList = java.util.Arrays.asList(
                lang.getMessage("stop.help_create"),
                lang.getMessage("stop.help_delete"),
                lang.getMessage("stop.help_list"),
                lang.getMessage("stop.help_setcorners"),
                lang.getMessage("stop.help_setpoint"),
                lang.getMessage("stop.help_addtransfer"),
                lang.getMessage("stop.help_deltransfer"),
                lang.getMessage("stop.help_listtransfers"),
                lang.getMessage("stop.help_settitle"),
                lang.getMessage("stop.help_deltitle"),
                lang.getMessage("stop.help_listtitles"),
                lang.getMessage("stop.help_rename"),
                lang.getMessage("stop.help_info"),
                lang.getMessage("stop.help_tp"),
                lang.getMessage("stop.help_trust"),
                lang.getMessage("stop.help_untrust"),
                lang.getMessage("stop.help_owner"),
                lang.getMessage("stop.help_link")
        );

        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) helpList.size() / pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        sender.sendMessage(lang.getMessage("stop.help_header") + " §e(" + page + "/" + totalPages + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, helpList.size());
        for (int i = start; i < end; i++) {
            sender.sendMessage(helpList.get(i));
        }
    }

    @Command("m|metro stop|s list")
    @CommandDescription("List all metro stops")
    public void list(Player player) {
        List<Stop> stops = new ArrayList<>(stopManager.getAllStopIds().stream()
                .map(stopManager::getStop)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        if (stops.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.list_empty"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.list_header"));
            stops.sort(Comparator.comparing(Stop::getId));

            for (int i = 0; i < stops.size(); i++) {
                Stop stop = stops.get(i);
                TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage(
                        "stop.list_prefix",
                        LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1))));
                message.addExtra(createTeleportComponent(stop));
                String suffixText = plugin.getLanguageManager().getMessage("stop.list_suffix",
                        LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId()));
                message.addExtra(new TextComponent(" " + suffixText));
                player.spigot().sendMessage(message);
            }
        }
    }

    @Command("m|metro stop|s create <id> <name>")
    @CommandDescription("Create a new metro stop")
    public void create(Player player, @Argument("id") String id, @Greedy @Argument("name") String name) {
        if (!OwnershipUtil.canCreateStop(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_create"));
            return;
        }

        SelectionManager selectionManager = plugin.getSelectionManager();
        if (!selectionManager.isSelectionComplete(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.selection_not_complete",
                    LanguageManager.put(LanguageManager.args(), "tool",
                            plugin.getConfigFacade().getSelectionToolName())));
            return;
        }

        Location corner1 = selectionManager.getCorner1(player);
        Location corner2 = selectionManager.getCorner2(player);

        StopCommandService.CreateStopResult result = stopService.createStop(id, name, corner1, corner2, player.getUniqueId());
        switch (result.status()) {
            case SUCCESS -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.create_success",
                    LanguageManager.put(LanguageManager.args(), "stop_name", name)));
            case INVALID_ID -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.id_invalid",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
            case EXISTS -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_exists",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.create_fail",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        }
    }

    @Command("m|metro stop|s delete <id>")
    @CommandDescription("Delete a metro stop")
    public void delete(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }

        if (stopService.deleteStop(id) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_fail"));
        }
    }

    @Command("m|metro stop|s tp <id>")
    @CommandDescription("Teleport to a metro stop")
    public void tp(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireStop(player, id);
        if (stop == null) {
            return;
        }
        Location location = stop.getStopPointLocation();
        if (location == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_no_point"));
            return;
        }

        org.cubexmc.metro.util.SchedulerUtil.teleportEntity(player, location).thenAccept(success -> {
            if (success) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
            }
        });
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

    @Command("m|metro stop|s setcorners <id>")
    @CommandDescription("Set stop corners from current selection")
    public void setCorners(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        SelectionManager selectionManager = plugin.getSelectionManager();
        if (!selectionManager.isSelectionComplete(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.selection_not_complete",
                    LanguageManager.put(LanguageManager.args(), "tool", plugin.getConfigFacade().getSelectionToolName())));
            return;
        }
        if (stopService.setCorners(id, selectionManager.getCorner1(player), selectionManager.getCorner2(player)) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.setcorners_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        }
    }

    @Command("m|metro stop|s setpoint [id] [yaw]")
    @CommandDescription("Set stop point at player position")
    public void setPoint(Player player,
                         @Argument(value = "id", suggestions = "stopIds") String id,
                         @Argument("yaw") Float yaw) {
        Stop stop;
        if (id == null) {
            stop = stopManager.getStopContainingLocation(player.getLocation());
            if (stop == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_in_area",
                        LanguageManager.put(LanguageManager.args(), "stop_name", "unknown")));
                return;
            }
            id = stop.getId();
        } else {
            stop = guard.requireStop(player, id);
        }
        
        if (stop == null) {
            return;
        }
        stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }

        StopCommandService.SetPointResult result = stopService.setPoint(id, stop, player.getLocation(), yaw);
        switch (result.status()) {
            case SUCCESS -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", id), "yaw", String.format("%.1f", result.yaw()))));
            case NOT_RAIL -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_rail"));
            case NOT_IN_STOP -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_in_area",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_fail"));
        }
    }

    @Command("m|metro stop|s addtransfer <id> <lineId>")
    @CommandDescription("Add transferable line to stop")
    public void addTransfer(Player player,
                            @Argument(value = "id", suggestions = "stopIds") String id,
                            @Argument(value = "lineId", suggestions = "lineIds") String lineId) {
        Stop stop = guard.requireManageableStop(player, id);
        Line line = guard.requireLine(player, lineId);
        if (stop == null) {
            return;
        }
        if (line == null) {
            return;
        }
        if (stopService.addTransferLine(id, lineId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.addtransfer_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "transfer_line_name", line.getName())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.addtransfer_exists",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "transfer_line_name", line.getName())));
        }
    }

    @Command("m|metro stop|s deltransfer <id> <lineId>")
    @CommandDescription("Remove transferable line from stop")
    public void delTransfer(Player player,
                            @Argument(value = "id", suggestions = "stopIds") String id,
                            @Argument(value = "lineId", suggestions = "lineIds") String lineId) {
        Stop stop = guard.requireManageableStop(player, id);
        Line line = guard.requireLine(player, lineId);
        if (stop == null) {
            return;
        }
        if (line == null) {
            return;
        }
        if (stopService.removeTransferLine(id, lineId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltransfer_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "transfer_line_name", line.getName())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltransfer_not_exists",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "transfer_line_name", line.getName())));
        }
    }

    @Command("m|metro stop|s listtransfers <id>")
    @CommandDescription("List transferable lines for stop")
    public void listTransfers(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireStop(player, id);
        if (stop == null) {
            return;
        }
        List<String> transferIds = stopManager.getTransferableLines(id);
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

    @Command("m|metro stop|s settitle <id> <titleType> <titleKey> <titleValue>")
    @CommandDescription("Set custom title entry for stop")
    public void setTitle(Player player,
                         @Argument(value = "id", suggestions = "stopIds") String id,
                         @Argument("titleType") String titleType,
                         @Argument("titleKey") String titleKey,
                         @Greedy @Argument("titleValue") String titleValue) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        StopCommandService.WriteStatus status = stopService.setCustomTitle(stop, titleType, titleKey, titleValue);
        if (status == StopCommandService.WriteStatus.INVALID_TITLE_TYPE) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_type_invalid",
                    LanguageManager.put(LanguageManager.args(), "title_type", titleType)));
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
            return;
        }
        if (status == StopCommandService.WriteStatus.INVALID_TITLE_KEY) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_key_invalid",
                    LanguageManager.put(LanguageManager.args(), "title_key", titleKey)));
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_keys"));
            return;
        }
        if (status == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.settitle_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_name", stop.getName()), "title_type", titleType), "title_key", titleKey), "title_value", titleValue)));
        }
    }

    @Command("m|metro stop|s deltitle <id> <titleType> [titleKey]")
    @CommandDescription("Delete custom title entry for stop")
    public void delTitle(Player player,
                         @Argument(value = "id", suggestions = "stopIds") String id,
                         @Argument("titleType") String titleType,
                         @Argument("titleKey") String titleKey) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        if (!StopCommandService.TITLE_TYPES.contains(titleType)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_type_invalid",
                    LanguageManager.put(LanguageManager.args(), "title_type", titleType)));
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
            return;
        }

        if (titleKey == null) {
            StopCommandService.WriteStatus status = stopService.removeCustomTitleType(stop, titleType);
            if (status == StopCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_type_success",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "title_type", titleType)));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_type_not_found",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()), "title_type", titleType)));
            }
            return;
        }

        if (!StopCommandService.TITLE_KEYS.contains(titleKey)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_key_invalid",
                    LanguageManager.put(LanguageManager.args(), "title_key", titleKey)));
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_keys"));
            return;
        }
        StopCommandService.WriteStatus status = stopService.removeCustomTitleKey(stop, titleType, titleKey);
        if (status == StopCommandService.WriteStatus.NOT_FOUND) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_not_found",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_name", stop.getName()), "title_type", titleType), "title_key", titleKey)));
            return;
        }
        if (status == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_name", stop.getName()), "title_type", titleType), "title_key", titleKey)));
        }
    }

    @Command("m|metro stop|s listtitles <id>")
    @CommandDescription("List custom title config")
    public void listTitles(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireStop(player, id);
        if (stop == null) {
            return;
        }
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
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "title_key", entry.getKey()), "title_value", entry.getValue())));
            }
        }
        if (!hasAny) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_empty"));
        }
    }

    @Command("m|metro stop|s rename <id> <name>")
    @CommandDescription("Rename stop display name")
    public void rename(Player player, @Argument(value = "id", suggestions = "stopIds") String id, @Greedy @Argument("name") String name) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        String oldName = stop.getName();
        if (stopService.renameStop(id, name) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "old_name", oldName), "new_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_fail"));
        }
    }

    @Command("m|metro stop|s info <id>")
    @CommandDescription("Show stop details")
    public void info(Player player, @Argument(value = "id", suggestions = "stopIds") String id) {
        Stop stop = guard.requireStop(player, id);
        if (stop == null) {
            return;
        }
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

        List<String> transferIds = stopManager.getTransferableLines(id);
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
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", transferLine.getId()), "line_name", transferLine.getName())));
                }
            }
        }

        List<Line> parentLines = lineManager.getLinesForStop(id);
        if (parentLines.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_no_parent_lines"));
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_parent_lines_header"));
        for (Line line : parentLines) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_parent_line_item",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", line.getId()), "line_name", line.getName())));
        }
    }

    @Command("m|metro stop|s trust <id> <playerName>")
    @CommandDescription("Grant stop admin")
    public void trust(Player player,
                      @Argument(value = "id", suggestions = "stopIds") String id,
                      @Argument("playerName") String playerName) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (stop.getAdmins().contains(target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.trust_exists",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (stopService.addAdmin(id, target.getUniqueId()) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.trust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", id), "player", playerName)));
        }
    }

    @Command("m|metro stop|s untrust <id> <playerName>")
    @CommandDescription("Revoke stop admin")
    public void untrust(Player player,
                        @Argument(value = "id", suggestions = "stopIds") String id,
                        @Argument("playerName") String playerName) {
        Stop stop = guard.requireManageableStop(player, id);
        if (stop == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (stopService.removeAdmin(id, target.getUniqueId()) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.untrust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", id), "player", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.untrust_fail",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        }
    }

    @Command("m|metro stop|s owner <id> <playerName>")
    @CommandDescription("Transfer stop ownership")
    public void owner(Player player,
                      @Argument(value = "id", suggestions = "stopIds") String id,
                      @Argument("playerName") String playerName) {
        Stop stop = guard.requireStop(player, id);
        if (stop == null) {
            return;
        }
        if (!guard.requireStopOwner(player, stop)) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (stopService.setOwner(id, target.getUniqueId()) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.owner_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", id), "owner", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        }
    }

    @Command("m|metro stop|s link <action> <stopId> <lineId>")
    @CommandDescription("Allow or deny linking a line to a stop")
    public void link(Player player,
                     @Argument("action") String action,
                     @Argument(value = "stopId", suggestions = "stopIds") String stopId,
                     @Argument(value = "lineId", suggestions = "lineIds") String lineId) {
        Stop stop = guard.requireManageableStop(player, stopId);
        if (stop == null) {
            return;
        }
        StopCommandService.WriteStatus status = stopService.updateLineLink(action, stopId, lineId);
        if ("allow".equalsIgnoreCase(action)) {
            if (status == StopCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_allow_success",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId), "line_id", lineId)));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_allow_exists",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId), "line_id", lineId)));
            }
            return;
        }
        if ("deny".equalsIgnoreCase(action)) {
            if (status == StopCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_deny_success",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId), "line_id", lineId)));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_deny_missing",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId), "line_id", lineId)));
            }
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_link"));
    }

    private String locationText(Location location) {
        if (location == null || location.getWorld() == null) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
