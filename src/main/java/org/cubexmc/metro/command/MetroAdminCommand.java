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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
            sender.sendMessage(ChatColor.RED + "该命令仅限玩家使用。");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 检查权限
        if (!player.hasPermission("metro.admin")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
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
                title = TextUtil.replacePlaceholders(title, testLine, testStop, lastStop, null, testStop);
                subtitle = TextUtil.replacePlaceholders(subtitle, testLine, testStop, lastStop, null, testStop);
                
                player.sendMessage(ChatColor.GREEN + "显示终点站提示: title=" + title + ", subtitle=" + subtitle);
                
                player.sendTitle(
                    title,
                    subtitle,
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
                title = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop);
                subtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop, terminalStop);
                
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
            plugin.reloadConfig();
            lineManager.reload();
            stopManager.reload();
            player.sendMessage(ChatColor.GREEN + "配置重新加载完成。");
        } else {
            // 未知命令
            sendHelpMessage(player);
        }
        
        return true;
    }
    
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== Metro 管理员命令帮助 =====");
        player.sendMessage(ChatColor.GOLD + "/m line create <line_id> <\"显示名称\">" + ChatColor.WHITE + ": 创建新线路");
        player.sendMessage(ChatColor.GOLD + "/m line delete <line_id>" + ChatColor.WHITE + ": 删除线路");
        player.sendMessage(ChatColor.GOLD + "/m line list" + ChatColor.WHITE + ": 列出所有线路");
        player.sendMessage(ChatColor.GOLD + "/m line addstop <line_id> <stop_id> [顺序索引]" + ChatColor.WHITE + ": 将停靠区添加到线路");
        player.sendMessage(ChatColor.GOLD + "/m line delstop <line_id> <stop_id>" + ChatColor.WHITE + ": 从线路移除停靠区");
        
        player.sendMessage(ChatColor.GOLD + "/m stop create <stop_id> <\"显示名称\">" + ChatColor.WHITE + ": 创建新停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop delete <stop_id>" + ChatColor.WHITE + ": 删除停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop list" + ChatColor.WHITE + ": 列出所有停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop setcorner1 <stop_id>" + ChatColor.WHITE + ": 设置停靠区空间的第一个角点");
        player.sendMessage(ChatColor.GOLD + "/m stop setcorner2 <stop_id>" + ChatColor.WHITE + ": 设置停靠区空间的第二个角点");
        player.sendMessage(ChatColor.GOLD + "/m stop setpoint <stop_id> [yaw]" + ChatColor.WHITE + ": 设置停靠区内的精确停靠点和发车朝向");
        
        player.sendMessage(ChatColor.GOLD + "/m reload" + ChatColor.WHITE + ": 重新加载配置");
        player.sendMessage(ChatColor.GOLD + "/m testendpoint" + ChatColor.WHITE + ": 测试终点站提示显示");
        player.sendMessage(ChatColor.GOLD + "/m teststopinfo <line_id> [stop_id]" + ChatColor.WHITE + ": 测试线路站点信息");
    }
    
    private void sendLineHelpMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== Metro 线路管理命令帮助 =====");
        player.sendMessage(ChatColor.GOLD + "/m line create <line_id> <\"显示名称\">" + ChatColor.WHITE + ": 创建新线路");
        player.sendMessage(ChatColor.GOLD + "/m line delete <line_id>" + ChatColor.WHITE + ": 删除线路");
        player.sendMessage(ChatColor.GOLD + "/m line list" + ChatColor.WHITE + ": 列出所有线路");
        player.sendMessage(ChatColor.GOLD + "/m line addstop <line_id> <stop_id> [顺序索引]" + ChatColor.WHITE + ": 将停靠区添加到线路");
        player.sendMessage(ChatColor.GOLD + "/m line delstop <line_id> <stop_id>" + ChatColor.WHITE + ": 从线路移除停靠区");
    }
    
    private void sendStopHelpMessage(Player player) {
        player.sendMessage(ChatColor.GREEN + "===== Metro 停靠区管理命令帮助 =====");
        player.sendMessage(ChatColor.GOLD + "/m stop create <stop_id> <\"显示名称\">" + ChatColor.WHITE + ": 创建新停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop delete <stop_id>" + ChatColor.WHITE + ": 删除停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop list" + ChatColor.WHITE + ": 列出所有停靠区");
        player.sendMessage(ChatColor.GOLD + "/m stop setcorner1 <stop_id>" + ChatColor.WHITE + ": 设置停靠区空间的第一个角点");
        player.sendMessage(ChatColor.GOLD + "/m stop setcorner2 <stop_id>" + ChatColor.WHITE + ": 设置停靠区空间的第二个角点");
        player.sendMessage(ChatColor.GOLD + "/m stop setpoint <stop_id> [yaw]" + ChatColor.WHITE + ": 设置停靠区内的精确停靠点和发车朝向");
    }
}