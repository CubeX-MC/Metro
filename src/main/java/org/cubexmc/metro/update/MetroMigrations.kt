package org.cubexmc.metro.update

import org.cubexmc.config.MigrationException
import org.cubexmc.config.MigrationFailurePolicy
import org.cubexmc.config.MigrationPlan
import org.cubexmc.config.MigrationRunner
import org.cubexmc.config.ResourceFiles
import org.cubexmc.metro.Metro

object MetroMigrations {
    const val CONFIG_VERSION: Int = 3
    const val LANG_VERSION: Int = 3

    @JvmField
    val BUNDLED_LANGUAGES: List<String> = listOf("zh_CN", "zh_TW", "en_US", "de_DE", "es_ES", "nl_NL", "tr_TR")

    @JvmStatic
    fun ensureConfigResources(plugin: Metro) {
        val resources = ResourceFiles(plugin)
        resources.saveIfMissing("config.yml")
        resources.saveIfMissing("lines.yml")
        resources.saveIfMissing("stops.yml")
    }

    @JvmStatic
    @Throws(MigrationException::class)
    fun migrateConfig(plugin: Metro) {
        MigrationRunner(plugin).run(
            MigrationPlan.yaml("Metro config", "config.yml")
                .versionKey("config-version")
                .missingVersion(1)
                .targetVersion(CONFIG_VERSION)
                .failurePolicy(MigrationFailurePolicy.ABORT)
                .addStep(MetroConfigModernizationStep(plugin))
                .addStep(MetroMidRouteExitFareStep(plugin)),
        )
    }

    @JvmStatic
    fun ensureLanguageResources(plugin: Metro) {
        val resources = ResourceFiles(plugin)
        for (language in BUNDLED_LANGUAGES) {
            resources.saveIfMissing(languagePath(language))
        }
    }

    @JvmStatic
    @Throws(MigrationException::class)
    fun migrateBundledLanguages(plugin: Metro) {
        for (language in BUNDLED_LANGUAGES) {
            migrateLanguage(plugin, language)
        }
    }

    @JvmStatic
    @Throws(MigrationException::class)
    fun migrateLanguage(plugin: Metro, language: String) {
        MigrationRunner(plugin).run(
            MigrationPlan.yaml("Metro language $language", languagePath(language))
                .versionKey("lang-version")
                .missingVersion(1)
                .targetVersion(LANG_VERSION)
                .failurePolicy(MigrationFailurePolicy.ABORT)
                .addStep(MetroLanguageModernizationStep(plugin))
                .addStep(MergeBundledDefaultsStep(plugin, 2, LANG_VERSION, "language")),
        )
    }

    @JvmStatic
    fun languagePath(language: String): String = "lang/$language.yml"
}
