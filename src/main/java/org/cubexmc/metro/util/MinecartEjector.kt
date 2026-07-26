package org.cubexmc.metro.util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Minecart

/**
 * Ejects passengers on the plugin's behalf.
 *
 * `Minecart.eject()` fires a cancellable `VehicleExitEvent`, which the
 * passenger exit lock (`settings.safe_mode.passenger_exit_lock`) cancels while
 * a train is in transit. Plugin-driven ejects — portals, derailment cleanup,
 * shutdown — must not be blocked by that lock, so they go through here and are
 * marked for the duration of the call.
 */
object MinecartEjector {
    private val ejecting: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun eject(minecart: Minecart) {
        val id = minecart.uniqueId
        ejecting.add(id)
        try {
            minecart.eject()
        } finally {
            ejecting.remove(id)
        }
    }

    @JvmStatic
    fun isPluginEjecting(minecart: Minecart): Boolean = ejecting.contains(minecart.uniqueId)
}
