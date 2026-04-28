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
import org.cubexmc.metro.gui.GuiColors;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.gui.ItemBuilder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

public final class LineListView {
    private final Metro plugin;

    public LineListView(Metro plugin) {
        this.plugin = plugin;
    }

    public void openLineList(Player player, int requestedPage, boolean showOnlyMine,
                             GuiHolder.GuiView previousView) {
        GuiHolder holder = createHolder(GuiHolder.GuiType.LINE_LIST, previousView);
        holder.setData("page", requestedPage);
        holder.setData("showOnlyMine", showOnlyMine);

        List<Line> allLines = new ArrayList<>(plugin.getLineManager().getAllLines());
        List<Line> filteredLines;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredLines = allLines.stream()
                    .filter(line -> OwnershipUtil.canManageLine(player, line))
                    .collect(Collectors.toList());
        } else {
            filteredLines = allLines;
        }

        Map<String, List<Line>> groupedLines = filteredLines.stream()
                .collect(Collectors.groupingBy(Line::getName));
        List<String> sortedNames = new ArrayList<>(groupedLines.keySet());
        sortedNames.sort(String::compareTo);

        holder.setData("lineNames", sortedNames);
        holder.setData("groupedLines", groupedLines);

        String titleKey = showOnlyMine ? "gui.line_list.title_mine" : "gui.line_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        renderPaginatedList(player, holder, title, sortedNames, requestedPage, (inv, name, slot) -> {
            List<Line> variants = groupedLines.get(name);
            Line representative = variants.get(0);

            List<String> lore = new ArrayList<>();
            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.line_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));
                lore.add(msg("gui.line_list.stop_count", "count",
                        String.valueOf(representative.getOrderedStopIds().size())));
                addLineSummaryLore(lore, representative);
                if (representative.getColor() != null) {
                    lore.add(msg("gui.line_list.color") + representative.getColor() + "■■■■■");
                }
                lore.add("");
                if (OwnershipUtil.canManageLine(player, representative)) {
                    lore.add(msg("gui.line_list.can_manage"));
                } else {
                    lore.add(msg("gui.line_list.view_only"));
                }
                lore.add("");
                lore.add(msg("gui.line_list.click_view"));
                if (OwnershipUtil.canManageLine(player, representative)) {
                    lore.add(msg("gui.line_list.click_settings"));
                }
            }

            inv.setItem(slot, new ItemBuilder(GuiColors.getWoolByColor(representative.getColor()))
                    .name((representative.getColor() != null ? representative.getColor() : "&f") + name)
                    .lore(lore)
                    .build());
        }, (inv, page, totalPages) -> addControlBar(inv, page, totalPages, showOnlyMine));
    }

    public void openLineVariants(Player player, String lineName, int requestedPage,
                                 GuiHolder.GuiView previousView) {
        GuiHolder holder = createHolder(GuiHolder.GuiType.LINE_VARIANTS, previousView);
        holder.setData("page", requestedPage);
        holder.setData("lineName", lineName);

        List<Line> variants = plugin.getLineManager().getAllLines().stream()
                .filter(line -> line.getName().equals(lineName))
                .sorted(Comparator.comparing(Line::getId))
                .collect(Collectors.toList());
        holder.setData("lines", variants);

        String title = ChatColor.translateAlternateColorCodes('&',
                lineName + " - " + msg("gui.common.variants_title"));
        renderPaginatedList(player, holder, title, variants, requestedPage, (inv, line, slot) -> {
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", line.getId()));
            lore.add(msg("gui.line_list.stop_count", "count", String.valueOf(line.getOrderedStopIds().size())));
            addLineSummaryLore(lore, line);
            if (line.getColor() != null) {
                lore.add(msg("gui.line_list.color") + line.getColor() + "■■■■■");
            }
            lore.add("");
            if (OwnershipUtil.canManageLine(player, line)) {
                lore.add(msg("gui.line_list.can_manage"));
            } else {
                lore.add(msg("gui.line_list.view_only"));
            }
            lore.add("");
            lore.add(msg("gui.line_list.click_view"));

            inv.setItem(slot, new ItemBuilder(GuiColors.getWoolByColor(line.getColor()))
                    .name((line.getColor() != null ? line.getColor() : "&f") + line.getId())
                    .lore(lore)
                    .build());
        }, this::addVariantNavigationControls);
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

    private void addLineSummaryLore(List<String> lore, Line line) {
        lore.add(msg("gui.line_boarding.next_stop", "stop_name", getInitialNextStopName(line)));
        lore.add(msg("gui.line_boarding.terminus", "terminus_name", getTerminusDisplayName(line)));
        lore.add(msg("gui.line_boarding.price", "price", formatTicketPrice(line)));
    }

    private String getInitialNextStopName(Line line) {
        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.size() < 2) {
            return msg("gui.line_boarding.unknown_stop");
        }
        String nextStopId = line.getNextStopId(stopIds.get(0));
        Stop nextStop = nextStopId == null ? null : plugin.getStopManager().getStop(nextStopId);
        return nextStop == null ? msg("gui.line_boarding.unknown_stop") : nextStop.getName();
    }

    private String getTerminusDisplayName(Line line) {
        if (line.getTerminusName() == null || line.getTerminusName().isBlank()) {
            return msg("line.info_default");
        }
        return line.getTerminusName();
    }

    private String formatTicketPrice(Line line) {
        double price = line.getTicketPrice();
        if (price <= 0) {
            return msg("gui.line_boarding.free");
        }
        return plugin.getTicketService().format(price);
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
