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
import org.cubexmc.metro.util.MinecartEjector
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
    private val travelDisplayController: TrainTravelDisplayController
    private val movementAssistController: TrainMovementAssistController
    private var passengerExitHandled: Boolean = false

    init {
        val lineManager = plugin.lineManager
        val line = lineManager.getLine(lineId)
        session = TrainSession(plugin, minecart, passenger, line, fromStopId, initialState)
        stateMachine = TrainStateMachine(session)
        trainScheduler = TrainScheduler(plugin)
        physicsController = TrainPhysicsController()
        eventPublisher = TrainEventPublisher(session)
        scoreboardController = TrainScoreboardController()
        travelDisplayController = TrainTravelDisplayController()
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
            MinecartEjector.eject(minecart)
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

    /** Whether the train is docked, i.e. passengers may leave. */
    fun isStoppedAtStation(): Boolean = session.state == TrainState.STOPPED_AT_STATION

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

        travelDisplayController.onTrainMoved(session)

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

        // Settle before the route sampling below: a stop whose stop point has
        // been cleared must still charge the distance already travelled, and
        // must still leave MOVING_IN_STATION.
        settleVariableFare(stop)

        val baseLocation = stop.stopPointLocation
        val line = session.line
        if (baseLocation != null && line != null) {
            val snapLocation = baseLocation.clone()
            snapLocation.x = snapLocation.blockX + 0.5
            snapLocation.z = snapLocation.blockZ + 0.5
            session.plugin.routeRecorder.sample(line.id, minecart, snapLocation)
        }

        val previousState = stateMachine.transitionTo(TrainState.STOPPED_AT_STATION, null)
        if (previousState == TrainState.MOVING_IN_STATION) {
            handleArrivalAtStation()
        }

        scoreboardController.updateBasedOnState(session)
    }

    private fun settleVariableFare(stop: Stop) {
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

        val intervals = if (rule.getMode() == PriceRule.PricingMode.INTERVAL) {
            session.plugin.priceService.countStopIntervals(line, session.lastSettledStopId, stop.id)
        } else {
            0
        }
        // Goes through the rule so that time discounts and max_price apply to
        // per-stop settlement too, not only to the boarding estimate.
        val variablePrice = rule.calculateVariableFare(
            distance,
            intervals,
            resolveGameTime(stop),
            session.fareChargedForTrip,
        )

        val status = if (variablePrice > 0) {
            session.plugin.ticketService.chargePrice(passenger, line, variablePrice)
        } else {
            TicketService.TicketChargeStatus.FREE
        }
        if (status == TicketService.TicketChargeStatus.CHARGED) {
            session.addFareCharged(variablePrice)
            passenger.sendMessage(
                session.plugin.languageManager.getMessage(
                    "economy.paid_distance",
                    LanguageManager.put(
                        LanguageManager.args(),
                        "price",
                        session.plugin.ticketService.format(variablePrice),
                    ),
                ),
            )
        }

        if (status == TicketService.TicketChargeStatus.CHARGED ||
            status == TicketService.TicketChargeStatus.FREE ||
            status == TicketService.TicketChargeStatus.ECONOMY_DISABLED
        ) {
            session.markFareSettledAt(stop.id)
        }
    }

    private fun resolveGameTime(stop: Stop): Long =
        session.minecart?.world?.time
            ?: stop.stopPointLocation?.world?.time
            ?: DEFAULT_GAME_TIME

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
                    MinecartEjector.eject(currentMinecart)
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

    fun handlePassengerExit() {
        if (passengerExitHandled) {
            return
        }
        passengerExitHandled = true

        if (session.state != TrainState.STOPPED_AT_STATION) {
            val targetStop = session.plugin.stopManager?.getStop(session.targetStopId)
            if (targetStop != null) {
                settleVariableFare(targetStop)
            }
        }
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
        /** Noon, used when no world clock is reachable for discount evaluation. */
        private const val DEFAULT_GAME_TIME = 6000L

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
