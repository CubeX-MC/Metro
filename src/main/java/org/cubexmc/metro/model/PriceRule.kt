package org.cubexmc.metro.model

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Price configuration for a metro line.
 */
class PriceRule {
    enum class PricingMode {
        FLAT,
        DISTANCE,
        INTERVAL,
    }

    private var mode: PricingMode = PricingMode.FLAT
    private var basePrice = 0.0
    private var perBlockRate = 0.0
    private var perIntervalRate = 0.0
    private var maxPrice = 0.0
    private val timeDiscounts: MutableList<TimeDiscount> = ArrayList()

    constructor()

    constructor(mode: PricingMode?, basePrice: Double) {
        this.mode = mode ?: PricingMode.FLAT
        this.basePrice = max(0.0, basePrice)
    }

    fun getMode(): PricingMode = mode

    fun setMode(mode: PricingMode?) {
        this.mode = mode ?: PricingMode.FLAT
    }

    fun getBasePrice(): Double = basePrice

    fun setBasePrice(basePrice: Double) {
        this.basePrice = max(0.0, basePrice)
    }

    fun getPerBlockRate(): Double = perBlockRate

    fun setPerBlockRate(perBlockRate: Double) {
        this.perBlockRate = max(0.0, perBlockRate)
    }

    fun getPerIntervalRate(): Double = perIntervalRate

    fun setPerIntervalRate(perIntervalRate: Double) {
        this.perIntervalRate = max(0.0, perIntervalRate)
    }

    fun getMaxPrice(): Double = maxPrice

    fun setMaxPrice(maxPrice: Double) {
        this.maxPrice = max(0.0, maxPrice)
    }

    fun getTimeDiscounts(): List<TimeDiscount> = ArrayList(timeDiscounts)

    fun setTimeDiscounts(discounts: List<TimeDiscount>?) {
        timeDiscounts.clear()
        if (discounts != null) {
            timeDiscounts.addAll(discounts)
        }
    }

    fun addTimeDiscount(discount: TimeDiscount?) {
        if (discount != null) {
            timeDiscounts.add(discount)
        }
    }

    fun clearTimeDiscounts() {
        timeDiscounts.clear()
    }

    fun getActiveDiscountMultiplier(gameTime: Long): Double {
        var multiplier = 1.0
        for (discount in timeDiscounts) {
            if (discount.isActive(gameTime)) {
                multiplier = min(multiplier, discount.getDiscountMultiplier())
            }
        }
        return multiplier
    }

    /**
     * Full trip price. Time discounts are applied *before* [maxPrice] so that
     * `max_price` is the highest amount a passenger can ever be charged;
     * capping first would make the discount cut into the cap instead.
     */
    fun calculatePrice(distanceBlocks: Double, intervals: Int, gameTime: Long): Double {
        val price = when (mode) {
            PricingMode.DISTANCE -> basePrice + distanceBlocks * perBlockRate
            PricingMode.INTERVAL -> basePrice + intervals * perIntervalRate
            PricingMode.FLAT -> basePrice
        }
        return capTripTotal(applyDiscount(price, gameTime), 0.0)
    }

    /**
     * Price of the variable part of a trip only, without [basePrice], which is
     * already charged when boarding.
     *
     * Variable fares are settled once per stop, but [maxPrice] caps the whole
     * trip, so [chargedForTripSoFar] (base price included) is deducted from the
     * remaining allowance instead of capping each segment on its own.
     */
    fun calculateVariableFare(
        distanceBlocks: Double,
        intervals: Int,
        gameTime: Long,
        chargedForTripSoFar: Double,
    ): Double {
        val price = when (mode) {
            PricingMode.DISTANCE -> distanceBlocks * perBlockRate
            PricingMode.INTERVAL -> intervals * perIntervalRate
            PricingMode.FLAT -> 0.0
        }
        return capTripTotal(applyDiscount(price, gameTime), chargedForTripSoFar)
    }

    private fun applyDiscount(price: Double, gameTime: Long): Double {
        val discount = getActiveDiscountMultiplier(gameTime)
        return if (discount < 1.0) price * discount else price
    }

    private fun capTripTotal(price: Double, chargedForTripSoFar: Double): Double {
        if (maxPrice <= 0.0) {
            return max(0.0, price)
        }
        val remaining = maxPrice - max(0.0, chargedForTripSoFar)
        return max(0.0, min(price, remaining))
    }

    fun getDescription(): String {
        val sb = StringBuilder()
        when (mode) {
            PricingMode.FLAT -> sb.append("Flat price: ").append(basePrice)
            PricingMode.DISTANCE -> {
                sb.append("Distance-based: ").append(basePrice).append(" + ").append(perBlockRate).append("/block")
                if (maxPrice > 0) sb.append(" (max ").append(maxPrice).append(")")
            }
            PricingMode.INTERVAL -> {
                sb.append("Interval-based: ").append(basePrice).append(" + ").append(perIntervalRate).append("/stop")
                if (maxPrice > 0) sb.append(" (max ").append(maxPrice).append(")")
            }
        }
        if (timeDiscounts.isNotEmpty()) {
            sb.append(" [").append(timeDiscounts.size).append(" discount period(s)]")
        }
        return sb.toString()
    }

    fun serialize(): Map<String, Any> {
        val map = HashMap<String, Any>()
        map["mode"] = mode.name.lowercase(Locale.getDefault())
        map["base_price"] = basePrice
        if (mode == PricingMode.DISTANCE) {
            map["per_block_rate"] = perBlockRate
        }
        if (mode == PricingMode.INTERVAL) {
            map["per_interval_rate"] = perIntervalRate
        }
        if (maxPrice > 0.0) {
            map["max_price"] = maxPrice
        }
        if (timeDiscounts.isNotEmpty()) {
            val discounts = ArrayList<Map<String, Any>>()
            for (discount in timeDiscounts) {
                discounts.add(discount.serialize())
            }
            map["time_discounts"] = discounts
        }
        return map
    }

    class TimeDiscount(startTick: Int, endTick: Int, discountMultiplier: Double) {
        private val startTick: Int = max(0, min(24000, startTick))
        private val endTick: Int = max(0, min(24000, endTick))
        private val discountMultiplier: Double = max(0.0, min(1.0, discountMultiplier))

        fun getStartTick(): Int = startTick

        fun getEndTick(): Int = endTick

        fun getDiscountMultiplier(): Double = discountMultiplier

        fun isActive(gameTicks: Long): Boolean {
            val time = gameTicks % 24000
            return if (startTick <= endTick) {
                time >= startTick && time <= endTick
            } else {
                time >= startTick || time <= endTick
            }
        }

        fun serialize(): Map<String, Any> {
            val map = HashMap<String, Any>()
            map["start_tick"] = startTick
            map["end_tick"] = endTick
            map["multiplier"] = discountMultiplier
            return map
        }

        companion object {
            @JvmStatic
            fun deserialize(map: Map<String, Any>): TimeDiscount {
                val start = (map["start_tick"] as? Number)?.toInt() ?: 0
                val end = (map["end_tick"] as? Number)?.toInt() ?: 0
                val multiplier = (map["multiplier"] as? Number)?.toDouble() ?: 1.0
                return TimeDiscount(start, end, multiplier)
            }
        }
    }

    companion object {
        @JvmStatic
        fun deserialize(map: Map<String, Any>): PriceRule {
            val rule = PriceRule()
            if (map.containsKey("mode")) {
                try {
                    rule.setMode(PricingMode.valueOf((map["mode"] as String).uppercase(Locale.getDefault())))
                } catch (_: IllegalArgumentException) {
                    rule.setMode(PricingMode.FLAT)
                }
            }
            if (map.containsKey("base_price")) {
                rule.setBasePrice((map["base_price"] as Number).toDouble())
            }
            if (map.containsKey("per_block_rate")) {
                rule.setPerBlockRate((map["per_block_rate"] as Number).toDouble())
            }
            if (map.containsKey("per_interval_rate")) {
                rule.setPerIntervalRate((map["per_interval_rate"] as Number).toDouble())
            }
            if (map.containsKey("max_price")) {
                rule.setMaxPrice((map["max_price"] as Number).toDouble())
            }
            val discountList = map["time_discounts"] as? List<Map<String, Any>>
            if (discountList != null) {
                for (discount in discountList) {
                    rule.addTimeDiscount(TimeDiscount.deserialize(discount))
                }
            }
            return rule
        }
    }
}
