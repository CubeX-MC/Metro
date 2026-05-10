package org.cubexmc.metro.service;

import org.bukkit.World;
import org.cubexmc.metro.model.PriceRule;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import java.util.List;

/**
 * Calculates prices based on line pricing rules, distance traveled, and time discounts.
 * Replaces the simple getTicketPrice() logic with multi-mode pricing.
 */
public class PriceService {

    /**
     * Calculate the estimated price to board a line from a given stop.
     * Uses the minimum possible fare (1 stop interval or 0 blocks extra).
     *
     * @param line the metro line
     * @return the estimated price
     */
    public double getEstimatedPrice(Line line) {
        if (line == null) return 0.0;

        PriceRule rule = line.getPriceRule();
        if (rule == null) {
            // Fallback to legacy ticket price
            return Math.max(0.0, line.getTicketPrice());
        }

        // For display: estimate minimum price
        return rule.calculatePrice(0, 1, 6000); // assume 1 interval, daytime
    }

    /**
     * Calculate the actual price for a completed ride segment.
     *
     * @param line     the line being ridden
     * @param entryStop the stop where the player boarded
     * @param exitStop  the stop where the player exited
     * @param distanceTraveledBlocks actual blocks traveled (from train tracking)
     * @param intervals  number of stop intervals passed (can be 0 if unknown)
     * @param world     the world (for game time)
     * @return the calculated fare
     */
    public double calculatePrice(Line line, Stop entryStop, Stop exitStop,
                                 double distanceTraveledBlocks, int intervals, World world) {
        if (line == null) return 0.0;

        PriceRule rule = line.getPriceRule();
        if (rule == null) {
            // Fallback to legacy flat ticket price
            return Math.max(0.0, line.getTicketPrice());
        }

        // Count intervals if not provided
        if (intervals <= 0 && entryStop != null && exitStop != null) {
            intervals = countStopIntervals(line, entryStop.getId(), exitStop.getId());
        }
        if (intervals <= 0) intervals = 1;

        long gameTime = world != null ? world.getTime() : 6000;

        return rule.calculatePrice(distanceTraveledBlocks, intervals, gameTime);
    }

    /**
     * Calculate the fare for distance-based pricing when only distance is known.
     *
     * @param line     the metro line
     * @param distanceTraveledBlocks blocks traveled
     * @param world    the world (for game time)
     * @return calculated price
     */
    public double calculateDistancePrice(Line line, double distanceTraveledBlocks, World world) {
        if (line == null) return 0.0;

        PriceRule rule = line.getPriceRule();
        if (rule == null) {
            return Math.max(0.0, line.getTicketPrice());
        }

        long gameTime = world != null ? world.getTime() : 6000;
        return rule.calculatePrice(distanceTraveledBlocks, 0, gameTime);
    }

    /**
     * Count the number of stop intervals between two stops on a line.
     */
    public int countStopIntervals(Line line, String entryStopId, String exitStopId) {
        if (line == null || entryStopId == null || exitStopId == null) return 0;

        List<String> stopIds = line.getOrderedStopIds();
        int entryIndex = stopIds.indexOf(entryStopId);
        int exitIndex = stopIds.indexOf(exitStopId);

        if (entryIndex == -1 || exitIndex == -1) return 0;

        if (line.isCircular()) {
            // For circular lines, take the shorter path
            int forwardDist = (exitIndex - entryIndex + stopIds.size()) % stopIds.size();
            int backwardDist = (entryIndex - exitIndex + stopIds.size()) % stopIds.size();
            return Math.min(forwardDist, backwardDist);
        }

        // For normal lines, only forward direction counts
        if (exitIndex <= entryIndex) return 0;
        return exitIndex - entryIndex;
    }

    /**
     * Get a human-readable pricing description for a line.
     */
    public String getPriceDescription(Line line) {
        if (line == null) return "Free";

        PriceRule rule = line.getPriceRule();
        if (rule != null) {
            return rule.getDescription();
        }
        return String.valueOf(Math.max(0.0, line.getTicketPrice()));
    }

    /**
     * Check if the line has a time discount active right now.
     */
    public boolean hasActiveDiscount(Line line, World world) {
        if (line == null || world == null) return false;
        PriceRule rule = line.getPriceRule();
        if (rule == null) return false;
        return rule.getActiveDiscountMultiplier(world.getTime()) < 1.0;
    }
}
