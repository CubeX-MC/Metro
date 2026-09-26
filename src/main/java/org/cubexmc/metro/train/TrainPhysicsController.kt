package org.cubexmc.metro.train

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import org.bukkit.Material
import org.bukkit.block.data.Powerable
import org.bukkit.entity.Minecart
import org.bukkit.util.Vector
import org.cubexmc.metro.util.LocationUtil

/**
 * Minecart speed, launch and stall recovery calculations for train sessions.
 */
class TrainPhysicsController {
    private var experimentalBrakeCart: Minecart? = null
    private var experimentalUnbrakedSpeed = 0.0
    private var experimentalAppliedLimit = 0.0

    fun resetExperimentalApproachBraking() {
        experimentalBrakeCart = null
    }
    fun applyApproachBraking(minecart: Minecart, distance: Double, defaultMaxSpeed: Double) {
        val minSpeed = 0.1
        val speedRatio = min(1.0, distance / 15.0)
        val targetSpeed = minSpeed + (defaultMaxSpeed - minSpeed) * speedRatio.pow(0.7)

        val currentMaxSpeed = minecart.maxSpeed
        if (currentMaxSpeed > 0.0) {
            minecart.maxSpeed = min(currentMaxSpeed, targetSpeed)
        }
    }

    /** Leave room for the next native tick, including its powered-rail boost. */
    fun applyExperimentalApproachBraking(minecart: Minecart, distance: Double) {
        val currentMaxSpeed = minecart.maxSpeed
        if (currentMaxSpeed <= 0.0) {
            resetExperimentalApproachBraking()
            return
        }
        // Preserve a cap set by the line or BLOCK_BASED controller. Restore it when
        // a winding approach moves away from the stop instead of staying slowed forever.
        if (experimentalBrakeCart !== minecart || currentMaxSpeed != experimentalAppliedLimit) {
            experimentalUnbrakedSpeed = currentMaxSpeed
        }
        experimentalBrakeCart = minecart
        // Begin outside the station region: one experimental tick can cross it entirely.
        // A small positive floor lets native rails finish the approach into the 0.8-block stop radius.
        experimentalAppliedLimit = min(experimentalUnbrakedSpeed, max(0.1, (distance - 0.8) / 2.0))
        if (experimentalAppliedLimit != currentMaxSpeed) {
            minecart.maxSpeed = experimentalAppliedLimit
        }
    }
    fun initMinecartVelocity(minecart: Minecart, yaw: Float): Vector? {
        val location = minecart.location
        val block = location.block
        val blockData = block.blockData

        if (block.type != Material.POWERED_RAIL || blockData !is Powerable || !blockData.isPowered) {
            return null
        }

        val launchRad = Math.toRadians(yaw.toDouble())
        val direction = Vector(
            -sin(launchRad),
            0.0,
            cos(launchRad),
        )
        val normalizedDirection = direction.clone().normalize()
        minecart.velocity = direction.multiply(0.4)
        return normalizedDirection
    }

    fun canRecoverStalledMinecart(session: TrainSession): Boolean {
        val minecart = session.minecart
        return minecart != null &&
            !minecart.isDead &&
            minecart.isValid &&
            session.state == TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS &&
            minecart.maxSpeed > 0.0 &&
            LocationUtil.isOnRail(minecart.location) &&
            session.lastTravelDirection != null
    }

    fun isBelowCruiseSpeed(minecart: Minecart, minCruiseSpeed: Double): Boolean {
        val velocity = minecart.velocity
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        return horizontalSpeed < minCruiseSpeed
    }

    fun buildAssistVelocity(lastTravelDirection: Vector, targetSpeed: Double): Vector =
        lastTravelDirection.clone().normalize().multiply(targetSpeed)

    fun resolveAssistSpeed(minecart: Minecart, configuredSpeed: Double, minCruiseSpeed: Double): Double {
        val maxSpeed = minecart.maxSpeed
        val targetSpeed = if (configuredSpeed > 0.0) min(configuredSpeed, maxSpeed) else maxSpeed
        return max(minCruiseSpeed, targetSpeed)
    }
}
