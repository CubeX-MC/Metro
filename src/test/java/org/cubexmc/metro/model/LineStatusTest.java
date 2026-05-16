package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LineStatusTest {

    @Test
    void fromConfigShouldParseCaseInsensitiveStatusKeys() {
        assertEquals(LineStatus.NORMAL, LineStatus.fromConfig("normal"));
        assertEquals(LineStatus.SUSPENDED, LineStatus.fromConfig("SUSPENDED"));
        assertEquals(LineStatus.MAINTENANCE, LineStatus.fromConfig("Maintenance"));
    }

    @Test
    void fromConfigShouldTrimWhitespace() {
        assertEquals(LineStatus.SUSPENDED, LineStatus.fromConfig(" suspended "));
    }

    @Test
    void fromConfigShouldDefaultInvalidBlankAndNullToNormal() {
        assertEquals(LineStatus.NORMAL, LineStatus.fromConfig(null));
        assertEquals(LineStatus.NORMAL, LineStatus.fromConfig(""));
        assertEquals(LineStatus.NORMAL, LineStatus.fromConfig("   "));
        assertEquals(LineStatus.NORMAL, LineStatus.fromConfig("closed"));
    }

    @Test
    void configKeysShouldBeLowercase() {
        assertEquals("normal", LineStatus.NORMAL.getConfigKey());
        assertEquals("suspended", LineStatus.SUSPENDED.getConfigKey());
        assertEquals("maintenance", LineStatus.MAINTENANCE.getConfigKey());
    }

    @Test
    void onlySuspendedLinesShouldBlockBoarding() {
        assertTrue(LineStatus.NORMAL.isBoardable());
        assertTrue(LineStatus.MAINTENANCE.isBoardable());
        assertFalse(LineStatus.SUSPENDED.isBoardable());
    }
}
