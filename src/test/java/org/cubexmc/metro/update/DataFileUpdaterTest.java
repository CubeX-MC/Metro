package org.cubexmc.metro.update;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataFileUpdaterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMigrateLegacyLineKeys() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), """
                red:
                  name: Red
                  ordered_platform_ids:
                    - A
                    - B
                """);

        DataFileUpdater.migrateLines(createPluginMock());

        YamlConfiguration config = YamlConfiguration.loadConfiguration(tempDir.resolve("lines.yml").toFile());
        assertEquals(DataFileUpdater.CURRENT_SCHEMA_VERSION, config.getInt("schema_version"));
        assertEquals(java.util.List.of("A", "B"), config.getStringList("red.ordered_stop_ids"));
        assertFalse(config.contains("red.ordered_platform_ids"));
        assertTrue(config.contains("red.route_points"));
        assertFalse(config.getBoolean("red.rail_protected"));
    }

    @Test
    void shouldMigrateLegacyStopKeys() throws IOException {
        Files.writeString(tempDir.resolve("stops.yml"), """
                central:
                  name: Central
                  corner1: world,0,64,0
                  corner2: world,5,70,5
                  stopPoint: world,2,65,2
                  launchYaw: 90.0
                """);

        DataFileUpdater.migrateStops(createPluginMock());

        YamlConfiguration config = YamlConfiguration.loadConfiguration(tempDir.resolve("stops.yml").toFile());
        assertEquals("Central", config.getString("central.display_name"));
        assertEquals("world,0,64,0", config.getString("central.corner1_location"));
        assertEquals("world,5,70,5", config.getString("central.corner2_location"));
        assertEquals("world,2,65,2", config.getString("central.stoppoint_location"));
        assertEquals(90.0, config.getDouble("central.launch_yaw"));
        assertFalse(config.contains("central.stopPoint"));
    }

    @Test
    void shouldMigrateRootPortalSections() throws IOException {
        Files.writeString(tempDir.resolve("portals.yml"), """
                p1:
                  entrance: world,10,64,10
                  destination: world_nether,20.5,70.0,20.5,180.0
                """);

        DataFileUpdater.migratePortals(createPluginMock());

        YamlConfiguration config = YamlConfiguration.loadConfiguration(tempDir.resolve("portals.yml").toFile());
        assertFalse(config.contains("p1"));
        assertEquals("world", config.getString("portals.p1.world"));
        assertEquals(10, config.getInt("portals.p1.x"));
        assertEquals("world_nether", config.getString("portals.p1.dest_world"));
        assertEquals(20.5, config.getDouble("portals.p1.dest_x"));
        assertEquals(180.0, config.getDouble("portals.p1.dest_yaw"));
    }

    private JavaPlugin createPluginMock() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DataFileUpdaterTest"));
        return plugin;
    }
}
