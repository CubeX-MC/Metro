package org.cubexmc.metro.gui.view;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiColors;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.gui.ItemBuilder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;

public final class LineSettingsView {
    private final Metro plugin;

    public LineSettingsView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String lineId, GuiHolder.GuiView previousView) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.LINE_SETTINGS);
        holder.setPreviousView(previousView);
        holder.setData("lineId", lineId);

        Inventory inv = Bukkit.createInventory(holder, GuiSlots.LINE_SETTINGS_SIZE,
                ChatColor.translateAlternateColorCodes('&', msg("gui.line_settings.title") + line.getName()));
        holder.setInventory(inv);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < GuiSlots.LINE_SETTINGS_SIZE; i++) {
            inv.setItem(i, filler);
        }

        boolean recording = plugin.getRouteRecorder().isRecording(lineId);
        inv.setItem(GuiSlots.LINE_SETTINGS_RENAME, new ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.line_settings.rename"))
                .lore(msg("gui.line_settings.rename_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_COLOR, new ItemBuilder(GuiColors.getWoolByColor(line.getColor()))
                .name(msg("gui.line_settings.set_color"))
                .lore(msg("gui.line_settings.current_color", "color", line.getColor()),
                        msg("gui.line_settings.set_color_lore"))
                .build());

        String terminusName = line.getTerminusName() == null || line.getTerminusName().isBlank()
                ? msg("line.info_default")
                : line.getTerminusName();
        inv.setItem(GuiSlots.LINE_SETTINGS_TERMINUS, new ItemBuilder(Material.OAK_SIGN)
                .name(msg("gui.line_settings.set_terminus"))
                .lore(msg("gui.line_settings.current_terminus", "terminus_name", terminusName),
                        msg("gui.line_settings.set_terminus_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_MAX_SPEED, new ItemBuilder(Material.MINECART)
                .name(msg("gui.line_settings.set_speed"))
                .lore(msg("gui.line_settings.set_speed_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_TICKET_PRICE, new ItemBuilder(Material.EMERALD)
                .name(msg("gui.line_settings.set_price"))
                .lore(msg("gui.line_settings.set_price_lore"))
                .build());

        inv.setItem(GuiSlots.LINE_SETTINGS_ROUTE_RECORDING, new ItemBuilder(recording ? Material.REDSTONE_TORCH : Material.MINECART)
                .name(msg(recording ? "gui.line_settings.record_stop" : "gui.line_settings.record_start"))
                .lore(msg(recording ? "gui.line_settings.record_stop_lore" : "gui.line_settings.record_start_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_ROUTE_INFO, new ItemBuilder(Material.COMPASS)
                .name(msg("gui.line_settings.route_info"))
                .lore(msg("gui.line_settings.route_info_lore",
                        "point_count", String.valueOf(line.getRoutePoints().size())))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_CLEAR_ROUTE, new ItemBuilder(Material.TNT)
                .name(msg("gui.line_settings.clear_route"))
                .lore(msg("gui.line_settings.clear_route_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_RAIL_PROTECTION, new ItemBuilder(line.isRailProtected() ? Material.IRON_BARS : Material.RAIL)
                .name(msg(line.isRailProtected()
                        ? "gui.line_settings.protection_on"
                        : "gui.line_settings.protection_off"))
                .lore(msg("gui.line_settings.protection_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_CLONE_REVERSE, new ItemBuilder(Material.COMPARATOR)
                .name(msg("gui.line_settings.clone_reverse"))
                .lore(msg("gui.line_settings.clone_reverse_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_DELETE, new ItemBuilder(Material.BARRIER)
                .name(msg("gui.line_settings.delete"))
                .lore(msg("gui.line_settings.delete_lore"))
                .build());
        inv.setItem(GuiSlots.LINE_SETTINGS_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_line_list"))
                .build());

        player.openInventory(inv);
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
