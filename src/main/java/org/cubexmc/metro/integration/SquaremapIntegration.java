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
    public void enable() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            return;
        }

        if (!"SQUAREMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
            plugin.getLogger().warning("[Squaremap] squaremap plugin not found. Skipping integration.");
            return;
        }

        try {
            Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[Squaremap] API not found, skipping integration.");
            return;
        }

        plugin.getLogger().info("[Squaremap] API detected. Rendering metro stops on map...");
        renderMetroNetwork();
        enabled = true;
    }

    @Override
    public void refresh() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled() || !"SQUAREMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
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
                if (stop == null || stop.getStopPointLocation() == null || stop.getStopPointLocation().getWorld() == null) {
                    continue;
                }

                String worldName = stop.getStopPointLocation().getWorld().getName();

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
            for (RoutePoint routePoint : routePoints) {
                if (worldName.equals(routePoint.worldName())) {
                    points.add(Point.of(routePoint.x(), routePoint.z()));
                }
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
        Location loc = stop.getStopPointLocation();
        String stopLabel = (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
        String poiId = ("stop_" + stop.getId()).toLowerCase();

        Marker poi = Marker.circle(Point.of(loc.getX(), loc.getZ()), 3.0);
        poi.markerOptions(MarkerOptions.builder()
                .hoverTooltip(buildStopTooltip(stop))
                .fillColor(getStopColor(stop))
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
