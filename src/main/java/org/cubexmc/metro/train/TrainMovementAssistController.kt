package org.cubexmc.metro.train

import kotlin.math.max
import org.bukkit.entity.Minecart
import org.bukkit.util.Vector

/**
 * Handles safe-mode movement assist for stalled minecarts.
 */
class TrainMovementAssistController(
    private val session: TrainSession,
    private val trainScheduler: TrainScheduler,
    private val physicsController: TrainPhysicsController,
    private val cancelHandler: Runnable,
    private val passengerExitHandler: Runnable,
) {
    private var movementAssistTaskId: Any? = null

    fun start() {
        stop()
        val minecart = session.minecart ?: return
        val config = session.plugin.configFacade
        val cruiseEnabled = config.isCruiseControlEnabled()
        if (!config.isSafeModeMovementAssist() && !cruiseEnabled) {
            return
        }
        val interval = if (cruiseEnabled) {
            max(1L, config.getCruiseControlIntervalTicks())
        } else {
            max(1L, config.getSafeModeStallRecoveryTicks())
        }
        movementAssistTaskId = trainScheduler.entityRun(
            minecart,
            Runnable { recoverStalledMinecart() },
            interval,
            interval,
        )
    }

    fun stop() {
        trainScheduler.cancel(movementAssistTaskId)
        movementAssistTaskId = null
    }

    private fun recoverStalledMinecart() {
        val config = session.plugin.configFacade
        val cruiseEnabled = config.isCruiseControlEnabled()
        if (!config.isSafeModeMovementAssist() && !cruiseEnabled) {
            stop()
            return
        }
        val minecart = session.minecart
        if (minecart == null || minecart.isDead || !minecart.isValid) {
            cancelHandler.run()
            return
        }
        if (!physicsController.canRecoverStalledMinecart(session)) {
            return
        }
        if (!session.isPassengerStillRiding()) {
            passengerExitHandler.run()
            return
        }
        val lastTravelDirection = session.lastTravelDirection ?: return

        if (cruiseEnabled) {
            driveAtCruiseSpeed(minecart, lastTravelDirection, config.getCruiseControlTargetSpeed())
            return
        }

        val minCruiseSpeed = max(0.01, config.getSafeModeMinCruiseSpeed())
        if (!physicsController.isBelowCruiseSpeed(minecart, minCruiseSpeed)) {
            return
        }

        val targetSpeed = physicsController.resolveAssistSpeed(
            minecart,
            config.getCartSpeed(),
            minCruiseSpeed,
        )
        minecart.velocity = physicsController.buildAssistVelocity(lastTravelDirection, targetSpeed)
    }

    /**
     * Keeps the cart at its configured speed instead of waiting for vanilla
     * powered rails, which plateau around 1.5 blocks/tick.
     */
    private fun driveAtCruiseSpeed(minecart: Minecart, direction: Vector, configuredSpeed: Double) {
        val targetSpeed = physicsController.resolveCruiseSpeed(minecart, configuredSpeed)
        if (targetSpeed <= 0.0) {
            return
        }
        // 只在明显低于目标速度时补推，避免每 tick 覆盖原版物理
        if (!physicsController.isBelowCruiseSpeed(minecart, targetSpeed * CRUISE_ENGAGE_RATIO)) {
            return
        }
        minecart.velocity = physicsController.buildAssistVelocity(direction, targetSpeed)
    }

    private companion object {
        /** 低于目标速度这个比例时才补推 */
        const val CRUISE_ENGAGE_RATIO = 0.95
    }
}
