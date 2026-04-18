package org.cubexmc.metro.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 可选的 BlueMap 集成模块。
 * 当服务器安装了 BlueMap 插件时，自动在网页地图上绘制地铁网络。
 * 该类通过 BlueMapAPI 的 onEnable 回调注册，确保 BlueMap 准备就绪后再执行。
 */
public class BlueMapIntegration {

    private static final String MARKER_SET_ID = "metro_network";

    private final Metro plugin;
    private boolean enabled = false;

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

        BlueMapAPI.onEnable(api -> {
            plugin.getLogger().info("[BlueMap] BlueMap API detected. Rendering metro network on map...");
            renderMetroNetwork(api);
            enabled = true;
        });

        BlueMapAPI.onDisable(api -> {
            enabled = false;
            plugin.getLogger().info("[BlueMap] BlueMap API disabled. Metro markers removed.");
        });
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
        enabled = false;
        plugin.getLogger().info("[BlueMap] Metro markers removed.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ========== 核心渲染逻辑 ==========

    private void renderMetroNetwork(BlueMapAPI api) {
        LineManager lineManager = plugin.getLineManager();
        StopManager stopManager = plugin.getStopManager();

        // 先清理旧的 MarkerSet
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove(MARKER_SET_ID);
        }

        List<org.cubexmc.metro.model.Line> allLines = lineManager.getAllLines();
        if (allLines == null || allLines.isEmpty()) {
            return;
        }

        for (org.cubexmc.metro.model.Line line : allLines) {
            renderLine(api, line, stopManager);
        }
    }

    private void renderLine(BlueMapAPI api, org.cubexmc.metro.model.Line line, StopManager stopManager) {
        String worldName = line.getWorldName();
        if (worldName == null) return;

        // 获取该世界对应的 BlueMap 世界和地图
        for (BlueMapMap map : api.getMaps()) {
            BlueMapWorld bmWorld = map.getWorld();
            String bmWorldId = bmWorld.getId();
            
            // 严格匹配世界名称，避免 world 匹配到 world_nether 的情况
            boolean match = bmWorldId.equalsIgnoreCase(worldName);
            if (!match && bmWorldId.contains(":")) {
                String[] parts = bmWorldId.split(":");
                match = parts[parts.length - 1].equalsIgnoreCase(worldName);
            }
            if (!match) {
                continue;
            }

            // 获取或创建 MarkerSet
            String markerLabel = plugin.getConfigFacade().getMapMarkerSetLabel();
            boolean defaultVisible = plugin.getConfigFacade().isMapDefaultVisible();
            MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(
                    MARKER_SET_ID,
                    id -> MarkerSet.builder()
                            .label(markerLabel)
                            .defaultHidden(!defaultVisible)
                            .build()
            );

            List<String> stopIds = line.getOrderedStopIds();
            if (stopIds.isEmpty()) continue;

            Color lineColor = parseLineColor(line.getColor());

            // 构建折线：依次连接各站点
            de.bluecolored.bluemap.api.math.Line.Builder lineBuilder =
                    de.bluecolored.bluemap.api.math.Line.builder();
            boolean hasPoints = false;

            for (int i = 0; i < stopIds.size(); i++) {
                Stop stop = stopManager.getStop(stopIds.get(i));
                if (stop == null || stop.getStopPointLocation() == null) continue;

                Location loc = stop.getStopPointLocation();
                lineBuilder.addPoint(
                        new com.flowpowered.math.vector.Vector3d(loc.getX(), loc.getY(), loc.getZ())
                );
                hasPoints = true;

                // 为每个站点添加 POI 标记（如果配置启用）
                if (plugin.getConfigFacade().isMapShowStopMarkers()) {
                    String poiId = "poi_" + line.getId() + "_" + stop.getId();
                    String label = (stop.getName() != null && !stop.getName().isEmpty()) ? stop.getName() : stop.getId();
                    POIMarker poi = POIMarker.builder()
                            .label(label)
                            .position(loc.getX(), loc.getY(), loc.getZ())
                            .build();
                    markerSet.put(poiId, poi);
                }
            }

            if (!hasPoints) continue;

            // 创建线路折线标记
            try {
                String lineMarkerId = "line_" + line.getId();
                LineMarker lineMarker = LineMarker.builder()
                        .label(line.getName() + " (" + line.getId() + ")")
                        .line(lineBuilder.build())
                        .lineColor(lineColor)
                        .lineWidth(plugin.getConfigFacade().getMapLineWidth())
                        .build();
                markerSet.put(lineMarkerId, lineMarker);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[BlueMap] Failed to create line marker for " + line.getId(), e);
            }
        }
    }

    /**
     * 将 Minecraft 聊天颜色代码转换为 BlueMap RGBA Color。
     */
    private Color parseLineColor(String chatColor) {
        if (chatColor == null || chatColor.isEmpty()) {
            return new Color(255, 255, 255, 255); // 白色
        }

        // 去掉 & 前缀取最后一个颜色字符
        char code = chatColor.charAt(chatColor.length() - 1);
        return switch (code) {
            case '0' -> new Color(0, 0, 0, 255);        // 黑色
            case '1' -> new Color(0, 0, 170, 255);       // 深蓝
            case '2' -> new Color(0, 170, 0, 255);       // 深绿
            case '3' -> new Color(0, 170, 170, 255);     // 深青
            case '4' -> new Color(170, 0, 0, 255);       // 深红
            case '5' -> new Color(170, 0, 170, 255);     // 深紫
            case '6' -> new Color(255, 170, 0, 255);     // 金色
            case '7' -> new Color(170, 170, 170, 255);   // 灰色
            case '8' -> new Color(85, 85, 85, 255);      // 深灰
            case '9' -> new Color(85, 85, 255, 255);     // 蓝色
            case 'a' -> new Color(85, 255, 85, 255);     // 绿色
            case 'b' -> new Color(85, 255, 255, 255);    // 青色
            case 'c' -> new Color(255, 85, 85, 255);     // 红色
            case 'd' -> new Color(255, 85, 255, 255);    // 粉色
            case 'e' -> new Color(255, 255, 85, 255);    // 黄色
            default  -> new Color(255, 255, 255, 255);   // 白色
        };
    }
}
