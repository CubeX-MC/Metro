package org.cubexmc.metro.train;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;

class ScoreboardManagerTest {

    private Metro plugin;
    private ConfigFacade configFacade;
    private ScoreboardLibrary library;
    private StopManager stopManager;
    private LineManager lineManager;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        configFacade = mock(ConfigFacade.class);
        library = mock(ScoreboardLibrary.class);
        stopManager = mock(StopManager.class);
        lineManager = mock(LineManager.class);

        when(plugin.getGlobalScoreboardLibrary()).thenReturn(library);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(configFacade.isScoreboardEnabled()).thenReturn(true);
        when(configFacade.getSbStyleCurrent()).thenReturn("&a■ ");
        when(configFacade.getSbStylePassed()).thenReturn("&7■ ");
        when(configFacade.getSbStyleWaitingNext()).thenReturn("&e■ ");
        when(configFacade.getSbStyleMovingNext()).thenReturn("&b■ ");
        when(configFacade.getSbStyleTerminal()).thenReturn("&c■ ");
        when(configFacade.getSbStyleFolding()).thenReturn("&8...");
        when(configFacade.getSbStyleNext()).thenReturn("&f■ ");
        when(configFacade.getLineSymbol()).thenReturn("●");
    }

    @Test
    void shouldNotUpdateWhenPlayerOffline() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        manager.updateEnteringStopScoreboard(player, line, "A");

        verify(library, never()).createSidebar();
    }

    @Test
    void shouldNotUpdateWhenLineIsNull() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        manager.updateEnteringStopScoreboard(player, null, "A");

        verify(library, never()).createSidebar();
    }

    @Test
    void shouldNotUpdateWhenLibraryIsNull() {
        when(plugin.getGlobalScoreboardLibrary()).thenReturn(null);
        ScoreboardManager manager = new ScoreboardManager(plugin);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        manager.updateEnteringStopScoreboard(player, line, "A");

        verify(plugin).debug(eq("scoreboard"), any());
    }

    @Test
    void shouldNotUpdateWhenScoreboardDisabled() {
        when(configFacade.isScoreboardEnabled()).thenReturn(false);
        ScoreboardManager manager = new ScoreboardManager(plugin);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        manager.updateEnteringStopScoreboard(player, line, "A");

        verify(library, never()).createSidebar();
    }

    @Test
    void shouldCreateSidebarOnFirstUpdate() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("A")).thenReturn(stopA);
        when(stopManager.getStop("B")).thenReturn(stopB);

        Sidebar sidebar = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar);

        manager.updateEnteringStopScoreboard(player, line, "A");

        verify(library).createSidebar();
        verify(sidebar).addPlayer(player);
    }

    @Test
    void shouldReuseSidebarOnSecondUpdate() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("TestPlayer");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("A")).thenReturn(stopA);
        when(stopManager.getStop("B")).thenReturn(stopB);

        Sidebar sidebar = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar);

        manager.updateEnteringStopScoreboard(player, line, "A");
        manager.updateTravelingScoreboard(player, line, "B");

        verify(library).createSidebar();
        verify(sidebar).addPlayer(player);
    }

    @Test
    void shouldClearScoreboardAndCloseSidebar() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("TestPlayer");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Stop stopA = new Stop("A", "Alpha");
        when(stopManager.getStop("A")).thenReturn(stopA);

        Sidebar sidebar = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar);

        manager.updateEnteringStopScoreboard(player, line, "A");
        manager.clearScoreboard(player);

        verify(sidebar).removePlayer(player);
        verify(sidebar).close();
    }

    @Test
    void shouldSkipClearWhenPlayerNull() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        manager.clearScoreboard(null);

        verify(library, never()).createSidebar();
    }

    @Test
    void shouldSkipClearWhenPlayerOffline() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);

        manager.clearScoreboard(player);

        verify(library, never()).createSidebar();
    }

    @Test
    void shouldClearPlayerDisplayIncludingTitle() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("TestPlayer");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Stop stopA = new Stop("A", "Alpha");
        when(stopManager.getStop("A")).thenReturn(stopA);

        Sidebar sidebar = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar);

        manager.updateEnteringStopScoreboard(player, line, "A");
        manager.clearPlayerDisplay(player);

        verify(sidebar).close();
        verify(player).sendTitle("", "", 0, 0, 0);
    }

    @Test
    void shouldShutdownAllSidebars() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();

        Player player1 = mock(Player.class);
        when(player1.isOnline()).thenReturn(true);
        when(player1.getUniqueId()).thenReturn(playerId1);
        when(player1.getName()).thenReturn("Player1");

        Player player2 = mock(Player.class);
        when(player2.isOnline()).thenReturn(true);
        when(player2.getUniqueId()).thenReturn(playerId2);
        when(player2.getName()).thenReturn("Player2");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        Stop stopA = new Stop("A", "Alpha");
        when(stopManager.getStop("A")).thenReturn(stopA);

        Sidebar sidebar1 = mock(Sidebar.class);
        Sidebar sidebar2 = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar1, sidebar2);

        manager.updateEnteringStopScoreboard(player1, line, "A");
        manager.updateEnteringStopScoreboard(player2, line, "A");
        manager.shutdown();

        verify(sidebar1).close();
        verify(sidebar2).close();
    }

    @Test
    void shouldUpdateTerminalScoreboard() {
        ScoreboardManager manager = new ScoreboardManager(plugin);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");

        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        Stop stopA = new Stop("A", "Alpha");
        when(stopManager.getStop("A")).thenReturn(stopA);

        Sidebar sidebar = mock(Sidebar.class);
        when(library.createSidebar()).thenReturn(sidebar);

        manager.updateTerminalScoreboard(player, line, "A");

        verify(library).createSidebar();
    }
}
