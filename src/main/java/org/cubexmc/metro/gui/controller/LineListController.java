package org.cubexmc.metro.gui.controller;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.util.OwnershipUtil;

public final class LineListController {
    private final Metro plugin;

    public LineListController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleLineListClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineList(player, page - 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineList(player, page + 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_FILTER -> {
                plugin.getGuiManager().openLineList(player, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case GuiSlots.SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openMainMenu(player));
                return;
            }
            default -> {
            }
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<String> lineNames = holder.getData("lineNames");
        Map<String, List<Line>> groupedLines = holder.getData("groupedLines");
        if (lineNames == null || groupedLines == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= lineNames.size()) {
            return;
        }

        String name = lineNames.get(index);
        List<Line> variants = groupedLines.get(name);
        if (variants == null || variants.isEmpty()) {
            return;
        }

        if (variants.size() > 1) {
            plugin.getGuiManager().openLineVariants(player, name, 0, holder.snapshot());
            return;
        }

        openLine(player, holder, variants.get(0), isRightClick);
    }

    public void handleLineVariantsClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        String lineName = holder.getData("lineName");
        int totalPages = holder.getData("totalPages", 1);

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page - 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page + 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openLineList(player, 0, false));
                return;
            }
            default -> {
            }
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<Line> lines = holder.getData("lines");
        if (lines == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= lines.size()) {
            return;
        }

        openLine(player, holder, lines.get(index), isRightClick);
    }

    private void openLine(Player player, GuiHolder holder, Line line, boolean isRightClick) {
        if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
            plugin.getGuiManager().openLineSettings(player, line.getId(), holder.snapshot());
        } else {
            plugin.getGuiManager().openLineDetail(player, line.getId(), 0, holder.snapshot());
        }
    }
}
