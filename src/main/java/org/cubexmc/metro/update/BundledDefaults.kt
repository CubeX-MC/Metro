package org.cubexmc.metro.update

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.bukkit.configuration.file.YamlConfiguration
import org.cubexmc.config.MigrationContext
import org.cubexmc.metro.Metro

/**
 * Copies keys the bundled resource has but the server's file does not.
 *
 * Existing values are never touched, so an admin's choices survive every
 * upgrade; only genuinely new keys are added.
 */
internal object BundledDefaults {
    fun mergeMissing(plugin: Metro, context: MigrationContext, label: String) {
        try {
            plugin.getResource(context.resourcePath()).use { inputStream ->
                if (inputStream == null) {
                    context.fail(context.resourcePath(), "Bundled $label resource is missing.")
                    return
                }
                val defaults = YamlConfiguration.loadConfiguration(
                    InputStreamReader(inputStream, StandardCharsets.UTF_8),
                )
                for (key in defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !context.yaml().contains(key)) {
                        context.yaml().set(key, defaults.get(key))
                    }
                }
            }
        } catch (ex: Exception) {
            context.fail(context.resourcePath(), "Failed to merge $label defaults: ${ex.message}")
        }
    }
}
