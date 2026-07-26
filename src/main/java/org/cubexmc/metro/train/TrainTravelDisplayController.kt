package org.cubexmc.metro.train

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.cubexmc.metro.model.Stop
import org.cubexmc.metro.util.ColorUtil
import org.cubexmc.metro.util.TextUtil

/**
 * Shows the remaining distance to the next stop on the passenger's action bar
 * while the train is in transit.
 *
 * Driven by vehicle movement rather than a repeating task, so it costs nothing
 * for trains that are docked, and is throttled to one update every
 * `titles.traveling.interval` movements.
 */
class TrainTravelDisplayController {
    private var movesSinceLastUpdate = Int.MAX_VALUE

    fun onTrainMoved(session: TrainSession) {
        if (session.state == TrainMovementTask.TrainState.STOPPED_AT_STATION) {
            movesSinceLastUpdate = Int.MAX_VALUE
            return
        }

        val plugin = session.plugin
        val config = plugin.configFacade
        if (!config.isTravelingActionbarEnabled()) {
            return
        }

        val template = config.getTravelingActionbar()
        if (template.isBlank()) {
            return
        }

        if (movesSinceLastUpdate < Int.MAX_VALUE) {
            movesSinceLastUpdate++
        }
        if (movesSinceLastUpdate < config.getTravelingActionbarInterval()) {
            return
        }
        movesSinceLastUpdate = 0

        val passenger = session.passenger
        val line = session.line
        if (passenger == null || !passenger.isOnline || line == null) {
            return
        }

        val nextStop = plugin.stopManager.getStop(session.targetStopId)
        val distanceBlocks = remainingDistance(session, nextStop)

        val stopIds = line.orderedStopIds
        val terminusStop = if (stopIds.isEmpty()) {
            null
        } else {
            plugin.stopManager.getStop(stopIds[stopIds.size - 1])
        }

        val text = ColorUtil.colorizeOrEmpty(
            TextUtil.replacePlaceholders(
                template,
                line,
                nextStop,
                plugin.stopManager.getStop(session.currentStopId),
                nextStop,
                terminusStop,
                plugin.lineManager,
                distanceBlocks,
            ),
        )
        passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(text))
    }

    /**
     * Straight-line distance from the cart to the next stop point, or `null`
     * when it cannot be measured (no stop point, or a portal moved the cart to
     * another world).
     */
    private fun remainingDistance(session: TrainSession, nextStop: Stop?): Double? {
        val stopLocation = nextStop?.stopPointLocation ?: return null
        val cartLocation = session.minecart?.location ?: return null
        if (cartLocation.world == null || cartLocation.world != stopLocation.world) {
            return null
        }
        return cartLocation.distance(stopLocation)
    }
}
