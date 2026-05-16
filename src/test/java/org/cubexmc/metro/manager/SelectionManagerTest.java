package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SelectionManagerTest {

    @Test
    void shouldStoreAndRetrieveCorners() {
        SelectionManager manager = new SelectionManager();
        Player player = mockPlayer();

        Location corner1 = mock(Location.class);
        Location corner2 = mock(Location.class);

        manager.setCorner1(player, corner1);
        manager.setCorner2(player, corner2);

        assertEquals(corner1, manager.getCorner1(player));
        assertEquals(corner2, manager.getCorner2(player));
    }

    @Test
    void shouldDetectIncompleteSelection() {
        SelectionManager manager = new SelectionManager();
        Player player = mockPlayer();

        assertFalse(manager.isSelectionComplete(player));
        assertNull(manager.getCorner1(player));

        manager.setCorner1(player, mock(Location.class));
        assertFalse(manager.isSelectionComplete(player));

        manager.setCorner2(player, mock(Location.class));
        assertTrue(manager.isSelectionComplete(player));
    }

    @Test
    void shouldIsolateSelectionsPerPlayer() {
        SelectionManager manager = new SelectionManager();
        Player player1 = mockPlayer();
        Player player2 = mockPlayer();

        Location loc1 = mock(Location.class);
        Location loc2 = mock(Location.class);

        manager.setCorner1(player1, loc1);
        manager.setCorner1(player2, loc2);

        assertEquals(loc1, manager.getCorner1(player1));
        assertEquals(loc2, manager.getCorner1(player2));
        assertFalse(manager.isSelectionComplete(player1));
        assertFalse(manager.isSelectionComplete(player2));
    }

    private Player mockPlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
