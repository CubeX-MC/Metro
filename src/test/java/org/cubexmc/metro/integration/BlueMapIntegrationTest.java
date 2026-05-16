package org.cubexmc.metro.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

class BlueMapIntegrationTest {

    @AfterEach
    void clearBlueMapApi() throws Exception {
        unregisterCurrentApi();
    }

    @Test
    void rendersMarkersUsingBlueMapWorldLookupWhenWorldIdsDiffer() throws Exception {
        Fixtures fixtures = new Fixtures();
        registerApi(fixtures.api);

        BlueMapIntegration integration = new BlueMapIntegration(fixtures.plugin);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(fixtures.bukkitWorld);

            integration.enable();

            MarkerSet markerSet = fixtures.markerSets.get("metro_network");
            assertNotNull(markerSet);
            assertFalse(markerSet.isDefaultHidden());
            LineMarker lineMarker = assertInstanceOf(LineMarker.class, markerSet.get("route_red"));
            assertEquals(3, lineMarker.getLine().getPointCount());
            ExtrudeMarker stopArea = assertInstanceOf(ExtrudeMarker.class, markerSet.get("stop_area_central"));
            assertEquals(63.0F, stopArea.getShapeMinY());
            assertEquals(66.0F, stopArea.getShapeMaxY());
        } finally {
            integration.disable();
        }
    }

    private static void registerApi(BlueMapAPI api) throws Exception {
        invokeBlueMapApiMethod("registerInstance", api);
    }

    private static void unregisterCurrentApi() throws Exception {
        Optional<BlueMapAPI> currentApi = BlueMapAPI.getInstance();
        if (currentApi.isPresent()) {
            invokeBlueMapApiMethod("unregisterInstance", currentApi.get());
        }
    }

    private static void invokeBlueMapApiMethod(String methodName, BlueMapAPI api) throws Exception {
        Method method = BlueMapAPI.class.getDeclaredMethod(methodName, BlueMapAPI.class);
        method.setAccessible(true);
        method.invoke(null, api);
    }

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final ConfigFacade configFacade = mock(ConfigFacade.class);
        private final LineManager lineManager = mock(LineManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final BlueMapAPI api = mock(BlueMapAPI.class);
        private final BlueMapMap map = mock(BlueMapMap.class);
        private final BlueMapWorld blueMapWorld = mock(BlueMapWorld.class);
        private final World bukkitWorld = mock(World.class);
        private final Map<String, MarkerSet> markerSets = new HashMap<>();

        private Fixtures() {
            when(plugin.getConfigFacade()).thenReturn(configFacade);
            when(plugin.getLineManager()).thenReturn(lineManager);
            when(plugin.getStopManager()).thenReturn(stopManager);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("BlueMapIntegrationTest"));

            when(configFacade.isMapIntegrationEnabled()).thenReturn(true);
            when(configFacade.getMapProvider()).thenReturn("BLUEMAP");
            when(configFacade.getMapMarkerSetLabel()).thenReturn("Metro Network");
            when(configFacade.isMapDefaultVisible()).thenReturn(true);
            when(configFacade.getMapLineWidth()).thenReturn(3);
            when(configFacade.isMapShowStopMarkers()).thenReturn(true);
            when(configFacade.isMapShowTransferInfo()).thenReturn(true);

            when(bukkitWorld.getName()).thenReturn("world");
            when(blueMapWorld.getId()).thenReturn("minecraft:overworld");
            when(blueMapWorld.getMaps()).thenReturn(List.of(map));
            when(map.getWorld()).thenReturn(blueMapWorld);
            when(map.getMarkerSets()).thenReturn(markerSets);
            when(api.getMaps()).thenReturn(List.of(map));
            when(api.getWorld(bukkitWorld)).thenReturn(Optional.of(blueMapWorld));

            Line line = new Line("red", "Red Line");
            line.setColor("&c");
            line.setRoutePoints(List.of(
                    new RoutePoint("world", 0.0, 64.0, 0.0),
                    new RoutePoint("world", 16.0, 64.0, 16.0)
            ));

            Stop stop = new Stop("central", "Central");
            stop.setCorner1(new Location(bukkitWorld, 6.0, 63.0, 6.0));
            stop.setCorner2(new Location(bukkitWorld, 10.0, 65.0, 10.0));
            stop.setStopPointLocation(new Location(bukkitWorld, 8.0, 64.0, 8.0));

            when(lineManager.getAllLines()).thenReturn(List.of(line));
            when(lineManager.getLinesForStop("central")).thenReturn(List.of(line));
            when(stopManager.getAllStops()).thenReturn(List.of(stop));
        }
    }
}
