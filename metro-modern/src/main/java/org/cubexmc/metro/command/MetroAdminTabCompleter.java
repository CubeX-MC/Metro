package org.cubexmc.metro.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
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
import org.cubexmc.metro.util.OwnershipUtil;

/**
 * 处理Metro插件命令的Tab补全
 */
public class MetroAdminTabCompleter implements TabCompleter {
    
    private final Metro plugin;
    
    // 主命令 (包含别名 l=line, s=stop)
    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "line", "l", "stop", "s", "reload"
    );
    
    // 线路子命令
    private static final List<String> LINE_SUBCOMMANDS = Arrays.asList(
            "create", "delete", "list", "setcolor", "setterminus", "addstop", "delstop", "stops", "rename",
            "setmaxspeed", "info", "trust", "untrust", "owner"
    );
    
    // 停靠区子命令
    private static final List<String> STOP_SUBCOMMANDS = Arrays.asList(
            "create", "delete", "list", "setcorners", "setpoint", "tp",
            "addtransfer", "deltransfer", "listtransfers", "settitle", "deltitle", "listtitles", "rename",
            "info", "trust", "untrust", "owner", "link"
    );
    
    // 颜色代码列表
    private static final List<String> COLOR_CODES = Arrays.asList(
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
            "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f"
    );
    
    // title类型列表
    private static final List<String> TITLE_TYPES = Arrays.asList(
            "stop_continuous", "arrive_stop", "terminal_stop", "departure"
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
        
        // 主命令补全
        if (args.length == 1) {
            return getCompletions(args[0], MAIN_COMMANDS);
        }
        
        // 子命令补全
        String mainCommand = args[0].toLowerCase();
        
        // 检查命令类型（支持别名）
        boolean isLineCommand = "line".equals(mainCommand) || "l".equals(mainCommand);
        boolean isStopCommand = "stop".equals(mainCommand) || "s".equals(mainCommand);

        if (isStopCommand && args.length >= 3 && "link".equals(args[1].toLowerCase())) {
            if (args.length == 3) {
                return getCompletions(args[2], Arrays.asList("allow", "deny"));
            } else if (args.length == 4) {
                return getStopIds(player, true);
            } else if (args.length == 5) {
                return getLineIds(player, false);
            }
            return Collections.emptyList();
        }

        if (args.length == 2) {
            if (isLineCommand) {
                return getCompletions(args[1], LINE_SUBCOMMANDS);
            } else if (isStopCommand) {
                return getCompletions(args[1], STOP_SUBCOMMANDS);
            }
        }
        
        // 特定子命令参数补全
        if (args.length == 3) {
            if (isLineCommand) {
                String subCommand = args[1].toLowerCase();
                if (isLineManageCommand(subCommand)) {
                    return getLineIds(player, true);
                }
                if ("stops".equals(subCommand) || "info".equals(subCommand)) {
                    return getLineIds(player, false);
                }
            } else if (isStopCommand) {
                String subCommand = args[1].toLowerCase();
                if (isStopManageCommand(subCommand)) {
                    return getStopIds(player, true);
                }
                if ("tp".equals(subCommand) || "info".equals(subCommand) || "list".equals(subCommand)
                        || "listtransfers".equals(subCommand) || "listtitles".equals(subCommand)) {
                    return getStopIds(player, false);
                }
            }
        }
        
        // 特定子命令的第四个参数补全
        if (args.length == 4) {
            if (isLineCommand) {
                String subCommand = args[1].toLowerCase();
                if ("setcolor".equals(subCommand)) {
                    return getCompletions(args[3], COLOR_CODES);
                } else if ("addstop".equals(subCommand)) {
                    return getStopIds(player, false);
                } else if ("setmaxspeed".equals(subCommand)) {
                    return getCompletions(args[3], Arrays.asList("0.4", "0.6", "0.8", "1.0"));
                } else if ("trust".equals(subCommand) || "untrust".equals(subCommand) || "owner".equals(subCommand)) {
                    return getPlayerNames(args[3]);
                }
            } else if (isStopCommand) {
                String subCommand = args[1].toLowerCase();
                if ("addtransfer".equals(subCommand) || "deltransfer".equals(subCommand)) {
                    return getLineIds(player, false);
                } else if ("settitle".equals(subCommand) || "deltitle".equals(subCommand)) {
                    return getCompletions(args[3], TITLE_TYPES);
                } else if ("trust".equals(subCommand) || "untrust".equals(subCommand) || "owner".equals(subCommand)) {
                    return getPlayerNames(args[3]);
                }
            }
        }
        
        // 特定子命令的第五个参数补全
        if (args.length == 5) {
            if (isStopCommand) {
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
    private List<String> getLineIds(Player player, boolean requireManage) {
        LineManager lineManager = plugin.getLineManager();
        return lineManager.getAllLines().stream()
                .filter(line -> !requireManage || OwnershipUtil.canManageLine(player, line))
                .map(Line::getId)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有停靠区ID
     */
    private List<String> getStopIds(Player player, boolean requireManage) {
        StopManager stopManager = plugin.getStopManager();
        return stopManager.getAllStopIds().stream()
                .filter(stopId -> {
                    if (!requireManage) {
                        return true;
                    }
                    Stop stop = stopManager.getStop(stopId);
                    return stop != null && OwnershipUtil.canManageStop(player, stop);
                })
                .collect(Collectors.toList());
    }

    private List<String> getPlayerNames(String currentArg) {
        List<String> names = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        return getCompletions(currentArg, names);
    }

    private boolean isLineManageCommand(String subCommand) {
        switch (subCommand) {
            case "delete":
            case "setcolor":
            case "setterminus":
            case "addstop":
            case "delstop":
            case "rename":
            case "setmaxspeed":
            case "trust":
            case "untrust":
            case "owner":
                return true;
            default:
                return false;
        }
    }

    private boolean isStopManageCommand(String subCommand) {
        switch (subCommand) {
            case "delete":
            case "setcorners":
            case "setpoint":
            case "addtransfer":
            case "deltransfer":
            case "settitle":
            case "deltitle":
            case "rename":
            case "trust":
            case "untrust":
            case "owner":
            case "link":
                return true;
            default:
                return false;
        }
    }
}