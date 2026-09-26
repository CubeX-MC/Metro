package org.cubexmc.metro.listener;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.*;
import org.cubexmc.metro.model.*;
import org.cubexmc.metro.service.LineSelectionService;
import org.cubexmc.metro.util.SchedulerUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class PlayerMoveTitleTest {
    Metro plugin=mock(Metro.class);
    ConfigFacade config=mock(ConfigFacade.class);
    StopManager stops=mock(StopManager.class);
    LineSelectionService selection=mock(LineSelectionService.class);
    LineManager lines=mock(LineManager.class);
    LanguageManager language=mock(LanguageManager.class);
    World world=mock(World.class);
    Player player=mock(Player.class);
    Stop a=new Stop("a","Alpha"),b=new Stop("b","Beta");
    Line line=new Line("red","Red");
    MockedStatic<SchedulerUtil> scheduler;
    PlayerMoveListener listener;
    @BeforeEach void setup() {
        scheduler=mockStatic(SchedulerUtil.class);
        scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin),eq(player),any(Runnable.class),anyLong(),anyLong()))
                .thenReturn(new Object());
        when(plugin.getConfigFacade()).thenReturn(config);when(plugin.getStopManager()).thenReturn(stops);
        when(plugin.getLineManager()).thenReturn(lines);when(plugin.getLineSelectionService()).thenReturn(selection);
        when(plugin.getLanguageManager()).thenReturn(language);
        when(language.getMessage(anyString(),anyMap())).thenReturn("routes");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("metro.use")).thenReturn(true);when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        when(config.isStopContinuousTitleEnabled()).thenReturn(true);when(config.isStopContinuousAlways()).thenReturn(false);
        when(config.getStopContinuousTitle(anyBoolean(),anyBoolean())).thenReturn("{stop_name}");
        when(config.getStopContinuousSubtitle(anyBoolean(),anyBoolean())).thenReturn("");
        when(config.getStopContinuousActionbar(anyBoolean(),anyBoolean())).thenReturn("");
        when(config.getStopContinuousStay()).thenReturn(40);
        line.addStop("a",-1);line.addStop("b",-1);line.addStop("c",-1);
        when(selection.getBoardableLines(any(Stop.class))).thenReturn(List.of(line));
        when(stops.getStop("a")).thenReturn(a);when(stops.getStop("b")).thenReturn(b);
        when(stops.getStopContainingLocation(any(Location.class))).thenAnswer(c -> {
            Location p=c.getArgument(0);return p.getX()<5 ? a : b;
        });
        listener=new PlayerMoveListener(plugin);
    }
    @AfterEach void cleanup() {listener.shutdown();scheduler.close();}
    void move(double from,double to) {
        Location target=new Location(world,to,64,0);when(player.getLocation()).thenReturn(target);
        listener.onPlayerMove(new PlayerMoveEvent(player,new Location(world,from,64,0),target));
    }
    @Test void oncePerEntryDisplaysAgainWhenReturningDirectlyFromAnotherStop() {
        a.setCustomTitle("stop_continuous",new HashMap<>(Map.of("title","<red><stop_name>")));
        move(-1,0);move(0,10);move(10,0);
        verify(player,times(2)).sendTitle("§cAlpha","",0,40,0);
        verify(player).sendTitle("Beta","",0,40,0);
    }
    @Test void sameCoordinatesInAnotherWorldStillCountAsAnEntry() {
        Location target=new Location(world,0,64,0);when(player.getLocation()).thenReturn(target);
        listener.onPlayerTeleport(new PlayerTeleportEvent(player,new Location(mock(World.class),0,64,0),target));
        verify(player).sendTitle("Alpha","",0,40,0);
    }
    @Test void multiLineStopUsesGlobalTitleAndCustomTemplates() {
        when(selection.getBoardableLines(a)).thenReturn(List.of(line,new Line("blue","Blue")));
        when(config.getStopContinuousTitle(false,false)).thenReturn("<gold>Metro <stop_name>");
        a.setCustomTitle("stop_continuous",new HashMap<>(Map.of("subtitle","<green><count> routes")));
        move(-1,0);
        verify(player).sendTitle("§6Metro Alpha","§a2 routes",0,40,0);
    }
    @Test void disabledStopDisplaySchedulesNothing() {
        when(config.isStopContinuousTitleEnabled()).thenReturn(false);
        move(-1,0);
        verify(player,never()).sendTitle(anyString(),anyString(),anyInt(),anyInt(),anyInt());
        scheduler.verifyNoInteractions();
    }
    @Test void quittingCancelsScheduledDisplay() {
        move(-1,0);
        listener.onPlayerQuit(new PlayerQuitEvent(player,""));
        scheduler.verify(() -> SchedulerUtil.cancelTask(any()));
    }
}
