package org.cubexmc.metro.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.TextUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

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
        
        // 测试终点站提示命令
        if (mainCommand.equals("testendpoint")) {
            player.sendMessage(ChatColor.GREEN + "正在测试终点站提示...");
            
            // 显示终点站Title
            if (plugin.isTerminalStopTitleEnabled()) {
                String title = plugin.getTerminalStopTitle();
                String subtitle = plugin.getTerminalStopSubtitle();
                
                // 创建测试数据
                Line testLine = new Line("test_line", "测试线路");
                Stop testStop = new Stop("test_stop", "测试站点");
                Stop lastStop = new Stop("last_stop", "上一站");
                // 终点站没有下一站，但终点站就是当前站
                
                // 替换占位符 - 终点站时nextStop为null，terminalStop为当前站
                title = TextUtil.replacePlaceholders(title, testLine, testStop, lastStop, null, testStop, plugin.getLineManager());
                subtitle = TextUtil.replacePlaceholders(subtitle, testLine, testStop, lastStop, null, testStop, plugin.getLineManager());
                
                player.sendMessage(ChatColor.GREEN + "显示终点站提示: title=" + title + ", subtitle=" + subtitle);
                
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    plugin.getTerminalStopFadeIn(),
                    plugin.getTerminalStopStay(),
                    plugin.getTerminalStopFadeOut()
                );
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "终点站提示未启用。配置中 titles.terminal_stop.enabled = false");
                return true;
            }
        }
        
        // 测试线路站点信息展示
        if (mainCommand.equals("teststopinfo")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "用法: /m teststopinfo <line_id> [stop_id]");
                return true;
            }
            
            String lineId = args[1];
            Line line = lineManager.getLine(lineId);
            
            if (line == null) {
                player.sendMessage(ChatColor.RED + "未找到线路: " + lineId);
                return true;
            }
            
            // 如果指定了停靠区，则显示该停靠区的信息
            if (args.length > 2) {
                String stopId = args[2];
                Stop stop = stopManager.getStop(stopId);
                
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                // 获取前一站和下一站
                String lastStopId = line.getPreviousStopId(stopId);
                String nextStopId = line.getNextStopId(stopId);
                
                Stop lastStop = lastStopId != null ? stopManager.getStop(lastStopId) : null;
                Stop nextStop = nextStopId != null ? stopManager.getStop(nextStopId) : null;
                
                // 获取终点站
                List<String> stopIds = line.getOrderedStopIds();
                Stop terminalStop = null;
                if (!stopIds.isEmpty()) {
                    String terminalStopId = stopIds.get(stopIds.size() - 1);
                    terminalStop = stopManager.getStop(terminalStopId);
                }
                
                // 显示该站点信息
                player.sendMessage(ChatColor.GREEN + "===== 停靠区信息 =====");
                player.sendMessage(ChatColor.GOLD + "线路: " + line.getName() + " (" + line.getId() + ")");
                player.sendMessage(ChatColor.GOLD + "当前站: " + stop.getName() + " (" + stop.getId() + ")");
                player.sendMessage(ChatColor.GOLD + "上一站: " + (lastStop != null ? lastStop.getName() : "无 (起始站)"));
                player.sendMessage(ChatColor.GOLD + "下一站: " + (nextStop != null ? nextStop.getName() : "无 (终点站)"));
                player.sendMessage(ChatColor.GOLD + "终点站: " + (terminalStop != null ? terminalStop.getName() : "无"));
                
                // 获取标题配置并测试显示
                String title = plugin.getEnterStopTitle();
                String subtitle = plugin.getEnterStopSubtitle();
                
                // 替换占位符
                title = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop, lineManager);
                subtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop, lineManager);
                
                player.sendMessage(ChatColor.GREEN + "标题预览: " + title);
                player.sendMessage(ChatColor.GREEN + "副标题预览: " + subtitle);
                
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    plugin.getEnterStopFadeIn(),
                    plugin.getEnterStopStay(),
                    plugin.getEnterStopFadeOut()
                );
            } else {
                // 显示线路上所有站点
                List<String> stopIds = line.getOrderedStopIds();
                
                if (stopIds.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "线路 " + line.getName() + " 上没有站点。");
                    return true;
                }
                
                player.sendMessage(ChatColor.GREEN + "===== 线路站点 =====");
                player.sendMessage(ChatColor.GOLD + "线路: " + line.getName() + " (" + line.getId() + ")");
                
                for (int i = 0; i < stopIds.size(); i++) {
                    String stopId = stopIds.get(i);
                    Stop stop = stopManager.getStop(stopId);
                    if (stop != null) {
                        String status = "";
                        if (i == 0) status = " (起始站)";
                        if (i == stopIds.size() - 1) status = " (终点站)";
                        
                        player.sendMessage(ChatColor.AQUA + "" + (i+1) + ". " + 
                                ChatColor.YELLOW + stop.getName() + 
                                ChatColor.WHITE + " (" + stop.getId() + ")" + 
                                ChatColor.GOLD + status);
                    }
                }
                
                player.sendMessage(ChatColor.GREEN + "使用 /m teststopinfo " + lineId + " <stop_id> 查看详细信息");
            }
            
            return true;
        }
        
        // 新的命令格式，按照README中的结构处理
        if (mainCommand.equals("line")) {
            // 线路管理命令
            if (args.length < 2) {
                sendLineHelpMessage(player);
                return true;
            }
            
            String subCommand = args[1].toLowerCase();
            
            // 设置线路颜色命令
            if (subCommand.equals("setcolor")) {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "用法: /m line setcolor <line_id> <颜色>");
                    player.sendMessage(ChatColor.YELLOW + "颜色示例: &a, &b, &c, &9 等Minecraft颜色代码");
                    return true;
                }
                
                String lineId = args[2];
                String color = args[3];
                
                Line line = lineManager.getLine(lineId);
                if (line == null) {
                    player.sendMessage(ChatColor.RED + "未找到线路: " + lineId);
                    return true;
                }
                
                lineManager.setLineColor(lineId, color);
                player.sendMessage(ChatColor.GREEN + "成功设置线路 " + line.getName() + " 的颜色为: " + 
                        ChatColor.translateAlternateColorCodes('&', color) + "示例文本");
                return true;
            }
            
            // 设置线路终点站方向名称命令
            if (subCommand.equals("setterminus")) {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "用法: /m line setterminus <line_id> <终点方向名称>");
                    return true;
                }
                
                String lineId = args[2];
                // 拼接剩余参数作为终点站方向名称
                StringBuilder terminusName = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) terminusName.append(" ");
                    terminusName.append(args[i]);
                }
                
                Line line = lineManager.getLine(lineId);
                if (line == null) {
                    player.sendMessage(ChatColor.RED + "未找到线路: " + lineId);
                    return true;
                }
                
                lineManager.setLineTerminusName(lineId, terminusName.toString());
                player.sendMessage(ChatColor.GREEN + "成功设置线路 " + line.getName() + " 的终点方向名称为: " + terminusName);
                return true;
            }
            
            switch (subCommand) {
                case "create":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m line create <line_id> <\"显示名称\">");
                        return true;
                    }
                    
                    String lineId = args[2];
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        nameBuilder.append(args[i]).append(" ");
                    }
                    String lineName = nameBuilder.toString().trim();
                    
                    if (lineManager.createLine(lineId, lineName)) {
                        player.sendMessage(ChatColor.GREEN + "成功创建线路: " + lineName);
                    } else {
                        player.sendMessage(ChatColor.RED + "线路ID " + lineId + " 已存在!");
                    }
                    break;
                    
                case "delete":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m line delete <line_id>");
                        return true;
                    }
                    
                    lineId = args[2];
                    if (lineManager.deleteLine(lineId)) {
                        player.sendMessage(ChatColor.GREEN + "成功删除线路: " + lineId);
                    } else {
                        player.sendMessage(ChatColor.RED + "找不到线路ID: " + lineId);
                    }
                    break;
                    
                case "list":
                    List<Line> lines = lineManager.getAllLines();
                    if (lines.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "暂无线路。");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "===== 线路列表 =====");
                        for (Line line : lines) {
                            player.sendMessage(ChatColor.GOLD + line.getId() + ChatColor.WHITE + ": " + 
                                    ChatColor.YELLOW + line.getName());
                        }
                    }
                    break;
                    
                case "addstop":
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.RED + "用法: /m line addstop <line_id> <stop_id> [顺序索引]");
                        return true;
                    }
                    
                    lineId = args[2];
                    String stopId = args[3];
                    
                    int index = -1;
                    if (args.length > 4) {
                        try {
                            index = Integer.parseInt(args[4]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "索引必须是一个数字。");
                            return true;
                        }
                    }
                    
                    if (lineManager.addStopToLine(lineId, stopId, index)) {
                        player.sendMessage(ChatColor.GREEN + "成功将停靠区 " + stopId + " 添加到线路 " + lineId);
                    } else {
                        player.sendMessage(ChatColor.RED + "添加停靠区失败，请检查线路ID和停靠区ID是否存在。");
                    }
                    break;
                    
                case "delstop":
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.RED + "用法: /m line delstop <line_id> <stop_id>");
                        return true;
                    }
                    
                    lineId = args[2];
                    stopId = args[3];
                    
                    if (lineManager.removeStopFromLine(lineId, stopId)) {
                        player.sendMessage(ChatColor.GREEN + "成功从线路 " + lineId + " 中移除停靠区 " + stopId);
                    } else {
                        player.sendMessage(ChatColor.RED + "移除停靠区失败，请检查线路ID和停靠区ID是否存在。");
                    }
                    break;
                    
                default:
                    sendLineHelpMessage(player);
                    break;
            }
        } else if (mainCommand.equals("stop")) {
            // 停靠区管理命令
            if (args.length < 2) {
                sendStopHelpMessage(player);
                return true;
            }
            
            String subCommand = args[1].toLowerCase();
            
            // 停靠区换乘管理命令
            if (subCommand.equals("addtransfer")) {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop addtransfer <stop_id> <transfer_line_id>");
                    return true;
                }
                
                String stopId = args[2];
                String transferLineId = args[3];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                Line transferLine = lineManager.getLine(transferLineId);
                if (transferLine == null) {
                    player.sendMessage(ChatColor.RED + "未找到线路: " + transferLineId);
                    return true;
                }
                
                if (stopManager.addTransferLine(stopId, transferLineId)) {
                    player.sendMessage(ChatColor.GREEN + "成功将线路 " + transferLine.getName() + 
                            " 添加为停靠区 " + stop.getName() + " 的可换乘线路");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "停靠区 " + stop.getName() + " 已存在可换乘线路 " + 
                            transferLine.getName());
                }
                return true;
            }
            
            if (subCommand.equals("deltransfer")) {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop deltransfer <stop_id> <transfer_line_id>");
                    return true;
                }
                
                String stopId = args[2];
                String transferLineId = args[3];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                Line transferLine = lineManager.getLine(transferLineId);
                if (transferLine == null) {
                    player.sendMessage(ChatColor.RED + "未找到线路: " + transferLineId);
                    return true;
                }
                
                if (stopManager.removeTransferLine(stopId, transferLineId)) {
                    player.sendMessage(ChatColor.GREEN + "成功从停靠区 " + stop.getName() + 
                            " 移除可换乘线路 " + transferLine.getName());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "停靠区 " + stop.getName() + " 不存在可换乘线路 " + 
                            transferLine.getName());
                }
                return true;
            }
            
            if (subCommand.equals("listtransfers")) {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop listtransfers <stop_id>");
                    return true;
                }
                
                String stopId = args[2];
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                List<String> transferLineIds = stop.getTransferableLines();
                if (transferLineIds.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "停靠区 " + stop.getName() + " 没有可换乘线路");
                    return true;
                }
                
                player.sendMessage(ChatColor.GREEN + "停靠区 " + stop.getName() + " 的可换乘线路:");
                for (String transferLineId : transferLineIds) {
                    Line transferLine = lineManager.getLine(transferLineId);
                    if (transferLine != null) {
                        player.sendMessage(ChatColor.YELLOW + "- " + 
                                ChatColor.translateAlternateColorCodes('&', transferLine.getColor()) + 
                                transferLine.getName() + 
                                ChatColor.GRAY + " (" + transferLineId + ")");
                    } else {
                        player.sendMessage(ChatColor.RED + "- " + transferLineId + " (无效线路)");
                    }
                }
                return true;
            }
            
            if (subCommand.equals("settitle")) {
                if (args.length < 6) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop settitle <stop_id> <title_type> <key> <value>");
                    player.sendMessage(ChatColor.RED + "title_type: stop_continuous, arrive_stop, terminal_stop, passenger_journey");
                    player.sendMessage(ChatColor.RED + "key: title, subtitle, actionbar");
                    return true;
                }
                
                String stopId = args[2];
                String titleType = args[3];
                String key = args[4];
                
                // 收集剩余参数为value
                StringBuilder valueBuilder = new StringBuilder();
                for (int i = 5; i < args.length; i++) {
                    if (i > 5) valueBuilder.append(" ");
                    valueBuilder.append(args[i]);
                }
                String value = valueBuilder.toString();
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                // 验证titleType
                if (!isValidTitleType(titleType)) {
                    player.sendMessage(ChatColor.RED + "无效的title类型: " + titleType);
                    player.sendMessage(ChatColor.RED + "有效类型: stop_continuous, arrive_stop, terminal_stop, passenger_journey");
                    return true;
                }
                
                // 验证key
                if (!isValidTitleKey(key)) {
                    player.sendMessage(ChatColor.RED + "无效的title键: " + key);
                    player.sendMessage(ChatColor.RED + "有效键: title, subtitle, actionbar");
                    return true;
                }
                
                // 设置自定义title
                Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                if (titleConfig == null) {
                    titleConfig = new HashMap<>();
                }
                titleConfig.put(key, value);
                stop.setCustomTitle(titleType, titleConfig);
                
                // 保存更改
                stopManager.saveConfig();
                
                player.sendMessage(ChatColor.GREEN + "成功为停靠区 " + stop.getName() + 
                        " 设置自定义title: [" + titleType + "." + key + "] = \"" + value + "\"");
                return true;
            }
            
            if (subCommand.equals("deltitle")) {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop deltitle <stop_id> <title_type> [key]");
                    player.sendMessage(ChatColor.RED + "省略key将删除整个title_type的所有设置");
                    return true;
                }
                
                String stopId = args[2];
                String titleType = args[3];
                String key = args.length > 4 ? args[4] : null;
                
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                // 验证titleType
                if (!isValidTitleType(titleType)) {
                    player.sendMessage(ChatColor.RED + "无效的title类型: " + titleType);
                    player.sendMessage(ChatColor.RED + "有效类型: stop_continuous, arrive_stop, terminal_stop, passenger_journey");
                    return true;
                }
                
                if (key == null) {
                    // 删除整个title类型
                    if (stop.removeCustomTitle(titleType)) {
                        stopManager.saveConfig();
                        player.sendMessage(ChatColor.GREEN + "成功从停靠区 " + stop.getName() + 
                                " 移除所有 " + titleType + " 自定义title设置");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "停靠区 " + stop.getName() + 
                                " 没有 " + titleType + " 自定义title设置");
                    }
                } else {
                    // 删除特定key
                    Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                    if (titleConfig != null && titleConfig.containsKey(key)) {
                        titleConfig.remove(key);
                        if (titleConfig.isEmpty()) {
                            stop.removeCustomTitle(titleType);
                        } else {
                            stop.setCustomTitle(titleType, titleConfig);
                        }
                        stopManager.saveConfig();
                        player.sendMessage(ChatColor.GREEN + "成功从停靠区 " + stop.getName() + 
                                " 移除自定义title: [" + titleType + "." + key + "]");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "停靠区 " + stop.getName() + 
                                " 没有自定义title: [" + titleType + "." + key + "]");
                    }
                }
                return true;
            }
            
            if (subCommand.equals("listtitles")) {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "用法: /m stop listtitles <stop_id>");
                    return true;
                }
                
                String stopId = args[2];
                Stop stop = stopManager.getStop(stopId);
                if (stop == null) {
                    player.sendMessage(ChatColor.RED + "未找到停靠区: " + stopId);
                    return true;
                }
                
                player.sendMessage(ChatColor.GREEN + "===== 停靠区 " + stop.getName() + " 自定义Title配置 =====");
                boolean hasCustomTitles = false;
                
                String[] titleTypes = {"stop_continuous", "arrive_stop", "terminal_stop", "passenger_journey"};
                for (String titleType : titleTypes) {
                    Map<String, String> titleConfig = stop.getCustomTitle(titleType);
                    if (titleConfig != null && !titleConfig.isEmpty()) {
                        hasCustomTitles = true;
                        player.sendMessage(ChatColor.GOLD + "类型: " + titleType);
                        for (Map.Entry<String, String> entry : titleConfig.entrySet()) {
                            player.sendMessage(ChatColor.YELLOW + "  - " + entry.getKey() + ": \"" + 
                                    ChatColor.WHITE + entry.getValue() + ChatColor.YELLOW + "\"");
                        }
                    }
                }
                
                if (!hasCustomTitles) {
                    player.sendMessage(ChatColor.YELLOW + "该停靠区没有自定义Title配置");
                }
                return true;
            }
            
            switch (subCommand) {
                case "create":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m stop create <stop_id> <\"显示名称\">");
                        return true;
                    }
                    
                    String stopId = args[2];
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        nameBuilder.append(args[i]).append(" ");
                    }
                    String stopName = nameBuilder.toString().trim();
                    
                    Stop newStop = stopManager.createStop(stopId, stopName);
                    if (newStop != null) {
                        player.sendMessage(ChatColor.GREEN + "成功创建停靠区: " + stopName);
                    } else {
                        player.sendMessage(ChatColor.RED + "停靠区ID " + stopId + " 已存在!");
                    }
                    break;
                    
                case "delete":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m stop delete <stop_id>");
                        return true;
                    }
                    
                    stopId = args[2];
                    if (stopManager.deleteStop(stopId)) {
                        player.sendMessage(ChatColor.GREEN + "成功删除停靠区: " + stopId);
                    } else {
                        player.sendMessage(ChatColor.RED + "找不到停靠区ID: " + stopId);
                    }
                    break;
                    
                case "list":
                    List<Stop> stops = new ArrayList<>(stopManager.getAllStopIds().stream()
                            .map(stopManager::getStop)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                    if (stops.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "暂无停靠区。");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "===== 停靠区列表 =====");
                        for (Stop stop : stops) {
                            player.sendMessage(ChatColor.GOLD + stop.getId() + ChatColor.WHITE + ": " + 
                                    ChatColor.YELLOW + stop.getName());
                        }
                    }
                    break;
                    
                case "setcorner1":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m stop setcorner1 <stop_id>");
                        return true;
                    }
                    
                    stopId = args[2];
                    Location location = player.getLocation();
                    
                    if (stopManager.setStopCorner1(stopId, location)) {
                        player.sendMessage(ChatColor.GREEN + "成功为停靠区 " + stopId + " 设置区域第一个角点: " + 
                            LocationUtil.locationToString(location));
                    } else {
                        player.sendMessage(ChatColor.RED + "设置停靠区角点失败，请检查停靠区ID是否存在。");
                    }
                    break;
                    
                case "setcorner2":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m stop setcorner2 <stop_id>");
                        return true;
                    }
                    
                    stopId = args[2];
                    location = player.getLocation();
                    
                    if (stopManager.setStopCorner2(stopId, location)) {
                        player.sendMessage(ChatColor.GREEN + "成功为停靠区 " + stopId + " 设置区域第二个角点: " + 
                            LocationUtil.locationToString(location));
                    } else {
                        player.sendMessage(ChatColor.RED + "设置停靠区角点失败，请检查停靠区ID是否存在。");
                    }
                    break;
                    
                case "setpoint":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "用法: /m stop setpoint <stop_id> [yaw]");
                        return true;
                    }
                    
                    stopId = args[2];
                    float yaw = player.getLocation().getYaw();
                    
                    if (args.length > 3) {
                        try {
                            yaw = Float.parseFloat(args[3]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "发车朝向必须是一个有效的浮点数。");
                            return true;
                        }
                    }
                    
                    // 检查玩家是否站在铁轨上
                    location = player.getLocation();
                    if (!LocationUtil.isRail(location)) {
                        player.sendMessage(ChatColor.RED + "必须站在铁轨上设置停靠点。");
                        return true;
                    }
                    
                    if (stopManager.setStopPoint(stopId, location, yaw)) {
                        player.sendMessage(ChatColor.GREEN + "成功为停靠区 " + stopId + " 设置停靠点，发车朝向: " + yaw);
                    } else {
                        player.sendMessage(ChatColor.RED + "设置停靠点失败，请检查停靠区ID是否存在。");
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
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_testendpoint"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.help_teststopinfo"));
    }
    
    /**
     * 发送线路管理帮助信息
     */
    private void sendLineHelpMessage(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_header"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_create"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_delete"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_list"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_setcolor"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_setterminus"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_addstop"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_removestop"));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.line.help_stops"));
    }
    
    /**
     * 发送停靠区管理帮助信息
     */
    private void sendStopHelpMessage(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_header")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_create")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_delete")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_list")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_setcorner1")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_setcorner2")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_setpoint")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_addtransfer")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_deltransfer")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getLanguageManager().getMessage("command.stop.help_listtransfers")));
        
        // 添加自定义title相关的帮助信息
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&6/m stop settitle <stop_id> <title_type> <key> <value> &f- 设置停靠区自定义title"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&6/m stop deltitle <stop_id> <title_type> [key] &f- 删除停靠区自定义title"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&6/m stop listtitles <stop_id> &f- 查看停靠区所有自定义title"));
    }
    
    /**
     * 验证title类型是否有效
     */
    private boolean isValidTitleType(String titleType) {
        return titleType.equals("stop_continuous") || 
               titleType.equals("arrive_stop") || 
               titleType.equals("terminal_stop") || 
               titleType.equals("passenger_journey");
    }
    
    /**
     * 验证title键是否有效
     */
    private boolean isValidTitleKey(String key) {
        return key.equals("title") || 
               key.equals("subtitle") || 
               key.equals("actionbar");
    }
}