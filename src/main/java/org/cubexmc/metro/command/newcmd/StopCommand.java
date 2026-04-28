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
import org.cubexmc.metro.service.CommandDisplayService;
import org.cubexmc.metro.service.StopCommandService;
import org.cubexmc.metro.util.OwnershipUtil;

public class StopCommand {

    private final Metro plugin;
    private final StopManager stopManager;
    private final CommandGuard guard;
    private final StopCommandService stopService;
    private final StopCommandView view;

    public StopCommand(Metro plugin, StopManager stopManager, LineManager lineManager) {
        this.plugin = plugin;
        this.stopManager = stopManager;
        this.guard = new CommandGuard(plugin, lineManager, stopManager);
        this.stopService = new StopCommandService(stopManager);
        this.view = new StopCommandView(plugin, stopManager, lineManager, guard, new CommandDisplayService());
    }

    @Command("m|metro stop|s")
    @CommandDescription("Show Stop Help Menu")
    public void help(CommandSender sender) {
        view.showHelp(sender, 1);
    }

    @Command("m|metro stop|s help [page]")
    @CommandDescription("Show Stop Help Menu Page")
    public void helpPage(CommandSender sender, @Argument("page") Integer page) {
        view.showHelp(sender, page);
    }

    @Command("m|metro stop|s list [page]")
    @CommandDescription("List all metro stops")
    public void list(Player player, @Argument("page") Integer page) {
        view.listStops(player, stopService.listStops(), page);
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
        view.listTransfers(player, stop);
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
        view.listTitles(player, stop);
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
        view.sendInfo(player, stop);
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
        StopCommandService.WriteStatus status = stopService.addAdmin(stop, target.getUniqueId());
        if (status == StopCommandService.WriteStatus.EXISTS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.trust_exists",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        } else if (status == StopCommandService.WriteStatus.SUCCESS) {
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
        if (stopService.removeAdmin(stop, target.getUniqueId()) == StopCommandService.WriteStatus.SUCCESS) {
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
        if (stopService.setOwner(stop, target.getUniqueId()) == StopCommandService.WriteStatus.SUCCESS) {
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

}
