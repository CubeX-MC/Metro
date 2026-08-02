package org.cubexmc.metro.command.newcmd

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Portal
import org.cubexmc.metro.service.CommandDisplayService
import org.cubexmc.metro.service.PortalCommandService
import org.cubexmc.metro.update.DataFileUpdater
import org.cubexmc.metro.util.OwnershipUtil
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import java.util.function.Function

/**
 * 矿车传送门管理命令。
 */
class PortalCommand(private val plugin: Metro) {

    private val displayService = CommandDisplayService()
    private val portalService = PortalCommandService(plugin.portalManager)
    private val guard = CommandGuard(plugin, plugin.lineManager, plugin.stopManager)

    @Command("m|metro portal")
    @CommandDescription("显示传送门管理帮助")
    fun help(sender: CommandSender) {
        showHelp(sender)
    }

    @Command("m|metro portal help")
    @CommandDescription("显示传送门管理帮助")
    fun helpPage(sender: CommandSender) {
        showHelp(sender)
    }

    private fun showHelp(sender: CommandSender) {
        val lang = plugin.languageManager
        val help =
            displayService.helpSection(
                Function { key -> lang.getMessage(key) },
                "portal.help_header",
                HELP_KEYS,
            )
        sender.sendMessage(help.header())
        for (helpLine in help.lines()) {
            sender.sendMessage(helpLine)
        }
    }

    @Command("m|metro portal create <portalId>")
    @CommandDescription("Create a portal entrance at the current position")
    fun createPortal(sender: Player, @Argument("portalId") id: String) {
        if (!OwnershipUtil.canCreatePortal(sender)) {
            sender.sendMessage(plugin.languageManager.getMessage("portal.permission_create"))
            return
        }

        val result =
            portalService.createPortal(
                id,
                sender.location,
                sender.getTargetBlockExact(TARGET_BLOCK_RANGE),
                sender.uniqueId,
            )
        when (result.status()) {
            PortalCommandService.WriteStatus.SUCCESS -> {
                val loc = result.location() ?: return
                val args = LanguageManager.args()
                LanguageManager.put(args, "portal_id", id)
                LanguageManager.put(args, "x", loc.blockX.toString())
                LanguageManager.put(args, "y", loc.blockY.toString())
                LanguageManager.put(args, "z", loc.blockZ.toString())
                LanguageManager.put(args, "world", worldName(loc))
                sender.sendMessage(plugin.languageManager.getMessage("portal.create_success", args))
                sender.sendMessage(
                    plugin.languageManager.getMessage(
                        "portal.create_setdest_hint",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id),
                    ),
                )
            }

            PortalCommandService.WriteStatus.INVALID_ID ->
                sender.sendMessage(
                    plugin.languageManager.getMessage(
                        "portal.id_invalid",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id),
                    ),
                )

            PortalCommandService.WriteStatus.EXISTS ->
                sender.sendMessage(
                    plugin.languageManager.getMessage(
                        "portal.create_exists",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id),
                    ),
                )

            else ->
                sender.sendMessage(
                    plugin.languageManager.getMessage(
                        "portal.create_fail",
                        LanguageManager.put(LanguageManager.args(), "portal_id", id),
                    ),
                )
        }
    }

    @Command("m|metro portal setdest <portalId>")
    @CommandDescription("Set the current position as the portal destination")
    fun setDestination(
        sender: Player,
        @Argument(value = "portalId", suggestions = "portalIds") id: String,
    ) {
        guard.requireManageablePortal(sender, id) ?: return

        val result = portalService.setDestination(id, sender.location)
        if (result.status() == PortalCommandService.WriteStatus.NOT_FOUND) {
            sender.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id),
                ),
            )
            return
        }
        if (result.status() != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.setdest_fail",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id),
                ),
            )
            return
        }

        val loc = result.location() ?: return
        val args = LanguageManager.args()
        LanguageManager.put(args, "portal_id", id)
        LanguageManager.put(args, "x", String.format("%.1f", loc.x))
        LanguageManager.put(args, "y", String.format("%.1f", loc.y))
        LanguageManager.put(args, "z", String.format("%.1f", loc.z))
        LanguageManager.put(args, "yaw", String.format("%.1f", loc.yaw))
        LanguageManager.put(args, "world", worldName(loc))
        sender.sendMessage(plugin.languageManager.getMessage("portal.setdest_success", args))
    }

    @Command("m|metro portal delete <portalId> [confirm]")
    @CommandDescription("Delete a portal")
    fun deletePortal(
        sender: Player,
        @Argument(value = "portalId", suggestions = "portalIds") id: String,
        @Argument("confirm") confirm: String?,
    ) {
        guard.requireManageablePortal(sender, id) ?: return
        if (!guard.requireConfirmation(sender, confirm, "/m portal delete $id confirm")) {
            return
        }

        if (portalService.deletePortal(id) != PortalCommandService.WriteStatus.SUCCESS) {
            sender.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id),
                ),
            )
            return
        }
        sender.sendMessage(
            plugin.languageManager.getMessage(
                "portal.delete_success",
                LanguageManager.put(LanguageManager.args(), "portal_id", id),
            ),
        )
    }

    @Command("m|metro portal list [page]")
    @CommandDescription("列出所有传送门")
    fun listPortals(
        sender: CommandSender,
        @Argument(value = "page", suggestions = "pageNumbers") page: Int?,
    ) {
        val allPortals = portalService.listPortals()
        if (allPortals.isEmpty()) {
            sender.sendMessage(plugin.languageManager.getMessage("portal.list_empty"))
            return
        }

        val portalPage = displayService.paginate(allPortals, page)
        sender.sendMessage(
            displayService.pageHeader(
                plugin.languageManager.getMessage(
                    "portal.list_header",
                    LanguageManager.put(LanguageManager.args(), "count", allPortals.size.toString()),
                ),
                portalPage,
            ),
        )
        for (portal in portalPage.items()) {
            sender.sendMessage(plugin.languageManager.getMessage("portal.list_item", listItemArgs(portal)))
        }
    }

    private fun listItemArgs(portal: Portal): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        LanguageManager.put(args, "portal_id", portal.id)
        LanguageManager.put(args, "world", portal.worldName)
        LanguageManager.put(args, "x", portal.x.toString())
        LanguageManager.put(args, "y", portal.y.toString())
        LanguageManager.put(args, "z", portal.z.toString())
        LanguageManager.put(args, "dest_world", portal.destWorldName)
        LanguageManager.put(
            args,
            "dest",
            String.format("%.0f,%.0f,%.0f", portal.destX, portal.destY, portal.destZ),
        )
        return args
    }

    @Command("m|metro portal trust <portalId> <playerName>")
    @CommandDescription("Grant portal admin permissions")
    fun trust(
        player: Player,
        @Argument(value = "portalId", suggestions = "portalIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val portal = guard.requireManageablePortal(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        when (portalService.addAdmin(portal, target.uniqueId)) {
            PortalCommandService.WriteStatus.EXISTS ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "portal.trust_exists",
                        LanguageManager.put(LanguageManager.args(), "player", playerName),
                    ),
                )

            PortalCommandService.WriteStatus.SUCCESS ->
                player.sendMessage(
                    plugin.languageManager.getMessage("portal.trust_success", portalPlayerArgs(id, playerName)),
                )

            else ->
                player.sendMessage(
                    plugin.languageManager.getMessage("portal.trust_fail", portalPlayerArgs(id, playerName)),
                )
        }
    }

    @Command("m|metro portal untrust <portalId> <playerName>")
    @CommandDescription("Remove portal admin permissions")
    fun untrust(
        player: Player,
        @Argument(value = "portalId", suggestions = "portalIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val portal = guard.requireManageablePortal(player, id) ?: return
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (portalService.removeAdmin(portal, target.uniqueId) == PortalCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(
                plugin.languageManager.getMessage("portal.untrust_success", portalPlayerArgs(id, playerName)),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage("portal.untrust_fail", portalPlayerArgs(id, playerName)),
            )
        }
    }

    @Command("m|metro portal owner <portalId> <playerName>")
    @CommandDescription("转移传送门所有权")
    fun owner(
        player: Player,
        @Argument(value = "portalId", suggestions = "portalIds") id: String,
        @Argument(value = "playerName", suggestions = "playerNames") playerName: String,
    ) {
        val portal = guard.requireManageablePortal(player, id)
        if (portal == null || !guard.requirePortalOwner(player, portal)) {
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(playerNotFoundMessage(playerName))
            return
        }
        if (portalService.setOwner(portal, target.uniqueId) == PortalCommandService.WriteStatus.SUCCESS) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "portal_id", id)
            LanguageManager.put(args, "owner", playerName)
            player.sendMessage(plugin.languageManager.getMessage("portal.owner_success", args))
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "portal.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "portal_id", id),
                ),
            )
        }
    }

    @Command("m|metro portal reload")
    @CommandDescription("重新加载传送门配置")
    @Permission("metro.admin")
    fun reloadPortals(sender: CommandSender) {
        val result = portalService.reloadPortals { DataFileUpdater.migratePortals(plugin) }
        sender.sendMessage(
            plugin.languageManager.getMessage(
                "portal.reload_success",
                LanguageManager.put(LanguageManager.args(), "count", result.portalCount().toString()),
            ),
        )
    }

    private fun playerNotFoundMessage(playerName: String): String =
        plugin.languageManager.getMessage(
            "command.player_not_found",
            LanguageManager.put(LanguageManager.args(), "player", playerName),
        )

    private fun portalPlayerArgs(portalId: String, playerName: String): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        LanguageManager.put(args, "portal_id", portalId)
        LanguageManager.put(args, "player", playerName)
        return args
    }

    private fun worldName(location: Location): String = location.world?.name ?: ""

    private companion object {
        const val TARGET_BLOCK_RANGE = 5

        val HELP_KEYS =
            listOf(
                "portal.help_create",
                "portal.help_setdest",
                "portal.help_delete",
                "portal.help_list",
                "portal.help_trust",
                "portal.help_untrust",
                "portal.help_owner",
                "portal.help_reload",
            )
    }
}
