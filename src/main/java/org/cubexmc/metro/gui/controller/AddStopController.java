package org.cubexmc.metro.gui.controller;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

public final class AddStopController {
    private final Metro plugin;

    public AddStopController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleAddStopListClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openAddStopList(player, lineId, page - 1, showOnlyMine,
                            holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopList(player, lineId, page + 1, showOnlyMine,
                            holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_FILTER -> {
                plugin.getGuiManager().openAddStopList(player, lineId, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case GuiSlots.SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openLineDetail(player, lineId, 0));
                return;
            }
            default -> {
            }
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<String> stopNames = holder.getData("stopNames");
        Map<String, List<Stop>> groupedStops = holder.getData("groupedStops");
        if (stopNames == null || groupedStops == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= stopNames.size()) {
            return;
        }

        String name = stopNames.get(index);
        List<Stop> variants = groupedStops.get(name);
        if (variants == null || variants.isEmpty()) {
            return;
        }

        if (variants.size() > 1) {
            plugin.getGuiManager().openAddStopVariants(player, lineId, name, 0, holder.snapshot());
        } else {
            handleAddStopClick(player, line, variants.get(0), holder.getPreviousView());
        }
    }

    public void handleAddStopVariantsClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
        int totalPages = holder.getData("totalPages", 1);

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page - 1,
                            holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page + 1,
                            holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openAddStopList(player, lineId, 0, false));
                return;
            }
            default -> {
            }
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<Stop> stops = holder.getData("stops");
        if (stops == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < stops.size()) {
            handleAddStopClick(player, line, stops.get(index), holder.getPreviousView());
        }
    }

    private void handleAddStopClick(Player player, Line line, Stop stop, GuiHolder.GuiView returnView) {
        if (!OwnershipUtil.canManageLine(player, line)) {
            return;
        }

        if (plugin.getLineManager().addStopToLine(line.getId(), stop.getId(), -1)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stop.getId()), "line_id", line.getId())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
        }
        reopenAfterAddStop(player, line, returnView);
    }

    private void reopenAfterAddStop(Player player, Line line, GuiHolder.GuiView returnView) {
        GuiHolder.GuiView view = returnView;
        while (view != null && (view.getType() == GuiHolder.GuiType.ADD_STOP_LIST
                || view.getType() == GuiHolder.GuiType.ADD_STOP_VARIANTS)) {
            view = view.getPreviousView();
        }
        if (!plugin.getGuiManager().openView(player, view)) {
            plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
        }
    }
}
