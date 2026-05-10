package org.cubexmc.metro.command.newcmd;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.service.CommandDisplayService;
import org.cubexmc.metro.service.PortalCommandService;
import org.cubexmc.metro.update.DataFileUpdater;
import org.cubexmc.metro.util.OwnershipUtil;

import java.util.Arrays;
import java.util.List;

/**
 * 矿车传送门管理命令。
 */
public class PortalCommand {

    private static final List<String> HELP_KEYS = Arrays.asList(
            "portal.help_create",
            "portal.help_setdest",
            "portal.help_link",
            "portal.help_delete",
            "portal.help_list",
            "portal.help_trust",
            "portal.help_untrust",
            "portal.help_owner",
            "portal.help_reload"
    );

    private final Metro plugin;
    private final PortalManager portalManager;
    private final CommandDisplayService displayService;
    private final PortalCommandService portalService;
    private final CommandGuard guard;

    public PortalCommand(Metro plugin) {
        this.plugin = plugin;
        this.portalManager = plugin.getPortalManager();
        this.displayService = new CommandDisplayService();
        this.portalService = new PortalCommandService(portalManager);
        this.guard = new CommandGuard(plugin, plugin.getLineManager(), plugin.getStopManager());
    }

    @Command("m|metro portal")
    @CommandDescription("显示传送门管理帮助")
    public void help(CommandSender sender) {
        showHelp(sender);
    }

    @Command("m|metro portal help")
    @CommandDescription("显示传送门管理帮助")
    public void helpPage(CommandSender sender) {
        showHelp(sender);
    }

    private void showHelp(CommandSender sender) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        CommandDisplayService.HelpSection help = displayService.helpSection(key -> lang.getMessage(key),
                "portal.help_header", HELP_KEYS);
        sender.sendMessage(help.header());
        for (String helpLine : help.lines()) {
            sender.sendMessage(helpLine);
        }
    }

    @Command("m|metro portal create <portalId>")
    @CommandDescription("Create a portal entrance at the current position")
    public void createPortal(Player sender, @Argument("portalId") String id) {
        if (!OwnershipUtil.canCreatePortal(sender)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.permission_create"));
            return;
        }

        PortalCommandService.PortalWriteResult result =
                portalService.createPortal(id, sender.getLocation(), sender.getTargetBlockExact(5),
                        sender.getUniqueId());
        switch (result.status()) {
            case SUCCESS:
                {
                Location loc = result.location();
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_success",
                        LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(
                                LanguageManager.args(), "portal_id", id), "x", String.valueOf(loc.getBlockX())),
                                "y", String.valueOf(loc.getBlockY())), "z", String.valueOf(loc.getBlockZ())),
                                "world", loc.getWorld().getName())));
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_setdest_hint",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            }
                break;
            case INVALID_ID:
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.id_invalid",
                LanguageManager.put(LanguageManager.args(), "portal_id", id)));
                break;
            case EXISTS:
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_exists",
                LanguageManager.put(LanguageManager.args(), "portal_id", id)));
                break;
            default:
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_fail",
                LanguageManager.put(LanguageManager.args(), "portal_id", id)));
                break;
        }
    }

    @Command("m|metro portal setdest <portalId>")
    @CommandDescription("Set the current position as the portal destination")
    public void setDestination(Player sender, @Argument(value = "portalId", suggestions = "portalIds") String id) {
        Portal portal = guard.requireManageablePortal(sender, id);
        if (portal == null) {
            return;
        }

        PortalCommandService.PortalWriteResult result = portalService.setDestination(id, sender.getLocation());
        if (result.status() == PortalCommandService.WriteStatus.NOT_FOUND) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            return;
        }
        if (result.status() != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.setdest_fail",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            return;
        }

        Location loc = result.location();
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.setdest_success",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(
                        LanguageManager.args(), "portal_id", id), "x", String.format("%.1f", loc.getX())),
                        "y", String.format("%.1f", loc.getY())), "z", String.format("%.1f", loc.getZ())),
                        "yaw", String.format("%.1f", loc.getYaw())), "world", loc.getWorld().getName())));
    }

    @Command("m|metro portal link <id1> <id2>")
    @CommandDescription("双向配对两个传送门")
    public void linkPortals(Player sender,
                            @Argument(value = "id1", suggestions = "portalIds") String id1,
                            @Argument(value = "id2", suggestions = "portalIds") String id2) {
        Portal portal1 = guard.requireManageablePortal(sender, id1);
        if (portal1 == null) {
            return;
        }
        Portal portal2 = guard.requireManageablePortal(sender, id2);
        if (portal2 == null) {
            return;
        }

        if (portalService.linkPortals(id1, id2) != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.link_fail"));
            return;
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.link_success",
                LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id_1", id1), "portal_id_2", id2)));
    }

    @Command("m|metro portal delete <portalId>")
    @CommandDescription("Delete a portal")
    public void deletePortal(Player sender, @Argument(value = "portalId", suggestions = "portalIds") String id) {
        Portal portal = guard.requireManageablePortal(sender, id);
        if (portal == null) {
            return;
        }

        if (portalService.deletePortal(id) != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            return;
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.delete_success",
                LanguageManager.put(LanguageManager.args(), "portal_id", id)));
    }

    @Command("m|metro portal list [page]")
    @CommandDescription("列出所有传送门")
    public void listPortals(CommandSender sender, @Argument(value = "page", suggestions = "pageNumbers") Integer page) {
        List<Portal> allPortals = portalService.listPortals();
        if (allPortals.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.list_empty"));
            return;
        }

        CommandDisplayService.Page<Portal> portalPage = displayService.paginate(allPortals, page);
        sender.sendMessage(displayService.pageHeader(
                plugin.getLanguageManager().getMessage("portal.list_header",
                        LanguageManager.put(LanguageManager.args(), "count", String.valueOf(allPortals.size()))),
                portalPage));
        for (Portal p : portalPage.items()) {
            String linked = p.getLinkedPortalId() != null
                    ? plugin.getLanguageManager().getMessage("portal.list_linked",
                            LanguageManager.put(LanguageManager.args(), "linked_portal_id", p.getLinkedPortalId()))
                    : "";
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.list_item",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(
                            LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                    "portal_id", p.getId()), "world", p.getWorldName()),
                                    "x", String.valueOf(p.getX())), "y", String.valueOf(p.getY())),
                            "z", String.valueOf(p.getZ())), "dest_world", p.getDestWorldName()),
                            "dest", String.format("%.0f,%.0f,%.0f", p.getDestX(), p.getDestY(), p.getDestZ())),
                            "linked", linked)));
        }
    }

    @Command("m|metro portal trust <portalId> <playerName>")
    @CommandDescription("Grant portal admin permissions")
    public void trust(Player player,
                      @Argument(value = "portalId", suggestions = "portalIds") String id,
                      @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Portal portal = guard.requireManageablePortal(player, id);
        if (portal == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        PortalCommandService.WriteStatus status = portalService.addAdmin(portal, target.getUniqueId());
        if (status == PortalCommandService.WriteStatus.EXISTS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.trust_exists",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        } else if (status == PortalCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.trust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id", id), "player", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.trust_fail",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id", id), "player", playerName)));
        }
    }

    @Command("m|metro portal untrust <portalId> <playerName>")
    @CommandDescription("Remove portal admin permissions")
    public void untrust(Player player,
                        @Argument(value = "portalId", suggestions = "portalIds") String id,
                        @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Portal portal = guard.requireManageablePortal(player, id);
        if (portal == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        PortalCommandService.WriteStatus status = portalService.removeAdmin(portal, target.getUniqueId());
        if (status == PortalCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.untrust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id", id), "player", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.untrust_fail",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id", id), "player", playerName)));
        }
    }

    @Command("m|metro portal owner <portalId> <playerName>")
    @CommandDescription("转移传送门所有权")
    public void owner(Player player,
                      @Argument(value = "portalId", suggestions = "portalIds") String id,
                      @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Portal portal = guard.requireManageablePortal(player, id);
        if (portal == null || !guard.requirePortalOwner(player, portal)) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        PortalCommandService.WriteStatus status = portalService.setOwner(portal, target.getUniqueId());
        if (status == PortalCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.owner_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id", id), "owner", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
        }
    }

    @Command("m|metro portal reload")
    @CommandDescription("重新加载传送门配置")
    @Permission("metro.admin")
    public void reloadPortals(CommandSender sender) {
        PortalCommandService.ReloadResult result =
                portalService.reloadPortals(() -> DataFileUpdater.migratePortals(plugin));
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.reload_success",
                LanguageManager.put(LanguageManager.args(), "count", String.valueOf(result.portalCount()))));
    }
}
