package org.cubexmc.metro.command.newcmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.update.ConfigUpdater;
import org.cubexmc.metro.util.OwnershipUtil;

public class MetroMainCommand {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;

    public MetroMainCommand(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    @CommandMethod("m|metro")
    @CommandDescription("Metro Main Command")
    public void root(CommandSender sender) {
        help(sender);
    }

    @CommandMethod("m|metro help")
    @CommandDescription("Show Metro Help Menu")
    public void help(CommandSender sender) {
        org.cubexmc.metro.manager.LanguageManager lang = plugin.getLanguageManager();
        sender.sendMessage(lang.getMessage("command.help_header"));
        sender.sendMessage(lang.getMessage("command.help_gui"));
        sender.sendMessage(lang.getMessage("command.help_reload"));
        sender.sendMessage(lang.getMessage("command.help_line"));
        sender.sendMessage(lang.getMessage("command.help_stop"));
    }

    @CommandMethod("m|metro gui")
    @CommandDescription("Open the Metro GUI")
    @CommandPermission("metro.gui")
    public void gui(Player player) {
        plugin.getGuiManager().openMainMenu(player);
    }

    @CommandMethod("m|metro reload")
    @CommandDescription("Reload Metro configuration")
    @CommandPermission("metro.admin")
    public void reload(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!OwnershipUtil.hasAdminBypass(player)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("plugin.no_permission"));
                return;
            }
        }

        plugin.ensureDefaultConfigs();
        plugin.reloadConfig();
        ConfigUpdater.applyDefaults(plugin, "config.yml");
        plugin.getConfigFacade().reload();
        lineManager.reload();
        stopManager.reload();
        plugin.getLanguageManager().loadLanguages();

        plugin.refreshMapIntegrations();

        sender.sendMessage(plugin.getLanguageManager().getMessage("plugin.reload"));
    }
}
