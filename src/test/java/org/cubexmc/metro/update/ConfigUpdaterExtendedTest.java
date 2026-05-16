package org.cubexmc.metro.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigUpdaterExtendedTest {

    @Test
    void shouldNotMigrateWhenNoLegacySection() {
        YamlConfiguration config = new YamlConfiguration();
        assertFalse(ConfigUpdater.migrateLegacyEnterStop(config));
    }

    @Test
    void shouldMigrateNestedSections() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.title", "Hello");
        config.set("titles.enter_stop.subtitle", "World");
        config.set("titles.enter_stop.nested.key", "value");

        boolean migrated = ConfigUpdater.migrateLegacyEnterStop(config);

        assertTrue(migrated);
        assertEquals("Hello", config.getString("titles.stop_continuous.title"));
        assertEquals("World", config.getString("titles.stop_continuous.subtitle"));
        assertEquals("value", config.getString("titles.stop_continuous.nested.key"));
    }

    @Test
    void shouldHandleEmptyLegacySection() {
        YamlConfiguration config = new YamlConfiguration();
        config.createSection("titles.enter_stop");

        boolean migrated = ConfigUpdater.migrateLegacyEnterStop(config);

        assertTrue(migrated);
        assertTrue(config.isConfigurationSection("titles.stop_continuous"));
    }
}
