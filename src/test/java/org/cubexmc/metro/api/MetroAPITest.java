package org.cubexmc.metro.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.integration.VaultIntegration;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.PriceRule;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetroAPITest {

    private Metro plugin;
    private LineManager lineManager;
    private StopManager stopManager;
    private PortalManager portalManager;
    private ConfigFacade configFacade;
    private MetroAPI api;

    @BeforeEach
    void setUp() {
        MetroAPI.resetForTests();
        plugin = mock(Metro.class);
        lineManager = mock(LineManager.class);
        stopManager = mock(StopManager.class);
        portalManager = mock(PortalManager.class);
        configFacade = mock(ConfigFacade.class);

        YamlConfiguration config = new YamlConfiguration();
        config.set("economy.enabled", false);

        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getPortalManager()).thenReturn(portalManager);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getConfig()).thenReturn(config);
        when(configFacade.isEconomyEnabled()).thenReturn(false);

        MetroAPI.initialize(plugin);
        api = MetroAPI.getInstance();
    }

    @AfterEach
    void tearDown() {
        MetroAPI.resetForTests();
    }

    @Test
    void settingsShouldExposeStableReadOnlyValues() {
        VaultIntegration vault = mock(VaultIntegration.class);
        when(vault.isEnabled()).thenReturn(true);
        when(plugin.getVaultIntegration()).thenReturn(vault);
        when(configFacade.isPortalsEnabled()).thenReturn(true);
        when(configFacade.getPortalTriggerBlock()).thenReturn("DIAMOND_BLOCK");
        when(configFacade.getCartSpeed()).thenReturn(0.42);
        when(configFacade.getCartDepartureDelay()).thenReturn(80L);
        when(configFacade.getPortalTeleportDelay()).thenReturn(12);
        when(configFacade.isSafeModePassengerRailBreakProtection()).thenReturn(true);

        assertFalse(api.isEconomyEnabled());
        assertTrue(api.isVaultEconomyAvailable());
        assertTrue(api.isPortalsEnabled());
        assertEquals("DIAMOND_BLOCK", api.getPortalTriggerBlock());
        assertEquals(0.42, api.getDefaultCartSpeed());
        assertEquals(80L, api.getCartDepartureDelay());
        assertEquals(12, api.getPortalTeleportDelay());
        assertTrue(api.isPassengerRailBreakProtectionEnabled());
    }

    @Test
    void portalApiShouldQueryAndMutateThroughPortalManager() {
        UUID ownerId = UUID.randomUUID();
        Portal portal = new Portal("p1");
        Location entrance = location("world", 1, 64, 1);
        Location destination = location("world", 10, 70, 10);
        portal.setEntrance(entrance);
        portal.setDestination(destination);
        portal.setOwner(ownerId);

        when(portalManager.getPortal("p1")).thenReturn(null, portal, portal);
        when(portalManager.getAllPortals()).thenReturn(List.of(portal));
        when(portalManager.getPortalAt(entrance)).thenReturn(portal);
        when(portalManager.createPortal("p1", entrance, ownerId)).thenReturn(portal);
        when(portalManager.setDestination("p1", destination)).thenReturn(true);
        when(portalManager.linkPortals("p1", "p2")).thenReturn(true);
        when(portalManager.deletePortal("p1")).thenReturn(true);

        MetroAPI.PortalWriteResult create = api.createPortal("p1", entrance, ownerId);

        assertEquals(MetroAPI.PortalWriteStatus.SUCCESS, create.status());
        assertEquals(portal, create.portal());
        assertEquals(List.of(portal), api.getAllPortals());
        assertEquals(portal, api.getPortal("p1"));
        assertEquals(portal, api.getPortalAt(entrance));
        assertEquals(MetroAPI.PortalWriteStatus.SUCCESS, api.setPortalDestination("p1", destination));
        assertEquals(MetroAPI.PortalWriteStatus.SUCCESS, api.linkPortals("p1", "p2"));
        assertEquals(MetroAPI.PortalWriteStatus.SAME_PORTAL, api.linkPortals("p1", "p1"));
        assertEquals(MetroAPI.PortalWriteStatus.SUCCESS, api.deletePortal("p1"));
    }

    @Test
    void ownershipApiShouldExposeQueriesAndSafeMutations() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID newAdminId = UUID.randomUUID();
        Line line = new Line("red", "Red");
        line.setOwner(ownerId);
        line.addAdmin(adminId);
        Stop stop = new Stop("central", "Central");
        stop.setOwner(ownerId);
        stop.addAdmin(adminId);
        Portal portal = new Portal("p1");
        portal.setOwner(ownerId);
        portal.addAdmin(adminId);

        when(lineManager.getLine("red")).thenReturn(line);
        when(stopManager.getStop("central")).thenReturn(stop);
        when(portalManager.getPortal("p1")).thenReturn(portal);
        when(lineManager.setLineOwner("red", newAdminId)).thenReturn(true);
        when(lineManager.addLineAdmin("red", newAdminId)).thenReturn(true);
        when(lineManager.removeLineAdmin("red", adminId)).thenReturn(true);
        when(stopManager.setStopOwner("central", newAdminId)).thenReturn(true);
        when(stopManager.addStopAdmin("central", newAdminId)).thenReturn(true);
        when(stopManager.removeStopAdmin("central", adminId)).thenReturn(true);
        when(portalManager.setPortalOwner("p1", newAdminId)).thenReturn(true);
        when(portalManager.addPortalAdmin("p1", newAdminId)).thenReturn(true);
        when(portalManager.removePortalAdmin("p1", adminId)).thenReturn(true);

        assertEquals(ownerId, api.getLineOwner("red"));
        assertEquals(ownerId, api.getStopOwner("central"));
        assertEquals(ownerId, api.getPortalOwner("p1"));
        assertTrue(api.isLineAdmin("red", adminId));
        assertTrue(api.isStopAdmin("central", adminId));
        assertTrue(api.isPortalAdmin("p1", adminId));
        assertFalse(api.isLineServerOwned("red"));
        assertTrue(api.getLineAdmins("red").contains(adminId));

        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.setLineOwner("red", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.addLineAdmin("red", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.removeLineAdmin("red", adminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.setStopOwner("central", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.addStopAdmin("central", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.removeStopAdmin("central", adminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.setPortalOwner("p1", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.addPortalAdmin("p1", newAdminId));
        assertEquals(MetroAPI.OwnershipWriteStatus.SUCCESS, api.removePortalAdmin("p1", adminId));

        assertEquals(MetroAPI.OwnershipWriteStatus.OWNER_PROTECTED, api.removeLineAdmin("red", ownerId));
        verify(lineManager, never()).removeLineAdmin("red", ownerId);
    }

    @Test
    void permissionHelpersShouldUseOwnershipRules() {
        UUID ownerId = UUID.randomUUID();
        Player player = mock(Player.class);
        Line line = new Line("red", "Red");
        line.setOwner(ownerId);
        Stop stop = new Stop("central", "Central");
        stop.setOwner(ownerId);
        Portal portal = new Portal("p1");
        portal.setOwner(ownerId);

        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("metro.admin")).thenReturn(false);
        when(lineManager.getLine("red")).thenReturn(line);
        when(stopManager.getStop("central")).thenReturn(stop);
        when(portalManager.getPortal("p1")).thenReturn(portal);

        assertTrue(api.canManageLine(player, "red"));
        assertTrue(api.canManageStop(player, "central"));
        assertTrue(api.canManagePortal(player, "p1"));
        assertTrue(api.canModifyLineStops(player, "red", "central"));
        assertTrue(api.canLinkStopToLine(player, "red", "central"));
    }

    @Test
    void snapshotsShouldReturnReadOnlyCopies() {
        UUID ownerId = UUID.randomUUID();
        Line line = new Line("red", "Red");
        line.addStop("central", -1);
        line.addPortal("p1");
        line.setRoutePoints(List.of(new RoutePoint("world", 1, 2, 3)));
        line.setOwner(ownerId);
        line.setLineStatus(LineStatus.MAINTENANCE);
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.5);
        line.setPriceRule(rule);

        Stop stop = new Stop("central", "Central");
        stop.setOwner(ownerId);
        stop.setCorner1(location("world", 0, 60, 0));
        stop.setCorner2(location("world", 5, 65, 5));
        stop.setStopPointLocation(location("world", 2, 61, 2));

        Portal portal = new Portal("p1");
        portal.setEntrance(location("world", 1, 64, 1));
        portal.setDestination(location("world", 10, 70, 10));
        portal.setOwner(ownerId);

        when(lineManager.getLine("red")).thenReturn(line);
        when(lineManager.getAllLines()).thenReturn(List.of(line));
        when(stopManager.getStop("central")).thenReturn(stop);
        when(stopManager.getAllStops()).thenReturn(List.of(stop));
        when(portalManager.getPortal("p1")).thenReturn(portal);
        when(portalManager.getAllPortals()).thenReturn(List.of(portal));

        MetroAPI.LineSnapshot lineSnapshot = api.getLineSnapshot("red");
        MetroAPI.StopSnapshot stopSnapshot = api.getStopSnapshot("central");
        MetroAPI.PortalSnapshot portalSnapshot = api.getPortalSnapshot("p1");

        assertNotNull(lineSnapshot);
        assertEquals("red", lineSnapshot.id());
        assertEquals(LineStatus.MAINTENANCE, lineSnapshot.lineStatus());
        assertEquals(PriceRule.PricingMode.DISTANCE, lineSnapshot.priceRule().mode());
        assertThrows(UnsupportedOperationException.class, () -> lineSnapshot.orderedStopIds().add("other"));
        rule.setBasePrice(99.0);
        assertEquals(2.0, lineSnapshot.priceRule().basePrice());

        assertNotNull(stopSnapshot);
        assertEquals("central", stopSnapshot.id());
        assertEquals("world", stopSnapshot.stopPoint().worldName());

        assertNotNull(portalSnapshot);
        assertEquals("p1", portalSnapshot.id());
        assertEquals("world", portalSnapshot.destinationWorldName());
        assertEquals(1, api.getLineSnapshots().size());
        assertEquals("red", api.getLineSnapshots().get(0).id());
        assertEquals(List.of(stopSnapshot), api.getStopSnapshots());
        assertEquals(List.of(portalSnapshot), api.getPortalSnapshots());
    }

    @Test
    void snapshotsShouldReturnNullForMissingEntries() {
        assertNull(api.getLineSnapshot("missing"));
        assertNull(api.getStopSnapshot("missing"));
        assertNull(api.getPortalSnapshot("missing"));
        assertEquals(Set.of(), api.getPortalAdmins("missing"));
        assertEquals(MetroAPI.OwnershipWriteStatus.NOT_FOUND,
                api.setPortalOwner("missing", UUID.randomUUID()));
    }

    private Location location(String worldName, double x, double y, double z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getX()).thenReturn(x);
        when(location.getY()).thenReturn(y);
        when(location.getZ()).thenReturn(z);
        when(location.getBlockX()).thenReturn((int) Math.floor(x));
        when(location.getBlockY()).thenReturn((int) Math.floor(y));
        when(location.getBlockZ()).thenReturn((int) Math.floor(z));
        when(location.getYaw()).thenReturn(90.0F);
        return location;
    }
}
