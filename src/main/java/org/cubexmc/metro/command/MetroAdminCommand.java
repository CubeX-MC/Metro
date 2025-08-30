package org.cubexmc.metro.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;

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
        
        // 检查权限
        if (!player.hasPermission("metro.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("plugin.no_permission"));
            return true;
        }
        
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
                    lineId = args[2];
                    String lineName = args[3];
                    if (lineManager.createLine(lineId, lineName)) {
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
                    if (lineManager.deleteLine(lineId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
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
                    if (lineManager.setLineColor(lineId, color)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success", 
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(), 
                                        "line_id", lineId), "color", color)));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found", 
                                LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                    }
                    break;
                    
                case "setterminus":
                    if (args.length < 3) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.usage_setterminus"));
                        return true;
                    }
                    lineId = args[2];
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
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.listtitles_no_titles"));
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

                    Stop newStop = stopManager.createStop(stopId, stopName, corner1, corner2);
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
}