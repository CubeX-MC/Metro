package org.cubexmc.metro.gui.controller;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

final class GuiPermissionGuard {
    private final Metro plugin;

    GuiPermissionGuard(Metro plugin) {
        this.plugin = plugin;
    }

    boolean requireManageLine(Player player, Line line) {
        if (OwnershipUtil.canManageLine(player, line)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_manage",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "line_id", line.getId()), "owner", formatOwner(line.getOwner())),
                        "admins", formatAdmins(line.getAdmins()))));
        return false;
    }

    boolean requireManageStop(Player player, Stop stop) {
        if (OwnershipUtil.canManageStop(player, stop)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("stop.permission_manage",
                LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "stop_id", stop.getId()), "owner", formatOwner(stop.getOwner())),
                        "admins", formatAdmins(stop.getAdmins()))));
        return false;
    }

    boolean requireCreateLine(Player player) {
        if (OwnershipUtil.canCreateLine(player)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
        return false;
    }

    private String formatOwner(UUID ownerId) {
        if (ownerId == null) {
            return plugin.getLanguageManager().getMessage("ownership.server");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        return owner.getName() == null ? ownerId.toString() : owner.getName();
    }

    private String formatAdmins(Set<UUID> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return plugin.getLanguageManager().getMessage("ownership.none");
        }
        String text = adminIds.stream().map(this::formatOwner).collect(Collectors.joining(", "));
        return text.isBlank() ? plugin.getLanguageManager().getMessage("ownership.none") : text;
    }
}
