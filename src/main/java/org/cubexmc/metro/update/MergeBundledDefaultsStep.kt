package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep
import org.cubexmc.metro.Metro

/**
 * Version bump that only needs the newly bundled keys copied in.
 *
 * Used for releases that add settings without changing the meaning of any
 * existing one.
 */
open class MergeBundledDefaultsStep(
    private val plugin: Metro,
    private val from: Int,
    private val to: Int,
    private val label: String,
) : MigrationStep {
    override fun fromVersion(): Int = from

    override fun toVersion(): Int = to

    override fun description(): String = "Merge new Metro $label defaults for v$to."

    override fun migrate(context: MigrationContext) {
        BundledDefaults.mergeMissing(plugin, context, label)
    }
}
