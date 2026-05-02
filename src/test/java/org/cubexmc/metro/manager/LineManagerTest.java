package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                  portal_ids:
                    - p1
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
        assertEquals(List.of("p1"), line.getPortalIds());
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

    @Test
    void shouldPersistLineSettingsOwnerAdminsWorldAndRailProtection() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        LineManager manager = new LineManager(createPluginMock(tempDir));

        assertTrue(manager.createLine("green", "GreenLine", ownerId));
        assertTrue(manager.setLineName("green", "Renamed Green"));
        assertTrue(manager.setLineColor("green", "&#00FF00"));
        assertTrue(manager.setLineTerminusName("green", "Downtown"));
        assertTrue(manager.setLineMaxSpeed("green", 0.45D));
        assertTrue(manager.setLineTicketPrice("green", 2.5D));
        assertTrue(manager.setLineWorldName("green", "world"));
        assertTrue(manager.setLineRailProtected("green", true));
        assertFalse(manager.addLineAdmin("missing", adminId));
        assertTrue(manager.addLineAdmin("green", adminId));
        assertFalse(manager.addLineAdmin("green", adminId));
        assertFalse(manager.removeLineAdmin("green", ownerId));
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("lines.yml"));
        assertTrue(savedYaml.contains("name: Renamed Green"));
        assertTrue(savedYaml.contains("color: '&#00FF00'"));
        assertTrue(savedYaml.contains("terminus_name: Downtown"));
        assertTrue(savedYaml.contains("max_speed: 0.45"));
        assertTrue(savedYaml.contains("ticket_price: 2.5"));
        assertTrue(savedYaml.contains("rail_protected: true"));
        assertTrue(savedYaml.contains("owner: " + ownerId));
        assertTrue(savedYaml.contains("- " + adminId));
        assertTrue(savedYaml.contains("world: world"));
    }

    @Test
    void shouldMaintainStopIndexWhenStopsMoveAndAreRemovedFromAllLines() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        LineManager manager = new LineManager(createPluginMock(tempDir));
        UUID ownerId = UUID.randomUUID();

        assertTrue(manager.createLine("red", "RedLine", ownerId));
        assertTrue(manager.createLine("blue", "BlueLine", ownerId));
        assertTrue(manager.addStopToLine("red", "central", -1));
        assertTrue(manager.addStopToLine("red", "harbor", -1));
        assertTrue(manager.addStopToLine("blue", "central", -1));

        assertEquals(2, manager.getLinesForStop("central").size());
        assertTrue(manager.addStopToLine("red", "central", 1));
        assertEquals(List.of("harbor", "central"), manager.getLine("red").getOrderedStopIds());
        assertEquals(2, manager.getLinesForStop("central").size());

        assertTrue(manager.delStopFromLine("blue", "central"));
        assertEquals(1, manager.getLinesForStop("central").size());
        manager.delStopFromAllLines("central");
        assertTrue(manager.getLinesForStop("central").isEmpty());
        assertEquals(List.of("harbor"), manager.getLine("red").getOrderedStopIds());
        assertTrue(manager.getLine("blue").getOrderedStopIds().isEmpty());
        assertFalse(manager.delStopFromLine("missing", "central"));
    }

    @Test
    void shouldPersistAndRemoveLinePortals() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        LineManager manager = new LineManager(createPluginMock(tempDir));

        assertTrue(manager.createLine("red", "RedLine", UUID.randomUUID()));
        assertTrue(manager.createLine("blue", "BlueLine", UUID.randomUUID()));
        assertTrue(manager.addPortalToLine("red", "p1"));
        assertFalse(manager.addPortalToLine("red", "p1"));
        assertTrue(manager.addPortalToLine("blue", "p1"));
        assertTrue(manager.addPortalToLine("red", "p2"));
        assertEquals(List.of("p1", "p2"), manager.getLine("red").getPortalIds());

        assertTrue(manager.delPortalFromLine("red", "p2"));
        assertFalse(manager.delPortalFromLine("red", "missing"));
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("lines.yml"));
        assertTrue(savedYaml.contains("portal_ids:"));
        assertTrue(savedYaml.contains("- p1"));
        assertFalse(savedYaml.contains("- p2"));

        manager.delPortalFromAllLines("p1");
        assertTrue(manager.getLine("red").getPortalIds().isEmpty());
        assertTrue(manager.getLine("blue").getPortalIds().isEmpty());
    }

    @Test
    void shouldNotifyRailProtectionManagerForLineLifecycleChanges() throws IOException {
        Files.writeString(tempDir.resolve("lines.yml"), "");
        RailProtectionManager railProtectionManager = mock(RailProtectionManager.class);
        Metro plugin = createPluginMock(tempDir);
        when(plugin.getRailProtectionManager()).thenReturn(railProtectionManager);
        LineManager manager = new LineManager(plugin);

        assertFalse(manager.setLineRailProtected("missing", true));
        verify(railProtectionManager, never()).rebuildLine("missing");

        assertTrue(manager.createLine("red", "RedLine", UUID.randomUUID()));
        assertTrue(manager.setLineRoutePoints("red", List.of(new RoutePoint("world", 1, 64, 1))));
        assertTrue(manager.clearLineRoutePoints("red"));
        assertTrue(manager.deleteLine("red"));

        verify(railProtectionManager, org.mockito.Mockito.times(4)).rebuildLine("red");
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
