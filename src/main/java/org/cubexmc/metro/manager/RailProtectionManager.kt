package org.cubexmc.metro.manager

import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.cubexmc.core.hasFlag
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.RoutePoint
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.OwnershipUtil
import org.cubexmc.metro.util.SchedulerUtil
import org.cubexmc.metro.util.VersionUtil

/**
 * Builds an in-memory rail protection index from recorded route points.
 */
class RailProtectionManager(private val plugin: Metro) : Listener {
    private val lineToBlocks: MutableMap<String, MutableSet<BlockKey>> = HashMap()
    private val blockToLines: MutableMap<BlockKey, MutableSet<String>> = HashMap()
    private val lineToStats: MutableMap<String, ProtectionIndexStats> = HashMap()
    private val lineRevisions: MutableMap<String, Long> = HashMap()
    private val folia = VersionUtil.isFolia()
    private var nextRevision = 0L

    fun rebuildAll() {
        val desiredLines = LinkedHashMap<String, ProtectedLineSnapshot?>()
        for (line in plugin.lineManager.getAllLines()) {
            desiredLines[line.id] = snapshotProtectedLine(line)
        }

        if (!folia) {
            val results = desiredLines.values.filterNotNull().associate { snapshot ->
                snapshot.id to scanLineSynchronously(snapshot)
            }
            synchronized(this) {
                clearIndexLocked()
                for (result in results.values) {
                    addLineResultLocked(result)
                }
            }
            return
        }

        val expectedRevisions = LinkedHashMap<String, Long>()
        synchronized(this) {
            val knownLineIds = HashSet(lineRevisions.keys)
            knownLineIds.addAll(lineToBlocks.keys)
            knownLineIds.addAll(lineToStats.keys)
            for (lineId in knownLineIds) {
                if (!desiredLines.containsKey(lineId)) {
                    issueRevisionLocked(lineId)
                    removeLineLocked(lineId)
                }
            }
            for ((lineId, snapshot) in desiredLines) {
                val revision = issueRevisionLocked(lineId)
                if (snapshot == null) {
                    removeLineLocked(lineId)
                } else {
                    expectedRevisions[lineId] = revision
                }
            }
        }

        if (expectedRevisions.isEmpty()) {
            return
        }
        rebuildOnFolia(desiredLines.values.filterNotNull(), expectedRevisions)
    }

    fun rebuildLine(lineId: String) {
        val snapshot = snapshotProtectedLine(plugin.lineManager.getLine(lineId))
        if (!folia) {
            val result = snapshot?.let(::scanLineSynchronously)
            synchronized(this) {
                replaceLineLocked(lineId, result)
            }
            return
        }

        val revision = synchronized(this) {
            val issuedRevision = issueRevisionLocked(lineId)
            if (snapshot == null) {
                removeLineLocked(lineId)
            }
            issuedRevision
        }
        if (snapshot == null) {
            return
        }
        rebuildOnFolia(listOf(snapshot), mapOf(lineId to revision))
    }

    @Synchronized
    fun getProtectedBlockCount(lineId: String): Int = lineToBlocks[lineId]?.size ?: 0

    @Synchronized
    fun getProtectionIndexStats(lineId: String): ProtectionIndexStats =
        lineToStats[lineId] ?: ProtectionIndexStats.empty()

    private fun rebuildOnFolia(
        snapshots: List<ProtectedLineSnapshot>,
        expectedRevisions: Map<String, Long>,
    ) {
        val rebuildFuture = try {
            scanLinesOnRegions(snapshots)
        } catch (throwable: Throwable) {
            CompletableFuture.failedFuture(throwable)
        }
        rebuildFuture.whenComplete { results, failure ->
            if (failure != null) {
                plugin.logger.log(
                    Level.SEVERE,
                    "Failed to rebuild the Folia rail protection index; the existing index remains unchanged.",
                    unwrapCompletionFailure(failure),
                )
                return@whenComplete
            }
            synchronized(this) {
                for ((lineId, expectedRevision) in expectedRevisions) {
                    if (lineRevisions[lineId] != expectedRevision) {
                        continue
                    }
                    replaceLineLocked(lineId, results[lineId])
                }
            }
        }
    }

    private fun scanLinesOnRegions(
        snapshots: List<ProtectedLineSnapshot>,
    ): CompletableFuture<Map<String, LineIndexResult>> {
        val chunkScans = LinkedHashMap<ChunkKey, ChunkScan>()
        val worldCache = HashMap<String, World?>()
        val plans = snapshots.map { snapshot ->
            prepareRegionScan(snapshot, chunkScans) { worldName ->
                if (!worldCache.containsKey(worldName)) {
                    worldCache[worldName] = Bukkit.getWorld(worldName)
                }
                worldCache[worldName]
            }
        }
        val discoveredRails = ConcurrentHashMap.newKeySet<BlockKey>()
        val scanFutures = chunkScans.values.map { scan -> scheduleChunkScan(scan, discoveredRails) }
        return CompletableFuture.allOf(*scanFutures.toTypedArray()).thenApply {
            plans.associate { plan -> plan.snapshot.id to finishRegionScan(plan, discoveredRails) }
        }
    }

    private fun prepareRegionScan(
        snapshot: ProtectedLineSnapshot,
        chunkScans: MutableMap<ChunkKey, ChunkScan>,
        worldLookup: (String) -> World?,
    ): LineScanPlan {
        val builder = ProtectionIndexBuilder(snapshot.worldName)
        val pendingSamples = ArrayList<PendingSample>()
        for (point in sampleRoutePoints(snapshot.routePoints)) {
            builder.sample(point)
            if (builder.isWorldMismatch(point)) {
                builder.skippedWorldMismatch()
                continue
            }
            val world = worldLookup(point.worldName())
            if (world == null) {
                builder.skippedMissingWorld()
                continue
            }
            val candidates = candidateBlocks(world.name, point)
            pendingSamples.add(PendingSample(point, candidates))
            for (candidate in candidates) {
                val chunkKey = ChunkKey(candidate.worldName, candidate.x shr 4, candidate.z shr 4)
                chunkScans.computeIfAbsent(chunkKey) { ChunkScan(world) }.blocks.add(candidate)
            }
        }
        return LineScanPlan(snapshot, builder, pendingSamples)
    }

    private fun scheduleChunkScan(
        scan: ChunkScan,
        discoveredRails: MutableSet<BlockKey>,
    ): CompletableFuture<Void> {
        val completion = CompletableFuture<Void>()
        val anchor = scan.blocks.first()
        val location = Location(scan.world, anchor.x + 0.5, anchor.y.toDouble(), anchor.z + 0.5)
        try {
            SchedulerUtil.regionRun(
                plugin,
                location,
                Runnable {
                    try {
                        for (blockKey in scan.blocks) {
                            val material = scan.world.getBlockAt(blockKey.x, blockKey.y, blockKey.z).type
                            if (RAIL_MATERIALS.contains(material)) {
                                discoveredRails.add(blockKey)
                            }
                        }
                        completion.complete(null)
                    } catch (throwable: Throwable) {
                        completion.completeExceptionally(throwable)
                    }
                },
                0L,
                -1L,
            )
        } catch (throwable: Throwable) {
            completion.completeExceptionally(throwable)
        }
        return completion
    }

    private fun finishRegionScan(plan: LineScanPlan, discoveredRails: Set<BlockKey>): LineIndexResult {
        val blocks = HashSet<BlockKey>()
        for (sample in plan.pendingSamples) {
            val nearestRail = findNearestRail(sample.point, sample.candidates, discoveredRails)
            if (nearestRail == null) {
                plan.builder.skippedNoRail()
            } else {
                blocks.add(nearestRail)
            }
        }
        return LineIndexResult(plan.snapshot.id, blocks, plan.builder.build(blocks.size))
    }

    private fun scanLineSynchronously(snapshot: ProtectedLineSnapshot): LineIndexResult {
        val builder = ProtectionIndexBuilder(snapshot.worldName)
        val blocks = HashSet<BlockKey>()
        for (point in sampleRoutePoints(snapshot.routePoints)) {
            builder.sample(point)
            if (builder.isWorldMismatch(point)) {
                builder.skippedWorldMismatch()
                continue
            }
            val world = Bukkit.getWorld(point.worldName())
            if (world == null) {
                builder.skippedMissingWorld()
                continue
            }
            val nearestRail = findNearestRail(world, point)
            if (nearestRail == null) {
                builder.skippedNoRail()
            } else {
                blocks.add(nearestRail)
            }
        }
        return LineIndexResult(snapshot.id, blocks, builder.build(blocks.size))
    }

    private fun sampleRoutePoints(routePoints: List<RoutePoint>): List<RoutePoint> {
        if (routePoints.isEmpty()) {
            return emptyList()
        }
        val samples = ArrayList<RoutePoint>()
        for (index in routePoints.indices) {
            samples.add(routePoints[index])
            if (index + 1 < routePoints.size) {
                interpolateSegment(samples, routePoints[index], routePoints[index + 1])
            }
        }
        return samples
    }

    private fun interpolateSegment(samples: MutableList<RoutePoint>, from: RoutePoint, to: RoutePoint) {
        if (from.worldName() != to.worldName()) {
            return
        }
        val dx = to.x() - from.x()
        val dy = to.y() - from.y()
        val dz = to.z() - from.z()
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val sampleCount = kotlin.math.max(1, kotlin.math.ceil(distance / INTERPOLATION_STEP).toInt())
        for (index in 1 until sampleCount) {
            val ratio = index.toDouble() / sampleCount
            samples.add(
                RoutePoint(
                    from.worldName(),
                    from.x() + dx * ratio,
                    from.y() + dy * ratio,
                    from.z() + dz * ratio,
                ),
            )
        }
    }

    private fun candidateBlocks(worldName: String, point: RoutePoint): List<BlockKey> {
        val baseX = kotlin.math.floor(point.x()).toInt()
        val baseY = kotlin.math.floor(point.y()).toInt()
        val baseZ = kotlin.math.floor(point.z()).toInt()
        val candidates = ArrayList<BlockKey>(18)
        for (dy in intArrayOf(0, -1)) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    candidates.add(BlockKey(worldName, baseX + dx, baseY + dy, baseZ + dz))
                }
            }
        }
        return candidates
    }

    private fun findNearestRail(world: World, point: RoutePoint): BlockKey? {
        val candidates = candidateBlocks(world.name, point)
        var best: BlockKey? = null
        var bestDistance = Double.MAX_VALUE
        for (candidate in candidates) {
            val block = world.getBlockAt(candidate.x, candidate.y, candidate.z)
            if (!isRail(block)) {
                continue
            }
            val distance = distanceSquaredToBlockCenter(point, candidate.x, candidate.y, candidate.z)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    private fun findNearestRail(
        point: RoutePoint,
        candidates: List<BlockKey>,
        discoveredRails: Set<BlockKey>,
    ): BlockKey? {
        var best: BlockKey? = null
        var bestDistance = Double.MAX_VALUE
        for (candidate in candidates) {
            if (!discoveredRails.contains(candidate)) {
                continue
            }
            val distance = distanceSquaredToBlockCenter(point, candidate.x, candidate.y, candidate.z)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    private fun distanceSquaredToBlockCenter(point: RoutePoint, x: Int, y: Int, z: Int): Double {
        val dx = point.x() - (x + 0.5)
        val dy = point.y() - (y + 0.5)
        val dz = point.z() - (z + 0.5)
        return dx * dx + dy * dy + dz * dz
    }

    private fun snapshotProtectedLine(line: Line?): ProtectedLineSnapshot? {
        if (line == null || !line.isRailProtected) {
            return null
        }
        return ProtectedLineSnapshot(line.id, line.worldName, line.routePoints)
    }

    private fun issueRevisionLocked(lineId: String): Long {
        val revision = ++nextRevision
        lineRevisions[lineId] = revision
        return revision
    }

    private fun clearIndexLocked() {
        lineToBlocks.clear()
        blockToLines.clear()
        lineToStats.clear()
    }

    private fun replaceLineLocked(lineId: String, result: LineIndexResult?) {
        removeLineLocked(lineId)
        if (result != null) {
            addLineResultLocked(result)
        }
    }

    private fun addLineResultLocked(result: LineIndexResult) {
        lineToStats[result.lineId] = result.stats
        if (result.blocks.isEmpty()) {
            return
        }
        lineToBlocks[result.lineId] = result.blocks.toMutableSet()
        for (block in result.blocks) {
            blockToLines.computeIfAbsent(block) { HashSet() }.add(result.lineId)
        }
    }

    private fun removeLineLocked(lineId: String) {
        lineToStats.remove(lineId)
        val oldBlocks = lineToBlocks.remove(lineId) ?: return
        for (block in oldBlocks) {
            val lineIds = blockToLines[block] ?: continue
            lineIds.remove(lineId)
            if (lineIds.isEmpty()) {
                blockToLines.remove(block)
            }
        }
    }

    private fun unwrapCompletionFailure(failure: Throwable): Throwable = failure.cause ?: failure

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        if (isMetroPassengerBreakingRail(player, block)) {
            event.isCancelled = true
            player.sendMessage(plugin.languageManager.getMessage("protection.passenger_break_denied"))
            return
        }

        val protectedLineIds = getProtectedLines(block)
        if (protectedLineIds.isEmpty()) {
            return
        }
        if (canBreakProtectedRail(player, protectedLineIds)) {
            return
        }

        event.isCancelled = true
        player.sendMessage(plugin.languageManager.getMessage("protection.rail_break_denied"))
    }

    private fun isMetroPassengerBreakingRail(player: Player, block: Block): Boolean {
        if (!plugin.configFacade.isSafeModePassengerRailBreakProtection() || !isRail(block)) {
            return false
        }
        val minecart = player.vehicle as? Minecart ?: return false
        val key = MetroConstants.getMinecartKey() ?: return false
        return minecart.persistentDataContainer.hasFlag(key)
    }

    @Synchronized
    private fun getProtectedLines(block: Block): Set<String> {
        if (!isRail(block)) {
            return emptySet()
        }
        val lineIds = blockToLines[BlockKey.fromBlock(block)]
        return if (lineIds == null) emptySet() else HashSet(lineIds)
    }

    private fun canBreakProtectedRail(player: Player, protectedLineIds: Set<String>): Boolean {
        if (OwnershipUtil.hasAdminBypass(player)) {
            return true
        }
        for (lineId in protectedLineIds) {
            val line = plugin.lineManager.getLine(lineId)
            if (line != null && !OwnershipUtil.canManageLine(player, line)) {
                return false
            }
        }
        return true
    }

    private fun isRail(block: Block?): Boolean = block != null && RAIL_MATERIALS.contains(block.type)

    data class ProtectionIndexStats(
        private val sampledPoints: Int,
        private val indexedBlocks: Int,
        private val skippedWorldMismatch: Int,
        private val skippedMissingWorld: Int,
        private val skippedNoRail: Int,
    ) {
        fun sampledPoints(): Int = sampledPoints

        fun indexedBlocks(): Int = indexedBlocks

        fun skippedWorldMismatch(): Int = skippedWorldMismatch

        fun skippedMissingWorld(): Int = skippedMissingWorld

        fun skippedNoRail(): Int = skippedNoRail

        fun skippedTotal(): Int = skippedWorldMismatch + skippedMissingWorld + skippedNoRail

        fun hasWarnings(): Boolean = skippedTotal() > 0

        companion object {
            @JvmStatic
            fun empty(): ProtectionIndexStats = ProtectionIndexStats(0, 0, 0, 0, 0)
        }
    }

    private class ProtectionIndexBuilder(private val lineWorldName: String?) {
        private var sampledPoints = 0
        private var skippedWorldMismatch = 0
        private var skippedMissingWorld = 0
        private var skippedNoRail = 0

        fun sample(point: RoutePoint?) {
            if (point != null) {
                sampledPoints++
            }
        }

        fun isWorldMismatch(point: RoutePoint?): Boolean =
            point != null &&
                lineWorldName != null &&
                lineWorldName.isNotBlank() &&
                lineWorldName != point.worldName()

        fun skippedWorldMismatch() {
            skippedWorldMismatch++
        }

        fun skippedMissingWorld() {
            skippedMissingWorld++
        }

        fun skippedNoRail() {
            skippedNoRail++
        }

        fun build(indexedBlocks: Int): ProtectionIndexStats =
            ProtectionIndexStats(
                sampledPoints,
                indexedBlocks,
                skippedWorldMismatch,
                skippedMissingWorld,
                skippedNoRail,
            )
    }

    private data class ProtectedLineSnapshot(
        val id: String,
        val worldName: String?,
        val routePoints: List<RoutePoint>,
    )

    private data class LineIndexResult(
        val lineId: String,
        val blocks: Set<BlockKey>,
        val stats: ProtectionIndexStats,
    )

    private data class LineScanPlan(
        val snapshot: ProtectedLineSnapshot,
        val builder: ProtectionIndexBuilder,
        val pendingSamples: List<PendingSample>,
    )

    private data class PendingSample(
        val point: RoutePoint,
        val candidates: List<BlockKey>,
    )

    private data class ChunkKey(
        val worldName: String,
        val chunkX: Int,
        val chunkZ: Int,
    )

    private data class ChunkScan(
        val world: World,
        val blocks: MutableSet<BlockKey> = LinkedHashSet(),
    )

    private data class BlockKey(
        val worldName: String,
        val x: Int,
        val y: Int,
        val z: Int,
    ) {
        companion object {
            fun fromBlock(block: Block): BlockKey =
                BlockKey(block.world.name, block.x, block.y, block.z)
        }
    }

    companion object {
        private const val INTERPOLATION_STEP = 0.5
        private val RAIL_MATERIALS: Set<Material> = EnumSet.of(
            Material.RAIL,
            Material.POWERED_RAIL,
            Material.DETECTOR_RAIL,
            Material.ACTIVATOR_RAIL,
        )
    }
}
