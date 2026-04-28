package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class LineSelectionServiceTest {

    @Test
    void shouldFilterToBoardableLinesOnly() {
        World world = world("world");
        Stop current = stop("central", world);
        current.allowLine("red");

        Stop next = stop("next", world);
        Stop incompleteNext = new Stop("missing_point", "Missing Point");

        Line red = line("red", "world", "central", "next");
        Line terminal = line("terminal", "world", "central");
        Line missingNext = line("missing_next", "world", "central", "ghost");
        Line incomplete = line("incomplete", "world", "central", "missing_point");
        Line wrongWorld = line("wrong_world", "nether", "central", "next");
        Line denied = line("denied", "world", "central", "next");

        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        when(lineManager.getLinesForStop("central"))
                .thenReturn(List.of(denied, wrongWorld, incomplete, missingNext, terminal, red));
        when(stopManager.getStop("next")).thenReturn(next);
        when(stopManager.getStop("missing_point")).thenReturn(incompleteNext);

        LineSelectionService service = new LineSelectionService(lineManager, stopManager);

        assertEquals(List.of(red), service.getBoardableLines(current));
        assertEquals(red, service.resolveDefaultLine(player(0.0f), current, current.getStopPointLocation()));
    }

    @Test
    void shouldSortByIdAndUseRememberedChoice() {
        World world = world("world");
        Stop current = stop("central", world);
        Stop next = stop("next", world);
        Line a = line("a", "world", "central", "next");
        Line b = line("b", "world", "central", "next");

        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        when(lineManager.getLinesForStop("central")).thenReturn(List.of(b, a));
        when(stopManager.getStop("next")).thenReturn(next);

        LineSelectionService service = new LineSelectionService(lineManager, stopManager);
        Player player = player(90.0f);

        assertEquals(List.of(a, b), service.getBoardableLines(current));
        assertTrue(service.requiresChoice(player, current));

        service.rememberChoice(player, "central", "b");

        assertFalse(service.requiresChoice(player, current));
        assertEquals(b, service.resolveDefaultLine(player, current, current.getStopPointLocation()));
    }

    @Test
    void shouldReturnNullWhenNoLineCanBoard() {
        Stop current = new Stop("central", "Central");
        LineManager lineManager = mock(LineManager.class);
        StopManager stopManager = mock(StopManager.class);
        when(lineManager.getLinesForStop("central")).thenReturn(List.of(line("red", null, "central")));

        LineSelectionService service = new LineSelectionService(lineManager, stopManager);

        assertTrue(service.getBoardableLines(current).isEmpty());
        assertFalse(service.requiresChoice(player(0.0f), current));
        assertNull(service.resolveDefaultLine(player(0.0f), current, null));
    }

    private Line line(String id, String worldName, String... stopIds) {
        Line line = new Line(id, id.toUpperCase());
        line.setWorldName(worldName);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        return line;
    }

    private Stop stop(String id, World world) {
        Stop stop = new Stop(id, id.toUpperCase());
        stop.setCorner1(new Location(world, 0, 64, 0));
        stop.setCorner2(new Location(world, 5, 70, 5));
        stop.setStopPointLocation(new Location(world, 2, 65, 2));
        return stop;
    }

    private Player player(float yaw) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Location location = new Location(world("world"), 0, 64, 0);
        location.setYaw(yaw);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
