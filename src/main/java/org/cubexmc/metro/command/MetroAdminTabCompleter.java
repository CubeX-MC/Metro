package org.cubexmc.metro.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理Metro插件命令的Tab补全
 */
public class MetroAdminTabCompleter implements TabCompleter {
    
    private final Metro plugin;
    
    // 主命令
    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "line", "stop", "reload", "testendpoint", "teststopinfo"
    );
    
    // 线路子命令
    private static final List<String> LINE_SUBCOMMANDS = Arrays.asList(
            "create", "delete", "list", "setcolor", "setterminus", "addstop", "delstop", "stops"
    );
    
    // 停靠区子命令
    private static final List<String> STOP_SUBCOMMANDS = Arrays.asList(
            "create", "delete", "list", "setcorner1", "setcorner2", "setpoint",
            "addtransfer", "deltransfer", "listtransfers", "settitle", "deltitle", "listtitles"
    );
    
    // 颜色代码列表
    private static final List<String> COLOR_CODES = Arrays.asList(
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
            "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f"
    );
    
    // title类型列表
    private static final List<String> TITLE_TYPES = Arrays.asList(
            "stop_continuous", "arrive_stop", "terminal_stop", "passenger_journey"
    );
    
    // title键列表
    private static final List<String> TITLE_KEYS = Arrays.asList(
            "title", "subtitle", "actionbar"
    );
    
    public MetroAdminTabCompleter(Metro plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        Player player = (Player) sender;
        
        // 检查权限
        if (!player.hasPermission("metro.admin")) {
            return Collections.emptyList();
        }
        
        // 主命令补全
        if (args.length == 1) {
            return getCompletions(args[0], MAIN_COMMANDS);
        }
        
        // 子命令补全
        String mainCommand = args[0].toLowerCase();
        if (args.length == 2) {
            if ("line".equals(mainCommand)) {
                return getCompletions(args[1], LINE_SUBCOMMANDS);
            } else if ("stop".equals(mainCommand)) {
                return getCompletions(args[1], STOP_SUBCOMMANDS);
            } else if ("teststopinfo".equals(mainCommand)) {
                return getLineIds();
            }
        }
        
        // 特定子命令参数补全
        if (args.length == 3) {
            if ("line".equals(mainCommand)) {
                String subCommand = args[1].toLowerCase();
                if ("delete".equals(subCommand) || "setcolor".equals(subCommand) || 
                        "setterminus".equals(subCommand) || "addstop".equals(subCommand) || 
                        "delstop".equals(subCommand) || "stops".equals(subCommand)) {
                    return getLineIds();
                }
            } else if ("stop".equals(mainCommand)) {
                String subCommand = args[1].toLowerCase();
                if ("delete".equals(subCommand) || "setcorner1".equals(subCommand) || 
                        "setcorner2".equals(subCommand) || "setpoint".equals(subCommand) || 
                        "addtransfer".equals(subCommand) || "deltransfer".equals(subCommand) ||
                        "listtransfers".equals(subCommand) || "settitle".equals(subCommand) || 
                        "deltitle".equals(subCommand) || "listtitles".equals(subCommand)) {
                    return getStopIds();
                }
            } else if ("teststopinfo".equals(mainCommand)) {
                String lineId = args[1];
                return getStopsForLine(lineId);
            }
        }
        
        // 特定子命令的第四个参数补全
        if (args.length == 4) {
            if ("line".equals(mainCommand)) {
                String subCommand = args[1].toLowerCase();
                if ("setcolor".equals(subCommand)) {
                    return getCompletions(args[3], COLOR_CODES);
                } else if ("addstop".equals(subCommand)) {
                    return getStopIds();
                }
            } else if ("stop".equals(mainCommand)) {
                String subCommand = args[1].toLowerCase();
                if ("addtransfer".equals(subCommand) || "deltransfer".equals(subCommand)) {
                    return getLineIds();
                } else if ("settitle".equals(subCommand) || "deltitle".equals(subCommand)) {
                    return getCompletions(args[3], TITLE_TYPES);
                }
            }
        }
        
        // 特定子命令的第五个参数补全
        if (args.length == 5) {
            if ("stop".equals(mainCommand)) {
                String subCommand = args[1].toLowerCase();
                if ("settitle".equals(subCommand) || "deltitle".equals(subCommand)) {
                    return getCompletions(args[4], TITLE_KEYS);
                }
            }
        }
        
        return Collections.emptyList();
    }
    
    /**
     * 根据输入的前缀获取可能的补全列表
     * 
     * @param currentArg 当前输入的参数
     * @param options 所有可能的选项
     * @return 匹配的选项列表
     */
    private List<String> getCompletions(String currentArg, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(currentArg, options, completions);
        Collections.sort(completions);
        return completions;
    }
    
    /**
     * 获取所有线路ID
     */
    private List<String> getLineIds() {
        LineManager lineManager = plugin.getLineManager();
        return lineManager.getAllLines().stream()
                .map(Line::getId)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有停靠区ID
     */
    private List<String> getStopIds() {
        StopManager stopManager = plugin.getStopManager();
        return new ArrayList<>(stopManager.getAllStopIds());
    }
    
    /**
     * 获取指定线路上的所有停靠区ID
     */
    private List<String> getStopsForLine(String lineId) {
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            return Collections.emptyList();
        }
        return line.getOrderedStopIds();
    }
} 