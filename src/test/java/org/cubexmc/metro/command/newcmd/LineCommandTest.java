package org.cubexmc.metro.command.newcmd;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class LineCommandTest {

    @Test
    void shouldNotDeleteLineWithoutExplicitConfirmation() {
        Metro plugin = mock(Metro.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        Player player = mock(Player.class);
        Line line = new Line("red", "Red");

        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(lineManager.getLine("red")).thenReturn(line);
        when(player.hasPermission("metro.admin")).thenReturn(false);
        when(player.isOp()).thenReturn(true);
        when(languageManager.getMessage("command.confirm_required")).thenReturn("confirm required");
        when(languageManager.getMessage(eq("command.confirm_hint"), anyMap())).thenReturn("run command");

        new LineCommand(plugin, lineManager, stopManager).delete(player, "red", null);

        verify(lineManager, never()).deleteLine("red");
        verify(player).sendMessage("confirm required");
        verify(player).sendMessage("run command");
    }
}
