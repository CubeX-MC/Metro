package org.cubexmc.metro.gui.controller

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.OwnershipUtil
import java.util.UUID

internal class GuiPermissionGuard(private val plugin: Metro) {

    fun requireManageLine(player: Player, line: Line): Boolean {
        if (OwnershipUtil.canManageLine(player, line)) {
            return true
        }
        val args = LanguageManager.args()
        LanguageManager.put(args, "line_id", line.id)
        LanguageManager.put(args, "owner", formatOwner(line.owner))
        LanguageManager.put(args, "admins", formatAdmins(line.admins))
        player.sendMessage(plugin.languageManager.getMessage("line.permission_manage", args))
        return false
    }

    fun requireManageStop(player: Player, stop: Stop): Boolean {
        if (OwnershipUtil.canManageStop(player, stop)) {
            return true
        }
        val args = LanguageManager.args()
        LanguageManager.put(args, "stop_id", stop.id)
        LanguageManager.put(args, "owner", formatOwner(stop.owner))
        LanguageManager.put(args, "admins", formatAdmins(stop.admins))
        player.sendMessage(plugin.languageManager.getMessage("stop.permission_manage", args))
        return false
    }

    fun requireCreateLine(player: Player): Boolean {
        if (OwnershipUtil.canCreateLine(player)) {
            return true
        }
        player.sendMessage(plugin.languageManager.getMessage("line.permission_create"))
        return false
    }

    private fun formatOwner(ownerId: UUID?): String {
        if (ownerId == null) {
            return plugin.languageManager.getMessage("ownership.server")
        }
        val owner = Bukkit.getOfflinePlayer(ownerId)
        return owner.name ?: ownerId.toString()
    }

    private fun formatAdmins(adminIds: Set<UUID>?): String {
        if (adminIds == null || adminIds.isEmpty()) {
            return plugin.languageManager.getMessage("ownership.none")
        }
        val text = adminIds.joinToString(", ") { formatOwner(it) }
        return if (text.isBlank()) plugin.languageManager.getMessage("ownership.none") else text
    }
}
