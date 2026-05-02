package org.cubexmc.metro.gui.view;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.gui.ItemBuilder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

public final class LineDetailView {
    private static final int SLOT_SETTINGS = 50;

    private final Metro plugin;

    public LineDetailView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String lineId, int requestedPage, GuiHolder.GuiView previousView) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.LINE_DETAIL);
        holder.setPreviousView(previousView);
        holder.setData("lineId", lineId);
        holder.setData("page", requestedPage);

        List<String> stopIds = line.getOrderedStopIds();
        String coloredName = (line.getColor() != null ? line.getColor() : "") + line.getName();
        String title = ChatColor.translateAlternateColorCodes('&', msg("gui.line_detail.title") + coloredName);
        boolean canManage = OwnershipUtil.canManageLine(player, line);

        renderPaginatedList(player, holder, title, stopIds, requestedPage, (inv, stopId, slot, page) ->
                addStopItem(player, inv, stopIds, stopId, slot, page, canManage), (inv, page, totalPages) ->
                addControlBar(inv, page, totalPages, stopIds.size(), canManage));
    }

    private void addStopItem(Player player, Inventory inv, List<String> stopIds, String stopId, int slot, int page,
                             boolean canManage) {
        Stop stop = plugin.getStopManager().getStop(stopId);
        List<String> lore = new java.util.ArrayList<>();
        int index = slot + page * GuiSlots.ITEMS_PER_PAGE;
        lore.add(msg("gui.line_detail.index", "index", String.valueOf(index + 1)));

        if (stop != null) {
            lore.add(msg("gui.common.id", "id", stop.getId()));
            if (index == 0) {
                lore.add(msg("gui.line_detail.start_stop"));
            } else if (index == stopIds.size() - 1) {
                lore.add(msg("gui.line_detail.end_stop"));
            }
            lore.add("");
            if (player.hasPermission("metro.tp") && stop.getStopPointLocation() != null) {
                lore.add(msg("gui.line_detail.click_tp"));
            }
            if (canManage) {
                lore.add(msg("gui.line_detail.click_settings"));
                lore.add(msg("gui.line_detail.click_remove"));
            }

            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + stop.getName())
                    .lore(lore)
                    .build());
            return;
        }

        lore.add(msg("gui.line_detail.stop_not_exist"));
        inv.setItem(slot, new ItemBuilder(Material.BARRIER)
                .name("&c" + stopId)
                .lore(lore)
                .build());
    }

    private void renderPaginatedList(Player player, GuiHolder holder, String title, List<String> items,
                                     int requestedPage, ItemPopulator populator, ControlBarPopulator controlBar) {
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / GuiSlots.ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * GuiSlots.ITEMS_PER_PAGE;
        int end = Math.min(start + GuiSlots.ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            populator.populate(inv, items.get(i), i - start, page);
        }

        controlBar.populate(inv, page, totalPages);
        player.openInventory(inv);
    }

    private void addControlBar(Inventory inv, int page, int totalPages, int stopCount, boolean canManage) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = GuiSlots.ITEMS_PER_PAGE; i < 54; i++) {
            inv.setItem(i, filler);
        }

        if (page > 0) {
            inv.setItem(GuiSlots.SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.prev_page"))
                    .build());
        }
        inv.setItem(GuiSlots.SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1),
                        "total", String.valueOf(totalPages)))
                .lore(msg("gui.control.stop_count", "count", String.valueOf(stopCount)))
                .build());
        if (page < totalPages - 1) {
            inv.setItem(GuiSlots.SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
        inv.setItem(GuiSlots.SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_line_list"))
                .build());

        if (canManage) {
            inv.setItem(GuiSlots.SLOT_FILTER, new ItemBuilder(Material.EMERALD_BLOCK)
                    .name(msg("gui.line_detail.add_stop"))
                    .lore(msg("gui.line_detail.add_stop_lore"))
                    .build());
            inv.setItem(SLOT_SETTINGS, new ItemBuilder(Material.ANVIL)
                    .name(msg("gui.line_detail.settings"))
                    .lore(msg("gui.line_detail.settings_lore"))
                    .build());
        }
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

    @FunctionalInterface
    private interface ItemPopulator {
        void populate(Inventory inv, String item, int slot, int page);
    }

    @FunctionalInterface
    private interface ControlBarPopulator {
        void populate(Inventory inv, int page, int totalPages);
    }
}
