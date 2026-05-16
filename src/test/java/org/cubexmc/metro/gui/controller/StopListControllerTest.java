package org.cubexmc.metro.gui.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.gui.GuiSlots;
import org.junit.jupiter.api.Test;

class StopListControllerTest {

    @Test
    void shouldNavigateAndToggleStopListFilter() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = stopListHolder(1, false, 3);

        when(plugin.getGuiManager()).thenReturn(guiManager);

        StopListController controller = new StopListController(plugin);
        controller.handleStopListClick(player, holder, GuiSlots.SLOT_PREV_PAGE, false);
        controller.handleStopListClick(player, holder, GuiSlots.SLOT_NEXT_PAGE, false);
        controller.handleStopListClick(player, holder, GuiSlots.SLOT_FILTER, false);

        verify(guiManager).openStopList(player, 0, false, holder.getPreviousView());
        verify(guiManager).openStopList(player, 2, false, holder.getPreviousView());
        verify(guiManager).openStopList(player, 0, true, holder.getPreviousView());
    }

    private GuiHolder stopListHolder(int page, boolean showOnlyMine, int totalPages) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.STOP_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);
        holder.setData("totalPages", totalPages);
        return holder;
    }
}
