package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.StopCommandService.SetPointResult;
import org.cubexmc.metro.service.StopCommandService.WriteStatus;
import org.junit.jupiter.api.Test;

class StopCommandServiceTest {

    private final StopManager stopManager = mock(StopManager.class);
    private final StopCommandService service = new StopCommandService(stopManager);

    @Test
    void shouldRejectUnsafeStopIdsBeforeCreating() {
        StopCommandService.CreateStopResult result = service.createStop("bad.path", "Bad", null, null, UUID.randomUUID());

        assertEquals(WriteStatus.INVALID_ID, result.status());
        assertFalse(service.isValidId("../bad"));
        assertTrue(service.isValidId("central_1"));
        verify(stopManager, never()).createStop(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void shouldReportDuplicateAndSuccessfulCreates() {
        UUID ownerId = UUID.randomUUID();
        Stop central = new Stop("central", "Central");
        when(stopManager.createStop("central", "Central", null, null, ownerId)).thenReturn(central);
        when(stopManager.createStop("central", "Again", null, null, ownerId)).thenReturn(null);

        assertEquals(WriteStatus.SUCCESS, service.createStop("central", "Central", null, null, ownerId).status());
        assertEquals(WriteStatus.EXISTS, service.createStop("central", "Again", null, null, ownerId).status());
    }

    @Test
    void shouldListStopsInStableIdOrderAndSkipMissingStops() {
        Stop beta = new Stop("beta", "Beta");
        Stop alpha = new Stop("alpha", "Alpha");
        when(stopManager.getAllStopIds()).thenReturn(Set.of("beta", "missing", "alpha"));
        when(stopManager.getStop("beta")).thenReturn(beta);
        when(stopManager.getStop("alpha")).thenReturn(alpha);

        assertEquals(List.of(alpha, beta), service.listStops());
    }

    @Test
    void shouldValidateStopPointBeforeSaving() {
        Stop stop = mock(Stop.class);
        Location stoneLocation = location(Material.STONE, 90.0f);

        SetPointResult notRail = service.setPoint("central", stop, stoneLocation, null);

        assertEquals(WriteStatus.NOT_RAIL, notRail.status());
        verify(stopManager, never()).setStopPoint(org.mockito.Mockito.anyString(), org.mockito.Mockito.any(),
                org.mockito.Mockito.anyFloat());

        Location railLocation = location(Material.RAIL, 90.0f);
        when(stop.isInStop(railLocation)).thenReturn(false);

        SetPointResult outside = service.setPoint("central", stop, railLocation, null);

        assertEquals(WriteStatus.NOT_IN_STOP, outside.status());
        verify(stopManager, never()).setStopPoint("central", railLocation, 90.0f);

        when(stop.isInStop(railLocation)).thenReturn(true);
        when(stopManager.setStopPoint("central", railLocation, 135.0f)).thenReturn(true);

        SetPointResult success = service.setPoint("central", stop, railLocation, 135.0f);

        assertEquals(WriteStatus.SUCCESS, success.status());
        assertEquals(135.0f, success.yaw());
        verify(stopManager).setStopPoint("central", railLocation, 135.0f);
    }

    @Test
    void shouldValidateAndMutateCustomTitles() {
        Stop stop = new Stop("central", "Central");

        assertEquals(WriteStatus.INVALID_TITLE_TYPE,
                service.setCustomTitle(stop, "bad", "title", "Hello"));
        assertEquals(WriteStatus.INVALID_TITLE_KEY,
                service.setCustomTitle(stop, "departure", "bad", "Hello"));
        assertEquals(WriteStatus.SUCCESS,
                service.setCustomTitle(stop, "departure", "title", "Hello"));
        assertEquals(Map.of("title", "Hello"), stop.getCustomTitle("departure"));

        assertEquals(WriteStatus.NOT_FOUND,
                service.removeCustomTitleKey(stop, "departure", "subtitle"));
        assertEquals(WriteStatus.SUCCESS,
                service.removeCustomTitleKey(stop, "departure", "title"));
        assertEquals(WriteStatus.NOT_FOUND,
                service.removeCustomTitleType(stop, "departure"));

        verify(stopManager, org.mockito.Mockito.times(2)).saveConfig();
    }

    @Test
    void shouldRouteLinkActionsThroughManager() {
        when(stopManager.allowLineLink("central", "red")).thenReturn(true);
        when(stopManager.denyLineLink("central", "red")).thenReturn(false);

        assertEquals(WriteStatus.SUCCESS, service.updateLineLink("allow", "central", "red"));
        assertEquals(WriteStatus.NOT_FOUND, service.updateLineLink("deny", "central", "red"));
        assertEquals(WriteStatus.INVALID_ACTION, service.updateLineLink("toggle", "central", "red"));
    }

    @Test
    void shouldGrantStopAdminOnlyWhenTargetIsNotAlreadyAdmin() {
        UUID existingAdmin = UUID.randomUUID();
        UUID newAdmin = UUID.randomUUID();
        Stop stop = new Stop("central", "Central");
        stop.addAdmin(existingAdmin);
        when(stopManager.addStopAdmin("central", newAdmin)).thenReturn(true);

        assertEquals(WriteStatus.EXISTS, service.addAdmin(stop, existingAdmin));
        assertEquals(WriteStatus.SUCCESS, service.addAdmin(stop, newAdmin));

        verify(stopManager, never()).addStopAdmin("central", existingAdmin);
        verify(stopManager).addStopAdmin("central", newAdmin);
    }

    @Test
    void shouldRevokeStopAdminAndTransferOwnerThroughManager() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Stop stop = new Stop("central", "Central");
        when(stopManager.removeStopAdmin("central", adminId)).thenReturn(true);
        when(stopManager.setStopOwner("central", ownerId)).thenReturn(true);

        assertEquals(WriteStatus.SUCCESS, service.removeAdmin(stop, adminId));
        assertEquals(WriteStatus.SUCCESS, service.setOwner(stop, ownerId));

        verify(stopManager).removeStopAdmin("central", adminId);
        verify(stopManager).setStopOwner("central", ownerId);
    }

    private Location location(Material material, float yaw) {
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(material);
        when(location.getYaw()).thenReturn(yaw);
        return location;
    }
}
