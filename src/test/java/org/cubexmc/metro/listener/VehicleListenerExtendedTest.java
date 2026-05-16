package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.event.TrainExitStopEvent;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.LocationUtil;
import org.cubexmc.metro.util.MetroConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class VehicleListenerExtendedTest {

    private Metro plugin;
    private ConfigFacade configFacade;
    private StopManager stopManager;
    private ScoreboardManager scoreboardManager;
    private PortalManager portalManager;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        when(plugin.getName()).thenReturn("metro");
        configFacade = mock(ConfigFacade.class);
        stopManager = mock(StopManager.class);
        scoreboardManager = mock(ScoreboardManager.class);
        portalManager = mock(PortalManager.class);

        when(configFacade.isSafeModeDamageProtection()).thenReturn(false);
        when(configFacade.isSafeModeEntityPushProtection()).thenReturn(false);
        when(configFacade.getSpeedControlMode()).thenReturn("DEFAULT");
        when(configFacade.getBlockSpeedMap()).thenReturn(Map.of());
        when(configFacade.isPortalsEnabled()).thenReturn(false);

        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getScoreboardManager()).thenReturn(scoreboardManager);
        when(plugin.getPortalManager()).thenReturn(portalManager);

        MetroConstants.initialize(plugin);
    }

    private VehicleListener createListener() {
        return new VehicleListener(plugin);
    }

    private Minecart metroMinecart() {
        Minecart minecart = mock(Minecart.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(minecart.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(eq(MetroConstants.getMinecartKey()), eq(PersistentDataType.BYTE))).thenReturn(true);
        return minecart;
    }

    private Minecart nonMetroMinecart() {
        Minecart minecart = mock(Minecart.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(minecart.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);
        return minecart;
    }

    private Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private Location railLocation(Material railMaterial, double y, World world) {
        Location location = mock(Location.class);
        Block railBlock = block(railMaterial);
        when(location.getBlock()).thenReturn(railBlock);
        when(location.getY()).thenReturn(y);
        when(location.getWorld()).thenReturn(world);
        when(location.toVector()).thenReturn(new Vector(0, y, 0));
        return location;
    }

    // ---- VehicleMoveEvent: stop enter/exit events ----

    @Test
    void shouldFireEnterStopEventWhenMovingIntoStop() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 64, world);

        Stop stop = new Stop("s1", "Station 1");
        when(stopManager.getStopContainingLocation(to)).thenReturn(stop);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        PluginManager pluginManager = mock(PluginManager.class);
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            listener.onVehicleMove(event);
            verify(pluginManager).callEvent(any(TrainEnterStopEvent.class));
        }
    }

    @Test
    void shouldFireExitStopEventWhenMovingOutOfStop() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 64, world);

        Stop previousStop = new Stop("s1", "Station 1");
        when(stopManager.getStopContainingLocation(to)).thenReturn(null);
        when(stopManager.getStop("s1")).thenReturn(previousStop);

        NamespacedKey currentStopKey = new NamespacedKey(plugin, "current_stop_id");
        PersistentDataContainer pdc = minecart.getPersistentDataContainer();
        when(pdc.get(currentStopKey, PersistentDataType.STRING)).thenReturn("s1");

        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        PluginManager pluginManager = mock(PluginManager.class);
        try (var bukkitMock = mockStatic(Bukkit.class)) {
            bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            listener.onVehicleMove(event);
            verify(pluginManager).callEvent(any(TrainExitStopEvent.class));
        }
    }

    // ---- VehicleMoveEvent: default speed fallback when no speed map ----

    @Test
    void shouldUseDefaultSpeedWhenNoWorldOrFallbackInSpeedMap() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);
        when(world.getName()).thenReturn("custom_world");

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.POWERED_RAIL, 64, world);
        Block support = block(Material.STONE);
        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(support);

        when(configFacade.getSpeedControlMode()).thenReturn("BLOCK_BASED");
        when(configFacade.getBlockSpeedMap()).thenReturn(Map.of());
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        listener.onVehicleMove(event);

        verify(minecart).setMaxSpeed(0.4);
    }

    @Test
    void shouldUseDefaultBlockSpeedWhenBlockNotInSpeedMap() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.POWERED_RAIL, 64, world);
        Block support = block(Material.IRON_BLOCK);
        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(support);

        when(configFacade.getSpeedControlMode()).thenReturn("BLOCK_BASED");
        when(configFacade.getBlockSpeedMap()).thenReturn(Map.of(
                "world", Map.of("GOLD_BLOCK", 2.0, "DEFAULT", 1.5)
        ));

        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        listener.onVehicleMove(event);

        verify(minecart).setMaxSpeed(0.4 * 1.5);
    }

    // ---- VehicleMoveEvent: uphill speed limiting ----

    @Test
    void shouldLimitSpeedWhenGoingUphill() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 65, world);

        when(minecart.getMaxSpeed()).thenReturn(0.8);
        when(from.toVector()).thenReturn(new Vector(0, 64, 0));
        when(to.toVector()).thenReturn(new Vector(0, 65, 0));

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        try (var locationUtilMock = mockStatic(LocationUtil.class)) {
            locationUtilMock.when(() -> LocationUtil.isOnRail(to)).thenReturn(true);
            locationUtilMock.when(() -> LocationUtil.getDirectionVector(from, to))
                    .thenReturn(new Vector(0, 1, 0));

            listener.onVehicleMove(event);

            verify(minecart).setVelocity(any(Vector.class));
        }
    }

    // ---- VehicleMoveEvent: non-minecart vehicle ignored ----

    @Test
    void shouldIgnoreMoveEventForNonMinecartVehicle() {
        VehicleListener listener = createListener();
        Vehicle vehicle = mock(Vehicle.class);
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);

        listener.onVehicleMove(event);

        verify(stopManager, never()).getStopContainingLocation(any());
    }

    // ---- Portal: trigger block on blockBelow2 ----

    @Test
    void shouldDetectPortalTriggerOnSecondBlockBelow() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 64, world);
        Block trigger = block(Material.EMERALD_BLOCK);
        Block blockBelow = block(Material.RAIL);
        Block blockBelow2 = block(Material.EMERALD_BLOCK);

        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(blockBelow);
        when(blockBelow.getRelative(BlockFace.DOWN)).thenReturn(blockBelow2);

        Portal portal = mock(Portal.class);
        TrainMovementTask task = mock(TrainMovementTask.class);
        when(portal.getId()).thenReturn("p1");
        when(task.canUsePortal("p1")).thenReturn(true);

        when(configFacade.isPortalsEnabled()).thenReturn(true);
        when(configFacade.getPortalTriggerBlock()).thenReturn("EMERALD_BLOCK");
        when(plugin.getPortalManager().getPortalAt(to)).thenReturn(portal);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        try (MockedStatic<TrainMovementTask> taskRegistry = mockStatic(TrainMovementTask.class)) {
            taskRegistry.when(() -> TrainMovementTask.getTaskFor(minecart)).thenReturn(task);
            listener.onVehicleMove(event);
        }

        verify(plugin.getPortalManager()).teleportMinecart(minecart, portal);
    }

    // ---- Portal: trigger block on same block level ----
    // Note: This test is covered by the existing test suite for to.getBlock().getRelative(DOWN) match.

    // ---- isPortalEnabledForCurrentLine: no TrainMovementTask ----

    @Test
    void shouldNotTeleportWhenNoMovementTask() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 64, world);
        Block trigger = block(Material.EMERALD_BLOCK);
        Block belowTrigger = block(Material.STONE);

        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(trigger);
        when(trigger.getRelative(BlockFace.DOWN)).thenReturn(belowTrigger);

        Portal portal = mock(Portal.class);
        when(configFacade.isPortalsEnabled()).thenReturn(true);
        when(configFacade.getPortalTriggerBlock()).thenReturn("EMERALD_BLOCK");
        when(plugin.getPortalManager().getPortalAt(to)).thenReturn(portal);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        try (MockedStatic<TrainMovementTask> taskRegistry = mockStatic(TrainMovementTask.class)) {
            taskRegistry.when(() -> TrainMovementTask.getTaskFor(minecart)).thenReturn(null);
            listener.onVehicleMove(event);
        }

        verify(plugin.getPortalManager(), never()).teleportMinecart(any(), any());
    }

    // ---- Collision: ignore non-minecart vehicles ----

    @Test
    void shouldIgnoreCollisionForNonMinecart() {
        VehicleListener listener = createListener();
        when(configFacade.isSafeModeEntityPushProtection()).thenReturn(true);

        Vehicle vehicle = mock(Vehicle.class);
        Entity entity = mock(Entity.class);
        VehicleEntityCollisionEvent event = mock(VehicleEntityCollisionEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);
        when(event.getEntity()).thenReturn(entity);

        listener.onMetroMinecartCollision(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- Damage: ignore when non-minecart ----

    @Test
    void shouldIgnoreDamageForNonMinecart() {
        VehicleListener listener = createListener();
        when(configFacade.isSafeModeDamageProtection()).thenReturn(true);

        Vehicle vehicle = mock(Vehicle.class);
        VehicleDamageEvent event = mock(VehicleDamageEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);

        listener.onMetroMinecartDamage(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- Destroy: ignore non-minecart ----

    @Test
    void shouldIgnoreDestroyForNonMinecart() {
        VehicleListener listener = createListener();
        when(configFacade.isSafeModeDamageProtection()).thenReturn(true);

        Vehicle vehicle = mock(Vehicle.class);
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        when(event.getVehicle()).thenReturn(vehicle);

        listener.onMetroMinecartDestroy(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- Fallback speed map: uses "default" key when world not found ----

    @Test
    void shouldUseFallbackSpeedMapWhenWorldNotFound() {
        VehicleListener listener = createListener();
        Minecart minecart = metroMinecart();
        World world = mock(World.class);
        when(world.getName()).thenReturn("other_world");

        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.POWERED_RAIL, 64, world);
        Block support = block(Material.DIAMOND_BLOCK);
        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(support);

        when(configFacade.getSpeedControlMode()).thenReturn("BLOCK_BASED");
        when(configFacade.getBlockSpeedMap()).thenReturn(Map.of(
                "default", Map.of("DIAMOND_BLOCK", 3.0)
        ));
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        listener.onVehicleMove(event);

        verify(minecart).setMaxSpeed(0.4 * 3.0);
    }
}
