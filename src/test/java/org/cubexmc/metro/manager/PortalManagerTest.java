package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.persistence.SaveCoordinator;
import org.cubexmc.metro.util.SchedulerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class PortalManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreatePersistReloadAndFindPortalByLocation() throws IOException {
        PortalManager manager = new PortalManager(createPluginMock(tempDir));
        UUID ownerId = UUID.randomUUID();
        Location entrance = location("world", 10.7, 64.2, 20.9);
        Location destination = location("nether", 1.5, 70.0, -2.5);
        destination.setYaw(135.0F);

        Portal created = manager.createPortal("p1", entrance, ownerId);
        assertEquals(ownerId, created.getOwner());
        assertTrue(manager.setDestination("p1", destination));
        manager.forceSaveSync();

        String savedYaml = Files.readString(tempDir.resolve("portals.yml"));
        assertTrue(savedYaml.contains("portals:"));
        assertTrue(savedYaml.contains("p1:"));
        assertTrue(savedYaml.contains("world: world"));
        assertTrue(savedYaml.contains("dest_world: nether"));
        assertTrue(savedYaml.contains("owner: " + ownerId));

        PortalManager reloaded = new PortalManager(createPluginMock(tempDir));
        Portal portal = reloaded.getPortal("p1");
        assertNotNull(portal);
        assertEquals("p1", portal.getId());
        assertEquals(portal, reloaded.getPortalAt(location("world", 10, 64, 20)));
        assertNull(reloaded.getPortalAt(location("world", 11, 64, 20)));
    }

    @Test
    void shouldSavePortalChangesThroughAsyncCoordinator() throws IOException {
        PortalManager manager = new PortalManager(createPluginMock(tempDir));
        UUID adminId = UUID.randomUUID();

        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        assertTrue(manager.addPortalAdmin("p1", adminId));
        assertFalse(manager.addPortalAdmin("missing", adminId));
        manager.processAsyncSave();

        String savedYaml = Files.readString(tempDir.resolve("portals.yml"));
        assertTrue(savedYaml.contains("p1:"));
        assertTrue(savedYaml.contains("- " + adminId));
    }

    @Test
    void shouldUnlinkDeleteAndNotifyLineManager() throws IOException {
        LineManager lineManager = mock(LineManager.class);
        Metro plugin = createPluginMock(tempDir);
        when(plugin.getLineManager()).thenReturn(lineManager);
        PortalManager manager = new PortalManager(plugin);

        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.createPortal("p2", location("world", 2, 64, 2), UUID.randomUUID());
        assertTrue(manager.linkPortals("p1", "p2"));
        assertEquals("p2", manager.getPortal("p1").getLinkedPortalId());
        assertEquals("p1", manager.getPortal("p2").getLinkedPortalId());

        assertTrue(manager.deletePortal("p1"));
        assertFalse(manager.deletePortal("p1"));
        assertNull(manager.getPortal("p1"));
        assertNull(manager.getPortal("p2").getLinkedPortalId());
        verify(lineManager).delPortalFromAllLines("p1");

        manager.forceSaveSync();
        String savedYaml = Files.readString(tempDir.resolve("portals.yml"));
        assertFalse(savedYaml.contains("p1:"));
        assertTrue(savedYaml.contains("p2:"));
        assertFalse(savedYaml.contains("linked: p1"));
    }

    @Test
    void forceSaveShouldRetryAfterCoordinatorFailure() {
        SaveCoordinator coordinator = mock(SaveCoordinator.class);
        doThrow(new RuntimeException("boom")).when(coordinator).saveNow(any(Path.class), anyString());
        PortalManager manager = new PortalManager(createPluginMock(tempDir, coordinator));

        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());

        manager.forceSaveSync();
        manager.forceSaveSync();

        verify(coordinator, times(2)).saveNow(any(Path.class), anyString());
    }

    @Test
    void asyncSaveShouldRetryAfterCoordinatorFailure() {
        SaveCoordinator coordinator = mock(SaveCoordinator.class);
        doThrow(new RuntimeException("boom")).when(coordinator).submitSnapshot(any(Path.class), anyString());
        PortalManager manager = new PortalManager(createPluginMock(tempDir, coordinator));

        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());

        manager.processAsyncSave();
        manager.processAsyncSave();

        verify(coordinator, times(2)).submitSnapshot(any(Path.class), anyString());
    }

    @Test
    void concurrentMutationsAndSavesShouldRemainConsistent() throws Exception {
        PortalManager manager = new PortalManager(createPluginMock(tempDir));
        int portalCount = 24;
        for (int index = 0; index < portalCount; index++) {
            manager.createPortal("p" + index, location("world", index, 64, index), UUID.randomUUID());
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int index = 0; index < portalCount; index++) {
            final int portalIndex = index;
            tasks.add(() -> {
                String portalId = "p" + portalIndex;
                manager.setDestination(portalId, location("dest", portalIndex + 0.5, 70, portalIndex + 0.5));
                manager.addPortalAdmin(portalId, UUID.randomUUID());
                manager.getPortalAt(location("world", portalIndex, 64, portalIndex));
                manager.processAsyncSave();
                return null;
            });
        }

        try {
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(portalCount, manager.getAllPortals().size());
        manager.forceSaveSync();
        String savedYaml = Files.readString(tempDir.resolve("portals.yml"));
        assertTrue(savedYaml.contains("p0:"));
        assertTrue(savedYaml.contains("p23:"));
        assertTrue(savedYaml.contains("dest_world: dest"));
    }

    @Test
    void teleportShouldReturnBeforeTouchingMinecartWhenDestinationWorldIsUnavailable() {
        PortalManager manager = new PortalManager(createPluginMock(tempDir));
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("missing", 10, 70, 10));
        Portal portal = manager.getPortal("p1");
        Minecart minecart = mock(Minecart.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("missing")).thenReturn(null);

            manager.teleportMinecart(minecart, portal);
        }

        verifyNoInteractions(minecart);
    }

    @Test
    void teleportShouldRestoreOnlinePassengerToSpawnedMinecart() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Minecart sourceCart = minecartWithPassenger(world("world"), passenger(true));
        Player passenger = (Player) sourceCart.getPassengers().get(0);
        Minecart newCart = mock(Minecart.class);
        when(newCart.isValid()).thenReturn(true);
        when(destWorld.spawn(any(Location.class), eq(Minecart.class))).thenReturn(newCart);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);
            scheduler.when(() -> SchedulerUtil.teleportEntity(eq(passenger), any(Location.class)))
                    .thenReturn(CompletableFuture.completedFuture(true));

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));
        }

        verify(sourceCart).eject();
        verify(sourceCart).remove();
        verify(destWorld).spawn(any(Location.class), eq(Minecart.class));
        verify(newCart).addPassenger(passenger);
        verify(newCart).setVelocity(any());
    }

    @Test
    void teleportShouldRemoveSpawnedMinecartWhenPassengerTeleportFails() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Minecart sourceCart = minecartWithPassenger(world("world"), passenger(true));
        Player passenger = (Player) sourceCart.getPassengers().get(0);
        Minecart newCart = mock(Minecart.class);
        when(newCart.isValid()).thenReturn(true);
        when(destWorld.spawn(any(Location.class), eq(Minecart.class))).thenReturn(newCart);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);
            scheduler.when(() -> SchedulerUtil.teleportEntity(eq(passenger), any(Location.class)))
                    .thenReturn(CompletableFuture.completedFuture(false));

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));
        }

        verify(newCart, never()).addPassenger(passenger);
        verify(newCart, never()).setVelocity(any());
        verify(newCart).remove();
    }

    @Test
    void teleportShouldNotRestoreOfflinePassenger() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Minecart sourceCart = minecartWithPassenger(world("world"), passenger(false));
        Player passenger = (Player) sourceCart.getPassengers().get(0);
        Minecart newCart = mock(Minecart.class);
        when(newCart.isValid()).thenReturn(true);
        when(destWorld.spawn(any(Location.class), eq(Minecart.class))).thenReturn(newCart);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));

            scheduler.verify(() -> SchedulerUtil.teleportEntity(eq(passenger), any(Location.class)), never());
        }

        verify(newCart, never()).addPassenger(passenger);
        verify(newCart).setVelocity(any());
    }

    @Test
    void teleportShouldNotSpawnMinecartWhenDestinationChunkIsUnavailable() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        Minecart sourceCart = minecartWithoutPassenger(world("world"));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));
        }

        verify(destWorld, never()).spawn(any(Location.class), eq(Minecart.class));
    }

    @Test
    void teleportShouldNotRestorePassengerWhenNewMinecartIsInvalid() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Minecart sourceCart = minecartWithPassenger(world("world"), passenger(true));
        Player passenger = (Player) sourceCart.getPassengers().get(0);
        Minecart newCart = mock(Minecart.class);
        when(newCart.isValid()).thenReturn(false);
        when(destWorld.spawn(any(Location.class), eq(Minecart.class))).thenReturn(newCart);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);
            scheduler.when(() -> SchedulerUtil.teleportEntity(eq(passenger), any(Location.class)))
                    .thenReturn(CompletableFuture.completedFuture(true));

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));
        }

        verify(newCart, never()).addPassenger(passenger);
    }

    @Test
    void teleportShouldNotRemoveSourceMinecartWhenAlreadyInvalid() {
        Metro plugin = createPluginMock(tempDir);
        PortalManager manager = new PortalManager(plugin);
        manager.createPortal("p1", location("world", 1, 64, 1), UUID.randomUUID());
        manager.setDestination("p1", location("dest", 10, 70, 10));

        World destWorld = world("dest");
        when(destWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Minecart sourceCart = minecartWithoutPassenger(world("world"));
        when(sourceCart.isValid()).thenReturn(false);
        Minecart newCart = mock(Minecart.class);
        when(destWorld.spawn(any(Location.class), eq(Minecart.class))).thenReturn(newCart);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<SchedulerUtil> scheduler = org.mockito.Mockito.mockStatic(SchedulerUtil.class)) {
            bukkit.when(() -> Bukkit.getWorld("dest")).thenReturn(destWorld);
            executeScheduledWork(scheduler);

            manager.teleportMinecart(sourceCart, manager.getPortal("p1"));
        }

        verify(sourceCart, never()).remove();
    }

    private Metro createPluginMock(Path dataDir) {
        return createPluginMock(dataDir, new SaveCoordinator(createQuietLogger(), Runnable::run));
    }

    private Metro createPluginMock(Path dataDir, SaveCoordinator saveCoordinator) {
        Metro plugin = mock(Metro.class);
        Logger logger = createQuietLogger();
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getSaveCoordinator()).thenReturn(saveCoordinator);
        ConfigFacade configFacade = mock(ConfigFacade.class);
        when(configFacade.getPortalTeleportDelay()).thenReturn(0);
        when(configFacade.getCartSpeed()).thenReturn(0.4);
        when(configFacade.isPortalEffectParticles()).thenReturn(false);
        when(configFacade.isPortalEffectSound()).thenReturn(false);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getLanguageManager()).thenReturn(mock(LanguageManager.class));
        return plugin;
    }

    private Logger createQuietLogger() {
        Logger logger = Logger.getLogger("PortalManagerTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private Location location(String worldName, double x, double y, double z) {
        World world = world(worldName);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getX()).thenReturn(x);
        when(location.getY()).thenReturn(y);
        when(location.getZ()).thenReturn(z);
        when(location.getBlockX()).thenReturn((int) Math.floor(x));
        when(location.getBlockY()).thenReturn((int) Math.floor(y));
        when(location.getBlockZ()).thenReturn((int) Math.floor(z));
        when(location.getYaw()).thenReturn(0.0F);
        return location;
    }

    private World world(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        return world;
    }

    private Player passenger(boolean online) {
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(online);
        return passenger;
    }

    private Minecart minecartWithPassenger(World world, Player passenger) {
        Minecart minecart = minecartWithoutPassenger(world);
        when(minecart.getPassengers()).thenReturn(List.of(passenger));
        return minecart;
    }

    private Minecart minecartWithoutPassenger(World world) {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());
        Location sourceLocation = mock(Location.class);
        when(sourceLocation.getWorld()).thenReturn(world);
        when(minecart.getLocation()).thenReturn(sourceLocation);
        when(minecart.getPassengers()).thenReturn(Collections.emptyList());
        when(minecart.isValid()).thenReturn(true);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(), eq(PersistentDataType.BYTE))).thenReturn(false);
        when(minecart.getPersistentDataContainer()).thenReturn(pdc);
        return minecart;
    }

    private void executeScheduledWork(MockedStatic<SchedulerUtil> scheduler) {
        scheduler.when(() -> SchedulerUtil.entityRun(any(), any(Entity.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(2).run();
                    return new Object();
                });
        scheduler.when(() -> SchedulerUtil.regionRun(any(), any(Location.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(2).run();
                    return new Object();
                });
    }
}
