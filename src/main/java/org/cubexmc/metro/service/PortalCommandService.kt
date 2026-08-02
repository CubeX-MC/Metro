package org.cubexmc.metro.service

import java.util.UUID
import java.util.regex.Pattern
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.model.Portal

/**
 * Business operations used by portal commands.
 */
class PortalCommandService(portalManager: PortalManager?) {

    private val portalManagerRef: PortalManager? = portalManager
    private val portalManager: PortalManager
        get() = portalManagerRef ?: throw NullPointerException("portalManager")

    enum class WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_LOCATION,
        EXISTS,
        NOT_FOUND,
        FAILED,
    }

    data class PortalWriteResult(
        private val status: WriteStatus,
        private val portal: Portal?,
        private val location: Location?,
    ) {
        fun status(): WriteStatus = status

        fun portal(): Portal? = portal

        fun location(): Location? = location
    }

    data class ReloadResult(
        private val status: WriteStatus,
        private val portalCount: Int,
    ) {
        fun status(): WriteStatus = status

        fun portalCount(): Int = portalCount
    }

    fun createPortal(id: String?, fallbackLocation: Location?, targetBlock: Block?, ownerId: UUID?): PortalWriteResult {
        if (!isValidId(id)) {
            return PortalWriteResult(WriteStatus.INVALID_ID, null, null)
        }
        val portalId = id ?: return PortalWriteResult(WriteStatus.INVALID_ID, null, null)
        if (portalManager.getPortal(portalId) != null) {
            return PortalWriteResult(WriteStatus.EXISTS, null, null)
        }
        val entrance = resolveEntranceLocation(fallbackLocation, targetBlock)
        if (entrance == null || entrance.world == null) {
            return PortalWriteResult(WriteStatus.INVALID_LOCATION, null, entrance)
        }
        val portal = portalManager.createPortal(portalId, entrance, ownerId)
        return PortalWriteResult(WriteStatus.SUCCESS, portal, entrance)
    }

    fun setDestination(id: String, destination: Location?): PortalWriteResult {
        if (destination == null || destination.world == null) {
            return PortalWriteResult(WriteStatus.INVALID_LOCATION, null, destination)
        }
        if (!portalManager.setDestination(id, destination)) {
            return PortalWriteResult(WriteStatus.NOT_FOUND, null, destination)
        }
        return PortalWriteResult(WriteStatus.SUCCESS, portalManager.getPortal(id), destination)
    }

    fun deletePortal(id: String): WriteStatus =
        if (portalManager.deletePortal(id)) WriteStatus.SUCCESS else WriteStatus.NOT_FOUND

    fun addAdmin(portal: Portal?, adminId: UUID?): WriteStatus {
        if (portal == null || adminId == null) {
            return WriteStatus.FAILED
        }
        if (portal.admins.contains(adminId)) {
            return WriteStatus.EXISTS
        }
        return if (portalManager.addPortalAdmin(portal.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun removeAdmin(portal: Portal?, adminId: UUID?): WriteStatus {
        if (portal == null || adminId == null) {
            return WriteStatus.FAILED
        }
        return if (portalManager.removePortalAdmin(portal.id, adminId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun setOwner(portal: Portal?, ownerId: UUID?): WriteStatus {
        if (portal == null || ownerId == null) {
            return WriteStatus.FAILED
        }
        return if (portalManager.setPortalOwner(portal.id, ownerId)) WriteStatus.SUCCESS else WriteStatus.FAILED
    }

    fun listPortals(): List<Portal> = portalManager.getAllPortals()
        .sortedBy { portal -> portal.id }

    fun reloadPortals(migration: Runnable?): ReloadResult {
        migration?.run()
        portalManager.load()
        return ReloadResult(WriteStatus.SUCCESS, portalManager.getAllPortals().size)
    }

    fun isValidId(id: String?): Boolean =
        id != null &&
            id.isNotBlank() &&
            id.length <= MAX_ID_LENGTH &&
            ID_PATTERN.matcher(id).matches()

    private fun resolveEntranceLocation(fallbackLocation: Location?, targetBlock: Block?): Location? {
        if (targetBlock != null && isRail(targetBlock.type)) {
            return targetBlock.location
        }
        return fallbackLocation
    }

    private fun isRail(material: Material): Boolean =
        material == Material.RAIL ||
            material == Material.POWERED_RAIL ||
            material == Material.DETECTOR_RAIL ||
            material == Material.ACTIVATOR_RAIL

    companion object {
        private const val MAX_ID_LENGTH = 64
        private val ID_PATTERN: Pattern = Pattern.compile("[A-Za-z0-9_-]+")
    }
}
