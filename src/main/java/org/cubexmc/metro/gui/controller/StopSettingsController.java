package org.cubexmc.metro.gui.controller;

import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.ChatInputManager;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.StopCommandService;

public final class StopSettingsController {
    private static final int SLOT_RENAME = 11;
    private static final int SLOT_SET_POINT = 13;
    private static final int SLOT_DELETE = 15;
    private static final int SLOT_BACK = 22;

    private final Metro plugin;
    private final StopCommandService stopService;
    private final GuiPermissionGuard permissionGuard;

    public StopSettingsController(Metro plugin) {
        this.plugin = plugin;
        this.stopService = new StopCommandService(plugin.getStopManager());
        this.permissionGuard = new GuiPermissionGuard(plugin);
    }

    public void handleClick(Player player, GuiHolder holder, int slot) {
        String stopId = holder.getData("stopId");
        String fromLineId = holder.getData("fromLineId");
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            player.closeInventory();
            return;
        }

        GuiHolder.GuiView previousView = holder.getPreviousView();
        switch (slot) {
            case SLOT_RENAME -> requestStopRename(player, stop, previousView, fromLineId);
            case SLOT_SET_POINT -> handleSetStopPoint(player, stop, previousView, fromLineId);
            case SLOT_DELETE -> {
                if (!permissionGuard.requireManageStop(player, stop)) {
                    player.closeInventory();
                    return;
                }
                plugin.getGuiManager().openConfirmAction(player, "DELETE_STOP",
                        stopId, stop.getName(), fromLineId, 0, holder.snapshot());
            }
            case SLOT_BACK -> plugin.getGuiManager().openPreviousView(player, holder, () -> {
                if (fromLineId != null) {
                    plugin.getGuiManager().openLineDetail(player, fromLineId, 0);
                } else {
                    plugin.getGuiManager().openStopList(player, 0, false);
                }
            });
            default -> {
            }
        }
    }

    private void requestStopRename(Player player, Stop stop, GuiHolder.GuiView previousView, String fromLineId) {
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory();
            return;
        }
        String stopId = stop.getId();
        String oldName = stop.getName();
        plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_name"),
                new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        if (requireCurrentStop(player, stopId) == null) {
                            return;
                        }
                        if (stopService.renameStop(stopId, input) == StopCommandService.WriteStatus.SUCCESS) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_success",
                                    args("old_name", oldName, "new_name", input)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_fail"));
                        }
                        plugin.getGuiManager().openStopSettings(player, stopId, fromLineId, previousView);
                    }

                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openStopSettings(player, stopId, fromLineId, previousView);
                    }
                });
    }

    private void handleSetStopPoint(Player player, Stop stop, GuiHolder.GuiView previousView, String fromLineId) {
        if (!permissionGuard.requireManageStop(player, stop)) {
            player.closeInventory();
            return;
        }
        StopCommandService.SetPointResult result = stopService.setPoint(stop.getId(), stop, player.getLocation(), null);
        switch (result.status()) {
            case SUCCESS -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_success",
                    args("stop_id", stop.getId(), "yaw", String.format("%.1f", result.yaw()))));
            case NOT_RAIL -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_rail"));
            case NOT_IN_STOP -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_not_in_area",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("stop.setpoint_fail"));
        }
        plugin.getGuiManager().openStopSettings(player, stop.getId(), fromLineId, previousView);
    }

    private Stop requireCurrentStop(Player player, String stopId) {
        Stop currentStop = plugin.getStopManager().getStop(stopId);
        if (currentStop == null) {
            player.closeInventory();
            return null;
        }
        if (!permissionGuard.requireManageStop(player, currentStop)) {
            player.closeInventory();
            return null;
        }
        return currentStop;
    }

    private Map<String, Object> args(Object... replacements) {
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, String.valueOf(replacements[i]), replacements[i + 1]);
        }
        return args;
    }
}
