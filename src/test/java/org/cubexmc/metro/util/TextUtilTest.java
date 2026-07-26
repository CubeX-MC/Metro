package org.cubexmc.metro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

class TextUtilTest {

    @Test
    void shouldReplaceCommonPlaceholders() {
        Line line = new Line("line_1", "Line 1");
        line.setColor("&a");
        Stop current = new Stop("s1", "Central");
        Stop next = new Stop("s2", "Harbor");
        Stop terminal = new Stop("s3", "Airport");

        String template = "{line}:{line_id}:{stop_name}:{next_stop_name}:{terminal_stop_name}:{line_color_code}";
        String result = TextUtil.replacePlaceholders(template, line, current, null, next, terminal, null);

        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("line_1"));
        assertTrue(result.contains("Central"));
        assertTrue(result.contains("Harbor"));
        assertTrue(result.contains("Airport"));
        assertTrue(result.contains("\u00a7a"));
    }

    @Test
    void shouldRenderRoundedNextStopDistance() {
        Stop next = new Stop("s2", "Harbor");

        String result = TextUtil.replacePlaceholders(
                "{next_stop_name}:{next_stop_distance}", null, null, null, next, null, null, 128.6);

        assertEquals("Harbor:129", result);
    }

    @Test
    void shouldRenderEmptyDistanceWhenUnknown() {
        Stop next = new Stop("s2", "Harbor");

        assertEquals("Harbor:", TextUtil.replacePlaceholders(
                "{next_stop_name}:{next_stop_distance}", null, null, null, next, null, null, null));
        assertEquals("Harbor:", TextUtil.replacePlaceholders(
                "{next_stop_name}:{next_stop_distance}", null, null, null, next, null, null));
    }

    @Test
    void shouldColorizeHexLineColorPlaceholder() {
        Line line = new Line("line_1", "Line 1");
        line.setColor("&#55AAFF");

        String result = TextUtil.replacePlaceholders("{line_color_code}{line}", line, null, null, null, null, null);

        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("\u00a7"));
        assertFalse(result.contains("&#55AAFF"));
    }
}

