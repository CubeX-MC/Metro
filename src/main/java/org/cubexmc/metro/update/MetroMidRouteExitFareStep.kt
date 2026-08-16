package org.cubexmc.metro.update

import org.cubexmc.config.MigrationContext
import org.cubexmc.metro.Metro

/**
 * config v2 -> v3: adds `economy.mid_route_exit_fare`.
 *
 * `settings.safe_mode.passenger_exit_lock` changed its bundled default from
 * `true` to `false` in the same release, but a server that already has the key
 * keeps whatever it says: silently unlocking a line an admin deliberately
 * locked is not this step's call. Servers still on the old default are told
 * about it instead.
 */
class MetroMidRouteExitFareStep(plugin: Metro) :
    MergeBundledDefaultsStep(plugin, 2, MetroMigrations.CONFIG_VERSION, "config") {

    override fun description(): String =
        "Add economy.mid_route_exit_fare and report a still-enabled passenger exit lock."

    override fun migrate(context: MigrationContext) {
        super.migrate(context)
        if (context.yaml().getBoolean(EXIT_LOCK_PATH, false)) {
            context.warning(
                EXIT_LOCK_PATH,
                "Passengers still cannot leave a moving train. This default is now off, because a train " +
                    "that stops short of a station traps whoever is aboard. Set it to false unless a line " +
                    "passes through areas players must not reach; mid-route fares are covered by " +
                    "economy.mid_route_exit_fare.",
            )
        }
    }

    private companion object {
        const val EXIT_LOCK_PATH = "settings.safe_mode.passenger_exit_lock"
    }
}
