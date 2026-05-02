package org.cubexmc.metro.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 可选的 Squaremap 集成模块。
 * 当服务器安装了 Squaremap 插件且配置中 provider 设为 SQUAREMAP 时，
 * 自动在网页地图上绘制地铁网络的线路和站点。
 */
public class SquaremapIntegration implements MapIntegration {

    private static final String LAYER_ID = "metro_network";

    private final Metro plugin;
    private boolean enabled = false;
    private final Map<String, SimpleLayerProvider> layerProviders = new HashMap<>();

    public SquaremapIntegration(Metro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        try {
            if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
                return false;
            }
            Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider");
            return true;
        } catch (ClassNotFoundException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public void enable() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            return;
        }

        if (!matchesProvider()) {
            return;
        }

        if (!isAvailable()) {
            plugin.getLogger().warning("[Squaremap] squaremap plugin not found. Skipping integration.");
            return;
        }

        plugin.getLogger().info("[Squaremap] API detected. Rendering metro stops on map...");
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
        } else {
            renderMetroNetwork();
        }
    }

    @Override
    public void disable() {
        try {
            Squaremap api = SquaremapProvider.get();
            for (Map.Entry<String, SimpleLayerProvider> entry : layerProviders.entrySet()) {
                org.bukkit.World bukkitWorld = Bukkit.getWorld(entry.getKey());
                if (bukkitWorld != null) {
                    api.getWorldIfEnabled(xyz.jpenilla.squaremap.api.BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent(world -> {
                        world.layerRegistry().unregister(Key.of(LAYER_ID));
                    });
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        layerProviders.clear();
        enabled = false;
        plugin.getLogger().info("[Squaremap] Metro markers removed.");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private boolean matchesProvider() {
        String provider = plugin.getConfigFacade().getMapProvider();
        return "SQUAREMAP".equalsIgnoreCase(provider) || "AUTO".equalsIgnoreCase(provider);
    }

    private void renderMetroNetwork() {
        try {
            Squaremap api = SquaremapProvider.get();
            LineManager lineManager = plugin.getLineManager();
            StopManager stopManager = plugin.getStopManager();

            // 先清理旧标记
            for (SimpleLayerProvider provider : layerProviders.values()) {
                provider.clearMarkers();
            }

            String layerLabel = plugin.getConfigFacade().getMapMarkerSetLabel();
            boolean defaultVisible = plugin.getConfigFacade().isMapDefaultVisible();

            for (org.cubexmc.metro.model.Line line : lineManager.getAllLines()) {
                renderRoute(api, layerLabel, defaultVisible, line);
            }

            if (!plugin.getConfigFacade().isMapShowStopMarkers()) {
                return;
            }

            List<Stop> allStops = stopManager.getAllStops();
            if (allStops == null || allStops.isEmpty()) {
                return;
            }

            for (Stop stop : allStops) {
                if (stop == null) {
                    continue;
                }

                String worldName = MapGeometry.stopBounds(stop)
                        .map(MapGeometry.StopBounds::worldName)
                        .orElseGet(() -> stop.getStopPointLocation() != null && stop.getStopPointLocation().getWorld() != null
                                ? stop.getStopPointLocation().getWorld().getName()
                                : null);
                if (worldName == null) {
                    continue;
                }

                org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) continue;

                api.getWorldIfEnabled(xyz.jpenilla.squaremap.api.BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent(world -> {
                    SimpleLayerProvider provider = layerProviders.computeIfAbsent(worldName, k -> {
                        SimpleLayerProvider p = SimpleLayerProvider.builder(layerLabel)
                                .defaultHidden(!defaultVisible)
                                .build();
                        world.layerRegistry().register(Key.of(LAYER_ID), p);
                        return p;
                    });

                    renderStop(provider, stop);
                });
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Squaremap] Failed to render network.", e);
        }
    }

    private void renderRoute(Squaremap api, String layerLabel, boolean defaultVisible, org.cubexmc.metro.model.Line line) {
        List<RoutePoint> routePoints = line.getRoutePoints();
        if (routePoints.size() < 2) {
            return;
        }

        String worldName = routePoints.get(0).worldName();
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            return;
        }

        api.getWorldIfEnabled(xyz.jpenilla.squaremap.api.BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent(world -> {
            SimpleLayerProvider provider = layerProviders.computeIfAbsent(worldName, k -> {
                SimpleLayerProvider p = SimpleLayerProvider.builder(layerLabel)
                        .defaultHidden(!defaultVisible)
                        .build();
                world.layerRegistry().register(Key.of(LAYER_ID), p);
                return p;
            });

            List<Point> points = new ArrayList<>();
            for (RoutePoint routePoint : MapGeometry.orthogonalRoutePoints(routePoints, worldName)) {
                points.add(Point.of(routePoint.x(), routePoint.z()));
            }
            if (points.size() < 2) {
                return;
            }

            Marker polyline = Marker.polyline(points);
            polyline.markerOptions(MarkerOptions.builder()
                    .strokeColor(toAwtColor(MapLineColor.fromLineColor(line.getColor())))
                    .strokeWeight(plugin.getConfigFacade().getMapLineWidth())
                    .hoverTooltip(line.getName() + " (" + line.getId() + ")")
                    .build());
            provider.addMarker(Key.of(("route_" + line.getId()).toLowerCase()), polyline);
        });
    }

    private void renderStop(SimpleLayerProvider provider, Stop stop) {
        if (MapGeometry.stopBounds(stop).map(bounds -> renderStopArea(provider, stop, bounds)).orElse(false)) {
            return;
        }
        renderStopMarker(provider, stop);
    }

    private boolean renderStopArea(SimpleLayerProvider provider, Stop stop, MapGeometry.StopBounds bounds) {
        String stopId = ("stop_area_" + stop.getId()).toLowerCase();
        Marker area = Marker.rectangle(Point.of(bounds.minX(), bounds.minZ()), Point.of(bounds.maxX(), bounds.maxZ()));
        Color color = getStopColor(stop);
        area.markerOptions(MarkerOptions.builder()
                .hoverTooltip(buildStopTooltip(stop))
                .strokeColor(color)
                .strokeWeight(Math.max(1, plugin.getConfigFacade().getMapLineWidth()))
                .strokeOpacity(0.85)
                .fill(true)
                .fillColor(color)
                .fillOpacity(0.22)
                .build());

        provider.addMarker(Key.of(stopId), area);
        return true;
    }

    private void renderStopMarker(SimpleLayerProvider provider, Stop stop) {
        if (stop.getStopPointLocation() == null) return;
        Location loc = stop.getStopPointLocation();
        if (loc.getWorld() == null) return;
        String stopLabel = (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
        String poiId = ("stop_" + stop.getId()).toLowerCase();

        Marker poi = Marker.circle(Point.of(loc.getX(), loc.getZ()), 3.0);
        poi.markerOptions(MarkerOptions.builder()
                .hoverTooltip(buildStopTooltip(stop))
                .fillColor(getStopColor(stop))
                .fill(true)
                .strokeColor(Color.BLACK)
                .strokeWeight(1)
                .build());

        provider.addMarker(Key.of(poiId), poi);
    }

    private String buildStopTooltip(Stop stop) {
        List<String> parts = new ArrayList<>();
        String stopLabel = (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
        parts.add("<b>" + stopLabel + "</b>");

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

    private Color getStopColor(Stop stop) {
        List<org.cubexmc.metro.model.Line> servedLines = plugin.getLineManager().getLinesForStop(stop.getId());
        if (servedLines.isEmpty()) {
            return Color.WHITE;
        }
        return toAwtColor(MapLineColor.fromLineColor(servedLines.get(0).getColor()));
    }

    private Color toAwtColor(MapLineColor color) {
        return new Color(color.red(), color.green(), color.blue());
    }
}
