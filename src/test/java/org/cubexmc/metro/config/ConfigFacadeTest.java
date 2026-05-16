package org.cubexmc.metro.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class ConfigFacadeTest {

    @Test
    void shouldReadLegacyEnterStopWhenStopContinuousIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.enabled", false);
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.enter_stop.subtitle", "Legacy Subtitle");
        config.set("titles.enter_stop.fade_in", 3);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertFalse(facade.isStopContinuousTitleEnabled());
        assertEquals("Legacy Title", facade.getStopContinuousTitle(false, false));
        assertEquals("Legacy Subtitle", facade.getStopContinuousSubtitle(false, false));
        assertEquals(3, facade.getStopContinuousFadeIn());
        assertEquals("Legacy Title", facade.getEnterStopTitle());
    }

    @Test
    void shouldPreferStopContinuousOverLegacyEnterStop() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.stop_continuous.title", "Modern Title");
        config.set("titles.stop_continuous.start_stop.title", "Origin Title");

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals("Modern Title", facade.getStopContinuousTitle(false, false));
        assertEquals("Origin Title", facade.getStopContinuousTitle(true, false));
    }

    @Test
    void shouldRefreshStopContinuousValuesOnReload() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.stop_continuous.interval", 40);

        ConfigFacade facade = createFacade(config);
        facade.reload();
        assertEquals(40, facade.getStopContinuousInterval());

        config.set("titles.stop_continuous.interval", 80);
        facade.reload();
        assertEquals(80, facade.getStopContinuousInterval());
    }

    @Test
    void shouldClampMapRefreshDelayToAtLeastOneTick() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("map_integration.refresh_delay_ticks", 0L);
        config.set("route_recording.min_sample_distance_blocks", 0.0D);
        config.set("route_recording.simplify_epsilon_blocks", -1.0D);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals(1L, facade.getMapRefreshDelayTicks());
        assertEquals(0.1D, facade.getRouteRecordingMinSampleDistanceBlocks());
        assertEquals(0.0D, facade.getRouteRecordingSimplifyEpsilonBlocks());
    }

    @Test
    void shouldDefaultMapProviderToAuto() {
        ConfigFacade facade = createFacade(new YamlConfiguration());
        facade.reload();

        assertEquals("AUTO", facade.getMapProvider());
    }

    @Test
    void shouldReadCurrentSpeedControlWorldMap() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("speed_control.mode", "CUSTOM_BLOCKS");
        config.set("speed_control.worlds.world.powered_rail", 0.8D);
        config.set("speed_control.worlds.nether.detector_rail", 0.4D);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals("CUSTOM_BLOCKS", facade.getSpeedControlMode());
        assertEquals(0.8D, facade.getBlockSpeedMap().get("world").get("POWERED_RAIL"));
        assertEquals(0.4D, facade.getBlockSpeedMap().get("nether").get("DETECTOR_RAIL"));
    }

    @Test
    void shouldReadLegacySpeedControlBlockMapAsDefaultWorld() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("speed_control.block_speed_map.rail", 0.2D);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals(0.2D, facade.getBlockSpeedMap().get("default").get("RAIL"));
    }

    @Test
    void shouldApplySafeModeMasterSwitchToNestedProtections() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.safe_mode.enabled", false);
        config.set("settings.safe_mode.entity_push_protection", true);
        config.set("settings.safe_mode.damage_protection", true);
        config.set("settings.safe_mode.movement_assist", true);
        config.set("settings.safe_mode.passenger_rail_break_protection", true);
        config.set("settings.safe_mode.min_cruise_speed", 0.12D);
        config.set("settings.safe_mode.stall_recovery_ticks", 12L);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertFalse(facade.isSafeModeEntityPushProtection());
        assertFalse(facade.isSafeModeDamageProtection());
        assertFalse(facade.isSafeModeMovementAssist());
        assertFalse(facade.isSafeModePassengerRailBreakProtection());
        assertEquals(0.12D, facade.getSafeModeMinCruiseSpeed());
        assertEquals(12L, facade.getSafeModeStallRecoveryTicks());
    }

    @Test
    void shouldReadMapPortalSoundAndSettingValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("map_integration.enabled", true);
        config.set("map_integration.provider", "dynmap");
        config.set("map_integration.marker_set_label", "Transit");
        config.set("map_integration.default_visible", false);
        config.set("map_integration.line_width", 5);
        config.set("map_integration.show_stop_markers", false);
        config.set("map_integration.show_transfer_info", false);
        config.set("route_recording.min_sample_distance_blocks", 0.75D);
        config.set("route_recording.simplify_collinear_points", false);
        config.set("route_recording.simplify_epsilon_blocks", 0.05D);
        config.set("portals.enabled", false);
        config.set("portals.trigger_block", "nether_portal");
        config.set("portals.teleport_delay", 9);
        config.set("portals.effects.particles", false);
        config.set("portals.effects.sound", false);
        config.set("sounds.departure.enabled", false);
        config.set("sounds.departure.notes", List.of("A", "B"));
        config.set("sounds.departure.initial_delay", 2);
        config.set("sounds.arrival.enabled", false);
        config.set("sounds.arrival.notes", List.of("C"));
        config.set("sounds.arrival.initial_delay", 3);
        config.set("sounds.station_arrival.enabled", false);
        config.set("sounds.station_arrival.notes", List.of("D"));
        config.set("sounds.station_arrival.initial_delay", 4);
        config.set("sounds.waiting.enabled", false);
        config.set("sounds.waiting.notes", List.of("E"));
        config.set("sounds.waiting.initial_delay", 5);
        config.set("sounds.waiting.interval", 6);
        config.set("particles.enabled", false);
        config.set("settings.cart_speed", 0.7D);
        config.set("settings.cart_spawn_delay", 11L);
        config.set("settings.cart_departure_delay", 22L);
        config.set("settings.interact_cooldown", 33L);
        config.set("settings.minecart_pending_timeout", 44L);
        config.set("economy.enabled", false);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertTrue(facade.isMapIntegrationEnabled());
        assertEquals("DYNMAP", facade.getMapProvider());
        assertEquals("Transit", facade.getMapMarkerSetLabel());
        assertFalse(facade.isMapDefaultVisible());
        assertEquals(5, facade.getMapLineWidth());
        assertFalse(facade.isMapShowStopMarkers());
        assertFalse(facade.isMapShowTransferInfo());
        assertEquals(0.75D, facade.getRouteRecordingMinSampleDistanceBlocks());
        assertFalse(facade.isRouteRecordingSimplifyCollinearPoints());
        assertEquals(0.05D, facade.getRouteRecordingSimplifyEpsilonBlocks());
        assertFalse(facade.isPortalsEnabled());
        assertEquals("NETHER_PORTAL", facade.getPortalTriggerBlock());
        assertEquals(9, facade.getPortalTeleportDelay());
        assertFalse(facade.isPortalEffectParticles());
        assertFalse(facade.isPortalEffectSound());
        assertFalse(facade.isDepartureSoundEnabled());
        assertEquals(List.of("A", "B"), facade.getDepartureNotes());
        assertEquals(2, facade.getDepartureInitialDelay());
        assertFalse(facade.isArrivalSoundEnabled());
        assertEquals(List.of("C"), facade.getArrivalNotes());
        assertEquals(3, facade.getArrivalInitialDelay());
        assertFalse(facade.isStationArrivalSoundEnabled());
        assertEquals(List.of("D"), facade.getStationArrivalNotes());
        assertEquals(4, facade.getStationArrivalInitialDelay());
        assertFalse(facade.isWaitingSoundEnabled());
        assertEquals(List.of("E"), facade.getWaitingNotes());
        assertEquals(5, facade.getWaitingInitialDelay());
        assertEquals(6, facade.getWaitingSoundInterval());
        assertFalse(facade.isEnableParticles());
        assertEquals(0.7D, facade.getCartSpeed());
        assertEquals(11L, facade.getCartSpawnDelay());
        assertEquals(22L, facade.getCartDepartureDelay());
        assertEquals(33L, facade.getInteractCooldown());
        assertEquals(44L, facade.getMinecartPendingTimeout());
        assertFalse(facade.isEconomyEnabled());
    }

    @Test
    void shouldFallbackToDefaultSelectionToolWhenConfiguredMaterialIsInvalid() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.selection_tool", "NOT_A_TOOL");

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals(Material.GOLDEN_SHOVEL, facade.getSelectionTool());
        assertEquals("Golden Shovel", facade.getSelectionToolName());
    }

    private ConfigFacade createFacade(YamlConfiguration config) {
        Metro plugin = mock(Metro.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigFacadeTest"));
        return new ConfigFacade(plugin);
    }
}
