package org.cubexmc.metro.service

import java.util.UUID
import java.util.regex.Pattern
import org.bukkit.Location
import org.bukkit.Material
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Stop

/**
 * Business operations used by stop commands.
 */
class StopCommandService(stopManager: StopManager?) {
    private val stopManagerRef: StopManager? = stopManager
    private val stopManager: StopManager
        get() = stopManagerRef ?: throw NullPointerException("stopManager")

    enum class WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_ACTION,
        INVALID_TITLE_TYPE,
        INVALID_TITLE_KEY,
        EXISTS,
        NOT_FOUND,
        FAILED,
        NOT_RAIL,
        NOT_IN_STOP,
    }

    data class CreateStopResult(
        private val status: WriteStatus,
        private val stop: Stop?,
    ) {
        fun status(): WriteStatus = status

        fun stop(): Stop? = stop
    }

    data class SetPointResult(
        private val status: WriteStatus,
        private val yaw: Float,
    ) {
        fun status(): WriteStatus = status

        fun yaw(): Float = yaw
    }

    fun createStop(id: String?, name: String, corner1: Location?, corner2: Location?, ownerId: UUID?): CreateStopResult {
        if (!isValidId(id)) {
            return CreateStopResult(WriteStatus.INVALID_ID, null)
        }
        val stop = stopManager.createStop(id, name, corner1, corner2, ownerId)
        return if (stop == null) {
            CreateStopResult(WriteStatus.EXISTS, null)
        } else {
            CreateStopResult(WriteStatus.SUCCESS, stop)
        }
    }

    fun deleteStop(id: String): WriteStatus = if (stopManager.deleteStop(id)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun listStops(): List<Stop> = stopManager.getAllStopIds()
        .mapNotNull { stopId -> stopManager.getStop(stopId) }
        .sortedBy { stop -> stop.id }

    fun setCorners(id: String, corner1: Location, corner2: Location): WriteStatus =
        if (stopManager.setStopCorners(id, corner1, corner2)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun setPoint(id: String, stop: Stop, location: Location, yaw: Float?): SetPointResult {
        val type = location.block.type
        if (!isRail(type)) {
            return SetPointResult(WriteStatus.NOT_RAIL, 0.0f)
        }
        if (!stop.isInStop(location)) {
            return SetPointResult(WriteStatus.NOT_IN_STOP, 0.0f)
        }

        val resolvedYaw = yaw ?: location.yaw
        val status = if (stopManager.setStopPoint(id, location, resolvedYaw)) WriteStatus.SUCCESS else WriteStatus.FAILED
        return SetPointResult(status, resolvedYaw)
    }

    fun addTransferLine(stopId: String, lineId: String): WriteStatus =
        if (stopManager.addTransferLine(stopId, lineId)) WriteStatus.SUCCESS else WriteStatus.EXISTS

    fun removeTransferLine(stopId: String, lineId: String): WriteStatus =
        if (stopManager.removeTransferLine(stopId, lineId)) WriteStatus.SUCCESS else WriteStatus.NOT_FOUND

    fun setCustomTitle(stop: Stop, titleType: String, titleKey: String, titleValue: String): WriteStatus {
        val validation = validateTitlePath(titleType, titleKey)
        if (validation != WriteStatus.SUCCESS) {
            return validation
        }

        val existing = stop.getCustomTitle(titleType)
        val updated: MutableMap<String, String> = if (existing == null) HashMap() else HashMap(existing)
        updated[titleKey] = titleValue
        stop.setCustomTitle(titleType, updated)
        stopManager.saveConfig()
        return WriteStatus.SUCCESS
    }

    fun removeCustomTitleType(stop: Stop, titleType: String): WriteStatus {
        if (!TITLE_TYPES.contains(titleType)) {
            return WriteStatus.INVALID_TITLE_TYPE
        }
        if (!stop.removeCustomTitle(titleType)) {
            return WriteStatus.NOT_FOUND
        }
        stopManager.saveConfig()
        return WriteStatus.SUCCESS
    }

    fun removeCustomTitleKey(stop: Stop, titleType: String, titleKey: String): WriteStatus {
        val validation = validateTitlePath(titleType, titleKey)
        if (validation != WriteStatus.SUCCESS) {
            return validation
        }

        val existing = stop.getCustomTitle(titleType)
        if (existing == null || !existing.containsKey(titleKey)) {
            return WriteStatus.NOT_FOUND
        }
        val updated: MutableMap<String, String> = HashMap(existing)
        updated.remove(titleKey)
        if (updated.isEmpty()) {
            stop.removeCustomTitle(titleType)
        } else {
            stop.setCustomTitle(titleType, updated)
        }
        stopManager.saveConfig()
        return WriteStatus.SUCCESS
    }

    fun renameStop(id: String, name: String): WriteStatus =
        if (stopManager.setStopName(id, name)) WriteStatus.SUCCESS else WriteStatus.FAILED

    fun addAdmin(stop: Stop, adminId: UUID?): WriteStatus {
        if (adminId == null) {
            return WriteStatus.FAILED
        }
        if (stop.admins.contains(adminId)) {
            return WriteStatus.EXISTS
        }
        return if (stopManager.addStopAdmin(stop.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun removeAdmin(stop: Stop, adminId: UUID?): WriteStatus {
        if (adminId == null) {
            return WriteStatus.FAILED
        }
        return if (stopManager.removeStopAdmin(stop.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun setOwner(stop: Stop, ownerId: UUID?): WriteStatus {
        if (ownerId == null) {
            return WriteStatus.FAILED
        }
        return if (stopManager.setStopOwner(stop.id, ownerId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun updateLineLink(action: String, stopId: String, lineId: String): WriteStatus {
        if ("allow".equals(action, ignoreCase = true)) {
            return if (stopManager.allowLineLink(stopId, lineId)) WriteStatus.SUCCESS else WriteStatus.EXISTS
        }
        if ("deny".equals(action, ignoreCase = true)) {
            return if (stopManager.denyLineLink(stopId, lineId)) WriteStatus.SUCCESS else WriteStatus.NOT_FOUND
        }
        return WriteStatus.INVALID_ACTION
    }

    fun isValidId(id: String?): Boolean =
        id != null &&
            id.isNotBlank() &&
            id.length <= MAX_ID_LENGTH &&
            ID_PATTERN.matcher(id).matches()

    private fun validateTitlePath(titleType: String, titleKey: String): WriteStatus {
        if (!TITLE_TYPES.contains(titleType)) {
            return WriteStatus.INVALID_TITLE_TYPE
        }
        if (!TITLE_KEYS.contains(titleKey)) {
            return WriteStatus.INVALID_TITLE_KEY
        }
        return WriteStatus.SUCCESS
    }

    private fun isRail(material: Material): Boolean =
        material == Material.RAIL ||
            material == Material.POWERED_RAIL ||
            material == Material.DETECTOR_RAIL ||
            material == Material.ACTIVATOR_RAIL

    companion object {
        private const val MAX_ID_LENGTH = 64
        private val ID_PATTERN: Pattern = Pattern.compile("[A-Za-z0-9_-]+")

        @JvmField
        val TITLE_TYPES: Set<String> = setOf("stop_continuous", "arrive_stop", "terminal_stop", "departure", "waiting")

        @JvmField
        val TITLE_KEYS: Set<String> = setOf("title", "subtitle", "actionbar")
    }
}
