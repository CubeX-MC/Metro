package org.cubexmc.metro.gui.controller;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;

public final class MainMenuController {
    private static final int SLOT_LINE_MANAGE = 11;
    private static final int SLOT_STOP_MANAGE = 15;

    private final Metro plugin;

    public MainMenuController(Metro plugin) {
        this.plugin = plugin;
    }

    public void handleClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case SLOT_LINE_MANAGE -> plugin.getGuiManager().openLineList(player, 0, false, holder.snapshot());
            case SLOT_STOP_MANAGE -> plugin.getGuiManager().openStopList(player, 0, false, holder.snapshot());
            default -> {
            }
        }
    }
}
