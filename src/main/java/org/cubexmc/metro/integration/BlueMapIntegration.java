package org.cubexmc.metro.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;

import org.bukkit.Location;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可选的 BlueMap 集成模块。
 * 当服务器安装了 BlueMap 插件时，自动在网页地图上绘制地铁网络。
 * 该类通过 BlueMapAPI 的 onEnable 回调注册，确保 BlueMap 准备就绪后再执行。
 */
public class BlueMapIntegration {

    private static final String MARKER_SET_ID = "metro_network";

    private final Metro plugin;
    private final Consumer<BlueMapAPI> enableListener = this::handleBlueMapEnabled;
    private final Consumer<BlueMapAPI> disableListener = this::handleBlueMapDisabled;
    private boolean enabled = false;
    private boolean listenersRegistered = false;

    public BlueMapIntegration(Metro plugin) {
        this.plugin = plugin;
    }

    /**
     * 尝试启用 BlueMap 集成。
     * 如果 BlueMap 不在 classpath 中，将安静地跳过。
     */
    public void enable() {
        // 检查配置是否启用了地图集成
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            plugin.getLogger().info("[BlueMap] Map integration is disabled in config.yml.");
            return;
        }

        // 检查配置的 provider 是否为 BLUEMAP
        if (!"BLUEMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
            plugin.getLogger().info("[BlueMap] Map provider is set to '" 
                + plugin.getConfigFacade().getMapProvider() + "', skipping BlueMap integration.");
            return;
        }

        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[BlueMap] BlueMap not detected, skipping map integration.");
            return;
        }

        if (!listenersRegistered) {
            BlueMapAPI.onEnable(enableListener);
            BlueMapAPI.onDisable(disableListener);
            listenersRegistered = true;
        }
    }

    /**
     * 强制刷新网页地图上的地铁线路标记。
     * 可在管理员编辑线路后手动调用。
     */
    public void refresh() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled() || !"BLUEMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
            disable();
            return;
        }

        if (!enabled) {
            enable();
        } else {
            BlueMapAPI.getInstance().ifPresent(this::renderMetroNetwork);
        }
    }

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

    public boolean isEnabled() {
        return enabled;
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
        for (BlueMapMap map : api.getMaps()) {
            if (!matchesWorld(map, worldName)) {
                continue;
            }

            MarkerSet markerSet = getMarkerSet(map);
            de.bluecolored.bluemap.api.math.Line.Builder lineBuilder =
                    de.bluecolored.bluemap.api.math.Line.builder();
            int pointCount = 0;
            for (RoutePoint point : routePoints) {
                if (worldName.equals(point.worldName())) {
                    lineBuilder.addPoint(new com.flowpowered.math.vector.Vector3d(point.x(), point.y(), point.z()));
                    pointCount++;
                }
            }
            if (pointCount < 2) {
                return;
            }

            LineMarker lineMarker = LineMarker.builder()
                    .label(line.getName() + " (" + line.getId() + ")")
                    .line(lineBuilder.build())
                    .lineColor(parseLineColor(line.getColor()))
                    .lineWidth(plugin.getConfigFacade().getMapLineWidth())
                    .build();
            markerSet.put("route_" + line.getId(), lineMarker);
        }
    }

    private void renderStop(BlueMapAPI api, Stop stop) {
        if (stop == null || stop.getStopPointLocation() == null) return;

        Location loc = stop.getStopPointLocation();
        if (loc.getWorld() == null) return;

        String worldName = loc.getWorld().getName();

        // 获取该世界对应的 BlueMap 世界和地图
        for (BlueMapMap map : api.getMaps()) {
            if (!matchesWorld(map, worldName)) {
                continue;
            }

            // 获取或创建 MarkerSet
            MarkerSet markerSet = getMarkerSet(map);

            String label = (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
            POIMarker poi = POIMarker.builder()
                    .label(label)
                    .position(loc.getX(), loc.getY(), loc.getZ())
                    .build();
            poi.setDetail(buildStopDetail(stop));
            markerSet.put("stop_" + stop.getId(), poi);
        }
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

    private Color parseLineColor(String chatColor) {
        if (chatColor == null || chatColor.isEmpty()) {
            return new Color(255, 255, 255, 255);
        }

        char code = chatColor.charAt(chatColor.length() - 1);
        return switch (code) {
            case '0' -> new Color(0, 0, 0, 255);
            case '1' -> new Color(0, 0, 170, 255);
            case '2' -> new Color(0, 170, 0, 255);
            case '3' -> new Color(0, 170, 170, 255);
            case '4' -> new Color(170, 0, 0, 255);
            case '5' -> new Color(170, 0, 170, 255);
            case '6' -> new Color(255, 170, 0, 255);
            case '7' -> new Color(170, 170, 170, 255);
            case '8' -> new Color(85, 85, 85, 255);
            case '9' -> new Color(85, 85, 255, 255);
            case 'a' -> new Color(85, 255, 85, 255);
            case 'b' -> new Color(85, 255, 255, 255);
            case 'c' -> new Color(255, 85, 85, 255);
            case 'd' -> new Color(255, 85, 255, 255);
            case 'e' -> new Color(255, 255, 85, 255);
            default  -> new Color(255, 255, 255, 255);
        };
    }
}
