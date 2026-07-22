package org.cubexmc.metro.gui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class GuiListenerTest {

    private InventoryView viewWithTop(Inventory topInventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        return view;
    }

    @Test
    void shouldIgnoreInventoryClicksOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory topInventory = mock(Inventory.class);

        when(event.getView()).thenReturn(viewWithTop(topInventory));
        when(topInventory.getHolder()).thenReturn(mock(InventoryHolder.class));

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void shouldCancelMetroGuiClicksBeforeIgnoringOutsideSlots() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory topInventory = mock(Inventory.class);
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.MAIN_MENU);

        when(event.getView()).thenReturn(viewWithTop(topInventory));
        when(topInventory.getHolder()).thenReturn(holder);
        when(topInventory.getSize()).thenReturn(9);
        when(event.getRawSlot()).thenReturn(99);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelDragWhenSlotsAffectTopInventory() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory topInventory = mock(Inventory.class);

        when(event.getView()).thenReturn(viewWithTop(topInventory));
        when(topInventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));
        when(topInventory.getSize()).thenReturn(27);
        when(event.getRawSlots()).thenReturn(Set.of(5, 12));

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldAllowDragConfinedToPlayerLowerInventory() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory topInventory = mock(Inventory.class);

        when(event.getView()).thenReturn(viewWithTop(topInventory));
        when(topInventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));
        when(topInventory.getSize()).thenReturn(27);
        when(event.getRawSlots()).thenReturn(Set.of(27, 36, 45));

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void shouldIgnoreDraggingOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory topInventory = mock(Inventory.class);

        when(event.getView()).thenReturn(viewWithTop(topInventory));
        when(topInventory.getHolder()).thenReturn(null);

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }
}
