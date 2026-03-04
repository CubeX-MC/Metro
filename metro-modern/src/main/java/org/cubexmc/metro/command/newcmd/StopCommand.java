package org.cubexmc.metro.command.newcmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StopCommand {

    private final Metro plugin;
    private final StopManager stopManager;

    public StopCommand(Metro plugin, StopManager stopManager) {
        this.plugin = plugin;
        this.stopManager = stopManager;
    }

    @CommandMethod("m|metro stop|s")
    @CommandDescription("Show Stop Help Menu")
    public void help(CommandSender sender) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        sender.sendMessage(lang.getMessage("stop.help_header"));
        sender.sendMessage(lang.getMessage("stop.help_create"));
        sender.sendMessage(lang.getMessage("stop.help_delete"));
        sender.sendMessage(lang.getMessage("stop.help_list"));
        sender.sendMessage(lang.getMessage("stop.help_setcorners"));
        sender.sendMessage(lang.getMessage("stop.help_setpoint"));
        sender.sendMessage(lang.getMessage("stop.help_addtransfer"));
        sender.sendMessage(lang.getMessage("stop.help_deltransfer"));
        sender.sendMessage(lang.getMessage("stop.help_listtransfers"));
        sender.sendMessage(lang.getMessage("stop.help_settitle"));
        sender.sendMessage(lang.getMessage("stop.help_deltitle"));
        sender.sendMessage(lang.getMessage("stop.help_listtitles"));
        sender.sendMessage(lang.getMessage("stop.help_rename"));
        sender.sendMessage(lang.getMessage("stop.help_info"));
        sender.sendMessage(lang.getMessage("stop.help_tp"));
        sender.sendMessage(lang.getMessage("stop.help_trust"));
        sender.sendMessage(lang.getMessage("stop.help_untrust"));
        sender.sendMessage(lang.getMessage("stop.help_owner"));
        sender.sendMessage(lang.getMessage("stop.help_link"));
    }

    @CommandMethod("m|metro stop|s list")
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

    @CommandMethod("m|metro stop|s create <id> <name>")
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

        Stop newStop = stopManager.createStop(id, name, corner1, corner2, player.getUniqueId());
        if (newStop != null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.create_success",
                    LanguageManager.put(LanguageManager.args(), "stop_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_exists",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        }
    }

    @CommandMethod("m|metro stop|s delete <id>")
    @CommandDescription("Delete a metro stop")
    public void delete(Player player, @Argument("id") String id) {
        Stop stop = stopManager.getStop(id);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
            return;
        }
        if (!OwnershipUtil.canManageStop(player, stop)) {
            player.sendMessage("No permission.");
            return;
        }

        if (stopManager.deleteStop(id)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_fail"));
        }
    }

    @CommandMethod("m|metro stop|s tp <id>")
    @CommandDescription("Teleport to a metro stop")
    public void tp(Player player, @Argument("id") String id) {
        Stop stop = stopManager.getStop(id);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", id)));
            return;
        }
        Location location = stop.getStopPointLocation();
        if (location == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_no_point"));
            return;
        }

        player.teleport(location);
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
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
}
