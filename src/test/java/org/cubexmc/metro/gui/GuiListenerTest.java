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

    @Test
    void shouldIgnoreInventoryClicksOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(mock(InventoryHolder.class));

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void shouldCancelMetroGuiClicksBeforeIgnoringOutsideSlots() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.MAIN_MENU);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(9);
        when(event.getRawSlot()).thenReturn(99);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelDraggingInsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));
        when(inventory.getSize()).thenReturn(9);
        when(event.getRawSlots()).thenReturn(Set.of(4, 12));

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldIgnoreDraggingOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(null);

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void shouldAllowDragThatOnlyTouchesPlayerInventoryBelowMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));
        when(inventory.getSize()).thenReturn(9);
        when(event.getRawSlots()).thenReturn(Set.of(9, 10));

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }
}
