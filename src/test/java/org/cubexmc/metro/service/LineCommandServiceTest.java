package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.LineCommandService.AddStopResult;
import org.cubexmc.metro.service.LineCommandService.WriteStatus;
import org.junit.jupiter.api.Test;

class LineCommandServiceTest {

    private final LineManager lineManager = mock(LineManager.class);
    private final LineCommandService service = new LineCommandService(lineManager);

    @Test
    void shouldRejectUnsafeNewLineIdsBeforeWriting() {
        assertEquals(WriteStatus.INVALID_ID, service.createLine("red.line", "Red", UUID.randomUUID()));
        assertEquals(WriteStatus.INVALID_ID, service.cloneReverseLine("red", "bad/path", "_rev", UUID.randomUUID()));

        verify(lineManager, never()).createLine(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any());
        verify(lineManager, never()).cloneReverseLine(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
    }

    @Test
    void shouldValidateColorSpeedAndTicketPrice() {
        assertTrue(service.isValidColor("&a"));
        assertTrue(service.isValidColor("&#12ABef"));
        assertFalse(service.isValidColor("green"));

        assertEquals(WriteStatus.INVALID_COLOR, service.setColor("red", "green"));
        assertEquals(WriteStatus.INVALID_VALUE, service.setMaxSpeed("red", 0.0));
        assertEquals(WriteStatus.INVALID_VALUE, service.setMaxSpeed("red", Double.NaN));
        assertEquals(WriteStatus.INVALID_VALUE, service.setTicketPrice("red", -0.01));
        assertEquals(WriteStatus.INVALID_VALUE, service.setTicketPrice("red", Double.POSITIVE_INFINITY));

        verify(lineManager, never()).setLineColor("red", "green");
        verify(lineManager, never()).setLineMaxSpeed(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyDouble());
        verify(lineManager, never()).setLineTicketPrice(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyDouble());
    }

    @Test
    void shouldListLinesInStableIdOrder() {
        Line beta = line("beta", "world", List.of());
        Line alpha = line("alpha", "world", List.of());
        when(lineManager.getAllLines()).thenReturn(List.of(beta, alpha));

        assertEquals(List.of(alpha, beta), service.listLines());
    }

    @Test
    void shouldRejectAddingStopsWithoutUsableWorld() {
        Line line = line("red", null, List.of());
        Stop stop = stop("central", null);

        AddStopResult result = service.addStopToLine(line, stop, null);

        assertEquals(WriteStatus.STOP_NO_WORLD, result.status());
        verify(lineManager, never()).addStopToLine(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt());
    }

    @Test
    void shouldRejectAddingStopFromDifferentWorld() {
        Line line = line("red", "world", List.of());
        Stop stop = stop("central", "nether");

        AddStopResult result = service.addStopToLine(line, stop, null);

        assertEquals(WriteStatus.WORLD_MISMATCH, result.status());
        assertEquals("world", result.lineWorld());
        assertEquals("nether", result.stopWorld());
        verify(lineManager, never()).addStopToLine(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt());
    }

    @Test
    void shouldSetLineWorldWhenFirstStopIsAdded() {
        Line line = line("red", null, List.of());
        Stop stop = stop("central", "world");
        when(lineManager.addStopToLine("red", "central", -1)).thenReturn(true);

        AddStopResult result = service.addStopToLine(line, stop, null);

        assertEquals(WriteStatus.SUCCESS, result.status());
        verify(lineManager).addStopToLine("red", "central", -1);
        verify(lineManager).setLineWorldName("red", "world");
    }

    @Test
    void shouldRejectAppendingToCircularLine() {
        Line line = line("loop", "world", List.of("a", "b", "a"));
        Stop stop = stop("c", "world");

        AddStopResult result = service.addStopToLine(line, stop, null);

        assertEquals(WriteStatus.CIRCULAR_INVALID_INDEX, result.status());
        verify(lineManager, never()).addStopToLine(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt());
    }

    @Test
    void shouldGrantLineAdminOnlyWhenTargetIsNotAlreadyAdmin() {
        UUID existingAdmin = UUID.randomUUID();
        UUID newAdmin = UUID.randomUUID();
        Line line = line("red", "world", List.of());
        line.addAdmin(existingAdmin);
        when(lineManager.addLineAdmin("red", newAdmin)).thenReturn(true);

        assertEquals(WriteStatus.EXISTS, service.grantAdmin(line, existingAdmin));
        assertEquals(WriteStatus.SUCCESS, service.grantAdmin(line, newAdmin));

        verify(lineManager, never()).addLineAdmin("red", existingAdmin);
        verify(lineManager).addLineAdmin("red", newAdmin);
    }

    @Test
    void shouldRevokeLineAdminAndTransferOwnerThroughManager() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Line line = line("red", "world", List.of());
        when(lineManager.removeLineAdmin("red", adminId)).thenReturn(true);
        when(lineManager.setLineOwner("red", ownerId)).thenReturn(true);

        assertEquals(WriteStatus.SUCCESS, service.revokeAdmin(line, adminId));
        assertEquals(WriteStatus.SUCCESS, service.transferOwner(line, ownerId));

        verify(lineManager).removeLineAdmin("red", adminId);
        verify(lineManager).setLineOwner("red", ownerId);
    }

    private Line line(String id, String worldName, List<String> stopIds) {
        Line line = new Line(id, id);
        line.setWorldName(worldName);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        return line;
    }

    private Stop stop(String id, String worldName) {
        Stop stop = mock(Stop.class);
        when(stop.getId()).thenReturn(id);
        when(stop.getWorldName()).thenReturn(worldName);
        return stop;
    }
}
