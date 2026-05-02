package org.cubexmc.metro.gui.controller;

import java.util.List;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.model.Stop;

public final class LineBoardingChoiceController {
    private final Metro plugin;

    public LineBoardingChoiceController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        String stopId = holder.getData("stopId");
        int page = holder.getData("page", 0);
        int totalPages = holder.getData("totalPages", 1);

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE -> {
                Stop stop = plugin.getStopManager().getStop(stopId);
                if (stop != null && page > 0) {
                    plugin.getGuiManager().openLineBoardingChoice(player, stop, page - 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE -> {
                Stop stop = plugin.getStopManager().getStop(stopId);
                if (stop != null && page < totalPages - 1) {
                    plugin.getGuiManager().openLineBoardingChoice(player, stop, page + 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_BACK -> {
                player.closeInventory();
                return;
            }
            default -> {
            }
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<String> lineIds = holder.getData("lineIds");
        if (lineIds == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= lineIds.size()) {
            return;
        }

        String lineId = lineIds.get(index);
        if (isRightClick) {
            plugin.getGuiManager().openLineDetail(player, lineId, 0, holder.snapshot());
            return;
        }

        player.closeInventory();
        plugin.getPlayerInteractListener().boardSelectedLine(player, stopId, lineId);
    }
}
