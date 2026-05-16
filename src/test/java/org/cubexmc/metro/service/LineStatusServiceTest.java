package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.LineStatusChangeEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.junit.jupiter.api.Test;

class LineStatusServiceTest {

    private final Metro plugin = mock(Metro.class);
    private final LineManager lineManager = mock(LineManager.class);
    private final LineStatusService service = new LineStatusService(plugin, lineManager);

    @Test
    void newLineShouldBeNormal() {
        Line line = new Line("l1", "Test");
        assertEquals(LineStatus.NORMAL, service.getStatus(line));
    }

    @Test
    void nullLineShouldBeNormal() {
        assertEquals(LineStatus.NORMAL, service.getStatus(null));
    }

    @Test
    void setStatusShouldReturnTrue() {
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            PluginManager pluginManager = mock(PluginManager.class);
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            Line line = new Line("l1", "Test");
            assertTrue(service.setStatus(line, LineStatus.SUSPENDED));
            assertEquals(LineStatus.SUSPENDED, line.getLineStatus());
            verify(pluginManager).callEvent(argThat(event -> {
                if (!(event instanceof LineStatusChangeEvent changeEvent)) {
                    return false;
                }
                return changeEvent.getLine() == line
                        && changeEvent.getOldStatus() == LineStatus.NORMAL
                        && changeEvent.getNewStatus() == LineStatus.SUSPENDED;
            }));
        }
    }

    @Test
    void sameStatusShouldReturnFalse() {
        Line line = new Line("l1", "Test");
        assertFalse(service.setStatus(line, LineStatus.NORMAL));
    }

    @Test
    void suspendedLineShouldNotBeBoardable() {
        Line line = new Line("l1", "Test");
        line.setLineStatus(LineStatus.SUSPENDED);
        assertFalse(service.isBoardable(line));
    }

    @Test
    void maintenanceLineShouldBeBoardable() {
        Line line = new Line("l1", "Test");
        line.setLineStatus(LineStatus.MAINTENANCE);
        assertTrue(service.isBoardable(line));
    }

    @Test
    void normalLineShouldBeBoardable() {
        Line line = new Line("l1", "Test");
        assertTrue(service.isBoardable(line));
    }

    @Test
    void nullLineShouldNotBeBoardable() {
        assertFalse(service.isBoardable(null));
    }

    @Test
    void suspendedLineShouldBeSuspended() {
        Line line = new Line("l1", "Test");
        line.setLineStatus(LineStatus.SUSPENDED);
        assertTrue(service.isSuspended(line));
        assertFalse(service.isSuspended(null));
    }

    @Test
    void maintenanceLineShouldBeMaintenance() {
        Line line = new Line("l1", "Test");
        line.setLineStatus(LineStatus.MAINTENANCE);
        assertTrue(service.isMaintenance(line));
        assertFalse(service.isMaintenance(null));
    }

    @Test
    void getAlternativeLinesShouldReturnConfiguredRoutes() {
        Line line = new Line("l1", "Test");
        line.addAlternativeRoute("l2");
        line.addAlternativeRoute("l3");

        Line alt2 = new Line("l2", "Alt2");
        Line alt3 = new Line("l3", "Alt3");
        when(lineManager.getLine("l2")).thenReturn(alt2);
        when(lineManager.getLine("l3")).thenReturn(alt3);

        List<Line> alternatives = service.getAlternativeLines(line);
        assertEquals(2, alternatives.size());
        assertEquals("Alt2", alternatives.get(0).getName());
        assertEquals("Alt3", alternatives.get(1).getName());
    }

    @Test
    void getAlternativeLinesShouldSkipMissing() {
        Line line = new Line("l1", "Test");
        line.addAlternativeRoute("l2");
        when(lineManager.getLine("l2")).thenReturn(null);

        List<Line> alternatives = service.getAlternativeLines(line);
        assertTrue(alternatives.isEmpty());
    }

    @Test
    void nullLineShouldReturnEmptyAlternatives() {
        assertTrue(service.getAlternativeLines(null).isEmpty());
    }

    @Test
    void getSuspensionMessageShouldReturnMessage() {
        Line line = new Line("l1", "Test");
        line.setSuspensionMessage("Closed for repairs");
        assertEquals("Closed for repairs", service.getSuspensionMessage(line));
    }

    @Test
    void nullSuspensionMessageShouldReturnEmpty() {
        Line line = new Line("l1", "Test");
        assertEquals("", service.getSuspensionMessage(line));
    }

    @Test
    void nullLineSuspensionMessageShouldReturnEmpty() {
        assertEquals("", service.getSuspensionMessage(null));
    }

    @Test
    void getNonOperatingLinesShouldReturnSuspendedAndMaintenance() {
        Line normal = new Line("l1", "Normal");
        Line suspended = new Line("l2", "Suspended");
        suspended.setLineStatus(LineStatus.SUSPENDED);
        Line maintenance = new Line("l3", "Maintenance");
        maintenance.setLineStatus(LineStatus.MAINTENANCE);

        when(lineManager.getAllLines()).thenReturn(List.of(normal, suspended, maintenance));

        List<Line> nonOp = service.getNonOperatingLines();
        assertEquals(2, nonOp.size());
    }

    @Test
    void getNonOperatingLinesShouldExcludeNormal() {
        Line normal = new Line("l1", "Normal");
        when(lineManager.getAllLines()).thenReturn(List.of(normal));
        assertTrue(service.getNonOperatingLines().isEmpty());
    }
}
