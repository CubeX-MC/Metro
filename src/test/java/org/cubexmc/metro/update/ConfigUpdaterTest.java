package org.cubexmc.metro.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigUpdaterTest {

    @Test
    void shouldMigrateLegacyEnterStopToStopContinuousWhenNewPathIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.enabled", false);
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.enter_stop.fade_in", 3);

        boolean migrated = ConfigUpdater.migrateLegacyEnterStop(config);

        assertTrue(migrated);
        assertFalse(config.getBoolean("titles.stop_continuous.enabled"));
        assertEquals("Legacy Title", config.getString("titles.stop_continuous.title"));
        assertEquals(3, config.getInt("titles.stop_continuous.fade_in"));
    }

    @Test
    void shouldKeepExistingStopContinuousWhenMigratingLegacyEnterStop() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.stop_continuous.title", "Modern Title");

        boolean migrated = ConfigUpdater.migrateLegacyEnterStop(config);

        assertFalse(migrated);
        assertEquals("Modern Title", config.getString("titles.stop_continuous.title"));
    }
}
