package org.cubexmc.metro.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.LocationUtil;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * stop 共享子命令处理器（控制台/玩家通用）。
 */
public class StopCommandHandler {

    private final Metro plugin;

    public StopCommandHandler(Metro plugin) {
        this.plugin = plugin;
    }

    public Boolean handleShared(CommandSender sender, String[] args, StopManager stopManager, LineManager lineManager) {
        if (args.length < 2) {
            return true;
        }
        String subCommand = args[1].toLowerCase();
        if (subCommand.equals("list")) {
            return handleStopList(sender, stopManager);
        }
        if (subCommand.equals("info")) {
            return handleStopInfo(sender, args, stopManager);
        }
        return null;
    }

    private boolean handleStopList(CommandSender sender, StopManager stopManager) {
        List<Stop> stops = new ArrayList<>(stopManager.getAllStopIds().stream()
                .map(stopManager::getStop)
                .filter(Objects::nonNull)
                .toList());
        if (stops.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("stop.list_empty"));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("stop.list_header"));
            stops.sort((s1, s2) -> s1.getId().compareTo(s2.getId()));
            for (int i = 0; i < stops.size(); i++) {
                Stop stop = stops.get(i);
                if (sender instanceof Player player) {
                    TextComponent message = new TextComponent(plugin.getLanguageManager().getMessage("stop.list_prefix",
                            LanguageManager.put(LanguageManager.args(), "index", String.valueOf(i + 1))));
                    message.addExtra(createTeleportComponent(stop));
                    String suffixText = plugin.getLanguageManager().getMessage("stop.list_suffix",
                            LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId()));
                    message.addExtra(new TextComponent(" " + suffixText));
                    player.spigot().sendMessage(message);
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("stop.list_item_format",
                            LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "stop_id", stop.getId()), "stop_name", stop.getName())));
                }
            }
        }
        return true;
    }

    private boolean handleStopInfo(CommandSender sender, String[] args, StopManager stopManager) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("stop.usage_info"));
            return true;
        }
        String stopId = args[2];
        Stop stop = stopManager.getStop(stopId);
        if (stop == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_header",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_id",
                LanguageManager.put(LanguageManager.args(), "stop_id", stop.getId())));

        if (sender instanceof Player player) {
            TextComponent nameComponent = new TextComponent(plugin.getLanguageManager().getMessage("stop.info_name",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
            nameComponent.addExtra(createTeleportComponent(stop));
            player.spigot().sendMessage(nameComponent);
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_name",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner1",
                LanguageManager.put(LanguageManager.args(), "corner1",
                        stop.getCorner1() != null ? LocationUtil.locationToString(stop.getCorner1()) : "Not set")));
        sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_corner2",
                LanguageManager.put(LanguageManager.args(), "corner2",
                        stop.getCorner2() != null ? LocationUtil.locationToString(stop.getCorner2()) : "Not set")));
        sender.sendMessage(plugin.getLanguageManager().getMessage("stop.info_stoppoint",
                LanguageManager.put(LanguageManager.args(), "stoppoint",
                        stop.getStopPointLocation() != null ? LocationUtil.locationToString(stop.getStopPointLocation()) : "Not set")));
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
}

