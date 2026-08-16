package org.cubexmc.metro.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.config.MigrationContext;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class MetroModernizationResourceTest {

    @Test
    void bundledConfigUsesV2OnlyForDisplayWhitelist() {
        YamlConfiguration config = load("config.yml");

        assertEquals(MetroMigrations.CONFIG_VERSION, config.getInt("config-version"));
        assertEquals("<aqua><stop_name>", config.getString("titles.stop_continuous.title"));
        assertEquals("<gold>☛ <bold>", config.getString("scoreboard.styles.current_stop"));
        assertEquals("VANILLA_MOMENTUM", config.getString("speed_control.mode"));
        assertEquals("NOTE,18,1.0,PLING,0", config.getStringList("sounds.departure.notes").get(0));
        assertFalse(config.contains("lines.name"), "data resources must not be folded into config migration");
    }

    @Test
    void bundledLanguageUsesMiniMessageAndEscapesLiteralUsage() {
        YamlConfiguration language = load("lang/en_US.yml");

        assertEquals(MetroMigrations.LANG_VERSION, language.getInt("lang-version"));
        assertEquals("<green>Successfully created line: <line_id>", language.getString("line.create_success"));
        assertTrue(language.getString("line.usage_create").contains("\\<line_id>"));
        assertFalse(language.getString("line.usage_create").contains("&c"));
    }

    @Test
    void configMigrationConvertsOnlyDisplayWhitelistAndMergesV2Defaults() throws Exception {
        Metro plugin = pluginWithResource("config.yml", """
                config-version: 2
                titles:
                  departure:
                    subtitle: '<green>Default <line_id>'
                speed_control:
                  mode: VANILLA_MOMENTUM
                """);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(new ByteArrayInputStream("""
                titles:
                  departure:
                    title: '&6{line_name}'
                speed_control:
                  mode: '&6NOT_DISPLAY_TEXT'
                """.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));

        new MetroConfigModernizationStep(plugin).migrate(new SimpleMigrationContext("config.yml", yaml));

        assertEquals("<gold><line_name>", yaml.getString("titles.departure.title"));
        assertEquals("<green>Default <line_id>", yaml.getString("titles.departure.subtitle"));
        assertEquals("&6NOT_DISPLAY_TEXT", yaml.getString("speed_control.mode"));
    }

    @Test
    void languageMigrationConvertsExistingKeysBeforeMergingV2Defaults() throws Exception {
        Metro plugin = pluginWithResource("lang/en_US.yml", """
                lang-version: 2
                new_default: '<green>Default <line_id>'
                """);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(new ByteArrayInputStream("""
                line:
                  create_success: '&aCreated {line_id}'
                """.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));

        new MetroLanguageModernizationStep(plugin).migrate(new SimpleMigrationContext("lang/en_US.yml", yaml));

        assertEquals("<green>Created <line_id>", yaml.getString("line.create_success"));
        assertEquals("<green>Default <line_id>", yaml.getString("new_default"));
    }

    @Test
    void migrationStepsFormAnUnbrokenChainToTheCurrentConfigVersion() {
        Metro plugin = mock(Metro.class);

        assertEquals(1, new MetroConfigModernizationStep(plugin).fromVersion());
        assertEquals(new MetroConfigModernizationStep(plugin).toVersion(),
                new MetroMidRouteExitFareStep(plugin).fromVersion(),
                "a version with no step leaves upgraded servers without the new keys");
        assertEquals(MetroMigrations.CONFIG_VERSION, new MetroMidRouteExitFareStep(plugin).toVersion());
    }

    @Test
    void midRouteExitFareStepAddsTheNewKeyToAV2Config() throws Exception {
        Metro plugin = pluginWithResource("config.yml", """
                config-version: 3
                economy:
                  enabled: true
                  mid_route_exit_fare: NEXT_STOP
                """);
        YamlConfiguration yaml = yamlOf("""
                config-version: 2
                economy:
                  enabled: false
                """);

        new MetroMidRouteExitFareStep(plugin).migrate(new SimpleMigrationContext("config.yml", yaml));

        assertEquals("NEXT_STOP", yaml.getString("economy.mid_route_exit_fare"));
        assertFalse(yaml.getBoolean("economy.enabled"), "an existing choice must survive the merge");
    }

    @Test
    void midRouteExitFareStepKeepsAnExplicitExitLockAndWarnsAboutIt() throws Exception {
        Metro plugin = pluginWithResource("config.yml", """
                config-version: 3
                settings:
                  safe_mode:
                    passenger_exit_lock: false
                """);
        YamlConfiguration yaml = yamlOf("""
                config-version: 2
                settings:
                  safe_mode:
                    passenger_exit_lock: true
                """);
        CollectingMigrationContext context = new CollectingMigrationContext("config.yml", yaml);

        new MetroMidRouteExitFareStep(plugin).migrate(context);

        assertTrue(yaml.getBoolean("settings.safe_mode.passenger_exit_lock"),
                "the admin's lock choice must not be flipped silently");
        assertEquals(1, context.warnings.size());
        assertTrue(context.warnings.get(0).contains("passenger_exit_lock"));
    }

    @Test
    void midRouteExitFareStepStaysQuietWhenTheExitLockIsAlreadyOff() throws Exception {
        Metro plugin = pluginWithResource("config.yml", """
                config-version: 3
                settings:
                  safe_mode:
                    passenger_exit_lock: false
                """);
        YamlConfiguration yaml = yamlOf("""
                config-version: 2
                settings:
                  safe_mode:
                    passenger_exit_lock: false
                """);
        CollectingMigrationContext context = new CollectingMigrationContext("config.yml", yaml);

        new MetroMidRouteExitFareStep(plugin).migrate(context);

        assertTrue(context.warnings.isEmpty());
    }

    @Test
    void languageMergeStepAddsTheMidRouteExitMessageToAV2File() throws Exception {
        Metro plugin = pluginWithResource("lang/en_US.yml", """
                lang-version: 3
                economy:
                  paid_distance: '<green>Distance fare'
                  paid_mid_route_exit: '<green>Left mid-route'
                """);
        YamlConfiguration yaml = yamlOf("""
                lang-version: 2
                economy:
                  paid_distance: '<gold>My own wording'
                """);

        new MergeBundledDefaultsStep(plugin, 2, MetroMigrations.LANG_VERSION, "language")
                .migrate(new SimpleMigrationContext("lang/en_US.yml", yaml));

        assertEquals("<green>Left mid-route", yaml.getString("economy.paid_mid_route_exit"));
        assertEquals("<gold>My own wording", yaml.getString("economy.paid_distance"));
    }

    private YamlConfiguration yamlOf(String content) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    private static final class CollectingMigrationContext implements MigrationContext {
        private final String resourcePath;
        private final YamlConfiguration yaml;
        private final List<String> warnings = new ArrayList<>();

        private CollectingMigrationContext(String resourcePath, YamlConfiguration yaml) {
            this.resourcePath = resourcePath;
            this.yaml = yaml;
        }

        @Override
        public File file() {
            return new File(resourcePath);
        }

        @Override
        public String resourcePath() {
            return resourcePath;
        }

        @Override
        public YamlConfiguration yaml() {
            return yaml;
        }

        @Override
        public void warning(String path, String message) {
            warnings.add(path + ": " + message);
        }

        @Override
        public void fail(String path, String message) {
            throw new AssertionError(path + ": " + message);
        }
    }

    private YamlConfiguration load(String resourcePath) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertTrue(inputStream != null, () -> "Missing resource: " + resourcePath);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private Metro pluginWithResource(String resourcePath, String content) {
        Metro plugin = mock(Metro.class);
        when(plugin.getResource(resourcePath)).thenAnswer(invocation ->
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }

    private record SimpleMigrationContext(String resourcePath, YamlConfiguration yaml) implements MigrationContext {
        @Override
        public File file() {
            return new File(resourcePath);
        }

        @Override
        public void warning(String path, String message) {
        }

        @Override
        public void fail(String path, String message) {
            throw new AssertionError(path + ": " + message);
        }
    }
}
