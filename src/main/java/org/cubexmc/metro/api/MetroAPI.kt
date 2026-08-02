package org.cubexmc.metro.api

import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.config.ConfigFacade
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.LineStatus
import org.cubexmc.metro.model.Portal
import org.cubexmc.metro.model.PriceRule
import org.cubexmc.metro.model.RoutePoint
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.LineStatusService
import org.cubexmc.metro.service.PortalCommandService
import org.cubexmc.metro.service.PriceService
import org.cubexmc.metro.service.TicketService
import org.cubexmc.metro.util.OwnershipUtil
import org.cubexmc.metro.util.VersionUtil
import java.util.Collections
import java.util.UUID
import kotlin.math.max

/**
 * Public API for other plugins to integrate with Metro.
 * Access via [MetroAPI.getInstance].
 *
 * Read queries return live model objects for convenience; snapshot queries
 * return immutable API records. Mutations route through managers/services to
 * preserve save, refresh, event, and permission consistency.
 *
 * The raw managers ([getLineManager], [getStopManager], [getPortalManager],
 * [getPlugin]) are exposed for advanced use but are not the recommended
 * integration surface.
 *
 * @since 1.1.6
 */
class MetroAPI private constructor(private val plugin: Metro) {
    enum class PortalWriteStatus {
        SUCCESS,
        NOT_FOUND,
        EXISTS,
        INVALID_ID,
        INVALID_LOCATION,
        FAILED,
    }

    enum class OwnershipWriteStatus {
        SUCCESS,
        NOT_FOUND,
        EXISTS,
        NOT_ADMIN,
        INVALID_PLAYER,
        OWNER_PROTECTED,
        FAILED,
    }

    data class PortalWriteResult(
        private val status: PortalWriteStatus,
        private val portal: Portal?,
        private val location: Location?,
    ) {
        fun status(): PortalWriteStatus = status

        fun portal(): Portal? = portal

        fun location(): Location? = location
    }

    data class LocationSnapshot(
        private val worldName: String,
        private val x: Double,
        private val y: Double,
        private val z: Double,
        private val yaw: Float,
    ) {
        fun worldName(): String = worldName

        fun x(): Double = x

        fun y(): Double = y

        fun z(): Double = z

        fun yaw(): Float = yaw

        companion object {
            @JvmStatic
            fun from(location: Location?): LocationSnapshot? {
                val world = location?.world ?: return null
                return LocationSnapshot(
                    world.name,
                    location.x,
                    location.y,
                    location.z,
                    location.yaw,
                )
            }
        }
    }

    data class TimeDiscountSnapshot(
        private val startTick: Int,
        private val endTick: Int,
        private val discountMultiplier: Double,
    ) {
        fun startTick(): Int = startTick

        fun endTick(): Int = endTick

        fun discountMultiplier(): Double = discountMultiplier

        companion object {
            @JvmStatic
            fun from(discount: PriceRule.TimeDiscount): TimeDiscountSnapshot =
                TimeDiscountSnapshot(
                    discount.getStartTick(),
                    discount.getEndTick(),
                    discount.getDiscountMultiplier(),
                )
        }
    }

    data class PriceRuleSnapshot(
        private val mode: PriceRule.PricingMode,
        private val basePrice: Double,
        private val perBlockRate: Double,
        private val perIntervalRate: Double,
        private val maxPrice: Double,
        private val timeDiscounts: List<TimeDiscountSnapshot>,
    ) {
        fun mode(): PriceRule.PricingMode = mode

        fun basePrice(): Double = basePrice

        fun perBlockRate(): Double = perBlockRate

        fun perIntervalRate(): Double = perIntervalRate

        fun maxPrice(): Double = maxPrice

        fun timeDiscounts(): List<TimeDiscountSnapshot> = timeDiscounts

        companion object {
            @JvmStatic
            fun from(rule: PriceRule?): PriceRuleSnapshot? {
                if (rule == null) {
                    return null
                }
                return PriceRuleSnapshot(
                    rule.getMode(),
                    rule.getBasePrice(),
                    rule.getPerBlockRate(),
                    rule.getPerIntervalRate(),
                    rule.getMaxPrice(),
                    immutableList(rule.getTimeDiscounts().map { discount -> TimeDiscountSnapshot.from(discount) }),
                )
            }
        }
    }

    data class LineSnapshot(
        private val id: String,
        private val name: String,
        private val orderedStopIds: List<String>,
        private val portalIds: List<String>,
        private val routePoints: List<RoutePoint>,
        private val color: String?,
        private val terminusName: String?,
        private val maxSpeed: Double,
        private val ticketPrice: Double,
        private val railProtected: Boolean,
        private val routeRecordedAtEpochMillis: Long?,
        private val routeRecordedBy: UUID?,
        private val routeRecordedCartId: UUID?,
        private val owner: UUID?,
        private val admins: Set<UUID>,
        private val worldName: String?,
        private val priceRule: PriceRuleSnapshot?,
        private val lineStatus: LineStatus,
        private val alternativeRouteIds: List<String>,
        private val suspensionMessage: String?,
    ) {
        fun id(): String = id

        fun name(): String = name

        fun orderedStopIds(): List<String> = orderedStopIds

        fun portalIds(): List<String> = portalIds

        fun routePoints(): List<RoutePoint> = routePoints

        fun color(): String? = color

        fun terminusName(): String? = terminusName

        fun maxSpeed(): Double = maxSpeed

        fun ticketPrice(): Double = ticketPrice

        fun railProtected(): Boolean = railProtected

        fun routeRecordedAtEpochMillis(): Long? = routeRecordedAtEpochMillis

        fun routeRecordedBy(): UUID? = routeRecordedBy

        fun routeRecordedCartId(): UUID? = routeRecordedCartId

        fun owner(): UUID? = owner

        fun admins(): Set<UUID> = admins

        fun worldName(): String? = worldName

        fun priceRule(): PriceRuleSnapshot? = priceRule

        fun lineStatus(): LineStatus = lineStatus

        fun alternativeRouteIds(): List<String> = alternativeRouteIds

        fun suspensionMessage(): String? = suspensionMessage

        companion object {
            @JvmStatic
            fun from(line: Line?): LineSnapshot? {
                if (line == null) {
                    return null
                }
                return LineSnapshot(
                    line.id,
                    line.name,
                    immutableList(line.orderedStopIds),
                    immutableList(line.portalIds),
                    immutableList(line.routePoints),
                    line.color,
                    line.terminusName,
                    line.getMaxSpeed() ?: -1.0,
                    line.ticketPrice,
                    line.isRailProtected,
                    line.routeRecordedAtEpochMillis,
                    line.routeRecordedBy,
                    line.routeRecordedCartId,
                    line.owner,
                    immutableSet(line.admins),
                    line.worldName,
                    PriceRuleSnapshot.from(line.priceRule),
                    line.getLineStatus(),
                    immutableList(line.alternativeRouteIds),
                    line.suspensionMessage,
                )
            }
        }
    }

    data class StopSnapshot(
        private val id: String,
        private val name: String,
        private val corner1: LocationSnapshot?,
        private val corner2: LocationSnapshot?,
        private val stopPoint: LocationSnapshot?,
        private val launchYaw: Float,
        private val transferableLines: List<String>,
        private val owner: UUID?,
        private val admins: Set<UUID>,
        private val linkedLineIds: Set<String>,
        private val worldName: String?,
    ) {
        fun id(): String = id

        fun name(): String = name

        fun corner1(): LocationSnapshot? = corner1

        fun corner2(): LocationSnapshot? = corner2

        fun stopPoint(): LocationSnapshot? = stopPoint

        fun launchYaw(): Float = launchYaw

        fun transferableLines(): List<String> = transferableLines

        fun owner(): UUID? = owner

        fun admins(): Set<UUID> = admins

        fun linkedLineIds(): Set<String> = linkedLineIds

        fun worldName(): String? = worldName

        companion object {
            @JvmStatic
            fun from(stop: Stop?): StopSnapshot? {
                if (stop == null) {
                    return null
                }
                return StopSnapshot(
                    stop.id,
                    stop.name,
                    LocationSnapshot.from(stop.corner1),
                    LocationSnapshot.from(stop.corner2),
                    LocationSnapshot.from(stop.stopPointLocation),
                    stop.launchYaw,
                    immutableList(stop.transferableLines),
                    stop.owner,
                    immutableSet(stop.admins),
                    immutableSet(stop.linkedLineIds),
                    stop.worldName,
                )
            }
        }
    }

    data class PortalSnapshot(
        private val id: String,
        private val worldName: String?,
        private val x: Int,
        private val y: Int,
        private val z: Int,
        private val destinationWorldName: String?,
        private val destinationX: Double,
        private val destinationY: Double,
        private val destinationZ: Double,
        private val destinationYaw: Float,
        private val owner: UUID?,
        private val admins: Set<UUID>,
    ) {
        fun id(): String = id

        fun worldName(): String? = worldName

        fun x(): Int = x

        fun y(): Int = y

        fun z(): Int = z

        fun destinationWorldName(): String? = destinationWorldName

        fun destinationX(): Double = destinationX

        fun destinationY(): Double = destinationY

        fun destinationZ(): Double = destinationZ

        fun destinationYaw(): Float = destinationYaw

        fun owner(): UUID? = owner

        fun admins(): Set<UUID> = admins

        companion object {
            @JvmStatic
            fun from(portal: Portal?): PortalSnapshot? {
                if (portal == null) {
                    return null
                }
                return PortalSnapshot(
                    portal.id,
                    portal.worldName,
                    portal.x,
                    portal.y,
                    portal.z,
                    portal.destWorldName,
                    portal.destX,
                    portal.destY,
                    portal.destZ,
                    portal.destYaw,
                    portal.owner,
                    immutableSet(portal.admins),
                )
            }
        }
    }

    fun isEconomyEnabled(): Boolean = config().isEconomyEnabled()

    fun isVaultEconomyAvailable(): Boolean {
        val vaultIntegration = plugin.vaultIntegration
        return vaultIntegration != null && vaultIntegration.isEnabled
    }

    fun isPortalsEnabled(): Boolean = config().isPortalsEnabled()

    fun getPortalTriggerBlock(): String = config().getPortalTriggerBlock()

    fun getDefaultCartSpeed(): Double = config().getCartSpeed()

    fun getCartDepartureDelay(): Long = config().getCartDepartureDelay()

    fun getPortalTeleportDelay(): Int = config().getPortalTeleportDelay()

    fun isPassengerRailBreakProtectionEnabled(): Boolean = config().isSafeModePassengerRailBreakProtection()

    fun isFoliaRuntime(): Boolean = VersionUtil.isFolia()

    fun getLine(lineId: String?): Line? = plugin.lineManager.getLine(lineId)

    fun getAllLines(): List<Line> = plugin.lineManager.getAllLines()

    fun getLinesForStop(stopId: String?): List<Line> =
        if (stopId != null) plugin.lineManager.getLinesForStop(stopId) else emptyList()

    fun getLineSnapshot(lineId: String?): LineSnapshot? = LineSnapshot.from(getLine(lineId))

    fun getLineSnapshots(): List<LineSnapshot> = getAllLines().mapNotNull(LineSnapshot::from)

    fun getStop(stopId: String?): Stop? = plugin.stopManager.getStop(stopId)

    fun getAllStops(): List<Stop> = plugin.stopManager.getAllStops()

    fun getStopSnapshot(stopId: String?): StopSnapshot? = StopSnapshot.from(getStop(stopId))

    fun getStopSnapshots(): List<StopSnapshot> = getAllStops().mapNotNull(StopSnapshot::from)

    fun getPortal(portalId: String?): Portal? {
        val portalManager = plugin.portalManager
        return portalManager?.getPortal(portalId)
    }

    fun getAllPortals(): List<Portal> {
        val portalManager = plugin.portalManager
        return portalManager?.getAllPortals() ?: emptyList()
    }

    fun getPortalAt(location: Location?): Portal? {
        val portalManager = plugin.portalManager
        return portalManager?.getPortalAt(location)
    }

    fun getPortalSnapshot(portalId: String?): PortalSnapshot? = PortalSnapshot.from(getPortal(portalId))

    fun getPortalSnapshots(): List<PortalSnapshot> = getAllPortals().mapNotNull(PortalSnapshot::from)

    fun createPortal(portalId: String?, entrance: Location?, ownerId: UUID?): PortalWriteResult {
        val service = portalService() ?: return PortalWriteResult(PortalWriteStatus.FAILED, null, entrance)
        val result = service.createPortal(portalId, entrance, null, ownerId)
        return PortalWriteResult(toPortalWriteStatus(result.status()), result.portal(), result.location())
    }

    fun setPortalDestination(portalId: String?, destination: Location?): PortalWriteStatus {
        val service = portalService() ?: return PortalWriteStatus.FAILED
        if (portalId == null) {
            return PortalWriteStatus.FAILED
        }
        return toPortalWriteStatus(service.setDestination(portalId, destination).status())
    }

    fun deletePortal(portalId: String?): PortalWriteStatus {
        val service = portalService() ?: return PortalWriteStatus.FAILED
        if (portalId == null) {
            return PortalWriteStatus.FAILED
        }
        return toPortalWriteStatus(service.deletePortal(portalId))
    }

    fun getLineStatus(lineId: String?): LineStatus {
        val line = getLine(lineId)
        return line?.getLineStatus() ?: LineStatus.NORMAL
    }

    fun setLineStatus(lineId: String?, status: LineStatus?): Boolean {
        val line = getLine(lineId) ?: return false
        val statusService: LineStatusService = plugin.lineStatusService ?: return false
        return status != null && statusService.setStatus(line, status)
    }

    fun isLineSuspended(lineId: String?): Boolean = getLineStatus(lineId) == LineStatus.SUSPENDED

    fun isLineMaintenance(lineId: String?): Boolean = getLineStatus(lineId) == LineStatus.MAINTENANCE

    fun setSuspensionMessage(lineId: String?, message: String?) {
        val line = getLine(lineId)
        if (line != null) {
            line.suspensionMessage = message
            plugin.lineManager.saveConfig()
        }
    }

    fun getPriceRule(lineId: String?): PriceRule? {
        val line = getLine(lineId)
        return line?.priceRule
    }

    fun setPriceRule(lineId: String?, rule: PriceRule?) {
        val line = getLine(lineId)
        if (line != null) {
            line.priceRule = rule
            plugin.lineManager.saveConfig()
        }
    }

    fun calculatePrice(
        lineId: String?,
        entryStopId: String?,
        exitStopId: String?,
        distanceBlocks: Double,
        intervals: Int,
    ): Double {
        val line = getLine(lineId)
        val entryStop = getStop(entryStopId)
        val exitStop = getStop(exitStopId)
        if (line == null || entryStop == null || exitStop == null) {
            return 0.0
        }

        val priceService: PriceService = plugin.priceService
            ?: return max(0.0, line.ticketPrice)

        return priceService.calculatePrice(
            line,
            entryStop,
            exitStop,
            distanceBlocks,
            intervals,
            entryStop.stopPointLocation?.world,
        )
    }

    fun getEstimatedPrice(lineId: String?): Double {
        val line = getLine(lineId) ?: return 0.0
        val priceService: PriceService = plugin.priceService
            ?: return max(0.0, line.ticketPrice)
        return priceService.getEstimatedPrice(line)
    }

    fun getPriceDescription(lineId: String?): String {
        val line = getLine(lineId) ?: return "Free"
        val priceService: PriceService = plugin.priceService
            ?: return max(0.0, line.ticketPrice).toString()
        return priceService.getPriceDescription(line)
    }

    fun checkCanBoard(player: Player?, lineId: String?): TicketService.TicketCheck {
        val line = getLine(lineId)
        if (line == null || player == null) {
            return TicketService.TicketCheck(
                TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS,
                0.0,
                "0",
            )
        }
        return plugin.ticketService.checkCanBoard(player, line)
    }

    fun getLineOwner(lineId: String?): UUID? = getLine(lineId)?.owner

    fun getStopOwner(stopId: String?): UUID? = getStop(stopId)?.owner

    fun getPortalOwner(portalId: String?): UUID? = getPortal(portalId)?.owner

    fun getLineAdmins(lineId: String?): Set<UUID> {
        val line = getLine(lineId)
        return if (line != null) immutableSet(line.admins) else emptySet()
    }

    fun getStopAdmins(stopId: String?): Set<UUID> {
        val stop = getStop(stopId)
        return if (stop != null) immutableSet(stop.admins) else emptySet()
    }

    fun getPortalAdmins(portalId: String?): Set<UUID> {
        val portal = getPortal(portalId)
        return if (portal != null) immutableSet(portal.admins) else emptySet()
    }

    fun isLineServerOwned(lineId: String?): Boolean = OwnershipUtil.isServerOwned(getLine(lineId))

    fun isStopServerOwned(stopId: String?): Boolean = OwnershipUtil.isServerOwned(getStop(stopId))

    fun isPortalServerOwned(portalId: String?): Boolean = OwnershipUtil.isServerOwned(getPortal(portalId))

    fun isLineAdmin(lineId: String?, playerId: UUID?): Boolean = OwnershipUtil.isLineAdmin(playerId, getLine(lineId))

    fun isStopAdmin(stopId: String?, playerId: UUID?): Boolean = OwnershipUtil.isStopAdmin(playerId, getStop(stopId))

    fun isPortalAdmin(portalId: String?, playerId: UUID?): Boolean =
        OwnershipUtil.isPortalAdmin(playerId, getPortal(portalId))

    fun canManageLine(sender: CommandSender?, lineId: String?): Boolean =
        sender != null && OwnershipUtil.canManageLine(sender, getLine(lineId))

    fun canManageStop(sender: CommandSender?, stopId: String?): Boolean =
        sender != null && OwnershipUtil.canManageStop(sender, getStop(stopId))

    fun canManagePortal(sender: CommandSender?, portalId: String?): Boolean =
        sender != null && OwnershipUtil.canManagePortal(sender, getPortal(portalId))

    fun canModifyLineStops(sender: CommandSender?, lineId: String?, stopId: String?): Boolean =
        sender != null && OwnershipUtil.canModifyLineStops(sender, getLine(lineId), getStop(stopId))

    fun canLinkStopToLine(sender: CommandSender?, lineId: String?, stopId: String?): Boolean =
        sender != null && OwnershipUtil.canLinkStopToLine(sender, getLine(lineId), getStop(stopId))

    fun setLineOwner(lineId: String?, ownerId: UUID?): OwnershipWriteStatus {
        val line = getLine(lineId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        return if (plugin.lineManager.setLineOwner(line.id, ownerId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun addLineAdmin(lineId: String?, adminId: UUID?): OwnershipWriteStatus {
        val line = getLine(lineId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (line.admins.contains(adminId)) {
            return OwnershipWriteStatus.EXISTS
        }
        return if (plugin.lineManager.addLineAdmin(line.id, adminId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun removeLineAdmin(lineId: String?, adminId: UUID?): OwnershipWriteStatus {
        val line = getLine(lineId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (adminId == line.owner) {
            return OwnershipWriteStatus.OWNER_PROTECTED
        }
        if (!line.admins.contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN
        }
        return if (plugin.lineManager.removeLineAdmin(line.id, adminId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun setStopOwner(stopId: String?, ownerId: UUID?): OwnershipWriteStatus {
        val stop = getStop(stopId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        return if (plugin.stopManager.setStopOwner(stop.id, ownerId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun addStopAdmin(stopId: String?, adminId: UUID?): OwnershipWriteStatus {
        val stop = getStop(stopId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (stop.admins.contains(adminId)) {
            return OwnershipWriteStatus.EXISTS
        }
        return if (plugin.stopManager.addStopAdmin(stop.id, adminId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun removeStopAdmin(stopId: String?, adminId: UUID?): OwnershipWriteStatus {
        val stop = getStop(stopId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (adminId == stop.owner) {
            return OwnershipWriteStatus.OWNER_PROTECTED
        }
        if (!stop.admins.contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN
        }
        return if (plugin.stopManager.removeStopAdmin(stop.id, adminId)) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun setPortalOwner(portalId: String?, ownerId: UUID?): OwnershipWriteStatus {
        val portal = getPortal(portalId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        return if (plugin.portalManager?.setPortalOwner(portal.id, ownerId) == true) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun addPortalAdmin(portalId: String?, adminId: UUID?): OwnershipWriteStatus {
        val portal = getPortal(portalId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (portal.admins.contains(adminId)) {
            return OwnershipWriteStatus.EXISTS
        }
        return if (plugin.portalManager?.addPortalAdmin(portal.id, adminId) == true) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun removePortalAdmin(portalId: String?, adminId: UUID?): OwnershipWriteStatus {
        val portal = getPortal(portalId) ?: return OwnershipWriteStatus.NOT_FOUND
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER
        }
        if (adminId == portal.owner) {
            return OwnershipWriteStatus.OWNER_PROTECTED
        }
        if (!portal.admins.contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN
        }
        return if (plugin.portalManager?.removePortalAdmin(portal.id, adminId) == true) {
            OwnershipWriteStatus.SUCCESS
        } else {
            OwnershipWriteStatus.FAILED
        }
    }

    fun getLineManager(): LineManager = plugin.lineManager

    fun getStopManager(): StopManager = plugin.stopManager

    fun getPortalManager(): PortalManager? = plugin.portalManager

    fun getPlugin(): Metro = plugin

    private fun config(): ConfigFacade = plugin.configFacade

    private fun portalService(): PortalCommandService? {
        val portalManager = plugin.portalManager
        return if (portalManager != null) PortalCommandService(portalManager) else null
    }

    private fun toPortalWriteStatus(status: PortalCommandService.WriteStatus?): PortalWriteStatus =
        when (status) {
            PortalCommandService.WriteStatus.SUCCESS -> PortalWriteStatus.SUCCESS
            PortalCommandService.WriteStatus.INVALID_ID -> PortalWriteStatus.INVALID_ID
            PortalCommandService.WriteStatus.INVALID_LOCATION -> PortalWriteStatus.INVALID_LOCATION
            PortalCommandService.WriteStatus.EXISTS -> PortalWriteStatus.EXISTS
            PortalCommandService.WriteStatus.NOT_FOUND -> PortalWriteStatus.NOT_FOUND
            PortalCommandService.WriteStatus.FAILED, null -> PortalWriteStatus.FAILED
        }

    companion object {
        private var instance: MetroAPI? = null

        /**
         * @param plugin the Metro plugin instance
         * @since 1.1.6
         */
        @JvmStatic
        fun initialize(plugin: Metro?) {
            if (instance == null && plugin != null) {
                instance = MetroAPI(plugin)
            }
        }

        @JvmStatic
        fun resetForTests() {
            instance = null
        }

        @JvmStatic
        fun getInstance(): MetroAPI? = instance
    }
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
