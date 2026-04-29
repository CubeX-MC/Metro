package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
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
import org.cubexmc.metro.manager.StopManager;
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

    private Metro plugin(boolean damageProtection, boolean pushProtection) {
        Metro plugin = mock(Metro.class);
        when(plugin.getName()).thenReturn("metro");

        ConfigFacade configFacade = mock(ConfigFacade.class);
        StopManager stopManager = mock(StopManager.class);
        when(configFacade.isSafeModeDamageProtection()).thenReturn(damageProtection);
        when(configFacade.isSafeModeEntityPushProtection()).thenReturn(pushProtection);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getStopManager()).thenReturn(stopManager);

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
}
