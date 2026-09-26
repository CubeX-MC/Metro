package org.cubexmc.metro.command.newcmd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.*;
import org.cubexmc.metro.model.*;
import org.cubexmc.metro.service.StopCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildingWorkflowTest {
    Metro plugin = mock(Metro.class);
    StopManager stops = mock(StopManager.class);
    LineManager lines = mock(LineManager.class);
    LanguageManager language = mock(LanguageManager.class);
    SelectionManager selection = new SelectionManager();
    Player player = mock(Player.class);
    World world = mock(World.class);
    Location position = new Location(world, 3.2, 64.1, 3.8, 90, 0);
    Location c1 = new Location(world, 0, 63, 0), c2 = new Location(world, 10, 67, 10);
    Block rail = mock(Block.class);
    UUID owner = UUID.randomUUID();

    @BeforeEach void setup() {
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(position);
        when(player.hasPermission("metro.admin")).thenReturn(true);
        when(world.getName()).thenReturn("world");
        when(plugin.getSelectionManager()).thenReturn(selection);
        when(plugin.getLanguageManager()).thenReturn(language);
        when(language.getMessage(anyString())).thenAnswer(c -> c.getArgument(0));
        when(language.getMessage(anyString(), anyMap())).thenAnswer(c -> c.getArgument(0));
        when(world.getBlockAt(any(Location.class))).thenAnswer(c -> {
            Location at=c.getArgument(0); return world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        });
        when(world.getBlockAt(3,64,3)).thenReturn(rail);
        when(rail.getType()).thenReturn(Material.POWERED_RAIL);
        when(rail.getLocation()).thenAnswer(c -> new Location(world,3,64,3));
        selection.setCorner1(player,c1); selection.setCorner2(player,c2);
    }
    @Test void createsAllPlacementFieldsAndDefaultsNameToId() {
        when(stops.createStop(eq("central"),eq("central"),eq(c1),eq(c2),eq(owner),any(Location.class),eq(90f)))
            .thenAnswer(c -> {
                Stop stop = new Stop(c.getArgument(0, String.class),c.getArgument(1, String.class));
                stop.setCorner1(c1);stop.setCorner2(c2);stop.setStopPointLocation(c.getArgument(5));stop.setLaunchYaw(c.getArgument(6));
                return stop;
            });
        new StopCommand(plugin,stops,lines).create(player,"central",null);
        verify(stops).createStop("central","central",c1,c2,owner,new Location(world,3.5,64.1,3.5,90,0),90f);
        verify(player).sendMessage("stop.create_ready");
    }
    @Test void areaOnlyCreationDoesNotPickAnUnrelatedRail() {
        when(rail.getType()).thenReturn(Material.STONE);
        Block below = mock(Block.class); when(below.getType()).thenReturn(Material.STONE);
        when(rail.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(stops.createStop("central","Central",c1,c2,owner)).thenReturn(new Stop("central","Central"));
        new StopCommand(plugin,stops,lines).create(player,"central","Central");
        verify(player).sendMessage("stop.create_area_only");
        verify(stops,never()).createStop(any(),any(),any(),any(),any(),any(),anyFloat());
    }
    @Test void rejectsCrossWorldSelectionBeforeWriting() {
        selection.setCorner2(player,new Location(mock(World.class),10,67,10));
        new StopCommand(plugin,stops,lines).create(player,"central",null);
        verify(player).sendMessage("stop.selection_world_mismatch");
        verifyNoInteractions(stops);
    }
    @Test void permissionDenialDoesNotCreateAnything() {
        when(player.hasPermission("metro.admin")).thenReturn(false);
        new StopCommand(plugin,stops,lines).create(player,"central",null);
        verify(player).sendMessage("stop.permission_create");
        verifyNoInteractions(stops);
    }
    @Test void standingJustAboveRailUsesThatRailAndKeepsFacing() {
        Block air=mock(Block.class);when(air.getType()).thenReturn(Material.AIR);
        when(air.getRelative(BlockFace.DOWN)).thenReturn(rail);
        when(world.getBlockAt(3,65,3)).thenReturn(air);
        Location point=new StopCommandService(stops).resolveStandingRail(new Location(world,3.2,65,3.8,-90,0));
        assertEquals(new Location(world,3.5,64.1,3.5,-90,0),point);
    }
    @Test void addStopUsesTheUniqueCurrentStationAndAppends() {
        Stop stop=new Stop("central","Central");stop.setCorner1(c1);stop.setCorner2(c2);
        when(stops.getAllStopIds()).thenReturn(Set.of("central"));when(stops.getStop("central")).thenReturn(stop);
        Line line=new Line("red","Red");when(lines.getLine("red")).thenReturn(line);
        when(lines.addStopToLine("red","central",-1)).thenReturn(true);
        new LineCommand(plugin,lines,stops).addStop(player,"red",null,null);
        verify(lines).addStopToLine("red","central",-1);
        verify(player).sendMessage("line.addstop_success");
    }
    @Test void overlappingStationsRequireAnExplicitId() {
        Stop first=mock(Stop.class),second=mock(Stop.class);
        when(first.isInStop(position)).thenReturn(true);when(second.isInStop(position)).thenReturn(true);
        when(stops.getAllStopIds()).thenReturn(Set.of("a","b"));
        when(stops.getStop("a")).thenReturn(first);when(stops.getStop("b")).thenReturn(second);
        when(lines.getLine("red")).thenReturn(new Line("red","Red"));
        new LineCommand(plugin,lines,stops).addStop(player,"red",null,null);
        verify(player).sendMessage("stop.context_ambiguous");
        verify(lines,never()).addStopToLine(anyString(),anyString(),anyInt());
    }
}
