package org.cubexmc.metro.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapLineColorTest {

    @Test
    void shouldParseLegacyLineColors() {
        assertEquals(new MapLineColor(85, 255, 85), MapLineColor.fromLineColor("&a"));
        assertEquals(new MapLineColor(255, 85, 85), MapLineColor.fromLineColor("\u00a7c"));
        assertEquals(0x5555FF, MapLineColor.fromLineColor("&9").asRgbInt());
    }

    @Test
    void shouldParseHexLineColorsBeforeLegacyFallback() {
        assertEquals(new MapLineColor(18, 171, 239), MapLineColor.fromLineColor("&#12ABef"));
        assertEquals(new MapLineColor(85, 170, 255), MapLineColor.fromLineColor("prefix &#55AAFF &a"));
    }

    @Test
    void shouldParseBukkitExpandedHexColors() {
        assertEquals(new MapLineColor(85, 170, 255),
                MapLineColor.fromLineColor("\u00a7x\u00a75\u00a75\u00a7a\u00a7a\u00a7f\u00a7f"));
    }

    @Test
    void shouldFallbackToWhiteForMissingOrUnknownColors() {
        assertEquals(MapLineColor.WHITE, MapLineColor.fromLineColor(null));
        assertEquals(MapLineColor.WHITE, MapLineColor.fromLineColor(""));
        assertEquals(MapLineColor.WHITE, MapLineColor.fromLineColor("green"));
    }
}
