package org.cubexmc.metro.train

import org.bukkit.entity.Entity
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Line

/**
 * Mutable runtime data for one active train ride.
 */
class TrainSession(
    val plugin: Metro,
    var minecart: Minecart?,
    val passenger: Player?,
    val line: Line?,
    var currentStopId: String?,
    state: TrainMovementTask.TrainState,
) {
    var targetStopId: String? = line?.getNextStopId(currentStopId)
    var state: TrainMovementTask.TrainState =
        if (targetStopId == null) TrainMovementTask.TrainState.STOPPED_AT_STATION else state
    var isTeleporting: Boolean = false
    var lastTravelDirection: Vector? = null
    var lastSettledStopId: String? = currentStopId
        private set
    var distanceTraveled: Double = 0.0
        private set

    /**
     * Everything the passenger has paid for this trip, starting with the base
     * price charged when boarding. Used to cap the trip total at `max_price`.
     */
    var fareChargedForTrip: Double = line?.priceRule?.getBasePrice() ?: 0.0
        private set

    fun addDistance(blocks: Double): Double {
        distanceTraveled += blocks
        return distanceTraveled
    }

    fun addFareCharged(amount: Double) {
        if (amount > 0.0) {
            fareChargedForTrip += amount
        }
    }

    fun markFareSettledAt(stopId: String?) {
        lastSettledStopId = stopId
        distanceTraveled = 0.0
    }

    fun refreshTargetFromCurrentStop() {
        targetStopId = line?.getNextStopId(currentStopId)
    }

    fun isPassengerStillRiding(): Boolean {
        val currentPassenger = passenger
        if (currentPassenger == null || !currentPassenger.isOnline) {
            return false
        }

        val vehicle: Entity? = currentPassenger.vehicle
        return vehicle != null && vehicle == minecart
    }

    fun safePassengerName(): String = passenger?.name ?: "unknown"

    fun debug(message: String) {
        plugin.debug("train_state_transitions", message)
    }
}
