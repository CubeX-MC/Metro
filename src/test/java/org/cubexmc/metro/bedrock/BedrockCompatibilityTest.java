package org.cubexmc.metro.bedrock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import org.cubexmc.metro.util.SchedulerUtil;
import org.junit.jupiter.api.Test;

class BedrockCompatibilityTest {

    @Test
    void shouldDelegatePlainPlayerTeleportDirectly() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
        Player player = onlinePlayer();
        Location destination = destination();
        when(player.getVehicle()).thenReturn(null);

        try (var scheduler = mockStatic(SchedulerUtil.class)) {
            scheduler.when(() -> SchedulerUtil.teleportEntity(player, destination))
                    .thenReturn(CompletableFuture.completedFuture(true));

            assertTrue(compatibility.teleportPlayer(player, destination).join());

            scheduler.verify(() -> SchedulerUtil.teleportEntity(player, destination));
        }
    }

    @Test
    void shouldDismountMountedPlayerBeforeTeleporting() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
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

            assertTrue(compatibility.teleportPlayer(player, destination).join());

            verify(vehicle).removePassenger(player);
            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(player), any(Runnable.class),
                    eq(1L), eq(-1L)));
        }
    }

    @Test
    void shouldTeleportAndMountPassenger() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
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

            assertTrue(compatibility.teleportAndMountPassenger(passenger, destination, targetCart).join());

            verify(targetCart).addPassenger(passenger);
        }
    }

    @Test
    void onTrainArrivalShouldBeNoOpForJavaPlayer() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
        Player passenger = onlinePlayer();
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        try (var detector = mockStatic(BedrockDetector.class);
             var scheduler = mockStatic(SchedulerUtil.class)) {
            detector.when(() -> BedrockDetector.isBedrockPlayer(passenger)).thenReturn(false);

            compatibility.onTrainArrival(passenger, minecart);

            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong()), never());
        }
    }

    @Test
    void onTrainArrivalShouldScheduleEntityTaskForBedrockPlayer() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
        Player passenger = onlinePlayer();
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        try (var detector = mockStatic(BedrockDetector.class);
             var scheduler = mockStatic(SchedulerUtil.class)) {
            detector.when(() -> BedrockDetector.isBedrockPlayer(passenger)).thenReturn(true);
            scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong())).thenReturn(new Object());

            compatibility.onTrainArrival(passenger, minecart);

            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong()));
        }
    }

    @Test
    void onTrainDepartureShouldCancelActiveArrivalTask() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
        Player passenger = onlinePlayer();
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());
        Object scheduledHandle = new Object();

        try (var detector = mockStatic(BedrockDetector.class);
             var scheduler = mockStatic(SchedulerUtil.class)) {
            detector.when(() -> BedrockDetector.isBedrockPlayer(passenger)).thenReturn(true);
            scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong())).thenReturn(scheduledHandle);

            compatibility.onTrainArrival(passenger, minecart);
            compatibility.onTrainDeparture(minecart);

            scheduler.verify(() -> SchedulerUtil.cancelTask(scheduledHandle));
        }
    }

    @Test
    void onTrainArrivalShouldBeNoOpWhenArrivalSyncIsDisabledInConfig() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin, () -> false);
        Player passenger = onlinePlayer();
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        try (var detector = mockStatic(BedrockDetector.class);
             var scheduler = mockStatic(SchedulerUtil.class)) {
            detector.when(() -> BedrockDetector.isBedrockPlayer(passenger)).thenReturn(true);

            compatibility.onTrainArrival(passenger, minecart);

            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong()), never());
        }
    }

    @Test
    void onTrainArrivalShouldReadConfigFlagPerCallSoReloadTakesEffect() {
        Plugin plugin = mock(Plugin.class);
        java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean(false);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin, enabled::get);
        Player passenger = onlinePlayer();
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        try (var detector = mockStatic(BedrockDetector.class);
             var scheduler = mockStatic(SchedulerUtil.class)) {
            detector.when(() -> BedrockDetector.isBedrockPlayer(passenger)).thenReturn(true);
            scheduler.when(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong())).thenReturn(new Object());

            compatibility.onTrainArrival(passenger, minecart);
            enabled.set(true);
            compatibility.onTrainArrival(passenger, minecart);

            scheduler.verify(() -> SchedulerUtil.entityRun(eq(plugin), eq(minecart), any(Runnable.class),
                    anyLong(), anyLong()));
        }
    }

    @Test
    void onTrainDepartureShouldBeNoOpWhenNothingActive() {
        Plugin plugin = mock(Plugin.class);
        BedrockCompatibility compatibility = new BedrockCompatibility(plugin);
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        try (var scheduler = mockStatic(SchedulerUtil.class)) {
            compatibility.onTrainDeparture(minecart);

            scheduler.verify(() -> SchedulerUtil.cancelTask(any()), never());
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
