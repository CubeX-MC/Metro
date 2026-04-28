package org.cubexmc.metro.command.newcmd;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

/**
 * Shared command-layer guard for object lookup and ownership checks.
 */
final class CommandGuard {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;

    CommandGuard(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    Line requireLine(Player player, String lineId) {
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        }
        return line;
    }

    Line requireManageableLine(Player player, String lineId) {
        Line line = requireLine(player, lineId);
        if (line == null) {
            return null;
        }
        if (!OwnershipUtil.canManageLine(player, line)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "owner", formatOwner(line.getOwner())),
                            "admins", formatAdmins(line.getAdmins()))));
            return null;
        }
        return line;
    }

    Stop requireStop(Player player, String stopId) {
        Stop stop = stopManager.getStop(stopId);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
        }
        return stop;
    }

    Stop requireManageableStop(Player player, String stopId) {
        Stop stop = requireStop(player, stopId);
        if (stop == null) {
            return null;
        }
        if (!OwnershipUtil.canManageStop(player, stop)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_manage",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stop.getId()), "owner", formatOwner(stop.getOwner())),
                            "admins", formatAdmins(stop.getAdmins()))));
            return null;
        }
        return stop;
    }

    boolean canModifyLineStops(Player player, Line line, Stop stop) {
        if (OwnershipUtil.canModifyLineStops(player, line, stop)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_link",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "stop_id", stop.getId()), "owner", formatOwner(stop.getOwner())), "line_id", line.getId())));
        return false;
    }

    boolean requireLineOwner(Player player, Line line) {
        if (line.getOwner() == null || line.getOwner().equals(player.getUniqueId()) || OwnershipUtil.hasAdminBypass(player)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_owner"));
        return false;
    }

    boolean requireStopOwner(Player player, Stop stop) {
        if (stop.getOwner() == null || stop.getOwner().equals(player.getUniqueId()) || OwnershipUtil.hasAdminBypass(player)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_owner"));
        return false;
    }

    String formatOwner(UUID ownerId) {
        if (ownerId == null) {
            return plugin.getLanguageManager().getMessage("ownership.server");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        return owner.getName() == null ? ownerId.toString() : owner.getName();
    }

    String formatAdmins(Set<UUID> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        String text = adminIds.stream().map(this::formatOwner).collect(Collectors.joining(", "));
        return text.isBlank() ? plugin.getLanguageManager().getMessage("ownership.none") : text;
    }
}
