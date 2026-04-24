package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.cubexmc.metro.Metro;
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

    private Metro createPluginMock(Path dataDir) {
        Metro plugin = mock(Metro.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StopManagerTest"));
        return plugin;
    }
}
