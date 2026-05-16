package org.cubexmc.metro.gui.controller;

import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.LineCommandService;
import org.cubexmc.metro.service.StopCommandService;

public final class ConfirmActionController {
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;
    private static final int SLOT_BACK = 22;

    private final Metro plugin;
    private final GuiPermissionGuard permissionGuard;
    private final LineCommandService lineService;
    private final StopCommandService stopService;

    public ConfirmActionController(Metro plugin) {
        this.plugin = plugin;
        this.permissionGuard = new GuiPermissionGuard(plugin);
        this.lineService = new LineCommandService(plugin.getLineManager());
        this.stopService = new StopCommandService(plugin.getStopManager());
    }

    public void handleClick(Player player, GuiHolder holder, int slot) {
        if (slot == SLOT_CANCEL || slot == SLOT_BACK) {
            plugin.getGuiManager().openPreviousView(player, holder, () -> reopenSource(player, holder));
            return;
        }
        if (slot != SLOT_CONFIRM) {
            return;
        }

        String action = holder.getData("action");
        switch (action) {
            case "DELETE_LINE" -> confirmDeleteLine(player, holder);
            case "DELETE_STOP" -> confirmDeleteStop(player, holder);
            case "REMOVE_STOP_FROM_LINE" -> confirmRemoveStopFromLine(player, holder);
            case "CLEAR_ROUTE" -> confirmClearRoute(player, holder);
            default -> plugin.getGuiManager().openPreviousView(player, holder, () -> reopenSource(player, holder));
        }
    }

    private void confirmDeleteLine(Player player, GuiHolder holder) {
        String lineId = holder.getData("targetId");
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            plugin.getGuiManager().openLineList(player, 0, false);
            return;
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory();
            return;
        }
        if (lineService.deleteLine(lineId) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        }
        plugin.getGuiManager().openLineList(player, 0, false);
    }

    private void confirmDeleteStop(Player player, GuiHolder holder) {
        String stopId = holder.getData("targetId");
        String fromLineId = holder.getData("lineId");
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            reopenAfterStopDelete(player, fromLineId);
            return;
        }
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory();
            return;
        }
        if (stopService.deleteStop(stopId) == StopCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_success",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
        }
        reopenAfterStopDelete(player, fromLineId);
    }

    private void reopenAfterStopDelete(Player player, String fromLineId) {
        if (fromLineId != null) {
            plugin.getGuiManager().openLineDetail(player, fromLineId, 0);
        } else {
            plugin.getGuiManager().openStopList(player, 0, false);
        }
    }

    private void confirmRemoveStopFromLine(Player player, GuiHolder holder) {
        String stopId = holder.getData("targetId");
        String lineId = holder.getData("lineId");
        int returnPage = holder.getData("returnPage", 0);
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            player.closeInventory();
            return;
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory();
            return;
        }
        if (lineService.removeStopFromLine(line, stopId) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "stop_id", stopId),
                            "line_id", lineId)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
        }
        plugin.getGuiManager().openLineDetail(player, lineId, returnPage);
    }

    private void confirmClearRoute(Player player, GuiHolder holder) {
        String lineId = holder.getData("targetId");
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            player.closeInventory();
            return;
        }
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory();
            return;
        }
        plugin.getRouteRecorder().clearActive(lineId);
        LineCommandService.ClearRouteResult result = lineService.clearRoutePoints(line);
        if (result.status() == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clearroute_success",
                    args("line_id", lineId, "point_count", String.valueOf(result.previousPointCount()))));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clearroute_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        }
        if (!plugin.getGuiManager().openView(player, holder.getPreviousView())) {
            plugin.getGuiManager().openLineSettings(player, lineId);
        }
    }

    private void reopenSource(Player player, GuiHolder holder) {
        String action = holder.getData("action");
        String targetId = holder.getData("targetId");
        String lineId = holder.getData("lineId");
        int returnPage = holder.getData("returnPage", 0);
        switch (action) {
            case "DELETE_LINE" -> plugin.getGuiManager().openLineSettings(player, targetId);
            case "DELETE_STOP" -> plugin.getGuiManager().openStopSettings(player, targetId, lineId);
            case "REMOVE_STOP_FROM_LINE" -> plugin.getGuiManager().openLineDetail(player, lineId, returnPage);
            case "CLEAR_ROUTE" -> plugin.getGuiManager().openLineSettings(player, targetId);
            default -> player.closeInventory();
        }
    }

    private Map<String, Object> args(Object... replacements) {
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, String.valueOf(replacements[i]), replacements[i + 1]);
        }
        return args;
    }
}
