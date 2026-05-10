package org.cubexmc.metro.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Price configuration for a metro line.
 * Supports multiple pricing modes:
 * - FLAT: Fixed price per ride (legacy mode, uses ticketPrice)
 * - DISTANCE: Price calculated per block traveled
 * - INTERVAL: Zoned/interval pricing (each segment between stops costs a configurable amount)
 *
 * Also supports time-based discounts.
 */
public class PriceRule {

    /** Pricing mode */
    public enum PricingMode {
        FLAT,
        DISTANCE,
        INTERVAL
    }

    private PricingMode mode = PricingMode.FLAT;
    private double basePrice = 0.0;
    private double perBlockRate = 0.0;       // For DISTANCE mode: cost per block
    private double perIntervalRate = 0.0;    // For INTERVAL mode: cost per stop interval
    private double maxPrice = 0.0;            // Maximum price cap (0 = no cap)

    // Time-based discounts: key = "HH:mm-HH:mm" (open-closed range), value = discount multiplier (0.0-1.0)
    private final List<TimeDiscount> timeDiscounts = new ArrayList<>();

    public PriceRule() {
    }

    public PriceRule(PricingMode mode, double basePrice) {
        this.mode = mode;
        this.basePrice = Math.max(0.0, basePrice);
    }

    // --- Getters and Setters ---

    public PricingMode getMode() {
        return mode;
    }

    public void setMode(PricingMode mode) {
        this.mode = mode != null ? mode : PricingMode.FLAT;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = Math.max(0.0, basePrice);
    }

    public double getPerBlockRate() {
        return perBlockRate;
    }

    public void setPerBlockRate(double perBlockRate) {
        this.perBlockRate = Math.max(0.0, perBlockRate);
    }

    public double getPerIntervalRate() {
        return perIntervalRate;
    }

    public void setPerIntervalRate(double perIntervalRate) {
        this.perIntervalRate = Math.max(0.0, perIntervalRate);
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = Math.max(0.0, maxPrice);
    }

    public List<TimeDiscount> getTimeDiscounts() {
        return new ArrayList<>(timeDiscounts);
    }

    public void setTimeDiscounts(List<TimeDiscount> discounts) {
        this.timeDiscounts.clear();
        if (discounts != null) {
            this.timeDiscounts.addAll(discounts);
        }
    }

    public void addTimeDiscount(TimeDiscount discount) {
        if (discount != null) {
            this.timeDiscounts.add(discount);
        }
    }

    public void clearTimeDiscounts() {
        this.timeDiscounts.clear();
    }

    /**
     * Calculate the applicable discount multiplier at the given game time (ticks).
     * @param gameTime The current world time in ticks (0-24000)
     * @return discount multiplier (1.0 = no discount, 0.5 = 50% off, etc.)
     */
    public double getActiveDiscountMultiplier(long gameTime) {
        double multiplier = 1.0;
        for (TimeDiscount discount : timeDiscounts) {
            if (discount.isActive(gameTime)) {
                multiplier = Math.min(multiplier, discount.getDiscountMultiplier());
            }
        }
        return multiplier;
    }

    /**
     * Calculate final price for a ride given distance in blocks and number of intervals passed.
     * @param distanceBlocks distance traveled in blocks
     * @param intervals number of stop intervals
     * @param gameTime current world time in ticks (for discount calculation)
     * @return final price after mode calculation and discount
     */
    public double calculatePrice(double distanceBlocks, int intervals, long gameTime) {
        double price;
        switch (mode) {
            case DISTANCE:
                price = basePrice + (distanceBlocks * perBlockRate);
                break;
            case INTERVAL:
                price = basePrice + (intervals * perIntervalRate);
                break;
            case FLAT:
            default:
                price = basePrice;
                break;
        }

        // Apply cap
        if (maxPrice > 0.0 && price > maxPrice) {
            price = maxPrice;
        }

        // Apply time discount
        double discount = getActiveDiscountMultiplier(gameTime);
        if (discount < 1.0) {
            price *= discount;
        }

        return Math.max(0.0, price);
    }

    /**
     * Get a human-readable description of the pricing rule.
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        switch (mode) {
            case FLAT:
                sb.append("Flat price: ").append(basePrice);
                break;
            case DISTANCE:
                sb.append("Distance-based: ").append(basePrice).append(" + ").append(perBlockRate).append("/block");
                if (maxPrice > 0) sb.append(" (max ").append(maxPrice).append(")");
                break;
            case INTERVAL:
                sb.append("Interval-based: ").append(basePrice).append(" + ").append(perIntervalRate).append("/stop");
                if (maxPrice > 0) sb.append(" (max ").append(maxPrice).append(")");
                break;
        }
        if (!timeDiscounts.isEmpty()) {
            sb.append(" [").append(timeDiscounts.size()).append(" discount period(s)]");
        }
        return sb.toString();
    }

    // --- TimeDiscount inner class ---

    public static class TimeDiscount {
        private final int startTick;   // Start time in ticks (0-24000)
        private final int endTick;     // End time in ticks (0-24000)
        private final double discountMultiplier; // 0.0 to 1.0

        /**
         * @param startTick Start of discount period in game ticks (0-24000)
         * @param endTick   End of discount period in game ticks (0-24000)
         * @param discountMultiplier Price multiplier during this period (0.0-1.0)
         */
        public TimeDiscount(int startTick, int endTick, double discountMultiplier) {
            this.startTick = Math.max(0, Math.min(24000, startTick));
            this.endTick = Math.max(0, Math.min(24000, endTick));
            this.discountMultiplier = Math.max(0.0, Math.min(1.0, discountMultiplier));
        }

        public int getStartTick() { return startTick; }
        public int getEndTick() { return endTick; }
        public double getDiscountMultiplier() { return discountMultiplier; }

        /**
         * Check if this discount is active at the given game time.
         * Supports overnight ranges (e.g., 18000-6000 = night time).
         */
        public boolean isActive(long gameTicks) {
            long time = gameTicks % 24000;
            if (startTick <= endTick) {
                return time >= startTick && time <= endTick;
            } else {
                // Overnight range (e.g., 18000 to 6000)
                return time >= startTick || time <= endTick;
            }
        }

        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("start_tick", startTick);
            map.put("end_tick", endTick);
            map.put("multiplier", discountMultiplier);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static TimeDiscount deserialize(Map<String, Object> map) {
            int start = map.containsKey("start_tick") ? ((Number) map.get("start_tick")).intValue() : 0;
            int end = map.containsKey("end_tick") ? ((Number) map.get("end_tick")).intValue() : 0;
            double mult = map.containsKey("multiplier") ? ((Number) map.get("multiplier")).doubleValue() : 1.0;
            return new TimeDiscount(start, end, mult);
        }
    }

    /**
     * Serialize this PriceRule to a config-friendly map.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", mode.name().toLowerCase());
        map.put("base_price", basePrice);
        if (mode == PricingMode.DISTANCE) {
            map.put("per_block_rate", perBlockRate);
        }
        if (mode == PricingMode.INTERVAL) {
            map.put("per_interval_rate", perIntervalRate);
        }
        if (maxPrice > 0.0) {
            map.put("max_price", maxPrice);
        }
        if (!timeDiscounts.isEmpty()) {
            List<Map<String, Object>> discounts = new ArrayList<>();
            for (TimeDiscount td : timeDiscounts) {
                discounts.add(td.serialize());
            }
            map.put("time_discounts", discounts);
        }
        return map;
    }

    /**
     * Deserialize a PriceRule from a config map.
     */
    @SuppressWarnings("unchecked")
    public static PriceRule deserialize(Map<String, Object> map) {
        PriceRule rule = new PriceRule();
        if (map.containsKey("mode")) {
            try {
                rule.setMode(PricingMode.valueOf(((String) map.get("mode")).toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                rule.setMode(PricingMode.FLAT);
            }
        }
        if (map.containsKey("base_price")) {
            rule.setBasePrice(((Number) map.get("base_price")).doubleValue());
        }
        if (map.containsKey("per_block_rate")) {
            rule.setPerBlockRate(((Number) map.get("per_block_rate")).doubleValue());
        }
        if (map.containsKey("per_interval_rate")) {
            rule.setPerIntervalRate(((Number) map.get("per_interval_rate")).doubleValue());
        }
        if (map.containsKey("max_price")) {
            rule.setMaxPrice(((Number) map.get("max_price")).doubleValue());
        }
        if (map.containsKey("time_discounts")) {
            List<Map<String, Object>> discountList = (List<Map<String, Object>>) map.get("time_discounts");
            for (Map<String, Object> d : discountList) {
                rule.addTimeDiscount(TimeDiscount.deserialize(d));
            }
        }
        return rule;
    }
}
