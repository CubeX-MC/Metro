package org.cubexmc.metro.gui.view;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.ItemBuilder;
import org.cubexmc.metro.manager.LanguageManager;

public final class ConfirmActionView {
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;
    private static final int SLOT_BACK = 22;

    private final Metro plugin;

    public ConfirmActionView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String action, String targetId, String targetName, String lineId, int returnPage,
                     GuiHolder.GuiView previousView) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.CONFIRM_ACTION);
        holder.setPreviousView(previousView);
        holder.setData("action", action);
        holder.setData("targetId", targetId);
        holder.setData("targetName", targetName);
        holder.setData("lineId", lineId);
        holder.setData("returnPage", returnPage);

        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.translateAlternateColorCodes('&', msg("gui.confirm.title")));
        holder.setInventory(inv);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        String target = targetName == null || targetName.isBlank() ? targetId : targetName + " (" + targetId + ")";
        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_CONCRETE)
                .name(msg("gui.confirm.confirm"))
                .lore(msg(messageKey(action), "target", target),
                        msg("gui.confirm.warning"),
                        "",
                        msg("gui.confirm.confirm_lore"))
                .build());
        inv.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_CONCRETE)
                .name(msg("gui.confirm.cancel"))
                .lore(msg("gui.confirm.cancel_lore"))
                .build());
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.common.back"))
                .build());

        player.openInventory(inv);
    }

    private String messageKey(String action) {
        return switch (action) {
            case "DELETE_LINE" -> "gui.confirm.delete_line";
            case "DELETE_STOP" -> "gui.confirm.delete_stop";
            case "REMOVE_STOP_FROM_LINE" -> "gui.confirm.remove_stop_from_line";
            case "CLEAR_ROUTE" -> "gui.confirm.clear_route";
            default -> "gui.confirm.generic";
        };
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
