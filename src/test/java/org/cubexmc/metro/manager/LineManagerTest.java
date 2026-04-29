package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.persistence.SaveCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadLineAndBuildStopIndexFromConfig() throws IOException {
        UUID recorderId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("lines.yml"), """
                l1:
                  name: MainLine
                  ordered_stop_ids:
                    - A
                    - B
                    - C
                  route_recorded_at: 1700000000000
                  route_recorded_by: '%s'
                  route_recorded_cart: '%s'
                  color: '&a'
                """.formatted(recorderId, cartId));

        LineManager manager = new LineManager(createPluginMock(tempDir));
        Line line = manager.getLine("l1");

        assertNotNull(line);
        assertEquals("MainLine", line.getName());
        assertEquals(List.of("A", "B", "C"), line.getOrderedStopIds());
        assertEquals(1700000000000L, line.getRouteRecordedAtEpochMillis());
        assertEquals(recorderId, line.getRouteRecordedBy());
        assertEquals(cartId, line.getRouteRecordedCartId());
        assertEquals(1, manager.getLinesForStop("B").size());
        assertTrue(manager.getLinesForStop("B").stream().anyMatch(it -> "l1".equals(it.getId())));
    }

    @Test
    void shouldCreateAddStopsAndDeleteLine() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        LineManager manager = new LineManager(createPluginMock(tempDir));

        boolean created = manager.createLine("blue", "BlueLine", UUID.randomUUID());
        assertTrue(created);
        assertTrue(manager.addStopToLine("blue", "S1", -1));
        assertTrue(manager.addStopToLine("blue", "S2", -1));
        assertEquals(1, manager.getLinesForStop("S1").size());

        assertTrue(manager.deleteLine("blue"));
        assertFalse(manager.deleteLine("blue"));
        assertTrue(manager.getLinesForStop("S1").isEmpty());
    }

    @Test
    void shouldRemoveDeletedLineFromSavedYaml() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        LineManager manager = new LineManager(createPluginMock(tempDir));

        assertTrue(manager.createLine("blue", "BlueLine", UUID.randomUUID()));
        manager.forceSaveSync();
        assertTrue(Files.readString(tempDir.resolve("lines.yml")).contains("blue:"));

        assertTrue(manager.deleteLine("blue"));
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("lines.yml"));
        assertFalse(savedYaml.contains("blue:"));
        assertFalse(savedYaml.contains("BlueLine"));
    }

    @Test
    void shouldSaveAndClearRouteRecordingMetadataWithRoutePoints() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        LineManager manager = new LineManager(createPluginMock(tempDir));
        UUID recorderId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        assertTrue(manager.createLine("red", "RedLine", UUID.randomUUID()));
        assertTrue(manager.setLineRoutePoints("red", List.of(
                new RoutePoint("world", 0.0, 64.0, 0.0),
                new RoutePoint("world", 5.0, 64.0, 0.0)
        ), 1700000000000L, recorderId, cartId));
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("lines.yml"));
        assertTrue(savedYaml.contains("route_recorded_at: 1700000000000"));
        assertTrue(savedYaml.contains("route_recorded_by: " + recorderId));
        assertTrue(savedYaml.contains("route_recorded_cart: " + cartId));

        assertTrue(manager.clearLineRoutePoints("red"));
        manager.forceSaveSync();

        savedYaml = Files.readString(tempDir.resolve("lines.yml"));
        assertFalse(savedYaml.contains("route_recorded_at"));
        assertFalse(savedYaml.contains("route_recorded_by"));
        assertFalse(savedYaml.contains("route_recorded_cart"));
    }

    private Metro createPluginMock(Path dataDir) {
        Metro plugin = mock(Metro.class);
        Logger logger = Logger.getLogger("LineManagerTest");
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getSaveCoordinator()).thenReturn(new SaveCoordinator(logger, Runnable::run));
        return plugin;
    }
}
