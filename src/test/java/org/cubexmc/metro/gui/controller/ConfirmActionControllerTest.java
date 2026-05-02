package org.cubexmc.metro.gui.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
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

    private GuiHolder confirmHolder(String action, String targetId) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.CONFIRM_ACTION);
        holder.setData("action", action);
        holder.setData("targetId", targetId);
        return holder;
    }
}
