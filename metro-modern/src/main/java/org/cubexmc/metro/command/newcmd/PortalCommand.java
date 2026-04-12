package org.cubexmc.metro.command.newcmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.model.Portal;

import java.util.List;

/**
 * 矿车传送门管理命令。
 * 所有命令均需要 metro.admin 权限。
 */
public class PortalCommand {

    private final Metro plugin;
    private final PortalManager portalManager;

    public PortalCommand(Metro plugin) {
        this.plugin = plugin;
        this.portalManager = plugin.getPortalManager();
    }

    @CommandMethod("m|metro portal create <id>")
    @CommandDescription("在当前位置创建一个传送门入口")
    @CommandPermission("metro.admin")
    public void createPortal(Player sender, @Argument("id") String id) {
        if (portalManager.getPortal(id) != null) {
            sender.sendMessage(ChatColor.RED + "传送门 '" + id + "' 已存在！");
            return;
        }

        Location loc = sender.getLocation();
        portalManager.createPortal(id, loc);
        sender.sendMessage(ChatColor.GREEN + "传送门 '" + id + "' 已创建于 "
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " (" + loc.getWorld().getName() + ")");
        sender.sendMessage(ChatColor.YELLOW + "请使用 /metro portal setdest " + id + " 设置目标位置。");
    }

    @CommandMethod("m|metro portal setdest <id>")
    @CommandDescription("将当前位置设置为传送门的目标位置")
    @CommandPermission("metro.admin")
    public void setDestination(Player sender, @Argument("id") String id) {
        if (!portalManager.setDestination(id, sender.getLocation())) {
            sender.sendMessage(ChatColor.RED + "传送门 '" + id + "' 不存在！");
            return;
        }

        Location loc = sender.getLocation();
        sender.sendMessage(ChatColor.GREEN + "传送门 '" + id + "' 目标已设置为 "
                + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())
                + " (Yaw: " + String.format("%.1f", loc.getYaw()) + ")"
                + " (" + loc.getWorld().getName() + ")");
    }

    @CommandMethod("m|metro portal link <id1> <id2>")
    @CommandDescription("双向配对两个传送门")
    @CommandPermission("metro.admin")
    public void linkPortals(Player sender,
                            @Argument("id1") String id1,
                            @Argument("id2") String id2) {
        if (!portalManager.linkPortals(id1, id2)) {
            sender.sendMessage(ChatColor.RED + "配对失败，请确保两个传送门都已存在。");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "传送门 '" + id1 + "' ↔ '" + id2 + "' 已双向配对。");
    }

    @CommandMethod("m|metro portal delete <id>")
    @CommandDescription("删除一个传送门")
    @CommandPermission("metro.admin")
    public void deletePortal(Player sender, @Argument("id") String id) {
        if (!portalManager.deletePortal(id)) {
            sender.sendMessage(ChatColor.RED + "传送门 '" + id + "' 不存在！");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "传送门 '" + id + "' 已删除。");
    }

    @CommandMethod("m|metro portal list")
    @CommandDescription("列出所有传送门")
    @CommandPermission("metro.admin")
    public void listPortals(CommandSender sender) {
        List<Portal> allPortals = portalManager.getAllPortals();
        if (allPortals.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "当前没有任何传送门。");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== 矿车传送门列表 (" + allPortals.size() + ") ===");
        for (Portal p : allPortals) {
            String linked = p.getLinkedPortalId() != null ? " §b↔ " + p.getLinkedPortalId() : "";
            sender.sendMessage(ChatColor.WHITE + " • " + ChatColor.AQUA + p.getId()
                    + ChatColor.GRAY + " [" + p.getWorldName() + " "
                    + p.getX() + "," + p.getY() + "," + p.getZ() + "]"
                    + ChatColor.YELLOW + " → "
                    + ChatColor.GRAY + "[" + p.getDestWorldName() + " "
                    + String.format("%.0f,%.0f,%.0f", p.getDestX(), p.getDestY(), p.getDestZ()) + "]"
                    + linked);
        }
    }

    @CommandMethod("m|metro portal reload")
    @CommandDescription("重新加载传送门配置")
    @CommandPermission("metro.admin")
    public void reloadPortals(CommandSender sender) {
        portalManager.load();
        sender.sendMessage(ChatColor.GREEN + "传送门配置已重新加载。共 " + portalManager.getAllPortals().size() + " 个传送门。");
    }
}
