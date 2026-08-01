package org.cubexmc.metro.integration

import org.bukkit.Bukkit
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop
import xyz.jpenilla.squaremap.api.BukkitAdapter
import xyz.jpenilla.squaremap.api.Key
import xyz.jpenilla.squaremap.api.Point
import xyz.jpenilla.squaremap.api.SimpleLayerProvider
import xyz.jpenilla.squaremap.api.Squaremap
import xyz.jpenilla.squaremap.api.SquaremapProvider
import xyz.jpenilla.squaremap.api.marker.Marker
import xyz.jpenilla.squaremap.api.marker.MarkerOptions
import java.awt.Color
import java.util.Locale
import java.util.logging.Level

/**
 * 可选的 Squaremap 集成模块。
 * 当服务器安装了 Squaremap 插件且配置中 provider 设为 SQUAREMAP 时，
 * 自动在网页地图上绘制地铁网络的线路和站点。
 */
class SquaremapIntegration(private val plugin: Metro) : MapIntegration {

    private var enabled = false
    private val layerProviders: MutableMap<String, SimpleLayerProvider> = HashMap()

    override fun isAvailable(): Boolean =
        try {
            if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
                false
            } else {
                Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider")
                true
            }
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    override fun enable() {
        if (!plugin.configFacade.isMapIntegrationEnabled()) {
            return
        }

        if (!matchesProvider()) {
            return
        }

        if (!isAvailable()) {
            plugin.logger.warning("[Squaremap] squaremap plugin not found. Skipping integration.")
            return
        }

        plugin.logger.info("[Squaremap] API detected. Rendering metro stops on map...")
        renderMetroNetwork()
        enabled = true
    }

    override fun refresh() {
        if (!plugin.configFacade.isMapIntegrationEnabled() || !matchesProvider()) {
            disable()
            return
        }

        if (!enabled) {
            enable()
        } else {
            renderMetroNetwork()
        }
    }

    override fun disable() {
        try {
            val api = SquaremapProvider.get()
            for ((worldName, _) in layerProviders) {
                val bukkitWorld = Bukkit.getWorld(worldName) ?: continue
                api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent { world ->
                    world.layerRegistry().unregister(Key.of(LAYER_ID))
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        layerProviders.clear()
        enabled = false
        plugin.logger.info("[Squaremap] Metro markers removed.")
    }

    override fun isEnabled(): Boolean = enabled

    private fun matchesProvider(): Boolean {
        val provider = plugin.configFacade.getMapProvider()
        return "SQUAREMAP".equals(provider, ignoreCase = true) || "AUTO".equals(provider, ignoreCase = true)
    }

    private fun renderMetroNetwork() {
        try {
            val api = SquaremapProvider.get()
            val lineManager = plugin.lineManager
            val stopManager = plugin.stopManager

            // 先清理旧标记
            for (provider in layerProviders.values) {
                provider.clearMarkers()
            }

            val layerLabel = plugin.configFacade.getMapMarkerSetLabel()
            val defaultVisible = plugin.configFacade.isMapDefaultVisible()

            for (line in lineManager.getAllLines()) {
                renderRoute(api, layerLabel, defaultVisible, line)
            }

            if (!plugin.configFacade.isMapShowStopMarkers()) {
                return
            }

            val allStops = stopManager.getAllStops()
            if (allStops.isEmpty()) {
                return
            }

            for (stop in allStops) {
                renderStopOnWorldLayer(api, layerLabel, defaultVisible, stop)
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "[Squaremap] Failed to render network.", e)
        }
    }

    private fun renderStopOnWorldLayer(
        api: Squaremap,
        layerLabel: String,
        defaultVisible: Boolean,
        stop: Stop,
    ) {
        val worldName =
            MapGeometry.stopBounds(stop)
                .map { bounds -> bounds.worldName() }
                .orElseGet { stop.stopPointLocation?.world?.name }
                ?: return

        val bukkitWorld = Bukkit.getWorld(worldName) ?: return

        api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent { world ->
            val provider =
                layerProviders.computeIfAbsent(worldName) {
                    val created = newLayerProvider(layerLabel, defaultVisible)
                    world.layerRegistry().register(Key.of(LAYER_ID), created)
                    created
                }

            renderStop(provider, stop)
        }
    }

    private fun renderRoute(api: Squaremap, layerLabel: String, defaultVisible: Boolean, line: Line) {
        val routePoints = line.routePoints
        if (routePoints.size < 2) {
            return
        }

        val worldName = routePoints[0].worldName()
        if (worldName.isBlank()) {
            return
        }
        val bukkitWorld = Bukkit.getWorld(worldName) ?: return

        api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(bukkitWorld)).ifPresent { world ->
            val provider =
                layerProviders.computeIfAbsent(worldName) {
                    val created = newLayerProvider(layerLabel, defaultVisible)
                    world.layerRegistry().register(Key.of(LAYER_ID), created)
                    created
                }

            val points =
                MapGeometry.orthogonalRoutePoints(routePoints, worldName)
                    .map { Point.of(it.x(), it.z()) }
            if (points.size < 2) {
                return@ifPresent
            }

            val polyline = Marker.polyline(points)
            polyline.markerOptions(
                MarkerOptions.builder()
                    .strokeColor(toAwtColor(MapLineColor.fromLineColor(line.color)))
                    .strokeWeight(plugin.configFacade.getMapLineWidth())
                    .hoverTooltip(line.name + " (" + line.id + ")")
                    .build(),
            )
            provider.addMarker(Key.of(("route_" + line.id).lowercase(Locale.getDefault())), polyline)
        }
    }

    private fun newLayerProvider(layerLabel: String, defaultVisible: Boolean): SimpleLayerProvider =
        SimpleLayerProvider.builder(layerLabel)
            .defaultHidden(!defaultVisible)
            .build()

    private fun renderStop(provider: SimpleLayerProvider, stop: Stop) {
        val bounds = MapGeometry.stopBounds(stop).orElse(null)
        if (bounds != null && renderStopArea(provider, stop, bounds)) {
            return
        }
        renderStopMarker(provider, stop)
    }

    private fun renderStopArea(
        provider: SimpleLayerProvider,
        stop: Stop,
        bounds: MapGeometry.StopBounds,
    ): Boolean {
        val stopId = ("stop_area_" + stop.id).lowercase(Locale.getDefault())
        val area =
            Marker.rectangle(
                Point.of(bounds.minX(), bounds.minZ()),
                Point.of(bounds.maxX(), bounds.maxZ()),
            )
        val color = getStopColor(stop)
        area.markerOptions(
            MarkerOptions.builder()
                .hoverTooltip(buildStopTooltip(stop))
                .strokeColor(color)
                .strokeWeight(maxOf(1, plugin.configFacade.getMapLineWidth()))
                .strokeOpacity(STOP_LINE_OPACITY)
                .fill(true)
                .fillColor(color)
                .fillOpacity(STOP_FILL_OPACITY)
                .build(),
        )

        provider.addMarker(Key.of(stopId), area)
        return true
    }

    private fun renderStopMarker(provider: SimpleLayerProvider, stop: Stop) {
        val loc = stop.stopPointLocation ?: return
        if (loc.world == null) return
        val poiId = ("stop_" + stop.id).lowercase(Locale.getDefault())

        val poi = Marker.circle(Point.of(loc.x, loc.z), STOP_MARKER_RADIUS)
        poi.markerOptions(
            MarkerOptions.builder()
                .hoverTooltip(buildStopTooltip(stop))
                .fillColor(getStopColor(stop))
                .fill(true)
                .strokeColor(Color.BLACK)
                .strokeWeight(1)
                .build(),
        )

        provider.addMarker(Key.of(poiId), poi)
    }

    private fun buildStopTooltip(stop: Stop): String {
        val parts = ArrayList<String>()
        parts.add("<b>" + stop.name.ifEmpty { stop.id } + "</b>")

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

    private fun getStopColor(stop: Stop): Color {
        val servedLines = plugin.lineManager.getLinesForStop(stop.id)
        if (servedLines.isEmpty()) {
            return Color.WHITE
        }
        return toAwtColor(MapLineColor.fromLineColor(servedLines[0].color))
    }

    private fun toAwtColor(color: MapLineColor): Color = Color(color.red(), color.green(), color.blue())

    private companion object {
        const val LAYER_ID = "metro_network"
        const val STOP_LINE_OPACITY = 0.85
        const val STOP_FILL_OPACITY = 0.22
        const val STOP_MARKER_RADIUS = 3.0
    }
}
