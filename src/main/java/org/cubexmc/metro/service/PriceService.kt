package org.cubexmc.metro.service

import org.bukkit.World
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.PriceRule
import org.cubexmc.metro.model.Stop
import kotlin.math.max

/**
 * Calculates prices based on line pricing rules, distance traveled, and time discounts.
 */
class PriceService {
    fun getEstimatedPrice(line: Line?): Double {
        if (line == null) return 0.0
        val rule: PriceRule? = line.priceRule
        if (rule == null) {
            return max(0.0, line.ticketPrice)
        }
        return rule.calculatePrice(0.0, 1, 6000)
    }

    fun calculatePrice(
        line: Line?,
        entryStop: Stop?,
        exitStop: Stop?,
        distanceTraveledBlocks: Double,
        intervals: Int,
        world: World?,
    ): Double {
        if (line == null) return 0.0
        val rule: PriceRule? = line.priceRule
        if (rule == null) {
            return max(0.0, line.ticketPrice)
        }

        var resolvedIntervals = intervals
        if (resolvedIntervals <= 0 && entryStop != null && exitStop != null) {
            resolvedIntervals = countStopIntervals(line, entryStop.id, exitStop.id)
        }
        if (resolvedIntervals <= 0) resolvedIntervals = 1

        val gameTime = world?.time ?: 6000
        return rule.calculatePrice(distanceTraveledBlocks, resolvedIntervals, gameTime)
    }

    fun calculateDistancePrice(line: Line?, distanceTraveledBlocks: Double, world: World?): Double {
        if (line == null) return 0.0
        val rule: PriceRule? = line.priceRule
        if (rule == null) {
            return max(0.0, line.ticketPrice)
        }
        val gameTime = world?.time ?: 6000
        return rule.calculatePrice(distanceTraveledBlocks, 0, gameTime)
    }

    fun countStopIntervals(line: Line?, entryStopId: String?, exitStopId: String?): Int {
        if (line == null || entryStopId == null || exitStopId == null) return 0

        val stopIds = line.orderedStopIds
        val entryIndex = stopIds.indexOf(entryStopId)
        val exitIndex = stopIds.indexOf(exitStopId)

        if (entryIndex == -1 || exitIndex == -1) return 0

        if (line.isCircular) {
            // Circular lines still travel in their configured order. The final
            // stop duplicates the first one, so exclude it from the modulus.
            val distinctStopCount = stopIds.size - 1
            if (distinctStopCount <= 0) return 0
            return (exitIndex - entryIndex + distinctStopCount) % distinctStopCount
        }

        if (exitIndex <= entryIndex) return 0
        return exitIndex - entryIndex
    }

    fun getPriceDescription(line: Line?): String {
        if (line == null) return "Free"
        val rule: PriceRule? = line.priceRule
        if (rule != null) {
            return rule.getDescription()
        }
        return max(0.0, line.ticketPrice).toString()
    }

    fun hasActiveDiscount(line: Line?, world: World?): Boolean {
        if (line == null || world == null) return false
        val rule: PriceRule = line.priceRule ?: return false
        return rule.getActiveDiscountMultiplier(world.time) < 1.0
    }
}
