package org.cubexmc.metro.gui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class GuiListenerTest {

    @Test
    void shouldIgnoreInventoryClicksOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory inventory = mock(Inventory.class);

        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(mock(InventoryHolder.class));

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void shouldCancelMetroGuiClicksBeforeIgnoringOutsideSlots() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Inventory inventory = mock(Inventory.class);
        GuiHolder holder = new GuiHolder(GuiHolder.GuiType.MAIN_MENU);

        when(event.getInventory()).thenReturn(inventory);
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

        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldIgnoreDraggingOutsideMetroGui() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        Inventory inventory = mock(Inventory.class);

        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(null);

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }
}
