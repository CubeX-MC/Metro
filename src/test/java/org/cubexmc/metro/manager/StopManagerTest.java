package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.persistence.SaveCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StopManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateStopAndResolveByLocation() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(createPluginMock(tempDir));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location c1 = new Location(world, 0, 0, 0);
        Location c2 = new Location(world, 10, 10, 10);

        assertNotNull(manager.createStop("s1", "Central", c1, c2, UUID.randomUUID()));
        assertNotNull(manager.getStopContainingLocation(new Location(world, 5, 5, 5)));
        assertNull(manager.getStopContainingLocation(new Location(world, 20, 20, 20)));
    }

    @Test
    void shouldDeleteStopAndNotifyLineManager() throws IOException {
        LineManager lineManager = mock(LineManager.class);
        Metro plugin = createPluginMock(tempDir);
        when(plugin.getLineManager()).thenReturn(lineManager);

        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(plugin);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location c1 = new Location(world, 0, 0, 0);
        Location c2 = new Location(world, 3, 3, 3);
        manager.createStop("s2", "Harbor", c1, c2, UUID.randomUUID());

        assertTrue(manager.deleteStop("s2"));
        verify(lineManager).delStopFromAllLines("s2");
        assertEquals(0, manager.getAllStopIds().size());
    }

    @Test
    void shouldFlushDirtyStopDataSynchronously() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(createPluginMock(tempDir));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location c1 = new Location(world, 0, 0, 0);
        Location c2 = new Location(world, 3, 3, 3);
        manager.createStop("s3", "Museum", c1, c2, UUID.randomUUID());

        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("stops.yml"));
        assertTrue(savedYaml.contains("s3:"));
        assertTrue(savedYaml.contains("display_name: Museum"));
    }

    @Test
    void shouldReindexStopWhenCornersOrStopPointChange() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(createPluginMock(tempDir));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location oldCorner1 = new Location(world, 0, 0, 0);
        Location oldCorner2 = new Location(world, 3, 3, 3);
        Location newCorner1 = new Location(world, 10, 10, 10);
        Location newCorner2 = new Location(world, 13, 13, 13);
        manager.createStop("s4", "Gallery", oldCorner1, oldCorner2, UUID.randomUUID());

        assertEquals("s4", manager.getStopContainingLocation(new Location(world, 1, 1, 1)).getId());
        assertTrue(manager.setStopCorners("s4", newCorner1, newCorner2));
        assertNull(manager.getStopContainingLocation(new Location(world, 1, 1, 1)));
        assertEquals("s4", manager.getStopContainingLocation(new Location(world, 11, 11, 11)).getId());
        assertTrue(manager.setStopPoint("s4", new Location(world, 12, 12, 12), 45.0F));
        assertEquals(45.0F, manager.getStop("s4").getLaunchYaw());
        assertFalse(manager.setStopCorners("missing", oldCorner1, oldCorner2));
        assertFalse(manager.setStopPoint("missing", oldCorner1, 0.0F));
    }

    @Test
    void shouldChooseBestOverlappingStopByYaw() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(createPluginMock(tempDir));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location eastCorner1 = new Location(world, 0, 0, 0);
        Location eastCorner2 = new Location(world, 5, 5, 5);
        Location westCorner1 = new Location(world, 1, 1, 1);
        Location westCorner2 = new Location(world, 6, 6, 6);
        manager.createStop("east", "Eastbound", eastCorner1, eastCorner2, UUID.randomUUID());
        manager.createStop("west", "Westbound", westCorner1, westCorner2, UUID.randomUUID());
        manager.setStopPoint("east", new Location(world, 1, 1, 1), 0.0F);
        manager.setStopPoint("west", new Location(world, 1, 1, 1), 180.0F);

        assertEquals("west", manager.getBestStopContainingLocation(new Location(world, 2, 2, 2), 170.0F).getId());
        assertEquals("east", manager.getBestStopContainingLocation(new Location(world, 2, 2, 2), 350.0F).getId());
        assertNull(manager.getBestStopContainingLocation(new Location(world, 10, 10, 10), 0.0F));
        assertNull(manager.getBestStopContainingLocation(null, 0.0F));
    }

    @Test
    void shouldPersistStopOwnershipAdminsTransfersAndLinkedLines() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), "");
        StopManager manager = new StopManager(createPluginMock(tempDir));
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertNotNull(manager.createStop("s5", "", null, null, ownerId));
        assertEquals("s5", manager.getStop("s5").getName());
        assertTrue(manager.setStopName("s5", "Depot"));
        assertTrue(manager.setStopOwner("s5", ownerId));
        assertTrue(manager.addStopAdmin("s5", adminId));
        assertFalse(manager.addStopAdmin("s5", adminId));
        assertFalse(manager.removeStopAdmin("s5", ownerId));
        assertTrue(manager.addTransferLine("s5", "red"));
        assertFalse(manager.addTransferLine("s5", "red"));
        assertTrue(manager.addTransferLine("s5", "blue"));
        assertTrue(manager.removeTransferLine("s5", "blue"));
        assertFalse(manager.removeTransferLine("s5", "missing"));
        assertEquals(List.of("red"), manager.getTransferableLines("s5"));
        assertTrue(manager.allowLineLink("s5", "red"));
        assertFalse(manager.allowLineLink("s5", "red"));
        assertFalse(manager.allowLineLink("missing", "red"));
        assertFalse(manager.denyLineLink("s5", "blue"));
        assertTrue(manager.denyLineLink("s5", "red"));
        assertTrue(manager.allowLineLink("s5", "green"));
        assertFalse(manager.setStopName("missing", "Nowhere"));
        assertFalse(manager.setStopOwner("missing", ownerId));
        assertFalse(manager.addStopAdmin("missing", adminId));
        assertFalse(manager.removeStopAdmin("missing", adminId));
        assertFalse(manager.addTransferLine("missing", "red"));
        assertFalse(manager.removeTransferLine("missing", "red"));
        assertTrue(manager.getTransferableLines("missing").isEmpty());
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("stops.yml"));
        assertTrue(savedYaml.contains("display_name: Depot"));
        assertTrue(savedYaml.contains("owner: " + ownerId));
        assertTrue(savedYaml.contains("- " + adminId));
        assertTrue(savedYaml.contains("transferable_lines:"));
        assertTrue(savedYaml.contains("- red"));
        assertTrue(savedYaml.contains("linked_lines:"));
        assertTrue(savedYaml.contains("- green"));
    }

    private Metro createPluginMock(Path dataDir) {
        Metro plugin = mock(Metro.class);
        Logger logger = Logger.getLogger("StopManagerTest");
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getSaveCoordinator()).thenReturn(new SaveCoordinator(logger, Runnable::run));
        return plugin;
    }
}
