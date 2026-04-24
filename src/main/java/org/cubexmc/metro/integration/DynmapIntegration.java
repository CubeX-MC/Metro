package org.cubexmc.metro.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 可选的 Dynmap 集成模块。
 * 当服务器安装了 Dynmap 插件且配置中 provider 设为 DYNMAP 时，
 * 自动在网页地图上绘制地铁网络的线路和站点。
 */
public class DynmapIntegration {

    private static final String MARKER_SET_ID = "metro_network";

    private final Metro plugin;
    private DynmapCommonAPI dynmapApi;
    private MarkerAPI markerApi;
    private boolean enabled = false;

    public DynmapIntegration(Metro plugin) {
        this.plugin = plugin;
    }

    /**
     * 尝试启用 Dynmap 集成。
     */
    public void enable() {
        // 检查配置是否启用了地图集成
        if (!plugin.getConfigFacade().isMapIntegrationEnabled()) {
            plugin.getLogger().info("[Dynmap] Map integration is disabled in config.yml.");
            return;
        }

        // 检查配置的 provider 是否为 DYNMAP
        if (!"DYNMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
            plugin.getLogger().info("[Dynmap] Map provider is set to '"
                    + plugin.getConfigFacade().getMapProvider() + "', skipping Dynmap integration.");
            return;
        }

        // 检验 Dynmap 插件是否已加载
        Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmapPlugin == null || !dynmapPlugin.isEnabled()) {
            plugin.getLogger().warning("[Dynmap] Dynmap plugin not found or not enabled. Skipping integration.");
            return;
        }

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

        plugin.getLogger().info("[Dynmap] Dynmap API detected. Rendering metro network on map...");
        renderMetroNetwork();
        enabled = true;
    }

    /**
     * 强制刷新网页地图上的地铁线路标记。
     */
    public void refresh() {
        if (!plugin.getConfigFacade().isMapIntegrationEnabled() || !"DYNMAP".equalsIgnoreCase(plugin.getConfigFacade().getMapProvider())) {
            disable();
            return;
        }

        if (!enabled) {
            enable();
        } else if (markerApi != null) {
            renderMetroNetwork();
        }
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    // ========== 核心渲染逻辑 ==========

    private void renderMetroNetwork() {
        LineManager lineManager = plugin.getLineManager();
        StopManager stopManager = plugin.getStopManager();

        String label = plugin.getConfigFacade().getMapMarkerSetLabel();

        // 获取或创建 MarkerSet（先删除旧的再重建，确保更新）
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

        List<org.cubexmc.metro.model.Line> allLines = lineManager.getAllLines();
        if (allLines == null || allLines.isEmpty()) {
            return;
        }

        for (org.cubexmc.metro.model.Line line : allLines) {
            renderLine(markerSet, line, stopManager);
        }
    }

    private void renderLine(MarkerSet markerSet, org.cubexmc.metro.model.Line line, StopManager stopManager) {
        String worldName = line.getWorldName();
        if (worldName == null) return;

        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds.isEmpty()) return;

        // 收集折线的坐标
        List<Double> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();
        List<Double> zList = new ArrayList<>();

        for (String stopId : stopIds) {
            Stop stop = stopManager.getStop(stopId);
            if (stop == null || stop.getStopPointLocation() == null) continue;

            Location loc = stop.getStopPointLocation();
            xList.add(loc.getX());
            yList.add(loc.getY());
            zList.add(loc.getZ());

            // 为每个站点添加标记（如果配置启用）
            if (plugin.getConfigFacade().isMapShowStopMarkers()) {
                String markerId = "poi_" + line.getId() + "_" + stopId;
                String stopLabel = (stop.getName() != null && !stop.getName().isEmpty())
                        ? stop.getName() : stopId;

                Marker marker = markerSet.createMarker(
                        markerId,
                        stopLabel,
                        worldName,
                        loc.getX(), loc.getY(), loc.getZ(),
                        markerApi.getMarkerIcon(MarkerIcon.DEFAULT),
                        false
                );

                if (marker != null) {
                    // 悬浮时显示站名和线路信息
                    marker.setDescription("<b>" + stopLabel + "</b><br>Line: " + line.getName());
                }
            }
        }

        if (xList.size() < 2) return;

        // 转为数组
        double[] x = xList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = yList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] z = zList.stream().mapToDouble(Double::doubleValue).toArray();

        // 创建折线标记
        String lineMarkerId = "line_" + line.getId();
        PolyLineMarker polyLine = markerSet.createPolyLineMarker(
                lineMarkerId,
                line.getName() + " (" + line.getId() + ")",
                false,  // non-html label
                worldName,
                x, y, z,
                false   // not persistent (we manage lifecycle)
        );

        if (polyLine != null) {
            int color = parseLineColorRGB(line.getColor());
            polyLine.setLineStyle(plugin.getConfigFacade().getMapLineWidth(), 0.8, color);
        }
    }

    /**
     * 将 Minecraft 聊天颜色代码转换为 Dynmap RGB int (0xRRGGBB)。
     */
    private int parseLineColorRGB(String chatColor) {
        if (chatColor == null || chatColor.isEmpty()) {
            return 0xFFFFFF;
        }

        char code = chatColor.charAt(chatColor.length() - 1);
        return switch (code) {
            case '0' -> 0x000000;   // 黑色
            case '1' -> 0x0000AA;   // 深蓝
            case '2' -> 0x00AA00;   // 深绿
            case '3' -> 0x00AAAA;   // 深青
            case '4' -> 0xAA0000;   // 深红
            case '5' -> 0xAA00AA;   // 深紫
            case '6' -> 0xFFAA00;   // 金色
            case '7' -> 0xAAAAAA;   // 灰色
            case '8' -> 0x555555;   // 深灰
            case '9' -> 0x5555FF;   // 蓝色
            case 'a' -> 0x55FF55;   // 绿色
            case 'b' -> 0x55FFFF;   // 青色
            case 'c' -> 0xFF5555;   // 红色
            case 'd' -> 0xFF55FF;   // 粉色
            case 'e' -> 0xFFFF55;   // 黄色
            default  -> 0xFFFFFF;   // 白色
        };
    }
}
