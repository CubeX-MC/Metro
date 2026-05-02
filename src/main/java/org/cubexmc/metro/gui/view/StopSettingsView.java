package org.cubexmc.metro.gui.view;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.ItemBuilder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Stop;

public final class StopSettingsView {
    private final Metro plugin;

    public StopSettingsView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String stopId, String fromLineId, GuiHolder.GuiView previousView) {
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.STOP_SETTINGS);
        holder.setPreviousView(previousView);
        holder.setData("stopId", stopId);
        if (fromLineId != null) {
            holder.setData("fromLineId", fromLineId);
        }

        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.translateAlternateColorCodes('&', msg("gui.stop_settings.title") + stop.getName()));
        holder.setInventory(inv);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(11, new ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.stop_settings.rename"))
                .lore(msg("gui.stop_settings.rename_lore"))
                .build());

        String stopPointText = stop.getStopPointLocation() == null
                ? msg("gui.stop_settings.stoppoint_not_set")
                : formatLocation(stop.getStopPointLocation());
        inv.setItem(13, new ItemBuilder(Material.RAIL)
                .name(msg("gui.stop_settings.set_point"))
                .lore(msg("gui.stop_settings.current_stoppoint", "stoppoint", stopPointText),
                        msg("gui.stop_settings.set_point_lore"))
                .build());
        inv.setItem(15, new ItemBuilder(Material.BARRIER)
                .name(msg("gui.stop_settings.delete"))
                .lore(msg("gui.stop_settings.delete_lore"))
                .build());
        inv.setItem(22, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_main"))
                .build());

        player.openInventory(inv);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return msg("gui.stop_settings.stoppoint_not_set");
        }
        return location.getWorld().getName() + " "
                + location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    private String msg(String key) {
        return plugin.getLanguageManager().getMessage(key);
    }

    private String msg(String key, String... replacements) {
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, replacements[i], replacements[i + 1]);
        }
        return plugin.getLanguageManager().getMessage(key, args);
    }
}
