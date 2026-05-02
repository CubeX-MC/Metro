package org.cubexmc.metro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionUtilTest {

    @Test
    void parsesClassicMinecraftVersion() {
        VersionUtil.ServerVersion version = VersionUtil.parseBukkitVersion("1.18.2-R0.1-SNAPSHOT");

        assertEquals(1, version.major());
        assertEquals(18, version.minor());
        assertEquals(2, version.patch());
        assertEquals("1.18.2", version.toString());
    }

    @Test
    void parsesDoubleDigitPatchVersion() {
        VersionUtil.ServerVersion version = VersionUtil.parseBukkitVersion("1.21.11-R0.1-SNAPSHOT");

        assertEquals(1, version.major());
        assertEquals(21, version.minor());
        assertEquals(11, version.patch());
        assertTrue(version.isAtLeast(1, 21, 10));
        assertFalse(version.isAtLeast(1, 21, 12));
    }

    @Test
    void parsesCalendarStyleMinecraftVersion() {
        VersionUtil.ServerVersion version = VersionUtil.parseBukkitVersion("26.1.2-R0.1-SNAPSHOT");

        assertEquals(26, version.major());
        assertEquals(1, version.minor());
        assertEquals(2, version.patch());
        assertTrue(version.isAtLeast(1, 18, 0));
        assertTrue(version.isAtLeast(26, 1, 2));
        assertFalse(version.isAtLeast(26, 1, 3));
    }

    @Test
    void prefersMinecraftVersionInServerVersionString() {
        VersionUtil.ServerVersion version = VersionUtil.parseBukkitVersion("git-Paper-123 (MC: 26.1.2)");

        assertEquals(26, version.major());
        assertEquals(1, version.minor());
        assertEquals(2, version.patch());
    }

    @Test
    void returnsUnknownForInvalidVersion() {
        VersionUtil.ServerVersion version = VersionUtil.parseBukkitVersion("unknown");

        assertEquals(0, version.major());
        assertEquals(0, version.minor());
        assertEquals(0, version.patch());
        assertFalse(version.isAtLeast(1, 18, 0));
    }
}
