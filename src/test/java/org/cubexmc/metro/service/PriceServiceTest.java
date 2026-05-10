package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.PriceRule;
import org.junit.jupiter.api.Test;

class PriceServiceTest {

    private final PriceService service = new PriceService();

    @Test
    void flatLineShouldReturnBasePriceAsEstimate() {
        Line line = new Line("l1", "Test");
        PriceRule rule = new PriceRule(PriceRule.PricingMode.FLAT, 7.0);
        line.setPriceRule(rule);
        assertEquals(7.0, service.getEstimatedPrice(line));
    }

    @Test
    void distanceLineShouldReturnBasePriceAsEstimate() {
        Line line = new Line("l1", "Test");
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.5);
        line.setPriceRule(rule);
        assertEquals(2.0, service.getEstimatedPrice(line));
    }

    @Test
    void nullLineShouldReturnZeroEstimate() {
        assertEquals(0.0, service.getEstimatedPrice(null));
    }

    @Test
    void legacyLineShouldReturnTicketPriceEstimate() {
        Line line = new Line("l1", "Test");
        line.setTicketPrice(3.0);
        assertEquals(3.0, service.getEstimatedPrice(line));
    }

    @Test
    void countOneInterval() {
        Line line = new Line("l1", "Test");
        line.addStop("A", -1);
        line.addStop("B", -1);
        assertEquals(1, service.countStopIntervals(line, "A", "B"));
    }

    @Test
    void countThreeIntervals() {
        Line line = new Line("l1", "Test");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);
        line.addStop("D", -1);
        assertEquals(3, service.countStopIntervals(line, "A", "D"));
    }

    @Test
    void reverseDirectionShouldReturnZero() {
        Line line = new Line("l1", "Test");
        line.addStop("A", -1);
        line.addStop("B", -1);
        assertEquals(0, service.countStopIntervals(line, "B", "A"));
    }

    @Test
    void circularLineShouldTakeShorterPath() {
        Line line = new Line("l1", "Circle");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);
        line.addStop("D", -1);
        line.addStop("A", -1);
        assertEquals(2, service.countStopIntervals(line, "D", "B"));
    }

    @Test
    void missingStopsShouldReturnZero() {
        Line line = new Line("l1", "Test");
        line.addStop("A", -1);
        assertEquals(0, service.countStopIntervals(line, "A", "MISSING"));
        assertEquals(0, service.countStopIntervals(line, "MISSING", "A"));
    }

    @Test
    void nullInputsShouldReturnZero() {
        assertEquals(0, service.countStopIntervals(null, "A", "B"));
        Line line = new Line("l1", "Test");
        assertEquals(0, service.countStopIntervals(line, null, "B"));
        assertEquals(0, service.countStopIntervals(line, "A", null));
    }

    @Test
    void sameStopShouldReturnZero() {
        Line line = new Line("l1", "Test");
        line.addStop("A", -1);
        line.addStop("B", -1);
        assertEquals(0, service.countStopIntervals(line, "A", "A"));
    }
}
