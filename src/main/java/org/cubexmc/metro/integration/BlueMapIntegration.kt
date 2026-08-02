package org.cubexmc.metro.integration

import com.flowpowered.math.vector.Vector3d
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.BlueMapMap
import de.bluecolored.bluemap.api.markers.ExtrudeMarker
import de.bluecolored.bluemap.api.markers.LineMarker
import de.bluecolored.bluemap.api.markers.MarkerSet
import de.bluecolored.bluemap.api.markers.POIMarker
import de.bluecolored.bluemap.api.math.Color
import de.bluecolored.bluemap.api.math.Shape
import de.bluecolored.bluemap.api.math.Line as BlueMapLine
import org.bukkit.Bukkit
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import java.util.function.Consumer

/**
 * 可选的 BlueMap 集成模块。
 * 当服务器安装了 BlueMap 插件时，自动在网页地图上绘制地铁网络。
 * 该类通过 BlueMapAPI 的 onEnable 回调注册，确保 BlueMap 准备就绪后再执行。
 */
class BlueMapIntegration(private val plugin: Metro) : MapIntegration {

    private val enableListener = Consumer<BlueMapAPI> { api -> handleBlueMapEnabled(api) }
    private val disableListener = Consumer<BlueMapAPI> { handleBlueMapDisabled() }
    private var enabled = false
    private var listenersRegistered = false

    override fun isAvailable(): Boolean =
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    /**
     * 尝试启用 BlueMap 集成。
     * 如果 BlueMap 不在 classpath 中，将安静地跳过。
     */
    override fun enable() {
        // 检查配置是否启用了地图集成
        if (!plugin.configFacade.isMapIntegrationEnabled()) {
            plugin.logger.info("[BlueMap] Map integration is disabled in config.yml.")
            return
        }

        // 检查配置的 provider 是否为 BLUEMAP 或 AUTO
        if (!matchesProvider()) {
            plugin.logger.info(
                "[BlueMap] Map provider is set to '" + plugin.configFacade.getMapProvider() +
                    "', skipping BlueMap integration.",
            )
            return
        }

        if (!isAvailable()) {
            plugin.logger.info("[BlueMap] BlueMap not detected, skipping map integration.")
            return
        }

        if (!listenersRegistered) {
            BlueMapAPI.onEnable(enableListener)
            BlueMapAPI.onDisable(disableListener)
            listenersRegistered = true
        }

        if (!enabled) {
            BlueMapAPI.getInstance().ifPresent { api -> handleBlueMapEnabled(api) }
        }
    }

    /**
     * 强制刷新网页地图上的地铁线路标记。
     * 可在管理员编辑线路后手动调用。
     */
    override fun refresh() {
        if (!plugin.configFacade.isMapIntegrationEnabled() || !matchesProvider()) {
            disable()
            return
        }

        val wasEnabled = enabled
        if (!enabled) {
            enable()
        }
        if (wasEnabled && enabled) {
            BlueMapAPI.getInstance().ifPresent { api -> renderMetroNetwork(api) }
        }
    }

    override fun disable() {
        BlueMapAPI.getInstance().ifPresent { api ->
            for (map in api.maps) {
                map.markerSets.remove(MARKER_SET_ID)
            }
        }
        if (listenersRegistered) {
            BlueMapAPI.unregisterListener(enableListener)
            BlueMapAPI.unregisterListener(disableListener)
            listenersRegistered = false
        }
        enabled = false
        plugin.logger.info("[BlueMap] Metro markers removed.")
    }

    override fun isEnabled(): Boolean = enabled

    private fun matchesProvider(): Boolean {
        val provider = plugin.configFacade.getMapProvider()
        return "BLUEMAP".equals(provider, ignoreCase = true) || "AUTO".equals(provider, ignoreCase = true)
    }

    // ========== 核心渲染逻辑 ==========

    private fun handleBlueMapEnabled(api: BlueMapAPI) {
        plugin.logger.info("[BlueMap] BlueMap API detected. Rendering metro stops on map...")
        renderMetroNetwork(api)
        enabled = true
    }

    private fun handleBlueMapDisabled() {
        enabled = false
        plugin.logger.info("[BlueMap] BlueMap API disabled. Metro markers removed.")
    }

    private fun renderMetroNetwork(api: BlueMapAPI) {
        val lineManager = plugin.lineManager
        val stopManager = plugin.stopManager

        // 先清理旧的 MarkerSet
        for (map in api.maps) {
            map.markerSets.remove(MARKER_SET_ID)
        }

        for (line in lineManager.getAllLines()) {
            renderRoute(api, line)
        }

        if (plugin.configFacade.isMapShowStopMarkers()) {
            val allStops = stopManager.getAllStops()
            if (allStops.isEmpty()) {
                return
            }
            for (stop in allStops) {
                renderStop(api, stop)
            }
        }
    }

    private fun renderRoute(api: BlueMapAPI, line: Line) {
        val routePoints = line.routePoints
        if (routePoints.size < 2) {
            return
        }

        val worldName = routePoints[0].worldName()
        if (worldName.isBlank()) {
            return
        }
        for (map in getMapsForWorld(api, worldName)) {
            val markerSet = getMarkerSet(map)
            val displayPoints = MapGeometry.orthogonalRoutePoints(routePoints, worldName)
            if (displayPoints.size < 2) {
                return
            }
            val lineBuilder = BlueMapLine.builder()
            for (point in displayPoints) {
                lineBuilder.addPoint(Vector3d(point.x(), point.y(), point.z()))
            }

            val lineMarker =
                LineMarker.builder()
                    .label(line.name + " (" + line.id + ")")
                    .line(lineBuilder.build())
                    .lineColor(toBlueMapColor(MapLineColor.fromLineColor(line.color)))
                    .lineWidth(plugin.configFacade.getMapLineWidth())
                    .build()
            markerSet.put("route_" + line.id, lineMarker)
        }
    }

    private fun renderStop(api: BlueMapAPI, stop: Stop) {
        val bounds = MapGeometry.stopBounds(stop).orElse(null)
        if (bounds != null && renderStopArea(api, stop, bounds)) {
            return
        }

        renderStopPoi(api, stop)
    }

    private fun renderStopArea(api: BlueMapAPI, stop: Stop, bounds: MapGeometry.StopBounds): Boolean {
        var rendered = false
        for (map in getMapsForWorld(api, bounds.worldName())) {
            val markerSet = getMarkerSet(map)
            val stopColor = getStopColor(stop)
            val area =
                ExtrudeMarker.builder()
                    .label(stopLabel(stop))
                    .shape(
                        Shape.createRect(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()),
                        bounds.minY().toFloat(),
                        bounds.maxY().toFloat(),
                    )
                    .lineColor(toBlueMapColor(stopColor, 1.0f))
                    .fillColor(toBlueMapColor(stopColor, STOP_FILL_ALPHA))
                    .lineWidth(maxOf(1, plugin.configFacade.getMapLineWidth()))
                    .build()
            area.setDetail(buildStopDetail(stop))
            markerSet.put("stop_area_" + stop.id, area)
            rendered = true
        }
        return rendered
    }

    private fun renderStopPoi(api: BlueMapAPI, stop: Stop) {
        val loc = stop.stopPointLocation ?: return
        val world = loc.world ?: return

        val worldName = world.name

        for (map in getMapsForWorld(api, worldName)) {
            val markerSet = getMarkerSet(map)

            val poi =
                POIMarker.builder()
                    .label(stopLabel(stop))
                    .position(loc.x, loc.y, loc.z)
                    .build()
            poi.setDetail(buildStopDetail(stop))
            markerSet.put("stop_" + stop.id, poi)
        }
    }

    private fun getMapsForWorld(api: BlueMapAPI, worldName: String): Collection<BlueMapMap> {
        val bukkitWorld = Bukkit.getWorld(worldName)
        if (bukkitWorld != null) {
            return api.getWorld(bukkitWorld)
                .map { blueMapWorld -> blueMapWorld.maps }
                .filter { maps -> maps.isNotEmpty() }
                .orElseGet { getMapsByWorldName(api, worldName) }
        }
        return getMapsByWorldName(api, worldName)
    }

    private fun getMapsByWorldName(api: BlueMapAPI, worldName: String): List<BlueMapMap> {
        val maps = ArrayList<BlueMapMap>()
        for (map in api.maps) {
            if (matchesWorld(map, worldName)) {
                maps.add(map)
            }
        }
        return maps
    }

    private fun matchesWorld(map: BlueMapMap, worldName: String): Boolean {
        val bmWorldId = map.world.id
        var match = bmWorldId.equals(worldName, ignoreCase = true)
        if (!match && bmWorldId.contains(":")) {
            val parts = bmWorldId.split(":").dropLastWhile { it.isEmpty() }
            match = parts[parts.size - 1].equals(worldName, ignoreCase = true)
        }
        return match
    }

    private fun getMarkerSet(map: BlueMapMap): MarkerSet {
        val markerLabel = plugin.configFacade.getMapMarkerSetLabel()
        val defaultVisible = plugin.configFacade.isMapDefaultVisible()
        return map.markerSets.computeIfAbsent(MARKER_SET_ID) {
            MarkerSet.builder()
                .label(markerLabel)
                .defaultHidden(!defaultVisible)
                .build()
        }
    }

    private fun buildStopDetail(stop: Stop): String {
        val detail = ArrayList<String>()
        detail.add("<b>" + stopLabel(stop) + "</b>")
        val servedLines = plugin.lineManager.getLinesForStop(stop.id)
        if (servedLines.isNotEmpty()) {
            detail.add("<b>Lines:</b> " + servedLines.joinToString(", ") { it.name + " (" + it.id + ")" })
        }
        val transfers = stop.transferableLines
        if (plugin.configFacade.isMapShowTransferInfo() && transfers.isNotEmpty()) {
            detail.add("<b>Transfers:</b> " + transfers.joinToString(", "))
        }
        return detail.joinToString("<br>")
    }

    private fun stopLabel(stop: Stop): String = stop.name.ifEmpty { stop.id }

    private fun getStopColor(stop: Stop): MapLineColor {
        val servedLines = plugin.lineManager.getLinesForStop(stop.id)
        if (servedLines.isEmpty()) {
            return MapLineColor.WHITE
        }
        return MapLineColor.fromLineColor(servedLines[0].color)
    }

    private fun toBlueMapColor(color: MapLineColor, alpha: Float = 1.0f): Color =
        Color(color.red(), color.green(), color.blue(), alpha)

    private companion object {
        const val MARKER_SET_ID = "metro_network"
        const val STOP_FILL_ALPHA = 0.22f
    }
}
