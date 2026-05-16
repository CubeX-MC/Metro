package org.cubexmc.metro.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Dynmap integration module.
 * Renders metro network lines and stops on the Dynmap web map.
 */
public class DynmapIntegration implements MapIntegration {

    private static final String MARKER_SET_ID = "metro_network";

    private final Metro plugin;
    private DynmapCommonAPI dynmapApi;
    private MarkerAPI markerApi;
    private boolean enabled = false;

    public DynmapIntegration(Metro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
            return dynmapPlugin != null && dynmapPlugin.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void enable() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            plugin.getLogger().info("[Dynmap] Map integration is disabled in config.yml.");
            return;
        }

        if (!matchesProvider()) {
            plugin.getLogger().info("[Dynmap] Map provider is set to '"
                    + plugin.getConfigFacade().getMapProvider() + "', skipping Dynmap integration.");
            return;
        }

        if (!isAvailable()) {
            plugin.getLogger().warning("[Dynmap] Dynmap plugin not found or not enabled. Skipping integration.");
            return;
        }
        Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");

        try {
            dynmapApi = (DynmapCommonAPI) dynmapPlugin;
            markerApi = dynmapApi.getMarkerAPI();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Dynmap] Failed to get Dynmap MarkerAPI.", e);
            return;
        }

        if (markerApi == null) {
            plugin.getLogger().warning("[Dynmap] Dynmap MarkerAPI is null. Skipping integration.");
            return;
        }

        plugin.getLogger().info("[Dynmap] Dynmap API detected. Rendering metro stops on map...");
        renderMetroNetwork();
        enabled = true;
    }

    @Override
    public void refresh() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled() || !matchesProvider()) {
            disable();
            return;
        }

        if (!enabled) {
            enable();
        } else if (markerApi != null) {
            renderMetroNetwork();
        }
    }

    @Override
    public void disable() {
        if (markerApi != null) {
            MarkerSet markerSet = markerApi.getMarkerSet(MARKER_SET_ID);
            if (markerSet != null) {
                markerSet.deleteMarkerSet();
            }
        }
        enabled = false;
        plugin.getLogger().info("[Dynmap] Metro markers removed.");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private boolean matchesProvider() {
        String provider = plugin.getConfigFacade().getMapProvider();
        return "DYNMAP".equalsIgnoreCase(provider) || "AUTO".equalsIgnoreCase(provider);
    }

    private void renderMetroNetwork() {
        LineManager lineManager = plugin.getLineManager();
        StopManager stopManager = plugin.getStopManager();

        String label = plugin.getConfigFacade().getMapMarkerSetLabel();

        MarkerSet markerSet = markerApi.getMarkerSet(MARKER_SET_ID);
        if (markerSet != null) {
            markerSet.deleteMarkerSet();
        }
        markerSet = markerApi.createMarkerSet(MARKER_SET_ID, label, null, false);

        if (markerSet == null) {
            plugin.getLogger().warning("[Dynmap] Failed to create MarkerSet.");
            return;
        }

        markerSet.setHideByDefault(!plugin.getConfigFacade().isMapDefaultVisible());

        for (org.cubexmc.metro.model.Line line : lineManager.getAllLines()) {
            renderRoute(markerSet, line);
        }

        if (plugin.getConfigFacade().isMapShowStopMarkers()) {
            List<Stop> allStops = stopManager.getAllStops();
            if (allStops == null || allStops.isEmpty()) {
                return;
            }
            for (Stop stop : allStops) {
                renderStop(markerSet, stop);
            }
        }
    }

    private void renderRoute(MarkerSet markerSet, org.cubexmc.metro.model.Line line) {
        List<RoutePoint> routePoints = line.getRoutePoints();
        if (routePoints.size() < 2) {
            return;
        }

        String worldName = routePoints.get(0).worldName();
        if (worldName == null || worldName.trim().isEmpty()) {
            return;
        }
        List<RoutePoint> displayPoints = MapGeometry.orthogonalRoutePoints(routePoints, worldName);
        if (displayPoints.size() < 2) {
            return;
        }

        List<Double> xList = new ArrayList<Double>();
        List<Double> yList = new ArrayList<Double>();
        List<Double> zList = new ArrayList<Double>();
        for (RoutePoint point : displayPoints) {
            xList.add(point.x());
            yList.add(point.y());
            zList.add(point.z());
        }
        if (xList.size() < 2) {
            return;
        }

        double[] x = xList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = yList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] z = zList.stream().mapToDouble(Double::doubleValue).toArray();

        PolyLineMarker polyLine = markerSet.createPolyLineMarker(
                "route_" + line.getId(),
                line.getName() + " (" + line.getId() + ")",
                false,
                worldName,
                x, y, z,
                false
        );

        if (polyLine != null) {
            polyLine.setLineStyle(plugin.getConfigFacade().getMapLineWidth(), 0.8,
                    MapLineColor.fromLineColor(line.getColor()).asRgbInt());
        }
    }

    private void renderStop(MarkerSet markerSet, Stop stop) {
        if (stop == null) {
            return;
        }

        if (MapGeometry.stopBounds(stop).map(bounds -> renderStopArea(markerSet, stop, bounds)).orElse(false)) {
            return;
        }

        renderStopMarker(markerSet, stop);
    }

    private boolean renderStopArea(MarkerSet markerSet, Stop stop, MapGeometry.StopBounds bounds) {
        double[] x = {bounds.minX(), bounds.maxX(), bounds.maxX(), bounds.minX()};
        double[] z = {bounds.minZ(), bounds.minZ(), bounds.maxZ(), bounds.maxZ()};
        AreaMarker area = markerSet.createAreaMarker(
                "stop_area_" + stop.getId(),
                stopLabel(stop),
                false,
                bounds.worldName(),
                x,
                z,
                false
        );
        if (area == null) {
            return false;
        }
        int color = getStopColor(stop).asRgbInt();
        area.setRangeY(bounds.maxY(), bounds.minY());
        area.setLineStyle(Math.max(1, plugin.getConfigFacade().getMapLineWidth()), 0.85, color);
        area.setFillStyle(0.22, color);
        area.setDescription(buildStopDescription(stop));
        return true;
    }

    private void renderStopMarker(MarkerSet markerSet, Stop stop) {
        if (stop.getStopPointLocation() == null) {
            return;
        }
        Location loc = stop.getStopPointLocation();
        if (loc.getWorld() == null) {
            return;
        }

        Marker marker = markerSet.createMarker(
                "stop_" + stop.getId(),
                stopLabel(stop),
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                markerApi.getMarkerIcon(MarkerIcon.DEFAULT),
                false
        );

        if (marker != null) {
            marker.setDescription(buildStopDescription(stop));
        }
    }

    private String buildStopDescription(Stop stop) {
        List<String> parts = new ArrayList<String>();
        parts.add("<b>" + stopLabel(stop) + "</b>");

        List<org.cubexmc.metro.model.Line> servedLines = plugin.getLineManager().getLinesForStop(stop.getId());
        if (!servedLines.isEmpty()) {
            parts.add("Lines: " + servedLines.stream()
                    .map(line -> line.getName() + " (" + line.getId() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
        }
        List<String> transfers = stop.getTransferableLines();
        if (plugin.getConfigFacade().isMapShowTransferInfo() && !transfers.isEmpty()) {
            parts.add("Transfers: " + String.join(", ", transfers));
        }
        return String.join("<br>", parts);
    }

    private String stopLabel(Stop stop) {
        return (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
    }

    private MapLineColor getStopColor(Stop stop) {
        List<org.cubexmc.metro.model.Line> servedLines = plugin.getLineManager().getLinesForStop(stop.getId());
        if (servedLines.isEmpty()) {
            return MapLineColor.WHITE;
        }
        return MapLineColor.fromLineColor(servedLines.get(0).getColor());
    }

}
