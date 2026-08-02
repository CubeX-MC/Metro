package org.cubexmc.metro.gui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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

    @Test
    void shouldRemoveLeakedGuiItemsWhenMetroGuiCloses() {
        Metro plugin = mock(Metro.class);
        GuiListener listener = new GuiListener(plugin);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        Player player = mock(Player.class);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        ItemStack guiItem = mock(ItemStack.class);
        ItemStack ownItem = mock(ItemStack.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(new GuiHolder(GuiHolder.GuiType.LINE_LIST));
        when(event.getPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Alice");
        when(player.getInventory()).thenReturn(playerInventory);
        when(player.getItemOnCursor()).thenReturn(guiItem);
        when(playerInventory.getContents()).thenReturn(new ItemStack[] { ownItem, guiItem });

        try (MockedStatic<GuiItemMarker> itemBuilder = mockStatic(GuiItemMarker.class);
                MockedStatic<SchedulerUtil> scheduler = mockStatic(SchedulerUtil.class)) {
            itemBuilder.when(() -> GuiItemMarker.isGuiItem(guiItem)).thenReturn(true);
            itemBuilder.when(() -> GuiItemMarker.isGuiItem(ownItem)).thenReturn(false);

            listener.onInventoryClose(event);
        }

        verify(player).setItemOnCursor(null);
        verify(playerInventory).setItem(1, null);
        verify(playerInventory, never()).setItem(0, null);
    }

    @Test
    void shouldIgnoreCloseOfForeignInventory() {
        GuiListener listener = new GuiListener(mock(Metro.class));
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        Player player = mock(Player.class);

        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(mock(InventoryHolder.class));
        when(event.getPlayer()).thenReturn(player);

        listener.onInventoryClose(event);

        verify(player, never()).getInventory();
    }
}
