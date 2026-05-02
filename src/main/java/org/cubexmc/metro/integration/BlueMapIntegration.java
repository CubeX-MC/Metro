package org.cubexmc.metro.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可选的 BlueMap 集成模块。
 * 当服务器安装了 BlueMap 插件时，自动在网页地图上绘制地铁网络。
 * 该类通过 BlueMapAPI 的 onEnable 回调注册，确保 BlueMap 准备就绪后再执行。
 */
public class BlueMapIntegration implements MapIntegration {

    private static final String MARKER_SET_ID = "metro_network";

    private final Metro plugin;
    private final Consumer<BlueMapAPI> enableListener = this::handleBlueMapEnabled;
    private final Consumer<BlueMapAPI> disableListener = this::handleBlueMapDisabled;
    private boolean enabled = false;
    private boolean listenersRegistered = false;

    public BlueMapIntegration(Metro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 尝试启用 BlueMap 集成。
     * 如果 BlueMap 不在 classpath 中，将安静地跳过。
     */
    @Override
    public void enable() {
        // 检查配置是否启用了地图集成
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            plugin.getLogger().info("[BlueMap] Map integration is disabled in config.yml.");
            return;
        }

        // 检查配置的 provider 是否为 BLUEMAP 或 AUTO
        if (!matchesProvider()) {
            plugin.getLogger().info("[BlueMap] Map provider is set to '" 
                + plugin.getConfigFacade().getMapProvider() + "', skipping BlueMap integration.");
            return;
        }

        if (!isAvailable()) {
            plugin.getLogger().info("[BlueMap] BlueMap not detected, skipping map integration.");
            return;
        }

        if (!listenersRegistered) {
            BlueMapAPI.onEnable(enableListener);
            BlueMapAPI.onDisable(disableListener);
            listenersRegistered = true;
        }

        if (!enabled) {
            BlueMapAPI.getInstance().ifPresent(this::handleBlueMapEnabled);
        }
    }

    /**
     * 强制刷新网页地图上的地铁线路标记。
     * 可在管理员编辑线路后手动调用。
     */
    @Override
    public void refresh() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled() || !matchesProvider()) {
            disable();
            return;
        }

        boolean wasEnabled = enabled;
        if (!enabled) {
            enable();
        }
        if (wasEnabled && enabled) {
            BlueMapAPI.getInstance().ifPresent(this::renderMetroNetwork);
        }
    }

    @Override
    public void disable() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapMap map : api.getMaps()) {
                map.getMarkerSets().remove(MARKER_SET_ID);
            }
        });
        if (listenersRegistered) {
            BlueMapAPI.unregisterListener(enableListener);
            BlueMapAPI.unregisterListener(disableListener);
            listenersRegistered = false;
        }
        enabled = false;
        plugin.getLogger().info("[BlueMap] Metro markers removed.");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private boolean matchesProvider() {
        String provider = plugin.getConfigFacade().getMapProvider();
        return "BLUEMAP".equalsIgnoreCase(provider) || "AUTO".equalsIgnoreCase(provider);
    }

    // ========== 核心渲染逻辑 ==========

    private void handleBlueMapEnabled(BlueMapAPI api) {
        plugin.getLogger().info("[BlueMap] BlueMap API detected. Rendering metro stops on map...");
        renderMetroNetwork(api);
        enabled = true;
    }

    private void handleBlueMapDisabled(BlueMapAPI api) {
        enabled = false;
        plugin.getLogger().info("[BlueMap] BlueMap API disabled. Metro markers removed.");
    }

    private void renderMetroNetwork(BlueMapAPI api) {
        LineManager lineManager = plugin.getLineManager();
        StopManager stopManager = plugin.getStopManager();

        // 先清理旧的 MarkerSet
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove(MARKER_SET_ID);
        }

        for (org.cubexmc.metro.model.Line line : lineManager.getAllLines()) {
            renderRoute(api, line);
        }

        if (plugin.getConfigFacade().isMapShowStopMarkers()) {
            List<Stop> allStops = stopManager.getAllStops();
            if (allStops == null || allStops.isEmpty()) {
                return;
            }
            for (Stop stop : allStops) {
                renderStop(api, stop);
            }
        }
    }

    private void renderRoute(BlueMapAPI api, org.cubexmc.metro.model.Line line) {
        List<RoutePoint> routePoints = line.getRoutePoints();
        if (routePoints.size() < 2) {
            return;
        }

        String worldName = routePoints.get(0).worldName();
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        for (BlueMapMap map : getMapsForWorld(api, worldName)) {
            MarkerSet markerSet = getMarkerSet(map);
            List<RoutePoint> displayPoints = MapGeometry.orthogonalRoutePoints(routePoints, worldName);
            if (displayPoints.size() < 2) {
                return;
            }
            de.bluecolored.bluemap.api.math.Line.Builder lineBuilder =
                    de.bluecolored.bluemap.api.math.Line.builder();
            for (RoutePoint point : displayPoints) {
                lineBuilder.addPoint(new com.flowpowered.math.vector.Vector3d(point.x(), point.y(), point.z()));
            }

            LineMarker lineMarker = LineMarker.builder()
                    .label(line.getName() + " (" + line.getId() + ")")
                    .line(lineBuilder.build())
                    .lineColor(toBlueMapColor(MapLineColor.fromLineColor(line.getColor())))
                    .lineWidth(plugin.getConfigFacade().getMapLineWidth())
                    .build();
            markerSet.put("route_" + line.getId(), lineMarker);
        }
    }

    private void renderStop(BlueMapAPI api, Stop stop) {
        if (stop == null) return;

        if (MapGeometry.stopBounds(stop).map(bounds -> renderStopArea(api, stop, bounds)).orElse(false)) {
            return;
        }

        renderStopPoi(api, stop);
    }

    private boolean renderStopArea(BlueMapAPI api, Stop stop, MapGeometry.StopBounds bounds) {
        boolean rendered = false;
        for (BlueMapMap map : getMapsForWorld(api, bounds.worldName())) {
            MarkerSet markerSet = getMarkerSet(map);
            MapLineColor stopColor = getStopColor(stop);
            ExtrudeMarker area = ExtrudeMarker.builder()
                    .label(stopLabel(stop))
                    .shape(Shape.createRect(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()),
                            (float) bounds.minY(), (float) bounds.maxY())
                    .lineColor(toBlueMapColor(stopColor, 1.0F))
                    .fillColor(toBlueMapColor(stopColor, 0.22F))
                    .lineWidth(Math.max(1, plugin.getConfigFacade().getMapLineWidth()))
                    .build();
            area.setDetail(buildStopDetail(stop));
            markerSet.put("stop_area_" + stop.getId(), area);
            rendered = true;
        }
        return rendered;
    }

    private void renderStopPoi(BlueMapAPI api, Stop stop) {
        if (stop.getStopPointLocation() == null) return;

        Location loc = stop.getStopPointLocation();
        if (loc.getWorld() == null) return;

        String worldName = loc.getWorld().getName();

        for (BlueMapMap map : getMapsForWorld(api, worldName)) {
            MarkerSet markerSet = getMarkerSet(map);

            POIMarker poi = POIMarker.builder()
                    .label(stopLabel(stop))
                    .position(loc.getX(), loc.getY(), loc.getZ())
                    .build();
            poi.setDetail(buildStopDetail(stop));
            markerSet.put("stop_" + stop.getId(), poi);
        }
    }

    private Collection<BlueMapMap> getMapsForWorld(BlueMapAPI api, String worldName) {
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            return api.getWorld(bukkitWorld)
                    .map(BlueMapWorld::getMaps)
                    .filter(maps -> !maps.isEmpty())
                    .orElseGet(() -> getMapsByWorldName(api, worldName));
        }
        return getMapsByWorldName(api, worldName);
    }

    private List<BlueMapMap> getMapsByWorldName(BlueMapAPI api, String worldName) {
        List<BlueMapMap> maps = new ArrayList<>();
        for (BlueMapMap map : api.getMaps()) {
            if (matchesWorld(map, worldName)) {
                maps.add(map);
            }
        }
        return maps;
    }

    private boolean matchesWorld(BlueMapMap map, String worldName) {
        BlueMapWorld bmWorld = map.getWorld();
        String bmWorldId = bmWorld.getId();
        boolean match = bmWorldId.equalsIgnoreCase(worldName);
        if (!match && bmWorldId.contains(":")) {
            String[] parts = bmWorldId.split(":");
            match = parts[parts.length - 1].equalsIgnoreCase(worldName);
        }
        return match;
    }

    private MarkerSet getMarkerSet(BlueMapMap map) {
        String markerLabel = plugin.getConfigFacade().getMapMarkerSetLabel();
        boolean defaultVisible = plugin.getConfigFacade().isMapDefaultVisible();
        return map.getMarkerSets().computeIfAbsent(
                MARKER_SET_ID,
                id -> MarkerSet.builder()
                        .label(markerLabel)
                        .defaultHidden(!defaultVisible)
                        .build()
        );
    }

    private String buildStopDetail(Stop stop) {
        List<String> detail = new ArrayList<>();
        detail.add("<b>" + stopLabel(stop) + "</b>");
        List<org.cubexmc.metro.model.Line> servedLines = plugin.getLineManager().getLinesForStop(stop.getId());
        if (!servedLines.isEmpty()) {
            detail.add("<b>Lines:</b> " + servedLines.stream()
                    .map(line -> line.getName() + " (" + line.getId() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
        }
        List<String> transfers = stop.getTransferableLines();
        if (plugin.getConfigFacade().isMapShowTransferInfo() && !transfers.isEmpty()) {
            detail.add("<b>Transfers:</b> " + String.join(", ", transfers));
        }
        return String.join("<br>", detail);
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

    private Color toBlueMapColor(MapLineColor color) {
        return toBlueMapColor(color, 1.0F);
    }

    private Color toBlueMapColor(MapLineColor color, float alpha) {
        return new Color(color.red(), color.green(), color.blue(), alpha);
    }
}
