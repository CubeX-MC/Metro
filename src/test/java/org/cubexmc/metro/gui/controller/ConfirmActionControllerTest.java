package org.cubexmc.metro.gui.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class ConfirmActionControllerTest {

    @Test
    void shouldReturnWithoutDeletingWhenCancelIsClicked() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = confirmHolder("DELETE_LINE", "red");

        when(plugin.getGuiManager()).thenReturn(guiManager);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getStopManager()).thenReturn(stopManager);

        new ConfirmActionController(plugin).handleClick(player, holder, 15);

        verify(guiManager).openPreviousView(org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(holder), org.mockito.ArgumentMatchers.any(Runnable.class));
        verify(lineManager, never()).deleteLine("red");
        verify(stopManager, never()).deleteStop(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldIgnoreNonControlSlotsWithoutDeleting() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        LineManager lineManager = mock(LineManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = confirmHolder("DELETE_LINE", "red");

        when(plugin.getGuiManager()).thenReturn(guiManager);
        when(plugin.getLineManager()).thenReturn(lineManager);

        new ConfirmActionController(plugin).handleClick(player, holder, 0);

        verify(guiManager, never()).openPreviousView(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(lineManager, never()).deleteLine("red");
    }

    @Test
    void shouldRecheckLineOwnershipBeforeConfirmedDelete() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        Player player = mock(Player.class);
        Line line = new Line("red", "Red");
        GuiHolder holder = confirmHolder("DELETE_LINE", "red");

        when(plugin.getGuiManager()).thenReturn(guiManager);
        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(lineManager.getLine("red")).thenReturn(line);
        when(player.hasPermission("metro.admin")).thenReturn(false);
        when(player.isOp()).thenReturn(false);
        when(languageManager.getMessage("ownership.server")).thenReturn("Server");
        when(languageManager.getMessage("ownership.none")).thenReturn("None");
        when(languageManager.getMessage(org.mockito.ArgumentMatchers.eq("line.permission_manage"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn("no line access");

        new ConfirmActionController(plugin).handleClick(player, holder, 11);

        verify(player).sendMessage("no line access");
        verify(player).closeInventory();
        verify(lineManager, never()).deleteLine("red");
    }

    private GuiHolder confirmHolder(String action, String targetId) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.CONFIRM_ACTION);
        holder.setData("action", action);
        holder.setData("targetId", targetId);
        return holder;
    }
}
