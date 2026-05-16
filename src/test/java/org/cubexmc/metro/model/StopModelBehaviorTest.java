package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StopModelBehaviorTest {

    @Test
    void shouldTrackBoundsTransfersOwnershipAndLinkedLines() {
        World world = world("world");
        World otherWorld = world("other");
        Stop stop = new Stop("central", "Central");

        assertNull(stop.getRange3D());
        assertFalse(stop.isInStop(new Location(world, 2, 65, 2)));

        stop.setCorner1(new Location(world, 5, 70, 5));
        stop.setCorner2(new Location(world, 0, 64, 0));
        stop.setStopPointLocation(new Location(world, 2.4, 65, 2.7));
        stop.setLaunchYaw(90.0f);

        assertTrue(stop.isInStop(new Location(world, 2, 65, 2)));
        assertFalse(stop.isInStop(new Location(world, 6, 65, 2)));
        assertFalse(stop.isInStop(new Location(otherWorld, 2, 65, 2)));
        assertNotNull(stop.getRange3D());
        assertEquals("world", stop.getWorldName());

        assertTrue(stop.addTransferableLine("red"));
        assertFalse(stop.addTransferableLine("red"));
        assertEquals(List.of("red"), stop.getTransferableLines());
        assertTrue(stop.removeTransferableLine("red"));

        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        stop.setOwner(owner);
        assertTrue(stop.getAdmins().contains(owner));
        assertTrue(stop.addAdmin(admin));
        assertFalse(stop.addAdmin(admin));
        assertFalse(stop.removeAdmin(owner));
        assertTrue(stop.removeAdmin(admin));
        stop.setAdmins(Set.of(admin));
        assertTrue(stop.getAdmins().containsAll(Set.of(owner, admin)));

        assertFalse(stop.allowLine(""));
        assertTrue(stop.allowLine("red"));
        assertTrue(stop.isLineAllowed("red"));
        assertTrue(stop.denyLine("red"));
        stop.setLinkedLineIds(List.of("blue"));
        assertEquals(Set.of("blue"), stop.getLinkedLineIds());
    }

    @Test
    void shouldSaveAndLoadConfigSections() {
        World world = world("world");
        Stop source = new Stop("central", "Central");
        source.setCorner1(new Location(world, 0, 64, 0));
        source.setCorner2(new Location(world, 5, 70, 5));
        source.setStopPointLocation(new Location(world, 2, 65, 2));
        source.setLaunchYaw(180.0f);
        source.addTransferableLine("red");
        source.setOwner(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        source.addAdmin(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        source.allowLine("blue");
        source.setCustomTitle("stop_continuous", Map.of("title", "Welcome"));

        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("central");
        source.saveToConfig(section);

        assertEquals("Central", section.getString("display_name"));
        assertEquals("world,0,64,0", section.getString("corner1_location"));
        assertEquals("world,5,70,5", section.getString("corner2_location"));
        assertEquals("world,2,65,2", section.getString("stoppoint_location"));
        assertEquals(180.0, section.getDouble("launch_yaw"));
        assertEquals(List.of("red"), section.getStringList("transferable_lines"));
        assertEquals("Welcome", section.getString("custom_titles.stop_continuous.title"));

        Stop loaded = new Stop("central", section);
        assertEquals("central", loaded.getId());
        assertEquals("Central", loaded.getName());
        assertEquals(List.of("red"), loaded.getTransferableLines());
        assertEquals("Welcome", loaded.getCustomTitle("stop_continuous").get("title"));
        assertTrue(loaded.getAdmins().contains(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertTrue(loaded.getLinkedLineIds().contains("blue"));
    }

    @Test
    void shouldRemoveCustomTitles() {
        Stop stop = new Stop("central", "Central");

        assertFalse(stop.removeCustomTitle("stop_continuous"));
        stop.setCustomTitle("stop_continuous", Map.of("subtitle", "Next"));
        assertEquals("Next", stop.getCustomTitle("stop_continuous").get("subtitle"));
        assertTrue(stop.removeCustomTitle("stop_continuous"));
        assertNull(stop.getCustomTitle("stop_continuous"));
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
