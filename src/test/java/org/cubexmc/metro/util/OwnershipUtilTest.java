package org.cubexmc.metro.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class OwnershipUtilTest {

    @Test
    void shouldAllowConsoleAndPermissionBasedCreation() {
        CommandSender console = mock(CommandSender.class);
        Player player = player(false, false);

        assertTrue(OwnershipUtil.hasAdminBypass(console));
        assertTrue(OwnershipUtil.canCreateLine(console));
        assertFalse(OwnershipUtil.hasAdminBypass(player));

        when(player.hasPermission("metro.line.create")).thenReturn(true);
        when(player.hasPermission("metro.stop.create")).thenReturn(true);
        assertTrue(OwnershipUtil.canCreateLine(player));
        assertTrue(OwnershipUtil.canCreateStop(player));
    }

    @Test
    void shouldEvaluateLineAndStopManagement() {
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Player ownerPlayer = player(owner, false, false);
        Player adminPlayer = player(admin, false, false);
        Player opPlayer = player(UUID.randomUUID(), true, false);
        Player bypassPlayer = player(UUID.randomUUID(), false, true);

        Line line = new Line("red", "Red");
        Stop stop = new Stop("central", "Central");

        assertTrue(OwnershipUtil.isServerOwned(line));
        assertTrue(OwnershipUtil.isServerOwned(stop));
        assertTrue(OwnershipUtil.canManageLine(opPlayer, line));
        assertTrue(OwnershipUtil.canManageStop(opPlayer, stop));
        assertTrue(OwnershipUtil.canManageLine(bypassPlayer, line));
        assertFalse(OwnershipUtil.canManageLine(adminPlayer, line));

        line.setOwner(owner);
        line.addAdmin(admin);
        stop.setOwner(owner);
        stop.addAdmin(admin);

        assertTrue(OwnershipUtil.isLineAdmin(owner, line));
        assertTrue(OwnershipUtil.isLineAdmin(admin, line));
        assertTrue(OwnershipUtil.isStopAdmin(admin, stop));
        assertTrue(OwnershipUtil.canManageLine(adminPlayer, line));
        assertTrue(OwnershipUtil.canManageStop(adminPlayer, stop));
        assertFalse(OwnershipUtil.isLineAdmin(null, line));
        assertFalse(OwnershipUtil.isStopAdmin(admin, null));
    }

    @Test
    void shouldEvaluateLineStopLinkingRules() {
        UUID lineAdminId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Player lineAdmin = player(lineAdminId, false, false);
        Player other = player(otherId, false, false);
        Player opPlayer = player(UUID.randomUUID(), true, false);

        Line line = new Line("red", "Red");
        line.setOwner(lineAdminId);
        Stop stop = new Stop("central", "Central");
        stop.setOwner(otherId);

        assertFalse(OwnershipUtil.canModifyLineStops(lineAdmin, line, stop));
        stop.allowLine("red");
        assertTrue(OwnershipUtil.canModifyLineStops(lineAdmin, line, stop));
        assertTrue(OwnershipUtil.canLinkStopToLine(lineAdmin, line, stop));
        assertFalse(OwnershipUtil.canLinkStopToLine(other, line, stop));

        Stop serverStop = new Stop("server", "Server");
        assertFalse(OwnershipUtil.canLinkStopToLine(lineAdmin, line, serverStop));
        assertTrue(OwnershipUtil.canLinkStopToLine(opPlayer, line, serverStop));
        assertTrue(OwnershipUtil.canLinkLineWithoutPlayer(line, stop));

        stop.denyLine("red");
        stop.setOwner(line.getOwner());
        assertTrue(OwnershipUtil.canLinkLineWithoutPlayer(line, stop));
        assertFalse(OwnershipUtil.canLinkLineWithoutPlayer(null, stop));
    }

    private Player player(boolean op, boolean adminPermission) {
        return player(UUID.randomUUID(), op, adminPermission);
    }

    private Player player(UUID uuid, boolean op, boolean adminPermission) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOp()).thenReturn(op);
        when(player.hasPermission("metro.admin")).thenReturn(adminPermission);
        return player;
    }
}
