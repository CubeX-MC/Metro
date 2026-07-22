package org.cubexmc.metro.train

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.util.Vector
import org.cubexmc.metro.Metro
import org.cubexmc.metro.event.TrainEnterStopEvent
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.PriceRule
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.service.TicketService
import org.cubexmc.metro.util.SchedulerUtil

/**
 * Controls one event-driven train ride from a stop to the next stop.
 */
class TrainMovementTask @JvmOverloads constructor(
    plugin: Metro,
    minecart: Minecart?,
    passenger: Player?,
    lineId: String?,
    fromStopId: String?,
    initialState: TrainState = TrainState.STOPPED_AT_STATION,
) : Listener {
    enum class TrainState {
        STOPPED_AT_STATION,
        MOVING_IN_STATION,
        MOVING_BETWEEN_STATIONS,
    }

    private val session: TrainSession
    private val stateMachine: TrainStateMachine
    private val trainScheduler: TrainScheduler
    private val physicsController: TrainPhysicsController
    private val eventPublisher: TrainEventPublisher
    private val scoreboardController: TrainScoreboardController
    private val movementAssistController: TrainMovementAssistController

    init {
        val lineManager = plugin.lineManager
        val line = lineManager.getLine(lineId)
        session = TrainSession(plugin, minecart, passenger, line, fromStopId, initialState)
        stateMachine = TrainStateMachine(session)
        trainScheduler = TrainScheduler(plugin)
        physicsController = TrainPhysicsController()
        eventPublisher = TrainEventPublisher(session)
        scoreboardController = TrainScoreboardController()
        movementAssistController = TrainMovementAssistController(
            session,
            trainScheduler,
            physicsController,
            Runnable { cancel() },
            Runnable { handlePassengerExit() },
        )

        if (line != null && session.targetStopId != null) {
            scoreboardController.updateBasedOnState(session)
        }
    }

    fun cancel() {
        val minecart = session.minecart
        session.plugin.bedrockCompatibility.onTrainDeparture(minecart)
        TrainTaskRegistry.unregister(minecart)
        movementAssistController.stop()
        trainScheduler.cancelAll()
        HandlerList.unregisterAll(this)
        session.debug(
            "Task cancelled for passenger=${session.safePassengerName()}, currentStop=${session.currentStopId}, " +
                "targetStop=${session.targetStopId}",
        )
    }

    fun removeMinecartAndCancel() {
        val minecart = session.minecart
        if (minecart != null && !minecart.isDead) {
            minecart.eject()
            minecart.remove()
        }
        cancel()
    }

    fun removeMinecartAndCancelOnEntityScheduler() {
        val minecart = session.minecart
        if (minecart == null) {
            cancel()
            return
        }
        SchedulerUtil.entityRun(session.plugin, minecart, Runnable { removeMinecartAndCancel() }, 0L, -1L)
    }

    fun transferMinecart(newCart: Minecart) {
        val previousCart = session.minecart
        session.minecart = newCart
        session.isTeleporting = false
        TrainTaskRegistry.transfer(previousCart, newCart, this)
        val line = session.line
        if (line != null && session.plugin.routeRecorder != null) {
            session.plugin.routeRecorder.transferCart(line.id, previousCart, newCart)
        }
        if (session.state == TrainState.MOVING_BETWEEN_STATIONS) {
            movementAssistController.start()
        }
        session.debug("Transferred movement task to new minecart UUID=${newCart.uniqueId}")
    }

    fun setTeleporting(teleporting: Boolean) {
        session.isTeleporting = teleporting
    }

    fun canUsePortal(portalId: String): Boolean {
        val line = session.line
        return line != null && line.containsPortal(portalId)
    }

    fun scheduleSessionTask(task: Runnable, delay: Long, period: Long): Any? {
        val minecart = session.minecart ?: return null
        return trainScheduler.entityRun(minecart, task, delay, period)
    }

    fun getSession(): TrainSession = session

    @EventHandler(priority = EventPriority.NORMAL)
    fun onTrainEnterStop(event: TrainEnterStopEvent) {
        if (event.minecart != session.minecart) {
            return
        }

        if (!session.isTeleporting && !session.isPassengerStillRiding()) {
            handlePassengerExit()
            return
        }

        val enteredStop = event.stop
        if (enteredStop != null && session.targetStopId != null && session.targetStopId == enteredStop.id) {
            transitionToMovingInStation(enteredStop)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleMove(event: VehicleMoveEvent) {
        val minecart = session.minecart
        if (event.vehicle != minecart || minecart == null) {
            return
        }

        val line = session.line
        if (line != null) {
            session.plugin.routeRecorder.sample(line.id, minecart, event.to)
        }
        updateLastTravelDirection(event.from, event.to)

        if (session.state == TrainState.MOVING_BETWEEN_STATIONS) {
            val from = event.from
            val to = event.to
            if (from.world != null && to.world != null && from.world == to.world) {
                val dx = to.x - from.x
                val dz = to.z - from.z
                val distance = kotlin.math.sqrt(dx * dx + dz * dz)
                if (distance > 0.001) {
                    session.addDistance(distance)
                }
            }
        }

        if (session.state != TrainState.MOVING_IN_STATION) {
            return
        }

        val targetStop = session.plugin.stopManager.getStop(session.targetStopId)
        val targetStopLocation = targetStop?.stopPointLocation ?: return
        val currentLocation = minecart.location
        if (currentLocation.world == null || currentLocation.world != targetStopLocation.world) {
            return
        }

        val distance = currentLocation.distance(targetStopLocation)
        if (distance < 0.8) {
            transitionToStoppedAtStation(targetStop)
            return
        }

        physicsController.applyApproachBraking(minecart, distance, session.plugin.configFacade.getCartSpeed())
    }

    private fun transitionToStoppedAtStation(stop: Stop) {
        val minecart = session.minecart ?: return
        minecart.velocity = Vector(0, 0, 0)
        minecart.maxSpeed = 0.0
        movementAssistController.stop()
        session.plugin.bedrockCompatibility.onTrainArrival(session.passenger, minecart)

        // 先结算里程费用，确保即使 stopPointLocation 为 null 也能正常扣费
        settleDistanceFare(stop)

        val baseLocation = stop.stopPointLocation ?: return
        val snapLocation = baseLocation.clone()
        snapLocation.x = snapLocation.blockX + 0.5
        snapLocation.z = snapLocation.blockZ + 0.5
        val line = session.line
        if (line != null) {
            session.plugin.routeRecorder.sample(line.id, minecart, snapLocation)
        }

        val previousState = stateMachine.transitionTo(TrainState.STOPPED_AT_STATION, null)
        if (previousState == TrainState.MOVING_IN_STATION) {
            handleArrivalAtStation()
        }

        scoreboardController.updateBasedOnState(session)
    }

    private fun settleDistanceFare(stop: Stop) {
        val line = session.line ?: return
        val rule = line.priceRule ?: return
        val distance = session.distanceTraveled
        if (distance <= 0 && rule.getMode() != PriceRule.PricingMode.INTERVAL) {
            return
        }

        val passenger = session.passenger
        if (passenger == null || !passenger.isOnline) {
            return
        }

        val rawVariablePrice = when (rule.getMode()) {
            PriceRule.PricingMode.DISTANCE -> distance * rule.getPerBlockRate()
            PriceRule.PricingMode.INTERVAL -> {
                val intervals = session.plugin.priceService.countStopIntervals(line, session.entryStopId, stop.id)
                intervals * rule.getPerIntervalRate()
            }

            else -> return
        }

        // 应用 maxPrice 上限：整个行程的总费用不得超过 maxPrice
        val variablePrice = if (rawVariablePrice > 0) {
            val maxPrice = rule.getMaxPrice()
            if (maxPrice > 0.0) {
                val remaining = maxPrice - rule.getBasePrice() - session.totalVariableCharged
                if (remaining <= 0) 0.0 else minOf(rawVariablePrice, remaining)
            } else {
                rawVariablePrice
            }
        } else {
            0.0
        }

        // 应用时间折扣
        val gameTime = passenger.world?.time ?: 6000L
        val discount = rule.getActiveDiscountMultiplier(gameTime)
        val finalPrice = if (variablePrice > 0 && discount < 1.0) {
            variablePrice * discount
        } else {
            variablePrice
        }

        if (finalPrice > 0) {
            val status = session.plugin.ticketService.chargePrice(passenger, line, finalPrice)
            if (status == TicketService.TicketChargeStatus.CHARGED) {
                session.totalVariableCharged += variablePrice // 按上限前价格记录，确保 maxPrice 正确生效
                passenger.sendMessage(
                    session.plugin.languageManager.getMessage(
                        "economy.paid_distance",
                        LanguageManager.put(
                            LanguageManager.args(),
                            "price",
                            session.plugin.ticketService.format(finalPrice),
                        ),
                    ),
                )
            }
        }

        session.addDistance(-distance)

        // 更新 entryStopId 为当前站，确保 INTERVAL 模式下下一段区间从当前站开始计算
        session.entryStopId = stop.id
    }

    private fun transitionToMovingInStation(targetStop: Stop) {
        movementAssistController.stop()

        val previousState = stateMachine.transitionTo(
            TrainState.MOVING_IN_STATION,
            "enteredStop=${targetStop.id}",
        )
        if (previousState == TrainState.MOVING_BETWEEN_STATIONS) {
            eventPublisher.publishEnteringStop(targetStop)
        }

        scoreboardController.updateBasedOnState(session)
    }

    private fun transitionToMovingBetweenStations() {
        stateMachine.transitionTo(TrainState.MOVING_BETWEEN_STATIONS, null)

        val passenger = session.passenger
        if (passenger != null && passenger.isOnline) {
            passenger.sendTitle("", "", 0, 0, 0)
        }
        scoreboardController.updateBasedOnState(session)
    }

    private fun handleArrivalAtStation() {
        handleArrivalAtStation(false)
    }

    fun startAtStation() {
        handleArrivalAtStation(true)
    }

    private fun handleArrivalAtStation(isNewlySpawned: Boolean) {
        val line = session.line
        if (line == null) {
            cancel()
            return
        }

        if (!isNewlySpawned) {
            session.currentStopId = session.targetStopId
        }

        val currentStop = session.plugin.stopManager.getStop(session.currentStopId)
        if (currentStop == null) {
            session.debug("Cancelling train because current stop is missing: ${session.currentStopId}")
            cancel()
            return
        }

        if (line.getNextStopId(session.currentStopId) == null) {
            if (!handleTerminalStation()) {
                return
            }
            session.targetStopId = null
            eventPublisher.publishDockedAtStop(currentStop, true)
            return
        }

        eventPublisher.publishDockedAtStop(currentStop, false)
        scoreboardController.updateBasedOnState(session)
        scheduleNextDeparture()
    }

    private fun handleDeparture() {
        val line = session.line
        if (line == null) {
            cancel()
            return
        }

        session.refreshTargetFromCurrentStop()
        if (session.targetStopId == null) {
            return
        }

        val stopManager = session.plugin.stopManager
        val currentStop = stopManager.getStop(session.currentStopId)
        val nextStop = stopManager.getStop(session.targetStopId)
        if (currentStop == null || nextStop == null || currentStop.stopPointLocation == null) {
            session.debug(
                "Cancelling train because departure stops are incomplete: current=${session.currentStopId}, " +
                    "target=${session.targetStopId}",
            )
            cancel()
            return
        }

        val minecart = session.minecart ?: return
        session.plugin.routeRecorder.sample(line.id, minecart, currentStop.stopPointLocation)
        eventPublisher.publishDeparture(currentStop, nextStop)

        var maxSpeed = line.getMaxSpeed() ?: -1.0
        if (maxSpeed == -1.0) {
            maxSpeed = session.plugin.configFacade.getCartSpeed()
        }
        minecart.maxSpeed = maxSpeed
        session.plugin.bedrockCompatibility.onTrainDeparture(minecart)

        val launchDirection = physicsController.initMinecartVelocity(minecart, currentStop.launchYaw)
        if (launchDirection != null) {
            session.lastTravelDirection = launchDirection
        }

        transitionToMovingBetweenStations()
        movementAssistController.start()
    }

    private fun scheduleNextDeparture() {
        val delay = session.plugin.configFacade.getCartDepartureDelay()
        session.debug(
            "Schedule departure in $delay ticks for passenger=${session.safePassengerName()}, " +
                "currentStop=${session.currentStopId}",
        )
        val minecart = session.minecart ?: return
        trainScheduler.entityRun(
            minecart,
            Runnable {
                if (session.isPassengerStillRiding()) {
                    handleDeparture()
                } else {
                    handlePassengerExit()
                }
            },
            delay,
            -1,
        )
    }

    private fun handleTerminalStation(): Boolean {
        val passenger = session.passenger
        val line = session.line
        if (passenger == null || !passenger.isOnline || line == null) {
            cancel()
            return false
        }
        session.debug("Terminal station reached for passenger=${session.safePassengerName()}, stop=${session.currentStopId}")

        val routeResult = session.plugin.routeRecorder.finishIfRecording(line.id, session.minecart)
        notifyRouteRecorder(routeResult)
        if (routeResult.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS) {
            session.plugin.logger.warning(
                "[RouteRecorder] Route recording for line ${line.id} reached the terminal but only collected " +
                    "${routeResult.pointCount()} point(s).",
            )
        }

        val minecart = session.minecart
        if (minecart == null) {
            cancel()
            return false
        }

        trainScheduler.entityRun(
            minecart,
            Runnable {
                val currentMinecart = session.minecart
                if (currentMinecart != null && !currentMinecart.isDead) {
                    currentMinecart.eject()
                    session.plugin.scoreboardManager.clearPlayerDisplay(passenger)

                    trainScheduler.entityRun(
                        currentMinecart,
                        Runnable {
                            if (!currentMinecart.isDead) {
                                currentMinecart.remove()
                            }
                            cancel()
                        },
                        40L,
                        -1,
                    )
                } else {
                    cancel()
                }
            },
            60L,
            -1,
        )
        return true
    }

    private fun notifyRouteRecorder(result: org.cubexmc.metro.manager.RouteRecorder.FinishResult) {
        if (result.recorderId() == null ||
            result.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.NOT_RECORDING
        ) {
            return
        }
        val recorder = Bukkit.getPlayer(result.recorderId() as UUID)
        if (recorder == null || !recorder.isOnline) {
            return
        }

        val args = LanguageManager.args()
        LanguageManager.put(args, "line_id", result.lineId())
        LanguageManager.put(args, "point_count", result.pointCount().toString())

        val key = when (result.status()) {
            org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.SAVED -> "line.record_saved"
            org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS -> "line.record_too_few"
            org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.FAILED -> "line.record_failed"
            org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.NOT_RECORDING -> null
        }
        if (key != null) {
            recorder.sendMessage(session.plugin.languageManager.getMessage(key, args))
        }
        if (result.status() == org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status.TOO_FEW_POINTS) {
            recorder.sendMessage(session.plugin.languageManager.getMessage("line.record_too_few_hint"))
        }
    }

    private fun handlePassengerExit() {
        cancel()
    }

    private fun updateLastTravelDirection(from: Location?, to: Location?) {
        if (session.state == TrainState.STOPPED_AT_STATION || from == null || to == null) {
            return
        }
        if (from.world == null || to.world == null || from.world != to.world) {
            return
        }
        val direction = to.toVector().subtract(from.toVector())
        if (direction.lengthSquared() < 0.0001) {
            return
        }
        session.lastTravelDirection = direction.normalize()
    }

    companion object {
        @JvmStatic
        fun getTaskFor(cart: Minecart?): TrainMovementTask? = TrainTaskRegistry.get(cart)

        @JvmStatic
        fun shutdownActiveTasks(): Int = TrainTaskRegistry.shutdownActiveTasks()

        @JvmStatic
        fun shutdownActiveTasks(plugin: Metro, folia: Boolean): Int =
            TrainTaskRegistry.shutdownActiveTasks(plugin, folia)

        @JvmStatic
        fun startTrainTask(plugin: Metro, minecart: Minecart, passenger: Player?, lineId: String, currentStopId: String) {
            TrainTaskStarter.start(plugin, minecart, passenger, lineId, currentStopId)
        }
    }
}
