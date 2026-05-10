package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PriceRuleTest {

    @Test
    void flatModeShouldReturnBasePriceOnly() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        assertEquals(5.0, rule.calculatePrice(100, 3, 6000));
    }

    @Test
    void distanceModeShouldAddPerBlockRate() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.1);
        assertEquals(12.0, rule.calculatePrice(100, 0, 6000));
    }

    @Test
    void intervalModeShouldAddPerIntervalRate() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.INTERVAL, 3.0);
        rule.setPerIntervalRate(1.5);
        assertEquals(9.0, rule.calculatePrice(0, 4, 6000));
    }

    @Test
    void maxPriceShouldCapDistanceFare() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.5);
        rule.setMaxPrice(10.0);
        assertEquals(10.0, rule.calculatePrice(200, 0, 6000));
    }

    @Test
    void maxPriceShouldCapIntervalFare() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.INTERVAL, 3.0);
        rule.setPerIntervalRate(2.0);
        rule.setMaxPrice(8.0);
        assertEquals(8.0, rule.calculatePrice(0, 10, 6000));
    }

    @Test
    void zeroMaxPriceShouldNotCap() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.5);
        rule.setMaxPrice(0.0);
        assertEquals(52.0, rule.calculatePrice(100, 0, 6000));
    }

    @Test
    void timeDiscountShouldReducePrice() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 10.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 12000, 0.7));
        assertEquals(7.0, rule.calculatePrice(0, 1, 6000));
    }

    @Test
    void expiredTimeDiscountShouldNotApply() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 10.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 6000, 0.5));
        assertEquals(10.0, rule.calculatePrice(0, 1, 12001));
    }

    @Test
    void overnightDiscountShouldWork() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 10.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(18000, 6000, 0.5));
        assertEquals(5.0, rule.calculatePrice(0, 1, 20000));
        assertEquals(5.0, rule.calculatePrice(0, 1, 3000));
        assertEquals(10.0, rule.calculatePrice(0, 1, 12000));
    }

    @Test
    void multipleDiscountsShouldPickBest() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 10.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 12000, 0.5));
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 24000, 0.8));
        assertEquals(5.0, rule.calculatePrice(0, 1, 6000));
    }

    @Test
    void discountShouldNotGoNegative() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 24000, -0.5));
        assertTrue(rule.calculatePrice(0, 1, 6000) >= 0.0);
    }

    @Test
    void negativeBasePriceShouldBeClamped() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, -5.0);
        assertEquals(0.0, rule.getBasePrice());
    }

    @Test
    void nullModeShouldDefaultToFlat() {
        PriceRule rule = new PriceRule();
        rule.setMode(null);
        assertEquals(PriceRule.PricingMode.FLAT, rule.getMode());
    }

    @Test
    void serializeAndDeserializeFlat() {
        PriceRule original = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        Map<String, Object> serialized = original.serialize();
        PriceRule restored = PriceRule.deserialize(serialized);

        assertEquals(PriceRule.PricingMode.FLAT, restored.getMode());
        assertEquals(5.0, restored.getBasePrice());
        assertEquals(0.0, restored.getMaxPrice());
    }

    @Test
    void serializeAndDeserializeDistance() {
        PriceRule original = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        original.setPerBlockRate(0.3);
        original.setMaxPrice(20.0);
        Map<String, Object> serialized = original.serialize();
        PriceRule restored = PriceRule.deserialize(serialized);

        assertEquals(PriceRule.PricingMode.DISTANCE, restored.getMode());
        assertEquals(2.0, restored.getBasePrice());
        assertEquals(0.3, restored.getPerBlockRate());
        assertEquals(20.0, restored.getMaxPrice());
    }

    @Test
    void serializeAndDeserializeInterval() {
        PriceRule original = new PriceRule(PriceRule.PricingMode.INTERVAL, 3.0);
        original.setPerIntervalRate(1.5);
        Map<String, Object> serialized = original.serialize();
        PriceRule restored = PriceRule.deserialize(serialized);

        assertEquals(PriceRule.PricingMode.INTERVAL, restored.getMode());
        assertEquals(3.0, restored.getBasePrice());
        assertEquals(1.5, restored.getPerIntervalRate());
    }

    @Test
    void serializeAndDeserializeWithDiscounts() {
        PriceRule original = new PriceRule(PriceRule.PricingMode.FLAT, 8.0);
        original.addTimeDiscount(new PriceRule.TimeDiscount(18000, 6000, 0.5));
        original.addTimeDiscount(new PriceRule.TimeDiscount(0, 12000, 0.8));
        Map<String, Object> serialized = original.serialize();
        PriceRule restored = PriceRule.deserialize(serialized);

        List<PriceRule.TimeDiscount> restoredDiscounts = restored.getTimeDiscounts();
        assertEquals(2, restoredDiscounts.size());
        assertEquals(0.5, restoredDiscounts.get(0).getDiscountMultiplier());
    }

    @Test
    void deserializeMissingModeShouldDefaultToFlat() {
        Map<String, Object> map = new HashMap<>();
        map.put("base_price", 3.0);
        PriceRule rule = PriceRule.deserialize(map);
        assertEquals(PriceRule.PricingMode.FLAT, rule.getMode());
        assertEquals(3.0, rule.getBasePrice());
    }

    @Test
    void deserializeInvalidModeShouldDefaultToFlat() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", "invalid_mode");
        map.put("base_price", 4.0);
        PriceRule rule = PriceRule.deserialize(map);
        assertEquals(PriceRule.PricingMode.FLAT, rule.getMode());
    }

    @Test
    void deserializeEmptyMapShouldReturnDefaults() {
        PriceRule rule = PriceRule.deserialize(new HashMap<>());
        assertEquals(PriceRule.PricingMode.FLAT, rule.getMode());
        assertEquals(0.0, rule.getBasePrice());
    }

    @Test
    void descriptionShouldContainModeName() {
        PriceRule flat = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        assertTrue(flat.getDescription().contains("Flat"));

        PriceRule dist = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        dist.setPerBlockRate(0.1);
        assertTrue(dist.getDescription().contains("Distance"));

        PriceRule interval = new PriceRule(PriceRule.PricingMode.INTERVAL, 3.0);
        interval.setPerIntervalRate(1.0);
        assertTrue(interval.getDescription().contains("Interval"));
    }

    @Test
    void descriptionShouldShowDiscountCount() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 6000, 0.5));
        assertTrue(rule.getDescription().contains("1 discount"));
    }

    @Test
    void defensiveCopyOfDiscounts() {
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 5.0);
        rule.addTimeDiscount(new PriceRule.TimeDiscount(0, 6000, 0.5));
        List<PriceRule.TimeDiscount> copy = rule.getTimeDiscounts();
        copy.clear();
        assertEquals(1, rule.getTimeDiscounts().size());
    }

    @Test
    void timeDiscountClampTicks() {
        PriceRule.TimeDiscount discount = new PriceRule.TimeDiscount(-100, 25000, 0.5);
        assertEquals(0, discount.getStartTick());
        assertEquals(24000, discount.getEndTick());
    }

    @Test
    void timeDiscountClampMultiplier() {
        PriceRule.TimeDiscount discount = new PriceRule.TimeDiscount(0, 12000, 1.5);
        assertEquals(1.0, discount.getDiscountMultiplier());
        PriceRule.TimeDiscount negative = new PriceRule.TimeDiscount(0, 12000, -0.5);
        assertEquals(0.0, negative.getDiscountMultiplier());
    }
}
