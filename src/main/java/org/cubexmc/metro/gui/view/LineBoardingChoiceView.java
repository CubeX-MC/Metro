package org.cubexmc.metro.gui.view;

import java.util.ArrayList;
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
import org.cubexmc.metro.service.TicketService.TicketCheck;
import org.cubexmc.metro.service.TicketService.TicketCheckStatus;

public final class LineBoardingChoiceView {
    private final Metro plugin;

    public LineBoardingChoiceView(Metro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Stop stop, int requestedPage, GuiHolder.GuiView previousView) {
        if (stop == null) {
            return;
        }

        List<Line> lines = plugin.getLineSelectionService().getBoardableLines(stop);
        if (lines.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_line"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.LINE_BOARDING_CHOICE);
        holder.setPreviousView(previousView);
        holder.setData("stopId", stop.getId());
        holder.setData("lineIds", lines.stream().map(Line::getId).collect(Collectors.toList()));

        String title = ChatColor.translateAlternateColorCodes('&',
                msg("gui.line_boarding.title", "stop_name", stop.getName()));
        render(player, holder, title, stop, lines, requestedPage);
    }

    private void render(Player player, GuiHolder holder, String title, Stop stop, List<Line> lines, int requestedPage) {
        int totalPages = Math.max(1, (int) Math.ceil((double) lines.size() / GuiSlots.ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * GuiSlots.ITEMS_PER_PAGE;
        int end = Math.min(start + GuiSlots.ITEMS_PER_PAGE, lines.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, createLineItem(player, stop, lines.get(i)));
        }

        addControlBar(inv, page, totalPages);
        player.openInventory(inv);
    }

    private ItemStack createLineItem(Player player, Stop stop, Line line) {
        Stop nextStop = getNextStop(line, stop);
        String nextStopName = nextStop != null ? nextStop.getName() : msg("gui.line_boarding.unknown_stop");
        String terminusName = line.getTerminusName() == null || line.getTerminusName().isBlank()
                ? msg("line.info_default")
                : line.getTerminusName();
        String blockedReason = getBoardingBlockReason(player, line);

        List<String> lore = new ArrayList<>();
        lore.add(msg("gui.common.id", "id", line.getId()));
        lore.add(msg("gui.line_boarding.next_stop", "stop_name", nextStopName));
        lore.add(msg("gui.line_boarding.terminus", "terminus_name", terminusName));
        lore.add(msg("gui.line_boarding.price", "price", formatTicketPrice(line)));
        lore.add("");
        if (blockedReason == null) {
            lore.add(msg("gui.line_boarding.click_board"));
        } else {
            lore.add(msg("gui.line_boarding.cannot_board", "reason", blockedReason));
        }
        lore.add(msg("gui.line_boarding.click_route"));

        Material material = blockedReason == null ? GuiColors.getWoolByColor(line.getColor()) : Material.BARRIER;
        return new ItemBuilder(material)
                .name((line.getColor() != null ? line.getColor() : "&f") + line.getName())
                .lore(lore)
                .build();
    }

    private void addControlBar(Inventory inv, int page, int totalPages) {
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
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1), "total",
                        String.valueOf(totalPages)))
                .build());
        if (page < totalPages - 1) {
            inv.setItem(GuiSlots.SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
        inv.setItem(GuiSlots.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg("gui.common.close"))
                .build());
    }

    private Stop getNextStop(Line line, Stop stop) {
        String nextStopId = line.getNextStopId(stop.getId());
        return nextStopId != null ? plugin.getStopManager().getStop(nextStopId) : null;
    }

    private String formatTicketPrice(Line line) {
        double price = line.getTicketPrice();
        if (price <= 0) {
            return msg("gui.line_boarding.free");
        }
        return plugin.getTicketService().format(price);
    }

    private String getBoardingBlockReason(Player player, Line line) {
        TicketCheck check = plugin.getTicketService().checkCanBoard(player, line);
        if (check.canBoard()) {
            return null;
        }
        if (check.getStatus() == TicketCheckStatus.INSUFFICIENT_FUNDS) {
            return msg("economy.insufficient_funds", "price", check.getFormattedPrice());
        }
        if (check.getStatus() == TicketCheckStatus.VAULT_UNAVAILABLE) {
            return msg("economy.vault_unavailable");
        }
        return msg("gui.line_boarding.no_permission");
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
