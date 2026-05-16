package org.cubexmc.metro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class TextUtilExtendedTest {

    @Test
    void shouldReturnEmptyForNullInput() {
        assertEquals("", TextUtil.replacePlaceholders(null, null, null, null, null, null, null));
    }

    @Test
    void shouldReplaceLastStopPlaceholders() {
        Line line = new Line("l1", "Line1");
        Stop last = new Stop("s0", "Previous");

        String result = TextUtil.replacePlaceholders("{last_stop_name}:{last_stop_id}", line, null, last, null, null, null);
        assertEquals("Previous:s0", result);
    }

    @Test
    void shouldClearLastStopPlaceholdersWhenNull() {
        String result = TextUtil.replacePlaceholders("{last_stop_name}:{last_stop_id}", null, null, null, null, null, null);
        assertEquals(":", result);
    }

    @Test
    void shouldReplaceNextStopTransfers() {
        Line line = new Line("l1", "Line1");
        line.setColor("&b");
        Stop current = new Stop("s1", "Central");
        Stop next = new Stop("s2", "Harbor");
        next.addTransferableLine("red");

        Line redLine = new Line("red", "Red Line");
        redLine.setColor("&c");

        LineManager lineManager = mock(LineManager.class);
        when(lineManager.getLine("red")).thenReturn(redLine);

        String result = TextUtil.replacePlaceholders(
                "{next_stop_name}:{next_stop_id}:{next_stop_transfers}",
                line, current, null, next, null, lineManager);

        assertTrue(result.contains("Harbor"));
        assertTrue(result.contains("s2"));
        assertTrue(result.contains("Red Line"));
    }

    @Test
    void shouldClearNextStopPlaceholdersWhenNull() {
        String result = TextUtil.replacePlaceholders("{next_stop_name}:{next_stop_id}:{next_stop_transfers}",
                null, null, null, null, null, null);
        assertEquals("::", result);
    }

    @Test
    void shouldReplaceTerminusNameForCircularWithNextFallback() {
        Line line = new Line("l1", "Circle");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("A", -1);
        assertTrue(line.isCircular());

        Stop next = new Stop("sB", "NextStop");

        String result = TextUtil.replacePlaceholders("{terminus_name}", line, null, null, next, null, null);
        assertEquals("NextStop", result);
    }

    @Test
    void shouldReplaceTerminusNameForNonCircularWithTerminalFallback() {
        Line line = new Line("l1", "Normal");
        line.addStop("A", -1);
        line.addStop("B", -1);
        assertFalse(line.isCircular());

        Stop terminal = new Stop("sB", "Terminal");

        String result = TextUtil.replacePlaceholders("{terminus_name}", line, null, null, null, terminal, null);
        assertEquals("Terminal", result);
    }

    @Test
    void shouldReplaceDestinationStopId() {
        Line line = new Line("l1", "Normal");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);

        String result = TextUtil.replacePlaceholders("{destination_stop_id}", line, null, null, null, null, null);
        assertEquals("C", result);
    }

    @Test
    void shouldHandleEmptyLineForDestinationStopId() {
        Line line = new Line("l1", "Empty");
        String result = TextUtil.replacePlaceholders("{destination_stop_id}", line, null, null, null, null, null);
        assertEquals("", result);
    }

    @Test
    void shouldReplaceTerminalStopPlaceholders() {
        Stop terminal = new Stop("sC", "Airport");

        String result = TextUtil.replacePlaceholders(
                "{terminal_stop_name}:{terminal_stop_id}:{destination_stop_name}",
                null, null, null, null, terminal, null);
        assertEquals("Airport:sC:Airport", result);
    }

    @Test
    void shouldClearTerminalStopPlaceholdersWhenNull() {
        String result = TextUtil.replacePlaceholders(
                "{terminal_stop_name}:{terminal_stop_id}:{destination_stop_name}",
                null, null, null, null, null, null);
        assertEquals("::", result);
    }

    @Test
    void shouldFormatTransferableLines() {
        Stop stop = new Stop("s1", "Central");
        stop.addTransferableLine("red");
        stop.addTransferableLine("blue");

        Line redLine = new Line("red", "Red Line");
        redLine.setColor("&c");
        Line blueLine = new Line("blue", "Blue Line");
        blueLine.setColor("&9");

        LineManager lineManager = mock(LineManager.class);
        when(lineManager.getLine("red")).thenReturn(redLine);
        when(lineManager.getLine("blue")).thenReturn(blueLine);

        String result = TextUtil.replacePlaceholders("{stop_transfers}", null, stop, null, null, null, lineManager);
        assertTrue(result.contains("Red Line"));
        assertTrue(result.contains("Blue Line"));
    }

    @Test
    void shouldClearTransfersWhenNoLineManager() {
        Stop stop = new Stop("s1", "Central");
        stop.addTransferableLine("red");

        String result = TextUtil.replacePlaceholders("{stop_transfers}", null, stop, null, null, null, null);
        assertEquals("", result);
    }

    @Test
    void shouldUseExplicitTerminusNameWhenSet() {
        Line line = new Line("l1", "Line1");
        line.setTerminusName("Eastbound");
        line.addStop("A", -1);
        line.addStop("B", -1);

        String result = TextUtil.replacePlaceholders("{terminus_name}", line, null, null, null, null, null);
        assertEquals("Eastbound", result);
    }
}
