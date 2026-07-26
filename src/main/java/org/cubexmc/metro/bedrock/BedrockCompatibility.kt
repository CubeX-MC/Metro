package org.cubexmc.metro.bedrock

import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier
import org.bukkit.Location
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Single entry point for Bedrock-aware behavior.
 *
 * All Bedrock workarounds live behind this facade so that business code can stay
 * agnostic of Geyser/Floodgate quirks. Java players take the lightweight code path;
 * Bedrock players get conservative timings and any future targeted fixes.
 */
class BedrockCompatibility @JvmOverloads constructor(
    private val plugin: Plugin,
    /**
     * Reads `settings.bedrock.arrival_sync` at call time so `/metro reload`
     * takes effect. Defaults to enabled when no supplier is given.
     */
    private val arrivalSyncEnabled: BooleanSupplier = BooleanSupplier { true },
) {
    private val arrivalSync = BedrockArrivalSync()

    /**
     * Returns true if the player is connected through Geyser/Floodgate.
     */
    fun isBedrock(player: Player?): Boolean = BedrockDetector.isBedrockPlayer(player)

    /**
     * Teleports a player to the given destination, dismounting any vehicle first and
     * applying conservative delays for Bedrock clients.
     */
    fun teleportPlayer(player: Player?, destination: Location?): CompletableFuture<Boolean> =
        BedrockMountSync.teleportPlayer(plugin, player, destination)

    /**
     * Teleports a passenger and remounts them on the supplied minecart. Used by portal
     * flows that need to move a riding player across worlds.
     */
    fun teleportAndMountPassenger(
        passenger: Player?,
        destination: Location?,
        targetCart: Minecart?,
    ): CompletableFuture<Boolean> = BedrockMountSync.teleportAndMountPassenger(plugin, passenger, destination, targetCart)

    /**
     * Hooks into train arrival. For Bedrock passengers schedules a periodic
     * zero-velocity update so the Bedrock client stops predicting forward motion
     * and the mounted player does not drift into the void. No-op for Java players.
     */
    fun onTrainArrival(passenger: Player?, minecart: Minecart?) {
        if (passenger == null || minecart == null) {
            return
        }
        if (!arrivalSyncEnabled.asBoolean || !isBedrock(passenger)) {
            return
        }
        arrivalSync.start(plugin, passenger, minecart)
    }

    /**
     * Cancels any active arrival sync for this minecart. Safe to call for Java
     * passengers or carts that never had a sync running.
     */
    fun onTrainDeparture(minecart: Minecart?) {
        arrivalSync.stop(minecart)
    }
}
