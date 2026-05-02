package org.cubexmc.metro.gui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LineListControllerTest {

    @Test
    void shouldNavigateAndToggleLineListFilter() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = lineListHolder(2, true, 4);

        when(plugin.getGuiManager()).thenReturn(guiManager);

        LineListController controller = new LineListController(plugin);
        controller.handleLineListClick(player, holder, GuiSlots.SLOT_PREV_PAGE, false);
        controller.handleLineListClick(player, holder, GuiSlots.SLOT_NEXT_PAGE, false);
        controller.handleLineListClick(player, holder, GuiSlots.SLOT_FILTER, false);

        verify(guiManager).openLineList(player, 1, true, holder.getPreviousView());
        verify(guiManager).openLineList(player, 3, true, holder.getPreviousView());
        verify(guiManager).openLineList(player, 0, false, holder.getPreviousView());
    }

    @Test
    void shouldOpenVariantsWhenDisplayedLineNameHasMultipleLines() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = lineListHolder(0, false, 1);
        Line redInbound = new Line("red_in", "Red");
        Line redOutbound = new Line("red_out", "Red");
        holder.setData("lineNames", List.of("Red"));
        holder.setData("groupedLines", Map.of("Red", List.of(redInbound, redOutbound)));

        when(plugin.getGuiManager()).thenReturn(guiManager);

        new LineListController(plugin).handleLineListClick(player, holder, 0, false);

        ArgumentCaptor<GuiHolder.GuiView> previousViewCaptor = ArgumentCaptor.forClass(GuiHolder.GuiView.class);
        verify(guiManager).openLineVariants(eq(player), eq("Red"), eq(0), previousViewCaptor.capture());
        GuiHolder.GuiView previousView = previousViewCaptor.getValue();
        assertEquals(GuiHolder.GuiType.LINE_LIST, previousView.getType());
        assertEquals(0, previousView.getData("page", -1));
        assertEquals(false, previousView.getData("showOnlyMine", true));
    }

    @Test
    void shouldOpenSingleLineDetailFromDisplayedSlot() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        Player player = mock(Player.class);
        GuiHolder holder = lineListHolder(0, false, 1);
        Line target = new Line("target", "Target");
        holder.setData("lineNames", List.of("a", "b", "c", "d", "target"));
        holder.setData("groupedLines", Map.of("target", List.of(target)));

        when(plugin.getGuiManager()).thenReturn(guiManager);

        new LineListController(plugin).handleLineListClick(player, holder, 4, false);

        verify(guiManager).openLineDetail(eq(player), eq("target"), eq(0), org.mockito.ArgumentMatchers.any());
    }

    private GuiHolder lineListHolder(int page, boolean showOnlyMine, int totalPages) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.LINE_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);
        holder.setData("totalPages", totalPages);
        return holder;
    }
}
