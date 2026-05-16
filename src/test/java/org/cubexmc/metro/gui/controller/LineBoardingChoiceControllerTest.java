package org.cubexmc.metro.gui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder;
import org.cubexmc.metro.gui.GuiSlots;
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LineBoardingChoiceControllerTest {

    @Test
    void shouldBoardTheLineIdDisplayedAtClickedSlot() {
        Metro plugin = mock(Metro.class);
        PlayerInteractListener interactListener = mock(PlayerInteractListener.class);
        Player player = mock(Player.class);
        GuiHolder holder = holder("central", 1, lineIds(40));

        when(plugin.getPlayerInteractListener()).thenReturn(interactListener);

        new LineBoardingChoiceController(plugin).handleClick(player, holder, 2, false);

        verify(player).closeInventory();
        verify(interactListener).boardSelectedLine(player, "central", "line-38");
    }

    @Test
    void shouldOpenLineDetailsForTheDisplayedLineOnRightClick() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        PlayerInteractListener interactListener = mock(PlayerInteractListener.class);
        Player player = mock(Player.class);
        GuiHolder holder = holder("central", 0, lineIds(5));

        when(plugin.getGuiManager()).thenReturn(guiManager);
        when(plugin.getPlayerInteractListener()).thenReturn(interactListener);

        new LineBoardingChoiceController(plugin).handleClick(player, holder, 3, true);

        ArgumentCaptor<GuiHolder.GuiView> previousViewCaptor = ArgumentCaptor.forClass(GuiHolder.GuiView.class);
        verify(guiManager).openLineDetail(eq(player), eq("line-03"), eq(0), previousViewCaptor.capture());
        GuiHolder.GuiView previousView = previousViewCaptor.getValue();
        assertEquals(GuiHolder.GuiType.LINE_BOARDING_CHOICE, previousView.getType());
        assertEquals("central", previousView.getData("stopId"));
        assertEquals(0, previousView.getData("page", -1));
        verify(interactListener, never()).boardSelectedLine(player, "central", "line-03");
        verify(player, never()).closeInventory();
    }

    @Test
    void shouldIgnoreClicksOutsideDisplayedLineSlots() {
        Metro plugin = mock(Metro.class);
        PlayerInteractListener interactListener = mock(PlayerInteractListener.class);
        Player player = mock(Player.class);
        GuiHolder holder = holder("central", 0, lineIds(2));

        when(plugin.getPlayerInteractListener()).thenReturn(interactListener);

        new LineBoardingChoiceController(plugin).handleClick(player, holder, 5, false);

        verify(interactListener, never()).boardSelectedLine(player, "central", "line-05");
        verify(player, never()).closeInventory();
    }

    @Test
    void shouldNavigatePagesWithTheSameStopAndPreviousView() {
        Metro plugin = mock(Metro.class);
        GuiManager guiManager = mock(GuiManager.class);
        StopManager stopManager = mock(StopManager.class);
        Player player = mock(Player.class);
        Stop stop = new Stop("central", "Central");
        GuiHolder holder = holder("central", 1, lineIds(80));
        holder.setData("totalPages", 3);

        when(plugin.getGuiManager()).thenReturn(guiManager);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(stopManager.getStop("central")).thenReturn(stop);

        LineBoardingChoiceController controller = new LineBoardingChoiceController(plugin);
        controller.handleClick(player, holder, GuiSlots.SLOT_PREV_PAGE, false);
        controller.handleClick(player, holder, GuiSlots.SLOT_NEXT_PAGE, false);

        verify(guiManager).openLineBoardingChoice(player, stop, 0, holder.getPreviousView());
        verify(guiManager).openLineBoardingChoice(player, stop, 2, holder.getPreviousView());
    }

    private GuiHolder holder(String stopId, int page, List<String> lineIds) {
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.LINE_BOARDING_CHOICE);
        holder.setData("stopId", stopId);
        holder.setData("page", page);
        holder.setData("totalPages", Math.max(1, (int) Math.ceil((double) lineIds.size() / GuiSlots.ITEMS_PER_PAGE)));
        holder.setData("lineIds", lineIds);
        return holder;
    }

    private List<String> lineIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(String.format("line-%02d", i));
        }
        return ids;
    }
}
