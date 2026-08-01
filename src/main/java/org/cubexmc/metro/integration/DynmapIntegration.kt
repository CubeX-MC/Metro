package org.cubexmc.metro.integration

import org.bukkit.Bukkit
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import org.dynmap.DynmapCommonAPI
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerIcon
import org.dynmap.markers.MarkerSet
import java.util.logging.Level

/**
 * 可选的 Dynmap 集成模块。
 * 当服务器安装了 Dynmap 插件且配置中 provider 设为 DYNMAP 时，
 * 自动在网页地图上绘制地铁网络的线路和站点。
 */
class DynmapIntegration(private val plugin: Metro) : MapIntegration {

    private var markerApi: MarkerAPI? = null
    private var enabled = false

    override fun isAvailable(): Boolean =
        try {
            val dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap")
            dynmapPlugin != null && dynmapPlugin.isEnabled
        } catch (_: RuntimeException) {
            false
        }

    /**
     * 尝试启用 Dynmap 集成。
     */
    override fun enable() {
        // 检查配置是否启用了地图集成
        if (!plugin.configFacade.isMapIntegrationEnabled()) {
            plugin.logger.info("[Dynmap] Map integration is disabled in config.yml.")
            return
        }

        // 检查配置的 provider 是否为 DYNMAP 或 AUTO
        if (!matchesProvider()) {
            plugin.logger.info(
                "[Dynmap] Map provider is set to '" + plugin.configFacade.getMapProvider() +
                    "', skipping Dynmap integration.",
            )
            return
        }

        // 检验 Dynmap 插件是否已加载
        if (!isAvailable()) {
            plugin.logger.warning("[Dynmap] Dynmap plugin not found or not enabled. Skipping integration.")
            return
        }
        val dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap")

        val markers =
            try {
                (dynmapPlugin as DynmapCommonAPI).markerAPI
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "[Dynmap] Failed to get Dynmap MarkerAPI.", e)
                return
            }
        markerApi = markers

        if (markers == null) {
            plugin.logger.warning("[Dynmap] Dynmap MarkerAPI is null. Skipping integration.")
            return
        }

        plugin.logger.info("[Dynmap] Dynmap API detected. Rendering metro stops on map...")
        renderMetroNetwork(markers)
        enabled = true
    }

    /**
     * 强制刷新网页地图上的地铁线路标记。
     */
    override fun refresh() {
        if (!plugin.configFacade.isMapIntegrationEnabled() || !matchesProvider()) {
            disable()
            return
        }

        if (!enabled) {
            enable()
            return
        }
        markerApi?.let { renderMetroNetwork(it) }
    }

    override fun disable() {
        val markerSet = markerApi?.getMarkerSet(MARKER_SET_ID)
        markerSet?.deleteMarkerSet()
        enabled = false
        plugin.logger.info("[Dynmap] Metro markers removed.")
    }

    override fun isEnabled(): Boolean = enabled

    private fun matchesProvider(): Boolean {
        val provider = plugin.configFacade.getMapProvider()
        return "DYNMAP".equals(provider, ignoreCase = true) || "AUTO".equals(provider, ignoreCase = true)
    }

    // ========== 核心渲染逻辑 ==========

    private fun renderMetroNetwork(markerApi: MarkerAPI) {
        val lineManager = plugin.lineManager
        val stopManager = plugin.stopManager

        val label = plugin.configFacade.getMapMarkerSetLabel()

        // 获取或创建 MarkerSet（先删除旧的再重建，确保更新）
        markerApi.getMarkerSet(MARKER_SET_ID)?.deleteMarkerSet()
        val markerSet = markerApi.createMarkerSet(MARKER_SET_ID, label, null, false)

        if (markerSet == null) {
            plugin.logger.warning("[Dynmap] Failed to create MarkerSet.")
            return
        }

        markerSet.setHideByDefault(!plugin.configFacade.isMapDefaultVisible())

        for (line in lineManager.getAllLines()) {
            renderRoute(markerSet, line)
        }

        if (plugin.configFacade.isMapShowStopMarkers()) {
            val allStops = stopManager.getAllStops()
            if (allStops.isEmpty()) {
                return
            }
            for (stop in allStops) {
                renderStop(markerApi, markerSet, stop)
            }
        }
    }

    private fun renderRoute(markerSet: MarkerSet, line: Line) {
        val routePoints = line.routePoints
        if (routePoints.size < 2) {
            return
        }

        val worldName = routePoints[0].worldName()
        if (worldName.isBlank()) {
            return
        }
        val displayPoints = MapGeometry.orthogonalRoutePoints(routePoints, worldName)
        if (displayPoints.size < 2) {
            return
        }

        val x = DoubleArray(displayPoints.size) { displayPoints[it].x() }
        val y = DoubleArray(displayPoints.size) { displayPoints[it].y() }
        val z = DoubleArray(displayPoints.size) { displayPoints[it].z() }

        val polyLine =
            markerSet.createPolyLineMarker(
                "route_" + line.id,
                line.name + " (" + line.id + ")",
                false,
                worldName,
                x,
                y,
                z,
                false,
            )

        polyLine?.setLineStyle(
            plugin.configFacade.getMapLineWidth(),
            ROUTE_LINE_OPACITY,
            MapLineColor.fromLineColor(line.color).asRgbInt(),
        )
    }

    private fun renderStop(markerApi: MarkerAPI, markerSet: MarkerSet, stop: Stop) {
        val bounds = MapGeometry.stopBounds(stop).orElse(null)
        if (bounds != null && renderStopArea(markerSet, stop, bounds)) {
            return
        }

        renderStopMarker(markerApi, markerSet, stop)
    }

    private fun renderStopArea(markerSet: MarkerSet, stop: Stop, bounds: MapGeometry.StopBounds): Boolean {
        val x = doubleArrayOf(bounds.minX(), bounds.maxX(), bounds.maxX(), bounds.minX())
        val z = doubleArrayOf(bounds.minZ(), bounds.minZ(), bounds.maxZ(), bounds.maxZ())
        val area =
            markerSet.createAreaMarker(
                "stop_area_" + stop.id,
                stopLabel(stop),
                false,
                bounds.worldName(),
                x,
                z,
                false,
            ) ?: return false
        val color = getStopColor(stop).asRgbInt()
        area.setRangeY(bounds.maxY(), bounds.minY())
        area.setLineStyle(maxOf(1, plugin.configFacade.getMapLineWidth()), STOP_LINE_OPACITY, color)
        area.setFillStyle(STOP_FILL_OPACITY, color)
        area.setDescription(buildStopDescription(stop))
        return true
    }

    private fun renderStopMarker(markerApi: MarkerAPI, markerSet: MarkerSet, stop: Stop) {
        val loc = stop.stopPointLocation ?: return
        val world = loc.world ?: return

        val marker =
            markerSet.createMarker(
                "stop_" + stop.id,
                stopLabel(stop),
                world.name,
                loc.x,
                loc.y,
                loc.z,
                markerApi.getMarkerIcon(MarkerIcon.DEFAULT),
                false,
            )

        marker?.setDescription(buildStopDescription(stop))
    }

    private fun buildStopDescription(stop: Stop): String {
        val parts = ArrayList<String>()
        parts.add("<b>" + stopLabel(stop) + "</b>")

        val servedLines = plugin.lineManager.getLinesForStop(stop.id)
        if (servedLines.isNotEmpty()) {
            parts.add("Lines: " + servedLines.joinToString(", ") { it.name + " (" + it.id + ")" })
        }
        val transfers = stop.transferableLines
        if (plugin.configFacade.isMapShowTransferInfo() && transfers.isNotEmpty()) {
            parts.add("Transfers: " + transfers.joinToString(", "))
        }
        return parts.joinToString("<br>")
    }

    private fun stopLabel(stop: Stop): String = stop.name.ifEmpty { stop.id }

    private fun getStopColor(stop: Stop): MapLineColor {
        val servedLines = plugin.lineManager.getLinesForStop(stop.id)
        if (servedLines.isEmpty()) {
            return MapLineColor.WHITE
        }
        return MapLineColor.fromLineColor(servedLines[0].color)
    }

    private companion object {
        const val MARKER_SET_ID = "metro_network"
        const val ROUTE_LINE_OPACITY = 0.8
        const val STOP_LINE_OPACITY = 0.85
        const val STOP_FILL_OPACITY = 0.22
    }
}
