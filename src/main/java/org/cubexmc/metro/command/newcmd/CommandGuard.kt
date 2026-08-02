package org.cubexmc.metro.command.newcmd

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Portal
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil
import java.util.UUID

/**
 * Shared command-layer guard for object lookup and ownership checks.
 */
internal class CommandGuard(
    private val plugin: Metro,
    private val lineManager: LineManager,
    private val stopManager: StopManager,
) {

    fun requireLine(player: Player, lineId: String): Line? {
        val line = lineManager.getLine(lineId)
        if (line == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        }
        return line
    }

    fun requireManageableLine(player: Player, lineId: String): Line? {
        val line = requireLine(player, lineId) ?: return null
        if (!OwnershipUtil.canManageLine(player, line)) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "line_id", line.id)
            LanguageManager.put(args, "owner", formatOwner(line.owner))
            LanguageManager.put(args, "admins", formatAdmins(line.admins))
            player.sendMessage(plugin.languageManager.getMessage("line.permission_manage", args))
            return null
        }
        return line
    }

    fun requireStop(player: Player, stopId: String): Stop? {
        val stop = stopManager.getStop(stopId)
        if (stop == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "stop.stop_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
                ),
            )
        }
        return stop
    }

    fun requirePermission(player: Player, permission: String): Boolean {
        if (player.hasPermission(permission)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("plugin.no_permission"))
        return false
    }

    fun requireConfirmation(player: Player, confirmation: String?, commandToConfirm: String): Boolean {
        if ("confirm".equals(confirmation, ignoreCase = true)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("command.confirm_required"))
        player.sendMessage(
            plugin.languageManager.getMessage(
                "command.confirm_hint",
                LanguageManager.put(LanguageManager.args(), "command", commandToConfirm),
            ),
        )
        return false
    }

    fun requireManageableStop(player: Player, stopId: String): Stop? {
        val stop = requireStop(player, stopId) ?: return null
        if (!OwnershipUtil.canManageStop(player, stop)) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "stop_id", stop.id)
            LanguageManager.put(args, "owner", formatOwner(stop.owner))
            LanguageManager.put(args, "admins", formatAdmins(stop.admins))
            player.sendMessage(plugin.languageManager.getMessage("stop.permission_manage", args))
            return null
        }
        return stop
    }

    fun requirePortal(player: Player, portalId: String): Portal? {
        val portal = plugin.portalManager?.getPortal(portalId)
        if (portal == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", portalId),
                ),
            )
        }
        return portal
    }

    fun requireManageablePortal(player: Player, portalId: String): Portal? {
        val portal = requirePortal(player, portalId) ?: return null
        if (!OwnershipUtil.canManagePortal(player, portal)) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "portal_id", portal.id)
            LanguageManager.put(args, "owner", formatOwner(portal.owner))
            LanguageManager.put(args, "admins", formatAdmins(portal.admins))
            player.sendMessage(plugin.languageManager.getMessage("portal.permission_manage", args))
            return null
        }
        return portal
    }

    fun canModifyLineStops(player: Player, line: Line, stop: Stop): Boolean {
        if (OwnershipUtil.canModifyLineStops(player, line, stop)) {
            return true
        }
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_id", stop.id)
        LanguageManager.put(args, "owner", formatOwner(stop.owner))
        LanguageManager.put(args, "line_id", line.id)
        player.sendMessage(plugin.languageManager.getMessage("stop.permission_link", args))
        return false
    }

    fun requireLineOwner(player: Player, line: Line): Boolean {
        val owner = line.owner
        if (owner == null || owner == player.uniqueId || OwnershipUtil.hasAdminBypass(player)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("line.permission_owner"))
        return false
    }

    fun requireStopOwner(player: Player, stop: Stop): Boolean {
        val owner = stop.owner
        if (owner == null || owner == player.uniqueId || OwnershipUtil.hasAdminBypass(player)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("stop.permission_owner"))
        return false
    }

    fun requirePortalOwner(player: Player, portal: Portal): Boolean {
        val owner = portal.owner
        if (owner == null || owner == player.uniqueId || OwnershipUtil.hasAdminBypass(player)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("portal.permission_owner"))
        return false
    }

    fun formatOwner(ownerId: UUID?): String {
        if (ownerId == null) {
            return plugin.languageManager.getMessage("ownership.server")
        }
        val owner = Bukkit.getOfflinePlayer(ownerId)
        return owner.name ?: ownerId.toString()
    }

    fun formatAdmins(adminIds: Set<UUID>?): String {
        if (adminIds == null || adminIds.isEmpty()) {
            return plugin.languageManager.getMessage("ownership.none")
        }
        val text = adminIds.joinToString(", ") { formatOwner(it) }
        return if (text.isBlank()) plugin.languageManager.getMessage("ownership.none") else text
    }
}
