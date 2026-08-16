package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.MetroTextRenderer

class MetroConfigModernizationStep(private val plugin: Metro) : MigrationStep {
    override fun fromVersion(): Int = 1

    override fun toVersion(): Int = 2

    override fun description(): String = "Convert Metro config display templates to MiniMessage and merge v2 defaults."

    override fun migrate(context: MigrationContext) {
        ConfigUpdater.migrateLegacyEnterStop(context.yaml())
        for (path in DISPLAY_PATHS) {
            if (context.yaml().isString(path)) {
                context.yaml().set(path, MetroTextRenderer.convertLegacyTemplate(context.yaml().getString(path, "")))
            }
        }
        BundledDefaults.mergeMissing(plugin, context, "config")
    }

    companion object {
        @JvmField
        val DISPLAY_PATHS: List<String> = listOf(
            "titles.enter_stop.title",
            "titles.enter_stop.subtitle",
            "titles.enter_stop.actionbar",
            "titles.enter_stop.start_stop.title",
            "titles.enter_stop.start_stop.subtitle",
            "titles.enter_stop.start_stop.actionbar",
            "titles.enter_stop.end_stop.title",
            "titles.enter_stop.end_stop.subtitle",
            "titles.enter_stop.end_stop.actionbar",
            "titles.stop_continuous.title",
            "titles.stop_continuous.subtitle",
            "titles.stop_continuous.actionbar",
            "titles.stop_continuous.start_stop.title",
            "titles.stop_continuous.start_stop.subtitle",
            "titles.stop_continuous.start_stop.actionbar",
            "titles.stop_continuous.end_stop.title",
            "titles.stop_continuous.end_stop.subtitle",
            "titles.stop_continuous.end_stop.actionbar",
            "titles.arrive_stop.title",
            "titles.arrive_stop.subtitle",
            "titles.terminal_stop.title",
            "titles.terminal_stop.subtitle",
            "titles.departure.title",
            "titles.departure.subtitle",
            "titles.departure.actionbar",
            "titles.waiting.title",
            "titles.waiting.subtitle",
            "titles.waiting.actionbar",
            "scoreboard.styles.current_stop",
            "scoreboard.styles.passed_stop",
            "scoreboard.styles.waiting_next_stop",
            "scoreboard.styles.moving_next_stop",
            "scoreboard.styles.terminal_stop",
            "scoreboard.styles.next_stop",
            "scoreboard.styles.other_stops",
            "scoreboard.styles.folding_symbol",
        )
    }
}
