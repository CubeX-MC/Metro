package org.cubexmc.metro.command.newcmd;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
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

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final LanguageManager languageManager = mock(LanguageManager.class);
        private final LineManager lineManager = mock(LineManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final Player player = mock(Player.class);
        private final CommandGuard guard = new CommandGuard(plugin, lineManager, stopManager);

        private Fixtures() {
            when(plugin.getLanguageManager()).thenReturn(languageManager);
            when(languageManager.getMessage(eq("line.line_not_found"), anyMap())).thenReturn("line missing");
            when(languageManager.getMessage(eq("line.permission_manage"), anyMap())).thenReturn("no access");
        }
    }
}
