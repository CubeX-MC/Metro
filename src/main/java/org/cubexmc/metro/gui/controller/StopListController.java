package org.cubexmc.metro.gui.controller;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;
import org.cubexmc.metro.util.SchedulerUtil;

public final class StopListController {
    private final Metro plugin;

    public StopListController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleStopListClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE:
                {
                if (page > 0) {
                    plugin.getGuiManager().openStopList(player, page - 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE:
                {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopList(player, page + 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_FILTER:
                {
                plugin.getGuiManager().openStopList(player, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case GuiSlots.SLOT_BACK:
                {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openMainMenu(player));
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

        List<String> stopNames = holder.getData("stopNames");
        Map<String, List<Stop>> groupedStops = holder.getData("groupedStops");
        if (stopNames == null || groupedStops == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= stopNames.size()) {
            return;
        }

        List<Stop> variants = groupedStops.get(stopNames.get(index));
        if (variants == null || variants.isEmpty()) {
            return;
        }

        if (variants.size() > 1) {
            plugin.getGuiManager().openStopVariants(player, stopNames.get(index), 0, holder.snapshot());
            return;
        }

        Stop stop = variants.get(0);
        if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
            plugin.getGuiManager().openStopSettings(player, stop.getId(), null, holder.snapshot());
        } else {
            handleStopClick(player, stop);
        }
    }

    public void handleStopVariantsClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
        int totalPages = holder.getData("totalPages", 1);

        switch (slot) {
            case GuiSlots.SLOT_PREV_PAGE:
                {
                if (page > 0) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page - 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_NEXT_PAGE:
                {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page + 1, holder.getPreviousView());
                }
                return;
            }
            case GuiSlots.SLOT_BACK:
                {
                plugin.getGuiManager().openPreviousView(player, holder,
                        () -> plugin.getGuiManager().openStopList(player, 0, false));
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

        List<Stop> stops = holder.getData("stops");
        if (stops == null) {
            return;
        }

        int index = page * GuiSlots.ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= stops.size()) {
            return;
        }

        Stop stop = stops.get(index);
        if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
            plugin.getGuiManager().openStopSettings(player, stop.getId(), null, holder.snapshot());
        } else {
            handleStopClick(player, stop);
        }
    }

    private void handleStopClick(Player player, Stop stop) {
        if (!player.hasPermission("metro.tp")) {
            return;
        }

        if (stop.getStopPointLocation() != null) {
            player.closeInventory();
            SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                }
            });
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stoppoint_not_set",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        }
    }
}
