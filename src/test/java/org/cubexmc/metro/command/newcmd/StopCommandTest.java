package org.cubexmc.metro.command.newcmd;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.junit.jupiter.api.Test;

class StopCommandTest {

    @Test
    void shouldDenyStopTeleportWithoutTeleportPermission() {
        Metro plugin = mock(Metro.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        StopManager stopManager = mock(StopManager.class);
        LineManager lineManager = mock(LineManager.class);
        Player player = mock(Player.class);

        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(player.hasPermission("metro.tp")).thenReturn(false);
        when(languageManager.getMessage("plugin.no_permission")).thenReturn("no permission");

        new StopCommand(plugin, stopManager, lineManager).tp(player, "central");

        verify(player).sendMessage("no permission");
        verify(stopManager, never()).getStop("central");
    }
}
