package org.cubexmc.metro.config

import java.util.Locale

/**
 * How a passenger leaving the minecart between two stops is charged.
 *
 * Only relevant when `settings.safe_mode.passenger_exit_lock` is off, i.e.
 * when passengers are actually allowed to dismount mid-route.
 */
enum class MidRouteExitFare {
    /**
     * Charge as if the passenger had ridden through to the next stop, so
     * dismounting early is never cheaper than staying on board.
     */
    NEXT_STOP,

    /** Charge only what was actually travelled. */
    ACTUAL,

    /** Charge nothing on top of what has already been settled. */
    NONE,

    ;

    companion object {
        @JvmStatic
        fun parse(raw: String?): MidRouteExitFare {
            if (raw == null || raw.isBlank()) {
                return NEXT_STOP
            }
            return entries.firstOrNull { it.name == raw.trim().uppercase(Locale.ROOT) } ?: NEXT_STOP
        }
    }
}
