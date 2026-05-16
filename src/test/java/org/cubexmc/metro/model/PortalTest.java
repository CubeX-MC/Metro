package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PortalTest {

    @Test
    void shouldRoundTripConfigAndMatchEntrance() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("portal");
        section.set("world", "world");
        section.set("x", 10);
        section.set("y", 64);
        section.set("z", 20);
        section.set("dest_world", "nether");
        section.set("dest_x", 1.5);
        section.set("dest_y", 70.0);
        section.set("dest_z", -2.5);
        section.set("dest_yaw", 135.0);
        section.set("linked", "return");

        Portal portal = Portal.fromConfig("p1", section);
        assertEquals("p1", portal.getId());
        assertEquals("world", portal.getWorldName());
        assertEquals(10, portal.getX());
        assertEquals("nether", portal.getDestWorldName());
        assertEquals("return", portal.getLinkedPortalId());

        World world = world("world");
        assertTrue(portal.matchesLocation(new Location(world, 10, 63, 20)));
        assertTrue(portal.matchesLocation(new Location(world, 10, 65, 20)));
        assertFalse(portal.matchesLocation(new Location(world("other"), 10, 64, 20)));
        assertFalse(portal.matchesLocation(null));

        YamlConfiguration out = new YamlConfiguration();
        portal.toConfig(out.createSection("portal"));
        assertEquals("return", out.getString("portal.linked"));
        assertEquals(135.0, out.getDouble("portal.dest_yaw"));
    }

    @Test
    void shouldSetLocationsAndResolveDestinationWorld() {
        World world = world("world");
        World nether = world("nether");
        Portal portal = new Portal("p1");

        portal.setEntrance(new Location(world, 10.7, 64.2, 20.9));
        Location destination = new Location(nether, 1.5, 70.0, -2.5);
        destination.setYaw(135.0f);
        portal.setDestination(destination);
        portal.setLinkedPortalId("return");

        assertEquals(10, portal.getX());
        assertEquals(64, portal.getY());
        assertEquals(20, portal.getZ());
        assertEquals(1.5, portal.getDestX());
        assertEquals(70.0, portal.getDestY());
        assertEquals(-2.5, portal.getDestZ());
        assertEquals(135.0f, portal.getDestYaw());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("nether")).thenReturn(nether);
            Location resolved = portal.getDestination();
            assertEquals(nether, resolved.getWorld());
            assertEquals(1.5, resolved.getX());
            assertEquals(135.0f, resolved.getYaw());

            bukkit.when(() -> Bukkit.getWorld("nether")).thenReturn(null);
            assertNull(portal.getDestination());
        }
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
