package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep

/** v4 -> v5 removes the ineffective velocity-based cruise control. */
class MetroRemoveCruiseControlStep : MigrationStep {
    override fun fromVersion(): Int = 4
    override fun toVersion(): Int = 5
    override fun description(): String = "Remove obsolete speed_control.cruise_control settings."

    override fun migrate(context: MigrationContext) {
        context.yaml().set("speed_control.cruise_control", null)
    }
}