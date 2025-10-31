package org.cubexmc.metro.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.OwnershipUtil;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * 处理Metro插件的管理员命令
 */
public class MetroAdminCommand implements CommandExecutor {
    
    private final Metro plugin;
    
    public MetroAdminCommand(Metro plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("plugin.players_only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }
        
        LineManager lineManager = plugin.getLineManager();
        StopManager stopManager = plugin.getStopManager();
        
        String mainCommand = args[0].toLowerCase();
        
        // 新的命令格式，按照README中的结构处理
        if (mainCommand.equals("line")) {
            // 线路管理命令
            if (args.length < 2) {
                sendLineHelpMessage(player);
                return true;
            }
            
            String subCommand = args[1].toLowerCase();
            String lineId;
            String stopId;
            
            // 处理线路相关子命令
            switch (subCommand) {
                case "create":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_create"));
                        return true;
                    }
                    if (!OwnershipUtil.canCreateLine(player)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
                        return true;
                    }
                    lineId = args[2];
                    String lineName = args[3];
                    if (lineManager.createLine(lineId, lineName, player.getUniqueId())) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.create_success", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.create_exists", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                case "delete":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_delete"));
                        return true;
                    }
                    lineId = args[2];
                    Line lineToDelete = lineManager.getLine(lineId);
                    if (lineToDelete == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, lineToDelete)) {
                        sendLinePermissionDenied(player, lineToDelete);
                        return true;
                    }
                    if (lineManager.deleteLine(lineId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_fail", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                case "list":
                    Collection<Line> lines = lineManager.getAllLines();
                    if (lines.isEmpty()) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.list_empty"));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.list_header"));
                        for (Line line : lines) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.list_item_format", 
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                            "line_name", line.getName()), "line_id", line.getId())));
                        }
                    }
                    break;
                    
                case "setcolor":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_setcolor"));
                        return true;
                    }
                    lineId = args[2];
                    String color = args[3];
                    Line colorLine = lineManager.getLine(lineId);
                    if (colorLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, colorLine)) {
                        sendLinePermissionDenied(player, colorLine);
                        return true;
                    }
                    if (lineManager.setLineColor(lineId, color)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "line_id", lineId), "color", color)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_fail", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                case "setterminus":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_setterminus"));
                        return true;
                    }
                    lineId = args[2];
                    Line terminusLine = lineManager.getLine(lineId);
                    if (terminusLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, terminusLine)) {
                        sendLinePermissionDenied(player, terminusLine);
                        return true;
                    }
                    if (args.length == 3) {
                        // Clear terminus name
                        if (lineManager.setLineTerminusName(lineId, null)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "line_id", lineId), "terminus_name", "")));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        }
                        return true;
                    }
                    String terminusName = args[3];
                    if (lineManager.setLineTerminusName(lineId, terminusName)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "line_id", lineId), "terminus_name", terminusName)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                case "setmaxspeed":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_setmaxspeed"));
                        return true;
                    }
                    lineId = args[2];
                    Line maxSpeedLine = lineManager.getLine(lineId);
                    if (maxSpeedLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, maxSpeedLine)) {
                        sendLinePermissionDenied(player, maxSpeedLine);
                        return true;
                    }
                    try {
                        double maxSpeed = Double.parseDouble(args[3]);
                        if (maxSpeed < 0) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
                            return true;
                        }
                        if (lineManager.setLineMaxSpeed(lineId, maxSpeed)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_success", 
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                            "line_id", lineId), "max_speed", String.valueOf(maxSpeed))));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
                                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
                    }
                    break;
                    
                case "rename":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_rename"));
                        return true;
                    }
                    
                    lineId = args[2];
                    String newName = args[3];
                    Line renameLine = lineManager.getLine(lineId);
                    if (renameLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, renameLine)) {
                        sendLinePermissionDenied(player, renameLine);
                        return true;
                    }
                    if (lineManager.setLineName(lineId, newName)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "line_id", lineId), "new_name", newName)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
                    }
                    break;
                    
                case "addstop":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_addstop"));
                        return true;
                    }
                    
                    lineId = args[2];
                    stopId = args[3];

                    Line lineToAdd = lineManager.getLine(lineId);
                    if (lineToAdd == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, lineToAdd)) {
                        sendLinePermissionDenied(player, lineToAdd);
                        return true;
                    }
                    Stop stopToAdd = stopManager.getStop(stopId);
                    if (stopToAdd == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    if (!OwnershipUtil.canLinkStopToLine(player, lineToAdd, stopToAdd)) {
                        sendStopLinkDenied(player, lineToAdd, stopToAdd);
                        return true;
                    }
                    
                    int index = -1;
                    if (args.length > 4) {
                        try {
                            index = Integer.parseInt(args[4]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.index_format"));
                            return true;
                        }
                    }

                    if (lineToAdd.isCircular() && index != -1 && index >= lineToAdd.getOrderedStopIds().size() - 1) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_circular_invalid_index"));
                        return true;
                    }

                    boolean wasCircular = lineToAdd.isCircular();
                    
                    if (lineManager.addStopToLine(lineId, stopId, index)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_id", stopId), "line_id", lineId)));
                        
                        Line updatedLine = lineManager.getLine(lineId);
                        if (!wasCircular && updatedLine.isCircular()) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.made_circular",
                                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        }
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
                    }
                    break;
                    
                case "delstop":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_delstop"));
                        return true;
                    }
                    
                    lineId = args[2];
                    stopId = args[3];

                    Line lineToDel = lineManager.getLine(lineId);
                    if (lineToDel == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, lineToDel)) {
                        sendLinePermissionDenied(player, lineToDel);
                        return true;
                    }
                    boolean wasCircularBeforeDelete = lineToDel.isCircular();
                    
                    if (lineManager.delStopFromLine(lineId, stopId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_id", stopId), "line_id", lineId)));

                        Line updatedLine = lineManager.getLine(lineId);
                        if (wasCircularBeforeDelete && !updatedLine.isCircular()) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.made_normal",
                                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        }
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
                    }
                    break;
                    
                case "stops":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_stops"));
                        return true;
                    }
                    
                    lineId = args[2];
                    Line line = lineManager.getLine(lineId);
                    
                    if (line == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    
                    List<String> stopIds = line.getOrderedStopIds();
                    
                    if (stopIds.isEmpty()) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_empty", 
                                LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
                        return true;
                    }
                    
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_header"));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            line.getColor() + line.getName() + " &f(" + line.getId() + ")"));
                    
                    for (int i = 0; i < stopIds.size(); i++) {
                        String currentStopId = stopIds.get(i);
                        Stop stop = stopManager.getStop(currentStopId);
                        if (stop != null) {
                            String status = "";
                            if (i == 0) status = plugin.getLanguageManager().getMessage("line.stops_status_start");
                            if (i == stopIds.size() - 1) status = plugin.getLanguageManager().getMessage("line.stops_status_end");
                            
                            TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage("line.stops_list_prefix", 
                                    LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i+1))));
                            message.addExtra(createTeleportComponent(stop));
                            String suffixText = plugin.getLanguageManager().getMessage("line.stops_list_suffix", 
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                            "stop_id", stop.getId()), "status", status));
                            message.addExtra(new TextComponent(" " + suffixText));
                            player.spigot().sendMessage(message);
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_invalid_stop",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                            "index", String.valueOf(i + 1)), "stop_id", currentStopId)));
                        }
                    }
                    break;

                case "info":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_info"));
                        return true;
                    }
                    lineId = args[2];
                    Line infoLine = lineManager.getLine(lineId);
                    if (infoLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.info_header",
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "line_id", infoLine.getId()), "line_name", infoLine.getName())));
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.info_owner",
                            LanguageManager.put(LanguageManager.args(), "owner", resolvePlayerName(infoLine.getOwner()))));
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.info_admins",
                            LanguageManager.put(LanguageManager.args(), "admins",
                                    formatAdminNames(infoLine.getAdmins(), infoLine.getOwner()))));
                    break;

                case "trust":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_trust"));
                        return true;
                    }
                    lineId = args[2];
                    String trustTargetName = args[3];
                    Line trustLine = lineManager.getLine(lineId);
                    if (trustLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, trustLine)) {
                        sendLinePermissionDenied(player, trustLine);
                        return true;
                    }
                    UUID trustTargetId = resolvePlayerUUID(trustTargetName);
                    if (trustTargetId == null) {
                        sendPlayerNotFound(player, trustTargetName);
                        return true;
                    }
                    if (!lineManager.addLineAdmin(lineId, trustTargetId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_exists",
                                LanguageManager.put(LanguageManager.args(), "player", trustTargetName)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "line_id", lineId), "player", resolvePlayerName(trustTargetId))));
                    }
                    break;

                case "untrust":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_untrust"));
                        return true;
                    }
                    lineId = args[2];
                    String untrustTargetName = args[3];
                    Line untrustLine = lineManager.getLine(lineId);
                    if (untrustLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageLine(player, untrustLine)) {
                        sendLinePermissionDenied(player, untrustLine);
                        return true;
                    }
                    UUID untrustTargetId = resolvePlayerUUID(untrustTargetName);
                    if (untrustTargetId == null) {
                        sendPlayerNotFound(player, untrustTargetName);
                        return true;
                    }
                    if (!lineManager.removeLineAdmin(lineId, untrustTargetId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_fail",
                                LanguageManager.put(LanguageManager.args(), "player", untrustTargetName)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "line_id", lineId), "player", resolvePlayerName(untrustTargetId))));
                    }
                    break;

                case "owner":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_owner"));
                        return true;
                    }
                    lineId = args[2];
                    String newOwnerName = args[3];
                    Line ownerLine = lineManager.getLine(lineId);
                    if (ownerLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                        return true;
                    }
                    UUID requesterId = player.getUniqueId();
                    if (!OwnershipUtil.hasAdminBypass(player)) {
                        if (ownerLine.getOwner() == null || !requesterId.equals(ownerLine.getOwner())) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_owner"));
                            return true;
                        }
                    }
                    UUID newOwnerId = resolvePlayerUUID(newOwnerName);
                    if (newOwnerId == null) {
                        sendPlayerNotFound(player, newOwnerName);
                        return true;
                    }
                    if (lineManager.setLineOwner(lineId, newOwnerId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "line_id", lineId), "owner", resolvePlayerName(newOwnerId))));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_fail",
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                default:
                    sendLineHelpMessage(player);
                    return true;
            }
        } else if (mainCommand.equals("stop")) {
            // 停靠区管理命令
            if (args.length < 2) {
                sendStopHelpMessage(player);
                return true;
            }
            
            String subCommand = args[1].toLowerCase();
            
            if ("tp".equals(subCommand)) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_tp"));
                    return true;
                }
                String stopId = args[2];
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }
                if (stop.getStopPointLocation() == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stoppoint_not_set", 
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                    return true;
                }
                player.teleportAsync(stop.getStopPointLocation()).thenAccept((success) -> {
                    if (success) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success", 
                                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                    } else {
                        player.sendMessage(ChatColor.RED + "Teleport failed. The destination might be unsafe.");
                    }
                });
                return true;
            }

            if ("info".equals(subCommand)) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_info"));
                    return true;
                }
                String stopId = args[2];
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }

                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_header", 
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_id", 
                        LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId())));
                
                TextComponent nameComponent = new TextComponent(plugin.getLanguageManager().getMessage("stop.info_name", 
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                nameComponent.addExtra(createTeleportComponent(stop));
                player.spigot().sendMessage(nameComponent);

                Location corner1 = stop.getCorner1();
                Location corner2 = stop.getCorner2();
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner1", 
                        LanguageManager.put(LanguageManager.args(), "corner1", corner1 != null ? LocationUtil.locationToString(corner1) : "Not set")));
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner2", 
                        LanguageManager.put(LanguageManager.args(), "corner2", corner2 != null ? LocationUtil.locationToString(corner2) : "Not set")));

                Location stopPoint = stop.getStopPointLocation();
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_stoppoint", 
                        LanguageManager.put(LanguageManager.args(), "stoppoint", stopPoint != null ? LocationUtil.locationToString(stopPoint) : "Not set")));
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_owner",
                        LanguageManager.put(LanguageManager.args(), "owner", resolvePlayerName(stop.getOwner()))));
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_admins",
                        LanguageManager.put(LanguageManager.args(), "admins",
                                formatAdminNames(stop.getAdmins(), stop.getOwner()))));
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.info_linked_lines",
                        LanguageManager.put(LanguageManager.args(), "lines",
                                formatLineIds(stop.getLinkedLineIds()))));
                
                return true;
            }
            
            // 停靠区换乘管理命令
            if (subCommand.equals("addtransfer")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_addtransfer"));
                    return true;
                }
                
                String stopId = args[2];
                String transferLineId = args[3];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }
                
                Line transferLine = lineManager.getLine(transferLineId);
                if (transferLine == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found", 
                            LanguageManager.put(LanguageManager.args(), "line_id", transferLineId)));
                    return true;
                }
                
                if (stopManager.addTransferLine(stopId, transferLineId)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.addtransfer_success", 
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                    "transfer_line_name", transferLine.getName()), "stop_name", stop.getName())));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.addtransfer_exists", 
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                    "stop_name", stop.getName()), "transfer_line_name", transferLine.getName())));
                }
                return true;
            }
            
            if (subCommand.equals("deltransfer")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_deltransfer"));
                    return true;
                }
                
                String stopId = args[2];
                String transferLineId = args[3];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }
                
                Line transferLine = lineManager.getLine(transferLineId);
                if (transferLine == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found", 
                            LanguageManager.put(LanguageManager.args(), "line_id", transferLineId)));
                    return true;
                }
                
                if (stopManager.removeTransferLine(stopId, transferLineId)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltransfer_success", 
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                    "stop_name", stop.getName()), "transfer_line_name", transferLine.getName())));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltransfer_not_exists", 
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                    "stop_name", stop.getName()), "transfer_line_name", transferLine.getName())));
                }
                return true;
            }
            
            if (subCommand.equals("listtransfers")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_listtransfers"));
                    return true;
                }
                
                String stopId = args[2];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                
                List<String> transferLineIds = stopManager.getTransferableLines(stopId);
                if (transferLineIds.isEmpty()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_empty", 
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                    return true;
                }
                
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_header", 
                        LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                for (String id : transferLineIds) {
                    Line txLine = lineManager.getLine(id);
                    if (txLine != null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_format", 
                                LanguageManager.put(LanguageManager.args(), "line_name", txLine.getName())));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.transfers_invalid", 
                                LanguageManager.put(LanguageManager.args(), "line_id", id)));
                    }
                }
                return true;
            }
            
            if (subCommand.equals("settitle")) {
                if (args.length < 6) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_settitle"));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_keys"));
                    return true;
                }
                
                String stopId = args[2];
                String titleType = args[3];
                String key = args[4];
                String value = args[5];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                
                if (!isValidTitleType(titleType)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_type_invalid", 
                            LanguageManager.put(LanguageManager.args(), "title_type", titleType)));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
                    return true;
                }
                
                if (!isValidTitleKey(key)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_key_invalid", 
                            LanguageManager.put(LanguageManager.args(), "title_key", key)));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_keys"));
                    return true;
                }
                
                Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                if (titleConfig == null) {
                    titleConfig = new HashMap<>();
                }
                titleConfig.put(key, value);
                stop.setCustomTitle(titleType, titleConfig);
                
                stopManager.saveConfig();
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.settitle_success", 
                        LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(
                                LanguageManager.args(), "stop_name", stop.getName()), 
                                "title_type", titleType), "title_key", key), "title_value", value)));
                return true;
            }
            
            if (subCommand.equals("deltitle")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_deltitle"));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
                    return true;
                }
                
                String stopId = args[2];
                String titleType = args[3];
                String key = args.length > 4 ? args[4] : null;
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                
                if (!isValidTitleType(titleType)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_type_invalid", 
                            LanguageManager.put(LanguageManager.args(), "title_type", titleType)));
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.title_types"));
                    return true;
                }
                
                if (key == null) {
                    if (stop.removeCustomTitle(titleType)) {
                        stopManager.saveConfig();
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_type_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_name", stop.getName()), "title_type", titleType)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_type_not_found", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_name", stop.getName()), "title_type", titleType)));
                    }
                } else {
                    Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                    if (titleConfig != null && titleConfig.containsKey(key)) {
                        titleConfig.remove(key);
                        if (titleConfig.isEmpty()) {
                            stop.removeCustomTitle(titleType);
                        } else {
                            stop.setCustomTitle(titleType, titleConfig);
                        }
                        stopManager.saveConfig();
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_name", stop.getName()), "title_type", titleType), "title_key", key)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.deltitle_not_found", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_name", stop.getName()), "title_type", titleType), "title_key", key)));
                    }
                }
                return true;
            }
            
            if (subCommand.equals("listtitles")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_listtitles"));
                    return true;
                }
                
                String stopId = args[2];
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                
                player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_header"));
                boolean hasCustomTitles = false;
                
                String[] titleTypes = {"stop_continuous", "arrive_stop", "terminal_stop", "departure"};
                for (String titleType : titleTypes) {
                    Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                    if (titleConfig != null && !titleConfig.isEmpty()) {
                        hasCustomTitles = true;
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_type", 
                                LanguageManager.put(LanguageManager.args(), "title_type", titleType)));
                        for (Map.Entry<String, String> entry : titleConfig.entrySet()) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_item", 
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                            "title_key", entry.getKey()), "title_value", entry.getValue())));
                        }
                    }
                }
                
                if (!hasCustomTitles) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_empty"));
                }
                return true;
            }
            
            // 重命名停靠区命令
            if (subCommand.equals("rename")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_rename"));
                    return true;
                }
                
                String stopId = args[2];
                // 拼接剩余参数作为新名称
                StringBuilder newName = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) newName.append(" ");
                    newName.append(args[i]);
                }
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found", 
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    return true;
                }
                if (!OwnershipUtil.canManageStop(player, stop)) {
                    sendStopPermissionDenied(player, stop);
                    return true;
                }
                
                String oldName = stop.getName();
                if (stopManager.setStopName(stopId, newName.toString())) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_success", 
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                    "old_name", oldName), "new_name", newName.toString())));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_fail"));
                }
                return true;
            }
            
            switch (subCommand) {
                case "create":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_create"));
                        return true;
                    }
                    if (!OwnershipUtil.canCreateStop(player)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_create"));
                        return true;
                    }
                    
                    String stopId = args[2];
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        nameBuilder.append(args[i]).append(" ");
                    }
                    String stopName = nameBuilder.toString().trim();
                    
                    SelectionManager selectionManager = plugin.getSelectionManager();
                    if (!selectionManager.isSelectionComplete(player)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.selection_not_complete"));
                        return true;
                    }
                    Location corner1 = selectionManager.getCorner1(player);
                    Location corner2 = selectionManager.getCorner2(player);

                    Stop newStop = stopManager.createStop(stopId, stopName, corner1, corner2, player.getUniqueId());
                    if (newStop != null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.create_success", 
                                LanguageManager.put(LanguageManager.args(), "stop_name", stopName)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_exists", 
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    }
                    break;
                    
                case "setcorners":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_setcorners"));
                        return true;
                    }

                    stopId = args[2];
                    SelectionManager selectionManagerCorners = plugin.getSelectionManager();
                    if (!selectionManagerCorners.isSelectionComplete(player)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.selection_not_complete"));
                        return true;
                    }

                    Location corner1ToSet = selectionManagerCorners.getCorner1(player);
                    Location corner2ToSet = selectionManagerCorners.getCorner2(player);

                    Stop cornersStop = stopManager.getStop(stopId);
                    if (cornersStop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageStop(player, cornersStop)) {
                        sendStopPermissionDenied(player, cornersStop);
                        return true;
                    }

                    if (stopManager.setStopCorners(stopId, corner1ToSet, corner2ToSet)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.setcorners_success",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    }
                    break;

                case "delete":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_delete"));
                        return true;
                    }
                    
                    stopId = args[2];
                    Stop stopToDelete = stopManager.getStop(stopId);
                    if (stopToDelete == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageStop(player, stopToDelete)) {
                        sendStopPermissionDenied(player, stopToDelete);
                        return true;
                    }
                    if (stopManager.deleteStop(stopId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_success", 
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_fail"));
                    }
                    break;
                    
                case "list":
                    List<Stop> stops = new ArrayList<>(stopManager.getAllStopIds().stream()
                            .map(stopManager::getStop)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                    if (stops.isEmpty()) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.list_empty"));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.list_header"));
                        
                        // 按停靠区ID排序以保持一致性
                        stops.sort((s1, s2) -> s1.getId().compareTo(s2.getId()));
                        
                        for (int i = 0; i < stops.size(); i++) {
                            Stop stop = stops.get(i);
                            
                            TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage("stop.list_prefix", 
                                    LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i+1))));
                            message.addExtra(createTeleportComponent(stop));
                            String suffixText = plugin.getLanguageManager().getMessage("stop.list_suffix", 
                                    LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId()));
                            message.addExtra(new TextComponent(" " + suffixText));
                            player.spigot().sendMessage(message);
                        }
                    }
                    break;
                    
                case "setpoint":
                    // 检查玩家是否在铁轨上
                    Location location = player.getLocation();
                    if (!LocationUtil.isRail(location)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_rail"));
                        return true;
                    }
                    
                    // 自动获取玩家所在的停靠区
                    Stop stop = stopManager.getStopContainingLocation(location);
                    if (stop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.no_stop_found_at_location"));
                        return true;
                    }
                    if (!OwnershipUtil.canManageStop(player, stop)) {
                        sendStopPermissionDenied(player, stop);
                        return true;
                    }
                    
                    stopId = stop.getId();
                    float yaw = player.getLocation().getYaw();
                    
                    // 如果有参数，尝试读取朝向角度
                    if (args.length > 2) {
                        try {
                            yaw = Float.parseFloat(args[2]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_yaw_invalid"));
                            return true;
                        }
                    }
                    
                    if (stopManager.setStopPoint(stopId, location, yaw)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "stop_id", stopId), "yaw", String.valueOf(yaw))));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_fail"));
                    }
                    break;

                case "trust":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_trust"));
                        return true;
                    }
                    stopId = args[2];
                    String stopTrustTarget = args[3];
                    Stop trustStop = stopManager.getStop(stopId);
                    if (trustStop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageStop(player, trustStop)) {
                        sendStopPermissionDenied(player, trustStop);
                        return true;
                    }
                    UUID trustStopUuid = resolvePlayerUUID(stopTrustTarget);
                    if (trustStopUuid == null) {
                        sendPlayerNotFound(player, stopTrustTarget);
                        return true;
                    }
                    if (!stopManager.addStopAdmin(stopId, trustStopUuid)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.trust_exists",
                                LanguageManager.put(LanguageManager.args(), "player", stopTrustTarget)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.trust_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "stop_id", stopId), "player", resolvePlayerName(trustStopUuid))));
                    }
                    break;

                case "untrust":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_untrust"));
                        return true;
                    }
                    stopId = args[2];
                    String stopUntrustTarget = args[3];
                    Stop untrustStop = stopManager.getStop(stopId);
                    if (untrustStop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    if (!OwnershipUtil.canManageStop(player, untrustStop)) {
                        sendStopPermissionDenied(player, untrustStop);
                        return true;
                    }
                    UUID untrustStopUuid = resolvePlayerUUID(stopUntrustTarget);
                    if (untrustStopUuid == null) {
                        sendPlayerNotFound(player, stopUntrustTarget);
                        return true;
                    }
                    if (!stopManager.removeStopAdmin(stopId, untrustStopUuid)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.untrust_fail",
                                LanguageManager.put(LanguageManager.args(), "player", stopUntrustTarget)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.untrust_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "stop_id", stopId), "player", resolvePlayerName(untrustStopUuid))));
                    }
                    break;

                case "owner":
                    if (args.length < 4) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_owner"));
                        return true;
                    }
                    stopId = args[2];
                    String stopOwnerName = args[3];
                    Stop ownerStop = stopManager.getStop(stopId);
                    if (ownerStop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                    UUID stopRequester = player.getUniqueId();
                    if (!OwnershipUtil.hasAdminBypass(player)) {
                        if (ownerStop.getOwner() == null || !stopRequester.equals(ownerStop.getOwner())) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_owner"));
                            return true;
                        }
                    }
                    UUID newStopOwner = resolvePlayerUUID(stopOwnerName);
                    if (newStopOwner == null) {
                        sendPlayerNotFound(player, stopOwnerName);
                        return true;
                    }
                    if (stopManager.setStopOwner(stopId, newStopOwner)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.owner_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "stop_id", stopId), "owner", resolvePlayerName(newStopOwner))));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.owner_fail",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                    }
                    break;

                case "link":
                    if (args.length < 5) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_link"));
                        return true;
                    }
                    String action = args[2].toLowerCase();
                    stopId = args[3];
                    String targetLineId = args[4];
                    Stop linkStop = stopManager.getStop(stopId);
                    if (linkStop == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                                LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                        return true;
                    }
                if (!OwnershipUtil.canManageStop(player, linkStop)) {
                        sendStopPermissionDenied(player, linkStop);
                        return true;
                    }
                    Line targetLine = lineManager.getLine(targetLineId);
                    if (targetLine == null) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                                LanguageManager.put(LanguageManager.args(), "line_id", targetLineId)));
                        return true;
                    }
                    if ("allow".equals(action)) {
                        if (stopManager.allowLineLink(stopId, targetLineId)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_allow_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "stop_id", stopId), "line_id", targetLineId)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_allow_exists",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "stop_id", stopId), "line_id", targetLineId)));
                        }
                    } else if ("deny".equals(action)) {
                        if (stopManager.denyLineLink(stopId, targetLineId)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_deny_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "stop_id", stopId), "line_id", targetLineId)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.link_deny_missing",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "stop_id", stopId), "line_id", targetLineId)));
                        }
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_link"));
                    }
                    break;
                
                default:
                    sendStopHelpMessage(player);
                    break;
            }
        } else if (mainCommand.equals("reload")) {
            // 重新加载配置
            
            // 确保所有默认配置文件存在
            plugin.ensureDefaultConfigs();
            
            // 重新加载所有配置
            plugin.reloadConfig();
            lineManager.reload();
            stopManager.reload();
            
            // 重新加载语言文件
            plugin.getLanguageManager().loadLanguages();
            
            // 使用语言管理器发送消息
            player.sendMessage(plugin.getLanguageManager().getMessage("plugin.reload"));
        } else {
            // 未知命令
            player.sendMessage(plugin.getLanguageManager().getMessage("command.unknown"));
            sendHelpMessage(player);
        }
        
        return true;
    }
    
    /**
     * 发送帮助信息到玩家
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_header"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_line"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_stop"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_reload"));
    }
    
    /**
     * 发送线路管理帮助信息
     */
    private void sendLineHelpMessage(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_header"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_create"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_delete"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_list"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_info"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_rename"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_setcolor"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_setterminus"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_setmaxspeed"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_addstop"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_delstop"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_stops"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_trust"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_untrust"));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.help_owner"));
    }
    
    /**
     * 发送停靠区管理帮助信息
     */
    private void sendStopHelpMessage(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_header"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_create"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_delete"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_list"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_info"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_rename"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_setcorners"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_setpoint"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_tp"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_addtransfer"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_deltransfer"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_listtransfers"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_settitle"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_deltitle"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_listtitles"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_trust"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_untrust"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_owner"));
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.help_link"));
    }
    
    /**
     * 检查给定的title_type是否有效
     * @param titleType 要检查的字符串
     * @return 如果有效则为true
     * @author CubexX
     */
    private boolean isValidTitleType(String titleType) {
        return titleType.equals("stop_continuous") || 
               titleType.equals("arrive_stop") || 
               titleType.equals("terminal_stop") ||
               titleType.equals("departure");
    }
    
    /**
     * 检查给定的key是否有效
     * @param key 要检查的字符串
     * @return 如果有效则为true
     */
    private boolean isValidTitleKey(String key) {
        return key.equals("title") || 
               key.equals("subtitle") || 
               key.equals("actionbar");
    }

    /**
     * 创建一个可以点击传送的站点名称组件
     * @param stop 站点
     * @return TextComponent
     */
    private TextComponent createTeleportComponent(Stop stop) {
        TextComponent stopComponent = new TextComponent(stop.getName());
        if (stop.getStopPointLocation() != null) {
            stopComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/metro stop tp " + stop.getId()));
            stopComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(plugin.getLanguageManager().getMessage("command.teleport_to", 
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName()))).create()));
        }
        return stopComponent;
    }

    private void sendLinePermissionDenied(Player player, Line line) {
        String ownerName = resolvePlayerName(line.getOwner());
        String adminNames = formatAdminNames(line.getAdmins(), line.getOwner());
        player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "line_id", line.getId()), "owner", ownerName), "admins", adminNames)));
    }

    private void sendStopPermissionDenied(Player player, Stop stop) {
        String ownerName = resolvePlayerName(stop.getOwner());
        String adminNames = formatAdminNames(stop.getAdmins(), stop.getOwner());
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_manage",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "stop_id", stop.getId()), "owner", ownerName), "admins", adminNames)));
    }

    private void sendStopLinkDenied(Player player, Line line, Stop stop) {
        String ownerName = resolvePlayerName(stop.getOwner());
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_link",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "stop_id", stop.getId()), "owner", ownerName), "line_id", line.getId())));
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return plugin.getLanguageManager().getMessage("ownership.server");
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return uuid.toString();
    }

    private UUID resolvePlayerUUID(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer != null) {
            return offlinePlayer.getUniqueId();
        }
        return null;
    }

    private String formatAdminNames(Set<UUID> adminIds, UUID ownerId) {
        Set<String> names = new HashSet<>();
        for (UUID uuid : adminIds) {
            if (uuid == null || (ownerId != null && ownerId.equals(uuid))) {
                continue;
            }
            names.add(resolvePlayerName(uuid));
        }
        if (names.isEmpty()) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        return String.join(", ", names);
    }

    private void sendPlayerNotFound(Player player, String name) {
        player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                LanguageManager.put(LanguageManager.args(), "player", name)));
    }

    private String formatLineIds(Set<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        return String.join(", ", lineIds);
    }
}