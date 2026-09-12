package org.cubexmc.metro.update

import org.cubexmc.metro.Metro

/**
 * config v3 -> v4: adds `economy.account`.
 *
 * Fares from a line **without an owner** used to be withdrawn and then simply
 * destroyed. This key names the server account they are paid into instead;
 * the bundled default is empty, which is exactly the old behaviour, so an
 * upgraded server keeps working the way it did until someone configures it.
 *
 * Owned lines are untouched: their fares still go to the line owner.
 */
class MetroEconomyAccountStep(plugin: Metro) :
    MergeBundledDefaultsStep(plugin, 3, MetroMigrations.CONFIG_VERSION, "config") {

    override fun description(): String =
        "Add economy.account (where fares from unowned lines are paid in)."
}
