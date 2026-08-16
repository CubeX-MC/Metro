package org.cubexmc.metro.update

import org.bukkit.configuration.ConfigurationSection
import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.MetroTextRenderer

class MetroLanguageModernizationStep(private val plugin: Metro) : MigrationStep {
    override fun fromVersion(): Int = 1

    override fun toVersion(): Int = 2

    override fun description(): String = "Convert Metro language text to MiniMessage and merge v2 defaults."

    override fun migrate(context: MigrationContext) {
        convertSection(context.yaml())
        BundledDefaults.mergeMissing(plugin, context, "language")
    }

    private fun convertSection(section: ConfigurationSection?) {
        if (section == null) {
            return
        }
        for (key in section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                convertSection(section.getConfigurationSection(key))
            } else if (section.isString(key)) {
                section.set(key, MetroTextRenderer.convertLegacyTemplate(section.getString(key, "")))
            } else if (section.isList(key)) {
                val values = section.getList(key)
                if (values != null && values.all { value -> value is String }) {
                    section.set(
                        key,
                        values.map { value -> MetroTextRenderer.convertLegacyTemplate(value as String) },
                    )
                }
            }
        }
    }

}
