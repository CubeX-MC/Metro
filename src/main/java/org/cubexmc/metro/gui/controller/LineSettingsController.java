package org.cubexmc.metro.gui.controller;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.ChatInputManager;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.RouteRecorder;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.service.LineCommandService;

public final class LineSettingsController {
    private final Metro plugin;
    private final LineCommandService lineService;

    public LineSettingsController(Metro plugin) {
        this.plugin = plugin;
        this.lineService = new LineCommandService(plugin.getLineManager());
    }

    public void handleClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        GuiHolder.GuiView previousView = holder.getPreviousView();
        switch (slot) {
            case GuiSlots.LINE_SETTINGS_ROUTE_RECORDING -> {
                handleRouteRecordingToggle(player, line);
                plugin.getGuiManager().openLineSettings(player, lineId, previousView);
            }
            case GuiSlots.LINE_SETTINGS_ROUTE_INFO -> {
                sendRouteInfo(player, line);
                plugin.getGuiManager().openLineSettings(player, lineId, previousView);
            }
            case GuiSlots.LINE_SETTINGS_CLEAR_ROUTE -> plugin.getGuiManager().openConfirmAction(player, "CLEAR_ROUTE",
                    lineId, line.getName(), null, 0, holder.snapshot());
            case GuiSlots.LINE_SETTINGS_RAIL_PROTECTION -> {
                toggleRailProtection(player, line);
                plugin.getGuiManager().openLineSettings(player, lineId, previousView);
            }
            case GuiSlots.LINE_SETTINGS_RENAME -> requestLineRename(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_MAX_SPEED -> requestLineSpeed(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_TICKET_PRICE -> requestLinePrice(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_CLONE_REVERSE -> requestCloneReverse(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_DELETE -> plugin.getGuiManager().openConfirmAction(player, "DELETE_LINE",
                    lineId, line.getName(), null, 0, holder.snapshot());
            case GuiSlots.LINE_SETTINGS_COLOR -> requestLineColor(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_TERMINUS -> requestLineTerminus(player, line, previousView);
            case GuiSlots.LINE_SETTINGS_BACK -> plugin.getGuiManager().openPreviousView(player, holder,
                    () -> plugin.getGuiManager().openLineList(player, 0, false));
            default -> {
            }
        }
    }

    private void toggleRailProtection(Player player, Line line) {
        String lineId = line.getId();
        boolean enabled = !line.isRailProtected();
        if (plugin.getLineManager().setLineRailProtected(lineId, enabled)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.protect_updated",
                    args("line_id", lineId,
                            "state", plugin.getLanguageManager().getMessage(enabled
                                    ? "line.protect_state_enabled"
                                    : "line.protect_state_disabled"))));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.protect_update_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        }
    }

    private void requestLineRename(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        String oldName = line.getName();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_name"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        if (plugin.getLineManager().setLineName(lineId, input)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success",
                                    args("old_name", oldName, "new_name", input)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void requestLineSpeed(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_speed"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        try {
                            double speed = Double.parseDouble(input);
                            if (speed <= 0) {
                                throw new NumberFormatException();
                            }
                            if (plugin.getLineManager().setLineMaxSpeed(lineId, speed)) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_success",
                                        args("line_id", lineId, "max_speed", String.valueOf(speed))));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void requestLinePrice(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        String lineName = line.getName();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_price"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        try {
                            double price = Double.parseDouble(input);
                            if (price < 0) {
                                throw new NumberFormatException();
                            }
                            if (plugin.getLineManager().setLineTicketPrice(lineId, price)) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_success",
                                        args("line_name", lineName, "price", String.valueOf(price))));
                            } else {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_invalid"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void requestCloneReverse(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_clone_info"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        String[] parts = input.split(" ");
                        if (parts.length >= 1 && !parts[0].isEmpty()) {
                            String newId = parts[0];
                            String stopIdSuffix = parts.length > 1 ? parts[1] : "_rev";
                            if (plugin.getLineManager().cloneReverseLine(lineId, newId, stopIdSuffix,
                                    player.getUniqueId())) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_success",
                                        LanguageManager.put(LanguageManager.args(), "new_line_id", newId)));
                            } else {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_fail"));
                            }
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void requestLineColor(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_color"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        String color = input.trim();
                        LineCommandService.WriteStatus status = lineService.setColor(lineId, color);
                        if (status == LineCommandService.WriteStatus.INVALID_COLOR) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_invalid",
                                    LanguageManager.put(LanguageManager.args(), "color", color)));
                        } else if (status == LineCommandService.WriteStatus.SUCCESS) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success",
                                    args("line_id", lineId, "line_name", line.getName(), "color", color)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_fail"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void requestLineTerminus(Player player, Line line, GuiHolder.GuiView previousView) {
        String lineId = line.getId();
        plugin.getChatInputManager().requestInput(player,
                plugin.getLanguageManager().getMessage("chat.enter_new_terminus"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        String terminusName = input.trim();
                        if (lineService.setTerminusName(lineId, terminusName)
                                == LineCommandService.WriteStatus.SUCCESS) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_success",
                                    args("line_id", lineId, "terminus_name", terminusName)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_fail"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId, previousView);
                    }
                });
    }

    private void handleRouteRecordingToggle(Player player, Line line) {
        String lineId = line.getId();
        RouteRecorder recorder = plugin.getRouteRecorder();
        if (recorder.isRecording(lineId)) {
            RouteRecorder.FinishResult result = recorder.stopAndSave(lineId);
            switch (result.status()) {
                case SAVED -> player.sendMessage(plugin.getLanguageManager().getMessage("line.record_saved",
                        args("line_id", lineId, "point_count", String.valueOf(result.pointCount()))));
                case TOO_FEW_POINTS -> player.sendMessage(plugin.getLanguageManager().getMessage("line.record_too_few",
                        LanguageManager.put(LanguageManager.args(), "point_count", String.valueOf(result.pointCount()))));
                case FAILED -> player.sendMessage(plugin.getLanguageManager().getMessage("line.record_failed",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                case NOT_RECORDING -> player.sendMessage(plugin.getLanguageManager().getMessage("line.record_not_recording",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            }
            return;
        }

        if (recorder.start(lineId)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.record_started",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            player.sendMessage(plugin.getLanguageManager().getMessage("line.record_hint"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.record_already",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
        }
    }

    private void sendRouteInfo(Player player, Line line) {
        RouteRecorder recorder = plugin.getRouteRecorder();
        player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_header",
                args("line_name", line.getName(), "line_id", line.getId())));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_saved_points",
                LanguageManager.put(LanguageManager.args(), "point_count", String.valueOf(line.getRoutePoints().size()))));
        player.sendMessage(plugin.getLanguageManager().getMessage("line.protect_status",
                LanguageManager.put(LanguageManager.args(), "state", plugin.getLanguageManager().getMessage(line.isRailProtected()
                        ? "line.protect_state_enabled"
                        : "line.protect_state_disabled"))));
        int protectedBlocks = plugin.getRailProtectionManager() == null
                ? 0
                : plugin.getRailProtectionManager().getProtectedBlockCount(line.getId());
        player.sendMessage(plugin.getLanguageManager().getMessage("line.protect_blocks",
                LanguageManager.put(LanguageManager.args(), "count", String.valueOf(protectedBlocks))));
        if (line.isRailProtected() && protectedBlocks == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.protect_no_blocks"));
        }
        if (recorder.isRecording(line.getId())) {
            UUID cartId = recorder.getRecordingCartId(line.getId());
            player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_recording",
                    LanguageManager.put(LanguageManager.args(), "state",
                            plugin.getLanguageManager().getMessage("line.routeinfo_recording_active"))));
            player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_buffered_points",
                    LanguageManager.put(LanguageManager.args(), "point_count",
                            String.valueOf(recorder.getActivePointCount(line.getId())))));
            player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_bound_cart",
                    LanguageManager.put(LanguageManager.args(), "cart_id",
                            cartId == null
                                    ? plugin.getLanguageManager().getMessage("line.routeinfo_waiting_cart")
                                    : cartId.toString())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.routeinfo_recording",
                    LanguageManager.put(LanguageManager.args(), "state",
                            plugin.getLanguageManager().getMessage("line.routeinfo_recording_inactive"))));
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
