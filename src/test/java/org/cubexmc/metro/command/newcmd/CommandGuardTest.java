package org.cubexmc.metro.command.newcmd;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class CommandGuardTest {

    @Test
    void shouldSendNotFoundWhenLineIsMissing() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.lineManager.getLine("missing")).thenReturn(null);
        when(fixtures.languageManager.getMessage(eq("line.line_not_found"), anyMap())).thenReturn("line missing");

        Line line = fixtures.guard.requireLine(fixtures.player, "missing");

        assertNull(line);
        verify(fixtures.player).sendMessage("line missing");
    }

    @Test
    void shouldReturnManageableLineForAdmin() {
        Fixtures fixtures = new Fixtures();
        Line line = new Line("red", "Red");
        when(fixtures.lineManager.getLine("red")).thenReturn(line);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(true);

        Line resolved = fixtures.guard.requireManageableLine(fixtures.player, "red");

        assertSame(line, resolved);
    }

    @Test
    void shouldSendPermissionMessageWhenLineCannotBeManaged() {
        Fixtures fixtures = new Fixtures();
        Line line = new Line("red", "Red");
        when(fixtures.lineManager.getLine("red")).thenReturn(line);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(false);
        when(fixtures.player.isOp()).thenReturn(false);
        when(fixtures.languageManager.getMessage("ownership.server")).thenReturn("Server");
        when(fixtures.languageManager.getMessage("ownership.none")).thenReturn("none");
        when(fixtures.languageManager.getMessage(eq("line.permission_manage"), anyMap())).thenReturn("no access");

        Line resolved = fixtures.guard.requireManageableLine(fixtures.player, "red");

        assertNull(resolved);
        verify(fixtures.player).sendMessage("no access");
    }

    @Test
    void shouldSendNotFoundWhenStopIsMissing() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.stopManager.getStop("missing")).thenReturn(null);
        when(fixtures.languageManager.getMessage(eq("stop.stop_not_found"), anyMap())).thenReturn("stop missing");

        Stop stop = fixtures.guard.requireStop(fixtures.player, "missing");

        assertNull(stop);
        verify(fixtures.player).sendMessage("stop missing");
    }

    @Test
    void shouldSendNoPermissionWhenPermissionIsMissing() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.player.hasPermission("metro.tp")).thenReturn(false);
        when(fixtures.languageManager.getMessage("plugin.no_permission")).thenReturn("no permission");

        assertFalse(fixtures.guard.requirePermission(fixtures.player, "metro.tp"));

        verify(fixtures.player).sendMessage("no permission");
    }

    @Test
    void shouldRequireExplicitConfirmationForDestructiveCommands() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.languageManager.getMessage("command.confirm_required")).thenReturn("confirm required");
        when(fixtures.languageManager.getMessage(eq("command.confirm_hint"), anyMap())).thenReturn("run command");

        assertFalse(fixtures.guard.requireConfirmation(fixtures.player, null, "/m line delete red confirm"));
        assertTrue(fixtures.guard.requireConfirmation(fixtures.player, "confirm", "/m line delete red confirm"));

        verify(fixtures.player).sendMessage("confirm required");
        verify(fixtures.player).sendMessage("run command");
    }

    @Test
    void shouldReturnManageableStopForStopAdmin() {
        Fixtures fixtures = new Fixtures();
        UUID playerId = UUID.randomUUID();
        Stop stop = new Stop("central", "Central");
        stop.setOwner(UUID.randomUUID());
        stop.addAdmin(playerId);
        when(fixtures.stopManager.getStop("central")).thenReturn(stop);
        when(fixtures.player.getUniqueId()).thenReturn(playerId);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(false);

        Stop resolved = fixtures.guard.requireManageableStop(fixtures.player, "central");

        assertSame(stop, resolved);
    }

    @Test
    void shouldSendNotFoundWhenPortalIsMissing() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.portalManager.getPortal("missing")).thenReturn(null);
        when(fixtures.languageManager.getMessage(eq("portal.not_found"), anyMap())).thenReturn("portal missing");

        Portal portal = fixtures.guard.requirePortal(fixtures.player, "missing");

        assertNull(portal);
        verify(fixtures.player).sendMessage("portal missing");
    }

    @Test
    void shouldSendPermissionMessageWhenPortalCannotBeManaged() {
        Fixtures fixtures = new Fixtures();
        Portal portal = new Portal("gate");
        when(fixtures.portalManager.getPortal("gate")).thenReturn(portal);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(false);
        when(fixtures.player.isOp()).thenReturn(false);
        when(fixtures.languageManager.getMessage("ownership.server")).thenReturn("Server");
        when(fixtures.languageManager.getMessage("ownership.none")).thenReturn("none");
        when(fixtures.languageManager.getMessage(eq("portal.permission_manage"), anyMap())).thenReturn("no portal access");

        Portal resolved = fixtures.guard.requireManageablePortal(fixtures.player, "gate");

        assertNull(resolved);
        verify(fixtures.player).sendMessage("no portal access");
    }

    @Test
    void shouldReturnManageablePortalForPortalAdmin() {
        Fixtures fixtures = new Fixtures();
        UUID playerId = UUID.randomUUID();
        Portal portal = new Portal("gate");
        portal.setOwner(UUID.randomUUID());
        portal.addAdmin(playerId);
        when(fixtures.portalManager.getPortal("gate")).thenReturn(portal);
        when(fixtures.player.getUniqueId()).thenReturn(playerId);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(false);

        Portal resolved = fixtures.guard.requireManageablePortal(fixtures.player, "gate");

        assertSame(portal, resolved);
    }

    @Test
    void shouldApplyLinkedStopPermissionWhenAddingStopsToLine() {
        Fixtures fixtures = new Fixtures();
        UUID playerId = UUID.randomUUID();
        Line line = new Line("red", "Red");
        line.setOwner(UUID.randomUUID());
        line.addAdmin(playerId);
        Stop serverStop = new Stop("central", "Central");
        when(fixtures.player.getUniqueId()).thenReturn(playerId);
        when(fixtures.player.hasPermission("metro.admin")).thenReturn(false);
        when(fixtures.languageManager.getMessage(eq("stop.permission_link"), anyMap())).thenReturn("link denied");
        when(fixtures.languageManager.getMessage("ownership.server")).thenReturn("Server");

        assertFalse(fixtures.guard.canModifyLineStops(fixtures.player, line, serverStop));
        verify(fixtures.player).sendMessage("link denied");

        Stop linkedStop = new Stop("exchange", "Exchange");
        linkedStop.setOwner(UUID.randomUUID());
        linkedStop.allowLine("red");

        assertTrue(fixtures.guard.canModifyLineStops(fixtures.player, line, linkedStop));
    }

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final LanguageManager languageManager = mock(LanguageManager.class);
        private final LineManager lineManager = mock(LineManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final PortalManager portalManager = mock(PortalManager.class);
        private final Player player = mock(Player.class);
        private final CommandGuard guard = new CommandGuard(plugin, lineManager, stopManager);

        private Fixtures() {
            when(plugin.getLanguageManager()).thenReturn(languageManager);
            when(plugin.getPortalManager()).thenReturn(portalManager);
            when(languageManager.getMessage(eq("line.line_not_found"), anyMap())).thenReturn("line missing");
            when(languageManager.getMessage(eq("line.permission_manage"), anyMap())).thenReturn("no access");
        }
    }
}
