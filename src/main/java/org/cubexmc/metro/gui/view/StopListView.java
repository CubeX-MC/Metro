package org.cubexmc.metro.gui.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public final class StopListView {
    private final Metro plugin;

    public StopListView(Metro plugin) {
        this.plugin = plugin;
    }

    public void openStopList(Player player, int requestedPage, boolean showOnlyMine,
                             GuiHolder.GuiView previousView) {
        GuiHolder holder = createHolder(GuiHolder.GuiType.STOP_LIST, previousView);
        holder.setData("page", requestedPage);
        holder.setData("showOnlyMine", showOnlyMine);

        List<Stop> allStops = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null)
                .collect(Collectors.toList());

        List<Stop> filteredStops;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredStops = allStops.stream()
                    .filter(stop -> OwnershipUtil.canManageStop(player, stop))
                    .collect(Collectors.toList());
        } else {
            filteredStops = allStops;
        }

        Map<String, List<Stop>> groupedStops = filteredStops.stream()
                .collect(Collectors.groupingBy(Stop::getName));
        List<String> sortedNames = new ArrayList<>(groupedStops.keySet());
        sortedNames.sort(String::compareTo);

        holder.setData("stopNames", sortedNames);
        holder.setData("groupedStops", groupedStops);

        String titleKey = showOnlyMine ? "gui.stop_list.title_mine" : "gui.stop_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        renderPaginatedList(player, holder, title, sortedNames, requestedPage, (inv, name, slot) -> {
            List<Stop> variants = groupedStops.get(name);
            Stop representative = variants.get(0);

            List<String> lore = new ArrayList<>();
            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.stop_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));
                addStopSummaryLore(player, lore, representative);
            }

            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + name)
                    .lore(lore)
                    .build());
        }, (inv, page, totalPages) -> addControlBar(inv, page, totalPages, showOnlyMine));
    }

    public void openStopVariants(Player player, String stopName, int requestedPage,
                                 GuiHolder.GuiView previousView) {
        GuiHolder holder = createHolder(GuiHolder.GuiType.STOP_VARIANTS, previousView);
        holder.setData("page", requestedPage);
        holder.setData("stopName", stopName);

        List<Stop> variants = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null && stop.getName().equals(stopName))
                .sorted(Comparator.comparing(Stop::getId))
                .collect(Collectors.toList());
        holder.setData("stops", variants);

        String title = ChatColor.translateAlternateColorCodes('&',
                stopName + " - " + msg("gui.common.variants_title"));
        renderPaginatedList(player, holder, title, variants, requestedPage, (inv, stop, slot) -> {
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", stop.getId()));
            addStopSummaryLore(player, lore, stop);

            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + stop.getId())
                    .lore(lore)
                    .build());
        }, this::addVariantNavigationControls);
    }

    private void addStopSummaryLore(Player player, List<String> lore, Stop stop) {
        List<String> lineNames = new ArrayList<>();
        for (Line line : plugin.getLineManager().getAllLines()) {
            if (line.containsStop(stop.getId())) {
                String coloredName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
                lineNames.add(coloredName);
            }
        }
        if (!lineNames.isEmpty()) {
            lore.add(msg("gui.stop_list.lines") + String.join("&7, ", lineNames));
        } else {
            lore.add(msg("gui.stop_list.no_lines"));
        }

        lore.add("");
        if (OwnershipUtil.canManageStop(player, stop)) {
            lore.add(msg("gui.line_list.can_manage"));
        } else {
            lore.add(msg("gui.line_list.view_only"));
        }
        lore.add("");
        if (player.hasPermission("metro.tp")) {
            if (stop.getStopPointLocation() != null) {
                lore.add(msg("gui.stop_list.click_tp"));
            } else {
                lore.add(msg("gui.stop_list.no_stoppoint"));
            }
        }
        if (OwnershipUtil.canManageStop(player, stop)) {
            lore.add(msg("gui.line_list.click_settings"));
        }
    }

    private GuiHolder createHolder(GuiHolder.GuiType type, GuiHolder.GuiView previousView) {
        GuiHolder holder = new GuiHolder(type);
        holder.setPreviousView(previousView);
        return holder;
    }

    private <T> void renderPaginatedList(Player player, GuiHolder holder, String title, List<T> items,
                                         int requestedPage, ItemPopulator<T> populator,
                                         ControlBarPopulator controlBar) {
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / GuiSlots.ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * GuiSlots.ITEMS_PER_PAGE;
        int end = Math.min(start + GuiSlots.ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            populator.populate(inv, items.get(i), i - start);
        }

        controlBar.populate(inv, page, totalPages);
        player.openInventory(inv);
    }

    private void addControlBar(Inventory inv, int page, int totalPages, boolean showOnlyMine) {
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
        inv.setItem(GuiSlots.SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_main"))
                .build());
        inv.setItem(GuiSlots.SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1),
                        "total", String.valueOf(totalPages)))
                .build());

        String filterName = showOnlyMine ? msg("gui.control.filter_mine") : msg("gui.control.filter_all");
        String filterLore = showOnlyMine ? msg("gui.control.filter_lore_mine") : msg("gui.control.filter_lore_all");
        inv.setItem(GuiSlots.SLOT_FILTER, new ItemBuilder(Material.HOPPER)
                .name(filterName)
                .lore(filterLore)
                .build());

        if (page < totalPages - 1) {
            inv.setItem(GuiSlots.SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
    }

    private void addVariantNavigationControls(Inventory inv, int page, int totalPages) {
        inv.setItem(GuiSlots.SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(msg("gui.common.back"))
                .build());
        if (totalPages <= 1) {
            return;
        }
        if (page > 0) {
            inv.setItem(GuiSlots.SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.common.prev_page"))
                    .build());
        }
        inv.setItem(GuiSlots.SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.common.page_info", "page", String.valueOf(page + 1),
                        "total", String.valueOf(totalPages)))
                .build());
        if (page < totalPages - 1) {
            inv.setItem(GuiSlots.SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.common.next_page"))
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
    private interface ItemPopulator<T> {
        void populate(Inventory inv, T item, int slot);
    }

    @FunctionalInterface
    private interface ControlBarPopulator {
        void populate(Inventory inv, int page, int totalPages);
    }
}
