package org.cubexmc.metro.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MountAwareTeleportUtilTest {

    @Test
    void shouldDelegatePlainPlayerTeleportDirectly() {
        Plugin plugin = mock(Plugin.class);
        Player player = onlinePlayer();
        Location destination = destination();
        when(player.getVehicle()).thenReturn(null);

        try (var scheduler = mockStatic(SchedulerUtil.class)) {
            scheduler.when(() -> SchedulerUtil.teleportEntity(player, destination))
                    .thenReturn(CompletableFuture.completedFuture(true));

            assertTrue(MountAwareTeleportUtil.teleportPlayer(plugin, player, destination).join());

            scheduler.verify(() -> SchedulerUtil.teleportEntity(player, destination));
        }
    }

    @Test
    void shouldDismountMountedPlayerBeforeTeleporting() {
        Plugin plugin = mock(Plugin.class);
        Player player = onlinePlayer();
        Entity vehicle = mock(Entity.class);
        Location destination = destination();
        when(player.getVehicle()).thenReturn(vehicle);

        try (var scheduler = mockStatic(SchedulerUtil.class)) {
            scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin), eq(player), any(Runnable.class),
                    eq(1L), eq(-1L))).thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return new Object();
                    });
            scheduler.when(() -> SchedulerUtil.teleportEntity(player, destination))
                    .thenReturn(CompletableFuture.completedFuture(true));

            assertTrue(MountAwareTeleportUtil.teleportPlayer(plugin, player, destination).join());

            verify(vehicle).removePassenger(player);
            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(player), any(Runnable.class),
                    eq(1L), eq(-1L)));
        }
    }

    @Test
    void shouldTeleportAndMountPassenger() {
        Plugin plugin = mock(Plugin.class);
        Player passenger = onlinePlayer();
        Location destination = destination();
        Minecart targetCart = mock(Minecart.class);
        when(targetCart.isValid()).thenReturn(true);
        when(targetCart.addPassenger(passenger)).thenReturn(true);

        try (var scheduler = mockStatic(SchedulerUtil.class)) {
            scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin), eq(passenger), any(Runnable.class),
                    anyLong(), eq(-1L))).thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return new Object();
                    });
            scheduler.when(() -> SchedulerUtil.regionRun(eq(plugin), eq(destination), any(Runnable.class),
                    eq(2L), eq(-1L))).thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return new Object();
                    });
            scheduler.when(() -> SchedulerUtil.teleportEntity(passenger, destination))
                    .thenReturn(CompletableFuture.completedFuture(true));

            assertTrue(MountAwareTeleportUtil.teleportAndMountPassenger(plugin, passenger, destination, targetCart)
                    .join());

            verify(targetCart).addPassenger(passenger);
        }
    }

    private Player onlinePlayer() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private Location destination() {
        World world = mock(World.class);
        return new Location(world, 10, 65, 10);
    }
}
