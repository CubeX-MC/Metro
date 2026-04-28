package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.service.PortalCommandService.PortalWriteResult;
import org.cubexmc.metro.service.PortalCommandService.WriteStatus;
import org.junit.jupiter.api.Test;

class PortalCommandServiceTest {

    private final PortalManager portalManager = mock(PortalManager.class);
    private final PortalCommandService service = new PortalCommandService(portalManager);

    @Test
    void shouldRejectUnsafePortalIdsBeforeCreating() {
        PortalWriteResult result = service.createPortal("bad.path", location("world"), null);

        assertEquals(WriteStatus.INVALID_ID, result.status());
        verify(portalManager, never()).createPortal(org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
    }

    @Test
    void shouldReportExistingPortalBeforeCreating() {
        when(portalManager.getPortal("p1")).thenReturn(new Portal("p1"));

        PortalWriteResult result = service.createPortal("p1", location("world"), null);

        assertEquals(WriteStatus.EXISTS, result.status());
        verify(portalManager, never()).createPortal(org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
    }

    @Test
    void shouldPreferTargetedRailForPortalEntrance() {
        Location fallback = location("world");
        Location railLocation = location("world");
        Block targetBlock = block(Material.POWERED_RAIL, railLocation);
        Portal portal = new Portal("p1");
        when(portalManager.createPortal("p1", railLocation)).thenReturn(portal);

        PortalWriteResult result = service.createPortal("p1", fallback, targetBlock);

        assertEquals(WriteStatus.SUCCESS, result.status());
        assertSame(portal, result.portal());
        assertSame(railLocation, result.location());
        verify(portalManager).createPortal("p1", railLocation);
    }

    @Test
    void shouldSetDestinationOnlyForExistingPortal() {
        Location destination = location("world");
        when(portalManager.setDestination("p1", destination)).thenReturn(false);

        assertEquals(WriteStatus.NOT_FOUND, service.setDestination("p1", destination).status());

        when(portalManager.setDestination("p1", destination)).thenReturn(true);
        Portal portal = new Portal("p1");
        when(portalManager.getPortal("p1")).thenReturn(portal);

        PortalWriteResult result = service.setDestination("p1", destination);

        assertEquals(WriteStatus.SUCCESS, result.status());
        assertSame(portal, result.portal());
    }

    @Test
    void shouldValidateLinkTargets() {
        assertEquals(WriteStatus.FAILED, service.linkPortals("p1", "p1"));
        verify(portalManager, never()).linkPortals("p1", "p1");

        when(portalManager.linkPortals("p1", "p2")).thenReturn(false);
        assertEquals(WriteStatus.NOT_FOUND, service.linkPortals("p1", "p2"));

        when(portalManager.linkPortals("p1", "p2")).thenReturn(true);
        assertEquals(WriteStatus.SUCCESS, service.linkPortals("p1", "p2"));
    }

    @Test
    void shouldListPortalsInStableIdOrder() {
        Portal beta = new Portal("beta");
        Portal alpha = new Portal("alpha");
        when(portalManager.getAllPortals()).thenReturn(List.of(beta, alpha));

        assertEquals(List.of(alpha, beta), service.listPortals());
    }

    @Test
    void shouldRunMigrationBeforeReloadingPortals() {
        AtomicBoolean migrated = new AtomicBoolean(false);
        when(portalManager.getAllPortals()).thenReturn(List.of(new Portal("p1"), new Portal("p2")));

        PortalCommandService.ReloadResult result = service.reloadPortals(() -> migrated.set(true));

        assertTrue(migrated.get());
        assertEquals(WriteStatus.SUCCESS, result.status());
        assertEquals(2, result.portalCount());
        verify(portalManager).load();
    }

    private Location location(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        return location;
    }

    private Block block(Material material, Location location) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(location);
        return block;
    }
}
