package org.cubexmc.metro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.cubexmc.metro.gui.GuiHolder.GuiType;
import org.cubexmc.metro.gui.GuiHolder.GuiView;

class GuiHolderTest {

    @Test
    void shouldStoreAndRetrieveType() {
        GuiHolder holder = new GuiHolder(GuiType.MAIN_MENU);
        assertEquals(GuiType.MAIN_MENU, holder.getType());
    }

    @Test
    void shouldStoreAndRetrieveData() {
        GuiHolder holder = new GuiHolder(GuiType.LINE_LIST);
        holder.setData("line_id", "l1");
        holder.setData("page", 3);

        assertEquals("l1", holder.<String>getData("line_id"));
        assertEquals(3, holder.<Integer>getData("page"));
        assertNull(holder.getData("nonexistent"));
    }

    @Test
    void shouldReturnDefaultWhenDataMissing() {
        GuiHolder holder = new GuiHolder(GuiType.STOP_LIST);
        assertEquals("fallback", holder.getData("missing", "fallback"));
        assertEquals(42, holder.<Integer>getData("count", 42));
    }

    @Test
    void shouldManagePreviousViewViaSnapshot() {
        GuiHolder parent = new GuiHolder(GuiType.MAIN_MENU);
        parent.setData("a", "b");
        GuiView parentSnapshot = parent.snapshot();

        GuiHolder holder = new GuiHolder(GuiType.LINE_DETAIL);
        holder.setPreviousView(parentSnapshot);
        assertEquals(GuiType.MAIN_MENU, holder.getPreviousView().getType());
        assertEquals("b", holder.getPreviousView().<String>getData("a"));
    }

    @Test
    void shouldManageInventory() {
        GuiHolder holder = new GuiHolder(GuiType.MAIN_MENU);
        assertNull(holder.getInventory());

        Inventory inv = org.mockito.Mockito.mock(Inventory.class);
        holder.setInventory(inv);
        assertEquals(inv, holder.getInventory());
    }

    @Test
    void shouldSnapshotImmutableData() {
        GuiHolder holder = new GuiHolder(GuiType.LINE_LIST);
        holder.setData("key", "value");

        GuiView snapshot = holder.snapshot();
        assertEquals(GuiType.LINE_LIST, snapshot.getType());
        assertEquals("value", snapshot.<String>getData("key"));
        assertEquals("default", snapshot.getData("missing", "default"));

        // Mutating original does not affect snapshot
        holder.setData("key", "changed");
        assertEquals("value", snapshot.<String>getData("key"));
    }

    @Test
    void shouldSupportNestedPreviousViewInSnapshot() {
        GuiHolder parent = new GuiHolder(GuiType.MAIN_MENU);
        parent.setData("a", "b");
        GuiView parentView = parent.snapshot();

        GuiHolder holder = new GuiHolder(GuiType.LINE_DETAIL);
        holder.setPreviousView(parentView);
        holder.setData("test", "data");

        GuiView snapshot = holder.snapshot();
        assertEquals(GuiType.MAIN_MENU, snapshot.getPreviousView().getType());
        assertEquals("b", snapshot.getPreviousView().<String>getData("a"));
        assertNull(snapshot.getPreviousView().getPreviousView());
    }

    @Test
    void shouldHaveAllGuiTypes() {
        assertTrue(GuiType.valueOf("MAIN_MENU") != null);
        assertTrue(GuiType.valueOf("CONFIRM_ACTION") != null);
        assertEquals(13, GuiType.values().length);
    }
}
