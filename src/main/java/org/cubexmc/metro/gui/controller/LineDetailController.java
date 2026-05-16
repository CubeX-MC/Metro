package org.cubexmc.metro.gui.controller;

import java.util.List;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;
import org.cubexmc.metro.util.SchedulerUtil;

public final class LineDetailController {
    private static final int SLOT_SETTINGS = 50;

    private final Metro plugin;

    public LineDetailController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleClick(Player player, GuiHolder holder, int slot, boolean isRightClick, boolean isShiftClick) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        int totalPages = holder.getData("totalPages", 1);

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE:
                {
                if (page > 0) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page - 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE:
                {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page + 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_FILTER:
                {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openAddStopList(player, lineId, 0, false, holder.snapshot());
                }
                return;
            }
            case GuiSlots.SLOT_BACK:
                {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openLineList(player, 0, false));
                return;
            }
            case SLOT_SETTINGS:
                {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openLineSettings(player, lineId, holder.snapshot());
                }
                return;
            }
            default:
                {
            }
                break;
        }

        if (slot >= GuiSlots.ITEMS_PER_PAGE) {
            return;
        }

        List<String> stopIds = line.getOrderedStopIds();
        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= stopIds.size()) {
            return;
        }

        String stopId = stopIds.get(index);
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            return;
        }

        if (isShiftClick) {
            if (OwnershipUtil.canManageLine(player, line)) {
                plugin.getGuiManager().openConfirmAction(player, "REMOVE_STOP_FROM_LINE",
                        stopId, stop.getName(), lineId, page, holder.snapshot());
            }
        } else if (isRightClick) {
            if (OwnershipUtil.canManageLine(player, line)) {
                plugin.getGuiManager().openStopSettings(player, stopId, lineId, holder.snapshot());
            }
        } else if (player.hasPermission("metro.tp") && stop.getStopPointLocation() != null) {
            player.closeInventory();
            SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                }
            });
        }
    }
}
