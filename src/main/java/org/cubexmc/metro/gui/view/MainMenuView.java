package org.cubexmc.metro.gui.view;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.ItemBuilder;

public final class MainMenuView {
    private final Metro plugin;

    public MainMenuView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.MAIN_MENU);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_GRAY + ChatColor.stripColor(msg("gui.main_menu.title")));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.RAIL)
                .name(msg("gui.main_menu.line_manage"))
                .lore(msg("gui.main_menu.line_manage_lore1"),
                        msg("gui.main_menu.line_manage_lore2"))
                .build());
        inv.setItem(15, new ItemBuilder(Material.MINECART)
                .name(msg("gui.main_menu.stop_manage"))
                .lore(msg("gui.main_menu.stop_manage_lore1"),
                        msg("gui.main_menu.stop_manage_lore2"))
                .build());

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    private String msg(String key) {
        return plugin.getLanguageManager().getMessage(key);
    }
}
