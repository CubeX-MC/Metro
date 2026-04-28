package org.cubexmc.metro.update;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LanguageResourceTest {

    private static final String[] LANGUAGES = {
            "zh_CN", "zh_TW", "en_US", "de_DE", "es_ES", "nl_NL"
    };

    @Test
    void bundledLanguagesShouldContainAllEnglishMessageKeys() {
        Set<String> baselineKeys = leafKeys(loadLanguage("en_US"));
        List<String> missingMessages = new ArrayList<>();

        for (String language : LANGUAGES) {
            YamlConfiguration configuration = loadLanguage(language);
            Set<String> missingKeys = new TreeSet<>(baselineKeys);
            missingKeys.removeAll(leafKeys(configuration));
            if (!missingKeys.isEmpty()) {
                missingMessages.add(language + " is missing language keys: " + missingKeys);
            }
        }

        assertTrue(missingMessages.isEmpty(), () -> String.join(System.lineSeparator(), missingMessages));
    }

    private YamlConfiguration loadLanguage(String language) {
        String path = "lang/" + language + ".yml";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        assertTrue(inputStream != null, () -> "Missing language resource: " + path);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private Set<String> leafKeys(YamlConfiguration configuration) {
        Set<String> keys = new TreeSet<>();
        for (String key : configuration.getKeys(true)) {
            if (!configuration.isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }
}
