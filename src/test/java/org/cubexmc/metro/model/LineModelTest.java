package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LineModelTest {

    @Test
    void shouldReturnNextStopOnNormalLine() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);

        assertEquals("B", line.getNextStopId("A"));
        assertEquals("C", line.getNextStopId("B"));
        assertNull(line.getNextStopId("C"));
    }

    @Test
    void shouldSupportCircularLineNextAndPrevious() {
        Line line = new Line("l1", "Circle");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);
        line.addStop("A", -1); // make circular

        assertTrue(line.isCircular());
        assertEquals("B", line.getNextStopId("A"));
        assertEquals("C", line.getNextStopId("B"));
        assertEquals("A", line.getNextStopId("C"));
        assertEquals("C", line.getPreviousStopId("A"));
    }
}

