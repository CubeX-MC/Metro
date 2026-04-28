package org.cubexmc.metro.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class ConfigFacadeTest {

    @Test
    void shouldReadLegacyEnterStopWhenStopContinuousIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.enabled", false);
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.enter_stop.subtitle", "Legacy Subtitle");
        config.set("titles.enter_stop.fade_in", 3);

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertFalse(facade.isStopContinuousTitleEnabled());
        assertEquals("Legacy Title", facade.getStopContinuousTitle(false, false));
        assertEquals("Legacy Subtitle", facade.getStopContinuousSubtitle(false, false));
        assertEquals(3, facade.getStopContinuousFadeIn());
        assertEquals("Legacy Title", facade.getEnterStopTitle());
    }

    @Test
    void shouldPreferStopContinuousOverLegacyEnterStop() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.enter_stop.title", "Legacy Title");
        config.set("titles.stop_continuous.title", "Modern Title");
        config.set("titles.stop_continuous.start_stop.title", "Origin Title");

        ConfigFacade facade = createFacade(config);
        facade.reload();

        assertEquals("Modern Title", facade.getStopContinuousTitle(false, false));
        assertEquals("Origin Title", facade.getStopContinuousTitle(true, false));
    }

    @Test
    void shouldRefreshStopContinuousValuesOnReload() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("titles.stop_continuous.interval", 40);

        ConfigFacade facade = createFacade(config);
        facade.reload();
        assertEquals(40, facade.getStopContinuousInterval());

        config.set("titles.stop_continuous.interval", 80);
        facade.reload();
        assertEquals(80, facade.getStopContinuousInterval());
    }

    private ConfigFacade createFacade(YamlConfiguration config) {
        Metro plugin = mock(Metro.class);
        when(plugin.getConfig()).thenReturn(config);
        return new ConfigFacade(plugin);
    }
}
