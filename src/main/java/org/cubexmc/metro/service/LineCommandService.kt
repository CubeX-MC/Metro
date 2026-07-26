package org.cubexmc.metro.service

import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import org.bukkit.Bukkit
import org.cubexmc.metro.event.LineStatusChangeEvent
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.LineStatus
import org.cubexmc.metro.model.Portal
import org.cubexmc.metro.model.PriceRule
import org.cubexmc.metro.model.Stop

/**
 * Business operations used by line commands.
 */
class LineCommandService(lineManager: LineManager?) {
    private val lineManagerRef: LineManager? = lineManager
    private val lineManager: LineManager
        get() = lineManagerRef ?: throw NullPointerException("lineManager")

    enum class WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_COLOR,
        INVALID_VALUE,
        EXISTS,
        NOT_FOUND,
        FAILED,
        STOP_NO_WORLD,
        CIRCULAR_INVALID_INDEX,
    }

    data class AddStopResult(
        private val status: WriteStatus,
    ) {
        fun status(): WriteStatus = status

        companion object {
            fun of(status: WriteStatus): AddStopResult = AddStopResult(status)
        }
    }

    data class ClearRouteResult(
        private val status: WriteStatus,
        private val previousPointCount: Int,
    ) {
        fun status(): WriteStatus = status

        fun previousPointCount(): Int = previousPointCount
    }

    fun createLine(id: String?, name: String?, ownerId: UUID?): WriteStatus {
        if (!isValidId(id)) {
            return WriteStatus.INVALID_ID
        }
        return if (lineManager.createLine(id, name, ownerId)) WriteStatus.SUCCESS else WriteStatus.EXISTS
    }

    fun deleteLine(id: String): WriteStatus =
        if (lineManager.deleteLine(id)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun listLines(): List<Line> = lineManager.getAllLines()
        .sortedBy { line -> line.id }

    fun renameLine(id: String, name: String?): WriteStatus =
        if (lineManager.setLineName(id, name)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun setColor(id: String, color: String?): WriteStatus {
        // The color argument is parsed greedily, so trim before validating.
        val normalized = color?.trim()
        if (!isValidColor(normalized)) {
            return WriteStatus.INVALID_COLOR
        }
        return if (lineManager.setLineColor(id, normalized)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun setTerminusName(id: String, terminusName: String?): WriteStatus =
        if (lineManager.setLineTerminusName(id, terminusName)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun setMaxSpeed(id: String, speed: Double): WriteStatus {
        if (!java.lang.Double.isFinite(speed) || speed <= 0.0) {
            return WriteStatus.INVALID_VALUE
        }
        return if (lineManager.setLineMaxSpeed(id, speed)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun addStopToLine(line: Line, stop: Stop, index: Int?): AddStopResult {
        val stopWorld = stop.worldName
        val lineWorld = line.worldName
        if (stopWorld == null || stopWorld.isBlank()) {
            return AddStopResult.of(WriteStatus.STOP_NO_WORLD)
        }

        val targetIndex = index ?: -1
        val orderedStopIds = line.orderedStopIds
        if (line.isCircular && (targetIndex < 0 || targetIndex >= orderedStopIds.size)) {
            return AddStopResult.of(WriteStatus.CIRCULAR_INVALID_INDEX)
        }

        if (!lineManager.addStopToLine(line.id, stop.id, targetIndex)) {
            return AddStopResult.of(WriteStatus.FAILED)
        }
        if (orderedStopIds.isEmpty()) {
            lineManager.setLineWorldName(line.id, stopWorld)
        } else if (lineWorld != null && lineWorld != stopWorld) {
            lineManager.setLineWorldName(line.id, null)
        }
        return AddStopResult.of(WriteStatus.SUCCESS)
    }

    fun removeStopFromLine(line: Line, stopId: String): WriteStatus {
        if (!lineManager.delStopFromLine(line.id, stopId)) {
            return WriteStatus.FAILED
        }
        if (line.orderedStopIds.isEmpty()) {
            lineManager.setLineWorldName(line.id, null)
        }
        return WriteStatus.SUCCESS
    }

    fun addPortalToLine(line: Line?, portal: Portal?): WriteStatus {
        if (line == null || portal == null) {
            return WriteStatus.FAILED
        }
        if (line.containsPortal(portal.id)) {
            return WriteStatus.EXISTS
        }
        return if (lineManager.addPortalToLine(line.id, portal.id)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun removePortalFromLine(line: Line?, portalId: String?): WriteStatus {
        if (line == null || portalId == null || portalId.isBlank()) {
            return WriteStatus.FAILED
        }
        if (!line.containsPortal(portalId)) {
            return WriteStatus.NOT_FOUND
        }
        return if (lineManager.delPortalFromLine(line.id, portalId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun setRailProtected(id: String, enabled: Boolean): WriteStatus =
        if (lineManager.setLineRailProtected(id, enabled)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun grantAdmin(line: Line, adminId: UUID?): WriteStatus {
        if (adminId == null) {
            return WriteStatus.INVALID_VALUE
        }
        if (line.admins.contains(adminId)) {
            return WriteStatus.EXISTS
        }
        return if (lineManager.addLineAdmin(line.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun revokeAdmin(line: Line, adminId: UUID?): WriteStatus {
        if (adminId == null) {
            return WriteStatus.INVALID_VALUE
        }
        return if (lineManager.removeLineAdmin(line.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun transferOwner(line: Line, ownerId: UUID?): WriteStatus {
        if (ownerId == null) {
            return WriteStatus.INVALID_VALUE
        }
        return if (lineManager.setLineOwner(line.id, ownerId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun clearRoutePoints(line: Line): ClearRouteResult {
        val previousCount = line.routePoints.size
        val status = if (lineManager.clearLineRoutePoints(line.id)) WriteStatus.SUCCESS else WriteStatus.FAILED
        return ClearRouteResult(status, previousCount)
    }

    fun cloneReverseLine(sourceId: String, newId: String?, stopIdSuffix: String?, ownerId: UUID?): WriteStatus {
        if (!isValidId(newId)) {
            return WriteStatus.INVALID_ID
        }
        return if (lineManager.cloneReverseLine(sourceId, newId, stopIdSuffix, ownerId)) {
            WriteStatus.SUCCESS
        } else {
            WriteStatus.FAILED
        }
    }

    fun setTicketPrice(id: String, price: Double): WriteStatus {
        if (!java.lang.Double.isFinite(price) || price < 0.0) {
            return WriteStatus.INVALID_VALUE
        }
        return if (lineManager.setLineTicketPrice(id, price)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun setPriceRule(id: String, mode: String, basePrice: Double, perUnit: Double?, maxPrice: Double?): WriteStatus {
        if (basePrice < 0.0 || (perUnit != null && perUnit < 0.0) || (maxPrice != null && maxPrice < 0.0)) {
            return WriteStatus.INVALID_VALUE
        }

        val pricingMode = try {
            PriceRule.PricingMode.valueOf(mode.uppercase(Locale.getDefault()))
        } catch (exception: IllegalArgumentException) {
            return WriteStatus.INVALID_VALUE
        }

        val line = lineManager.getLine(id) ?: return WriteStatus.NOT_FOUND
        val rule = PriceRule(pricingMode, basePrice)
        if (pricingMode == PriceRule.PricingMode.DISTANCE && perUnit != null) {
            rule.setPerBlockRate(perUnit)
        }
        if (pricingMode == PriceRule.PricingMode.INTERVAL && perUnit != null) {
            rule.setPerIntervalRate(perUnit)
        }
        if (maxPrice != null && maxPrice > 0.0) {
            rule.setMaxPrice(maxPrice)
        }

        line.priceRule = rule
        lineManager.saveConfig()
        return WriteStatus.SUCCESS
    }

    fun resetPriceRule(id: String): Boolean {
        val line = lineManager.getLine(id) ?: return false
        line.priceRule = null
        lineManager.saveConfig()
        return true
    }

    fun setLineStatus(id: String, status: String): WriteStatus {
        val lineStatus = LineStatus.fromConfig(status)
        if (lineStatus == LineStatus.NORMAL && !status.equals("normal", ignoreCase = true)) {
            return WriteStatus.INVALID_VALUE
        }

        val line = lineManager.getLine(id) ?: return WriteStatus.NOT_FOUND
        val oldStatus = line.getLineStatus()
        if (oldStatus == lineStatus) {
            return WriteStatus.SUCCESS
        }

        line.setLineStatus(lineStatus)
        Bukkit.getPluginManager().callEvent(LineStatusChangeEvent(line, oldStatus, lineStatus))
        lineManager.saveConfig()
        return WriteStatus.SUCCESS
    }

    fun addAlternativeRoute(id: String, altLineId: String): Boolean {
        val line = lineManager.getLine(id) ?: return false
        val result = line.addAlternativeRoute(altLineId)
        if (result) {
            lineManager.saveConfig()
        }
        return result
    }

    fun removeAlternativeRoute(id: String, altLineId: String): Boolean {
        val line = lineManager.getLine(id) ?: return false
        val result = line.removeAlternativeRoute(altLineId)
        if (result) {
            lineManager.saveConfig()
        }
        return result
    }

    fun setSuspensionMessage(id: String, message: String?): Boolean {
        val line = lineManager.getLine(id) ?: return false
        line.suspensionMessage = message
        lineManager.saveConfig()
        return true
    }

    fun isValidId(id: String?): Boolean =
        id != null &&
            id.isNotBlank() &&
            id.length <= MAX_ID_LENGTH &&
            ID_PATTERN.matcher(id).matches()

    fun isValidColor(color: String?): Boolean =
        color != null &&
            (
                LEGACY_COLOR_PATTERN.matcher(color).matches() ||
                    HEX_COLOR_PATTERN.matcher(color).matches()
                )

    companion object {
        private const val MAX_ID_LENGTH = 64
        private val ID_PATTERN: Pattern = Pattern.compile("[A-Za-z0-9_-]+")
        private val LEGACY_COLOR_PATTERN: Pattern = Pattern.compile("(?i)&[0-9A-FK-OR]")
        private val HEX_COLOR_PATTERN: Pattern = Pattern.compile("(?i)&#[0-9A-F]{6}")
    }
}
