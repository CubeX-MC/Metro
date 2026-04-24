package org.cubexmc.metro.util;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

/**
 * 所有权与权限检查工具类，封装线路和站点的管理逻辑。
 */
public final class OwnershipUtil {

    public static final String PERMISSION_ADMIN = "metro.admin";
    public static final String PERMISSION_LINE_CREATE = "metro.line.create";
    public static final String PERMISSION_STOP_CREATE = "metro.stop.create";

    private OwnershipUtil() {
    }

    public static boolean hasAdminBypass(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(PERMISSION_ADMIN);
    }

    public static boolean canCreateLine(CommandSender sender) {
        return hasAdminBypass(sender) || sender.hasPermission(PERMISSION_LINE_CREATE);
    }

    public static boolean canCreateStop(CommandSender sender) {
        return hasAdminBypass(sender) || sender.hasPermission(PERMISSION_STOP_CREATE);
    }

    public static boolean isServerOwned(Line line) {
        return line != null && line.getOwner() == null;
    }

    public static boolean isServerOwned(Stop stop) {
        return stop != null && stop.getOwner() == null;
    }

    public static boolean isLineAdmin(UUID playerId, Line line) {
        if (line == null || playerId == null) {
            return false;
        }
        Set<UUID> admins = line.getAdmins();
        return admins.contains(playerId);
    }

    public static boolean isStopAdmin(UUID playerId, Stop stop) {
        if (stop == null || playerId == null) {
            return false;
        }
        Set<UUID> admins = stop.getAdmins();
        return admins.contains(playerId);
    }

    public static boolean canManageLine(CommandSender sender, Line line) {
        if (hasAdminBypass(sender)) {
            return true;
        }
        if (!(sender instanceof Player player) || line == null) {
            return false;
        }
        if (isServerOwned(line)) {
            return player.isOp();
        }
        return isLineAdmin(player.getUniqueId(), line);
    }

    public static boolean canManageStop(CommandSender sender, Stop stop) {
        if (hasAdminBypass(sender)) {
            return true;
        }
        if (!(sender instanceof Player player) || stop == null) {
            return false;
        }
        if (isServerOwned(stop)) {
            return player.isOp();
        }
        return isStopAdmin(player.getUniqueId(), stop);
    }

    public static boolean canModifyLineStops(CommandSender sender, Line line, Stop stop) {
        if (!canManageLine(sender, line) || stop == null) {
            return false;
        }
        if (hasAdminBypass(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (isStopAdmin(playerId, stop)) {
            return true;
        }
        if (isServerOwned(stop)) {
            return player.isOp();
        }
        return stop.isLineAllowed(line.getId());
    }

    public static boolean canLinkStopToLine(CommandSender sender, Line line, Stop stop) {
        if (hasAdminBypass(sender)) {
            return true;
        }
        if (!(sender instanceof Player player) || line == null || stop == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();

        if (isServerOwned(line) && !player.isOp()) {
            return false;
        }

        if (isServerOwned(stop)) {
            return player.isOp();
        }

        if (!isLineAdmin(playerId, line)) {
            return false;
        }

        if (isStopAdmin(playerId, stop)) {
            return true;
        }

        return stop.isLineAllowed(line.getId());
    }

    public static boolean canLinkLineWithoutPlayer(Line line, Stop stop) {
        if (line == null || stop == null) {
            return false;
        }
        if (stop.isLineAllowed(line.getId())) {
            return true;
        }
        return Objects.equals(stop.getOwner(), line.getOwner());
    }
}

