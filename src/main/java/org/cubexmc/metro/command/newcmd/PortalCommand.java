package org.cubexmc.metro.command.newcmd;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.service.PortalCommandService;
import org.cubexmc.metro.update.DataFileUpdater;

import java.util.List;

/**
 * 矿车传送门管理命令。
 * 所有命令均需要 metro.admin 权限。
 */
public class PortalCommand {

    private final Metro plugin;
    private final PortalManager portalManager;
    private final PortalCommandService portalService;

    public PortalCommand(Metro plugin) {
        this.plugin = plugin;
        this.portalManager = plugin.getPortalManager();
        this.portalService = new PortalCommandService(portalManager);
    }

    @Command("m|metro portal")
    @CommandDescription("显示传送门管理帮助")
    @Permission("metro.admin")
    public void help(CommandSender sender) {
        showHelp(sender);
    }

    @Command("m|metro portal help")
    @CommandDescription("显示传送门管理帮助")
    @Permission("metro.admin")
    public void helpPage(CommandSender sender) {
        showHelp(sender);
    }

    private void showHelp(CommandSender sender) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        sender.sendMessage(lang.getMessage("portal.help_header"));
        sender.sendMessage(lang.getMessage("portal.help_create"));
        sender.sendMessage(lang.getMessage("portal.help_setdest"));
        sender.sendMessage(lang.getMessage("portal.help_link"));
        sender.sendMessage(lang.getMessage("portal.help_delete"));
        sender.sendMessage(lang.getMessage("portal.help_list"));
        sender.sendMessage(lang.getMessage("portal.help_reload"));
    }

    @Command("m|metro portal create <id>")
    @CommandDescription("在当前位置创建一个传送门入口")
    @Permission("metro.admin")
    public void createPortal(Player sender, @Argument("id") String id) {
        PortalCommandService.PortalWriteResult result =
                portalService.createPortal(id, sender.getLocation(), sender.getTargetBlockExact(5));
        switch (result.status()) {
            case SUCCESS -> {
                Location loc = result.location();
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_success",
                        LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.put(
                                LanguageManager.args(), "portal_id", id), "x", String.valueOf(loc.getBlockX())),
                                "y", String.valueOf(loc.getBlockY())), "z", String.valueOf(loc.getBlockZ())),
                                "world", loc.getWorld().getName())));
                sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_setdest_hint",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            }
            case INVALID_ID -> sender.sendMessage(plugin.getLanguageManager().getMessage("portal.id_invalid",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            case EXISTS -> sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_exists",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            default -> sender.sendMessage(plugin.getLanguageManager().getMessage("portal.create_fail",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
        }
    }

    @Command("m|metro portal setdest <id>")
    @CommandDescription("将当前位置设置为传送门的目标位置")
    @Permission("metro.admin")
    public void setDestination(Player sender, @Argument("id") String id) {
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
    @Permission("metro.admin")
    public void linkPortals(Player sender,
                            @Argument("id1") String id1,
                            @Argument("id2") String id2) {
        if (portalService.linkPortals(id1, id2) != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.link_fail"));
            return;
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.link_success",
                LanguageManager.put(LanguageManager.put(LanguageManager.args(), "portal_id_1", id1), "portal_id_2", id2)));
    }

    @Command("m|metro portal delete <id>")
    @CommandDescription("删除一个传送门")
    @Permission("metro.admin")
    public void deletePortal(Player sender, @Argument("id") String id) {
        if (portalService.deletePortal(id) != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id)));
            return;
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.delete_success",
                LanguageManager.put(LanguageManager.args(), "portal_id", id)));
    }

    @Command("m|metro portal list")
    @CommandDescription("列出所有传送门")
    @Permission("metro.admin")
    public void listPortals(CommandSender sender) {
        List<Portal> allPortals = portalManager.getAllPortals();
        if (allPortals.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("portal.list_empty"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.list_header",
                LanguageManager.put(LanguageManager.args(), "count", String.valueOf(allPortals.size()))));
        for (Portal p : allPortals) {
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

    @Command("m|metro portal reload")
    @CommandDescription("重新加载传送门配置")
    @Permission("metro.admin")
    public void reloadPortals(CommandSender sender) {
        DataFileUpdater.migratePortals(plugin);
        portalManager.load();
        sender.sendMessage(plugin.getLanguageManager().getMessage("portal.reload_success",
                LanguageManager.put(LanguageManager.args(), "count", String.valueOf(portalManager.getAllPortals().size()))));
    }
}
