package org.cubexmc.metro.command;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * line 共享子命令处理器（控制台/玩家通用）。
 */
public class LineCommandHandler {

    private final Metro plugin;

    public LineCommandHandler(Metro plugin) {
        this.plugin = plugin;
    }

    public Boolean handleShared(CommandSender sender, String[] args, LineManager lineManager, StopManager stopManager) {
        if (args.length < 2) {
            return true;
        }
        String subCommand = args[1].toLowerCase();
        if (subCommand.equals("list")) {
            return handleLineList(sender, lineManager);
        }
        if (subCommand.equals("info")) {
            return handleLineInfo(sender, args, lineManager);
        }
        if (subCommand.equals("stops")) {
            return handleLineStops(sender, args, lineManager, stopManager);
        }
        return null;
    }

    private boolean handleLineList(CommandSender sender, LineManager lineManager) {
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
        return true;
    }

    private boolean handleLineInfo(CommandSender sender, String[] args, LineManager lineManager) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.usage_info"));
            return true;
        }
        String lineId = args[2];
        Line infoLine = lineManager.getLine(lineId);
        if (infoLine == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return true;
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("line.info_header",
                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "line_id", infoLine.getId()), "line_name", infoLine.getName())));
        sender.sendMessage(plugin.getLanguageManager().getMessage("line.info_owner",
                LanguageManager.put(LanguageManager.args(), "owner", resolvePlayerName(infoLine.getOwner()))));
        sender.sendMessage(plugin.getLanguageManager().getMessage("line.info_admins",
                LanguageManager.put(LanguageManager.args(), "admins",
                        formatAdminNames(infoLine.getAdmins(), infoLine.getOwner()))));
        return true;
    }

    private boolean handleLineStops(CommandSender sender, String[] args, LineManager lineManager, StopManager stopManager) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.usage_stops"));
            return true;
        }

        String lineId = args[2];
        Line line = lineManager.getLine(lineId);

        if (line == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return true;
        }

        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_empty",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_header"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                line.getColor() + line.getName() + " &f(" + line.getId() + ")"));

        for (int i = 0; i < stopIds.size(); i++) {
            String currentStopId = stopIds.get(i);
            Stop stop = stopManager.getStop(currentStopId);
            if (stop != null) {
                String status = "";
                if (i == 0) {
                    status = plugin.getLanguageManager().getMessage("line.stops_status_start");
                }
                if (i == stopIds.size() - 1) {
                    status = plugin.getLanguageManager().getMessage("line.stops_status_end");
                }
                if (sender instanceof Player player) {
                    TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage("line.stops_list_prefix",
                            LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1))));
                    message.addExtra(createTeleportComponent(stop));
                    String suffixText = plugin.getLanguageManager().getMessage("line.stops_list_suffix",
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "stop_id", stop.getId()), "status", status));
                    message.addExtra(new TextComponent(" " + suffixText));
                    player.spigot().sendMessage(message);
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_prefix",
                            LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1)))
                            + stop.getName()
                            + plugin.getLanguageManager().getMessage("line.stops_list_suffix",
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "stop_id", stop.getId()), "status", status)));
                }
            } else {
                sender.sendMessage(plugin.getLanguageManager().getMessage("line.stops_list_invalid_stop",
                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                "index", String.valueOf(i + 1)), "stop_id", currentStopId)));
            }
        }
        return true;
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

}

