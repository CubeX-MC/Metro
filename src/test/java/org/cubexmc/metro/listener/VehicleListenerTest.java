package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.train.ScoreboardManager;
import org.cubexmc.metro.util.MetroConstants;
import org.junit.jupiter.api.Test;

class VehicleListenerTest {

    @Test
    void shouldCancelDamageToMetroMinecartWhenDamageProtectionIsEnabled() {
        VehicleListener listener = new VehicleListener(plugin(true, false));
        Minecart minecart = metroMinecart();
        VehicleDamageEvent event = mock(VehicleDamageEvent.class);
        when(event.getVehicle()).thenReturn(minecart);

        listener.onMetroMinecartDamage(event);

        verify(event).setDamage(0);
        verify(event).setCancelled(true);
    }

    @Test
    void shouldIgnoreDamageToNonMetroMinecart() {
        VehicleListener listener = new VehicleListener(plugin(true, false));
        Minecart minecart = nonMetroMinecart();
        VehicleDamageEvent event = mock(VehicleDamageEvent.class);
        when(event.getVehicle()).thenReturn(minecart);

        listener.onMetroMinecartDamage(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).setDamage(0);
    }

    @Test
    void shouldCancelDestroyingMetroMinecartWhenDamageProtectionIsEnabled() {
        VehicleListener listener = new VehicleListener(plugin(true, false));
        Minecart minecart = metroMinecart();
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        when(event.getVehicle()).thenReturn(minecart);

        listener.onMetroMinecartDestroy(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelExternalEntityCollisionsWithMetroMinecart() {
        VehicleListener listener = new VehicleListener(plugin(false, true));
        Minecart minecart = metroMinecart();
        Entity outsideEntity = mock(Entity.class);
        VehicleEntityCollisionEvent event = mock(VehicleEntityCollisionEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getEntity()).thenReturn(outsideEntity);
        when(minecart.getPassengers()).thenReturn(List.of());

        listener.onMetroMinecartCollision(event);

        verify(event).setCancelled(true);
        verify(event).setCollisionCancelled(true);
        verify(event).setPickupCancelled(true);
    }

    @Test
    void shouldAllowPassengerCollisionWithOwnMetroMinecart() {
        VehicleListener listener = new VehicleListener(plugin(false, true));
        Minecart minecart = metroMinecart();
        Entity passenger = mock(Entity.class);
        VehicleEntityCollisionEvent event = mock(VehicleEntityCollisionEvent.class);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getEntity()).thenReturn(passenger);
        when(minecart.getPassengers()).thenReturn(List.of(passenger));

        listener.onMetroMinecartCollision(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).setCollisionCancelled(true);
        verify(event, never()).setPickupCancelled(true);
    }

    @Test
    void shouldCancelEntityHitAgainstMetroMinecartWhenPushProtectionIsEnabled() {
        VehicleListener listener = new VehicleListener(plugin(false, true));
        Minecart minecart = metroMinecart();
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(minecart);

        listener.onEntityHitMetroMinecart(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldIgnoreMoveEventsForNonMetroMinecarts() {
        VehicleListener listener = new VehicleListener(plugin(false, false));
        Minecart minecart = nonMetroMinecart();
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(minecart);

        listener.onVehicleMove(event);

        verify(minecart, never()).eject();
        verify(minecart, never()).remove();
    }

    @Test
    void shouldEjectAndRemoveMetroMinecartWhenItDerails() {
        VehicleListener listener = new VehicleListener(plugin(false, false));
        Minecart minecart = metroMinecart();
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        Location from = derailedLocation();
        Location to = derailedLocation();
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        listener.onVehicleMove(event);

        verify(minecart).eject();
        verify(minecart).remove();
    }

    @Test
    void shouldFreezeMetroMinecartWhenMaxSpeedIsZeroOnRail() {
        VehicleListener listener = new VehicleListener(plugin(false, false));
        Minecart minecart = metroMinecart();
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        Location from = railLocation(Material.RAIL, 64, mock(World.class));
        Location to = railLocation(Material.RAIL, 64, mock(World.class));
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(minecart.getMaxSpeed()).thenReturn(0.0);

        listener.onVehicleMove(event);

        verify(minecart).setVelocity(org.mockito.ArgumentMatchers.argThat(vector ->
                vector.getX() == 0.0 && vector.getY() == 0.0 && vector.getZ() == 0.0));
        verify(minecart, never()).eject();
        verify(minecart, never()).remove();
    }

    @Test
    void shouldApplyWorldSpecificBlockSpeedWhenMetroMinecartMovesOnRail() {
        Metro plugin = plugin(false, false);
        VehicleListener listener = new VehicleListener(plugin);
        Minecart minecart = metroMinecart();
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        World world = mock(World.class);
        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.POWERED_RAIL, 64, world);
        Block support = block(Material.GOLD_BLOCK);
        when(world.getName()).thenReturn("world");
        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(support);
        when(plugin.getConfigFacade().getSpeedControlMode()).thenReturn("BLOCK_BASED");
        when(plugin.getConfigFacade().getBlockSpeedMap()).thenReturn(Map.of("world", Map.of("GOLD_BLOCK", 2.0)));
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        listener.onVehicleMove(event);

        verify(minecart).setMaxSpeed(0.8);
    }

    @Test
    void shouldTeleportMinecartWhenPortalTriggerBlockMatches() {
        Metro plugin = plugin(false, false);
        VehicleListener listener = new VehicleListener(plugin);
        Minecart minecart = metroMinecart();
        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        World world = mock(World.class);
        Location from = railLocation(Material.RAIL, 64, world);
        Location to = railLocation(Material.RAIL, 64, world);
        Block trigger = block(Material.EMERALD_BLOCK);
        Block belowTrigger = block(Material.STONE);
        Portal portal = mock(Portal.class);
        when(to.getBlock().getRelative(BlockFace.DOWN)).thenReturn(trigger);
        when(trigger.getRelative(BlockFace.DOWN)).thenReturn(belowTrigger);
        when(plugin.getConfigFacade().isPortalsEnabled()).thenReturn(true);
        when(plugin.getConfigFacade().getPortalTriggerBlock()).thenReturn("EMERALD_BLOCK");
        when(plugin.getPortalManager().getPortalAt(to)).thenReturn(portal);
        when(event.getVehicle()).thenReturn(minecart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(minecart.getMaxSpeed()).thenReturn(0.4);

        listener.onVehicleMove(event);

        verify(plugin.getPortalManager()).teleportMinecart(minecart, portal);
        verify(minecart, never()).setVelocity(any());
    }

    @Test
    void shouldNotCancelDamageWhenDamageProtectionIsDisabled() {
        VehicleListener listener = new VehicleListener(plugin(false, false));
        VehicleDamageEvent event = mock(VehicleDamageEvent.class);

        listener.onMetroMinecartDamage(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).setDamage(0);
    }

    private Metro plugin(boolean damageProtection, boolean pushProtection) {
        Metro plugin = mock(Metro.class);
        when(plugin.getName()).thenReturn("metro");

        ConfigFacade configFacade = mock(ConfigFacade.class);
        StopManager stopManager = mock(StopManager.class);
        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        PortalManager portalManager = mock(PortalManager.class);
        when(configFacade.isSafeModeDamageProtection()).thenReturn(damageProtection);
        when(configFacade.isSafeModeEntityPushProtection()).thenReturn(pushProtection);
        when(configFacade.getSpeedControlMode()).thenReturn("DEFAULT");
        when(configFacade.getBlockSpeedMap()).thenReturn(Map.of());
        when(configFacade.isPortalsEnabled()).thenReturn(false);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getScoreboardManager()).thenReturn(scoreboardManager);
        when(plugin.getPortalManager()).thenReturn(portalManager);

        MetroConstants.initialize(plugin);
        return plugin;
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

    private Location derailedLocation() {
        Location location = mock(Location.class);
        Location below = mock(Location.class);
        Block block = block(Material.AIR);
        Block blockBelow = block(Material.STONE);
        when(location.getBlock()).thenReturn(block);
        when(location.clone()).thenReturn(below);
        when(below.subtract(0, 1, 0)).thenReturn(below);
        when(below.getBlock()).thenReturn(blockBelow);
        return location;
    }

    private Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private Location railLocation(Material material, double y, World world) {
        Location location = mock(Location.class);
        Block block = block(material);
        when(location.getBlock()).thenReturn(block);
        when(location.getY()).thenReturn(y);
        when(location.getWorld()).thenReturn(world);
        when(location.toVector()).thenReturn(new org.bukkit.util.Vector(0, y, 0));
        return location;
    }
}
