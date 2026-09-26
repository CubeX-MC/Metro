package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep
import org.cubexmc.metro.Metro

/** Add contextual building feedback while preserving customized translations. */
class MetroBuildUxLanguageStep(private val plugin: Metro) : MigrationStep {
    override fun fromVersion(): Int = 3
    override fun toVersion(): Int = 4
    override fun description(): String = "Add station placement feedback and optional command arguments."
    override fun migrate(context: MigrationContext) {
        val yaml = context.yaml()
        for (domain in listOf("line", "stop")) {
            for (key in listOf("help_create", "usage_create")) {
                val path = "$domain.$key"
                yaml.getString(path)?.let {
                    yaml.set(path, it.replace("\\<display_name>", "[display_name]")
                        .replace("\\<\"display_name\">", "[display_name]"))
                }
            }
        }
        for (path in listOf("stop.help_setcorners", "stop.usage_setcorners", "line.help_addstop", "line.usage_addstop")) {
            yaml.getString(path)?.let { yaml.set(path, it.replace("\\<stop_id>", "[stop_id]")) }
        }
        yaml.getString("stop.title_types")?.takeIf { !it.contains("waiting") }?.let {
            yaml.set("stop.title_types", "$it, waiting")
        }
        BundledDefaults.mergeMissing(plugin, context, "language")
    }
}
