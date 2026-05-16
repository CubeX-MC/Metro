package org.cubexmc.metro.util;

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
        assertTrue(result.contains("&a"));
    }
}

