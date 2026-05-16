package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.RoutePoint;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RailProtectionManagerTest {

    @Test
    void shouldIndexInterpolatedRailBlocksAndRemoveLineFromIndex() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = protectedLine("red", List.of(
                new RoutePoint("world", 0.5, 64.0, 0.5),
                new RoutePoint("world", 2.5, 64.0, 0.5)
        ));
        when(lineManager.getAllLines()).thenReturn(List.of(line));
        when(lineManager.getLine("red")).thenReturn(line);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
                    int x = invocation.getArgument(0);
                    int y = invocation.getArgument(1);
                    int z = invocation.getArgument(2);
                    return block(world, x, y, z, y == 64 && z == 0 && x >= 0 && x <= 2
                            ? Material.RAIL
                            : Material.AIR);
                });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            RailProtectionManager manager = new RailProtectionManager(plugin);
            manager.rebuildAll();

            assertEquals(3, manager.getProtectedBlockCount("red"));
            assertEquals(new RailProtectionManager.ProtectionIndexStats(5, 3, 0, 0, 0),
                    manager.getProtectionIndexStats("red"));

            line.setRailProtected(false);
            manager.rebuildLine("red");

            assertEquals(0, manager.getProtectedBlockCount("red"));
            assertEquals(RailProtectionManager.ProtectionIndexStats.empty(),
                    manager.getProtectionIndexStats("red"));
        }
    }

    @Test
    void shouldRecordWorldMismatchWithoutLookingUpMissingWorld() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Line line = protectedLine("red", List.of(new RoutePoint("other", 0.5, 64.0, 0.5)));
        when(lineManager.getAllLines()).thenReturn(List.of(line));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            RailProtectionManager manager = new RailProtectionManager(plugin);
            manager.rebuildAll();

            assertEquals(0, manager.getProtectedBlockCount("red"));
            assertEquals(new RailProtectionManager.ProtectionIndexStats(1, 0, 1, 0, 0),
                    manager.getProtectionIndexStats("red"));
            bukkit.verify(() -> Bukkit.getWorld(anyString()), org.mockito.Mockito.never());
        }
    }

    @Test
    void shouldEnforceProtectedRailBreakPermissions() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        ConfigFacade configFacade = mock(ConfigFacade.class);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(configFacade.isSafeModePassengerRailBreakProtection()).thenReturn(false);
        when(languageManager.getMessage("protection.rail_break_denied")).thenReturn("denied");

        java.util.UUID ownerId = java.util.UUID.randomUUID();
        Line line = protectedLine("red", List.of(new RoutePoint("world", 0.5, 64.0, 0.5)));
        line.setOwner(ownerId);
        when(lineManager.getAllLines()).thenReturn(List.of(line));
        when(lineManager.getLine("red")).thenReturn(line);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Block rail = block(world, 0, 64, 0, Material.RAIL);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(rail);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            RailProtectionManager manager = new RailProtectionManager(plugin);
            manager.rebuildAll();

            BlockBreakEvent deniedEvent = new BlockBreakEvent(rail, player(java.util.UUID.randomUUID(), false));
            manager.onBlockBreak(deniedEvent);
            assertTrue(deniedEvent.isCancelled());

            BlockBreakEvent ownerEvent = new BlockBreakEvent(rail, player(ownerId, false));
            manager.onBlockBreak(ownerEvent);
            assertFalse(ownerEvent.isCancelled());
        }
    }

    private Line protectedLine(String id, List<RoutePoint> routePoints) {
        Line line = new Line(id, id);
        line.setWorldName("world");
        line.setRailProtected(true);
        line.setRoutePoints(routePoints);
        return line;
    }

    private Block block(World world, int x, int y, int z, Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private Player player(java.util.UUID uuid, boolean adminPermission) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("metro.admin")).thenReturn(adminPermission);
        when(player.isOp()).thenReturn(false);
        return player;
    }
}
