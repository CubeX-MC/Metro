package org.cubexmc.metro.service

import java.util.Objects
import java.util.UUID
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.math.max
import org.bukkit.entity.Player
import org.cubexmc.metro.integration.VaultIntegration
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.PriceRule

/**
 * Coordinates ticket price checks and delayed economy charges.
 *
 * The flow is: [checkCanBoard] -> [createTransaction] -> [charge]. This
 * two-phase design (check before boarding, charge on spawn) avoids locking
 * economy funds during the spawn delay.
 */
class TicketService(
    vaultSupplier: Supplier<VaultIntegration?>,
    economyEnabledSupplier: BooleanSupplier,
) {
    private val vaultSupplier: Supplier<VaultIntegration?> = Objects.requireNonNull(vaultSupplier, "vaultSupplier")
    private val economyEnabledSupplier: BooleanSupplier =
        Objects.requireNonNull(economyEnabledSupplier, "economyEnabledSupplier")

    /**
     * Result of a pre-boarding balance check.
     */
    enum class TicketCheckStatus {
        /** Player has sufficient funds to board. */
        OK,

        /** No charge applies for this line, boarding is free. */
        FREE,

        /** The in-game economy is disabled in config. */
        ECONOMY_DISABLED,

        /** Vault is not available; cannot check balance. */
        VAULT_UNAVAILABLE,

        /** Player does not have enough money for the fare. */
        INSUFFICIENT_FUNDS,
    }

    /**
     * Result of a delayed charge attempt at minecart spawn time.
     */
    enum class TicketChargeStatus {
        /** Amount was successfully withdrawn and transferred. */
        CHARGED,

        /** No charge applies, ride is free. */
        FREE,

        /** Economy is disabled in config. */
        ECONOMY_DISABLED,

        /** Vault is not available; cannot process payment. */
        VAULT_UNAVAILABLE,

        /** Player no longer has enough funds (race between check and charge). */
        INSUFFICIENT_FUNDS,

        /** Vault withdrawal returned `false` for an unknown reason. */
        TRANSACTION_FAILED,
    }

    /**
     * The result of [checkCanBoard].
     */
    class TicketCheck(
        val status: TicketCheckStatus,
        val price: Double,
        val formattedPrice: String?,
    ) {
        /**
         * Whether the player is allowed to board (status is
         * [TicketCheckStatus.OK], [TicketCheckStatus.FREE],
         * or [TicketCheckStatus.ECONOMY_DISABLED]).
         *
         * @return `true` if boarding is permitted
         */
        fun canBoard(): Boolean =
            status == TicketCheckStatus.OK ||
                status == TicketCheckStatus.FREE ||
                status == TicketCheckStatus.ECONOMY_DISABLED
    }

    /**
     * A delayed charge ticket created by [createTransaction].
     * The actual withdrawal happens later via [TicketService.charge].
     */
    class TicketTransaction private constructor(
        val player: Player,
        val line: Line,
        val price: Double,
    ) {
        var isCharged: Boolean = false
            private set

        fun markCharged() {
            isCharged = true
        }

        companion object {
            fun create(player: Player, line: Line, price: Double): TicketTransaction =
                TicketTransaction(
                    Objects.requireNonNull(player, "player"),
                    Objects.requireNonNull(line, "line"),
                    max(0.0, price),
                )
        }
    }

    /**
     * Checks whether a player can board the given line (balance check).
     * This is the first phase of the two-phase boarding flow.
     *
     * @param player the boarding player
     * @param line the line to board
     * @return a [TicketCheck] describing the result
     */
    fun checkCanBoard(player: Player, line: Line?): TicketCheck {
        val price = getEstimatedMinimumPrice(line)
        val formattedPrice = format(price)
        if (!economyEnabledSupplier.asBoolean) {
            return TicketCheck(TicketCheckStatus.ECONOMY_DISABLED, price, formattedPrice)
        }
        if (price <= 0.0) {
            return TicketCheck(TicketCheckStatus.FREE, price, formattedPrice)
        }

        val vault = getEnabledVault()
        if (vault == null) {
            return TicketCheck(TicketCheckStatus.VAULT_UNAVAILABLE, price, formattedPrice)
        }
        if (!vault.has(player, price)) {
            return TicketCheck(TicketCheckStatus.INSUFFICIENT_FUNDS, price, formattedPrice)
        }
        return TicketCheck(TicketCheckStatus.OK, price, formattedPrice)
    }

    /**
     * Creates a delayed-charge transaction for the player and line.
     * The actual withdrawal happens later via [charge].
     *
     * @param player the boarding player
     * @param line the line being boarded
     * @return a new, uncharged transaction
     */
    fun createTransaction(player: Player, line: Line): TicketTransaction =
        TicketTransaction.create(player, line, getTicketPrice(line))

    /**
     * Executes the actual economy withdrawal (second phase).
     * This is called at minecart spawn time, after the boardability
     * check in [checkCanBoard].
     *
     * @param transaction the transaction created by [createTransaction]
     * @return the charge result status
     */
    fun charge(transaction: TicketTransaction?): TicketChargeStatus {
        if (transaction == null) {
            return TicketChargeStatus.TRANSACTION_FAILED
        }
        if (!economyEnabledSupplier.asBoolean) {
            return TicketChargeStatus.ECONOMY_DISABLED
        }
        if (transaction.price <= 0.0) {
            return TicketChargeStatus.FREE
        }
        if (transaction.isCharged) {
            return TicketChargeStatus.CHARGED
        }

        val vault = getEnabledVault()
        if (vault == null) {
            return TicketChargeStatus.VAULT_UNAVAILABLE
        }
        if (!vault.has(transaction.player, transaction.price)) {
            return TicketChargeStatus.INSUFFICIENT_FUNDS
        }
        if (!vault.withdraw(transaction.player, transaction.price)) {
            return TicketChargeStatus.TRANSACTION_FAILED
        }

        transaction.markCharged()
        val owner: UUID? = transaction.line.owner
        if (owner != null) {
            if (!vault.deposit(owner, transaction.price)) {
                // Owner deposit failed: refund the passenger and report failure
                vault.deposit(transaction.player.uniqueId, transaction.price)
                return TicketChargeStatus.TRANSACTION_FAILED
            }
        }
        return TicketChargeStatus.CHARGED
    }

    /**
     * Formats a price amount using Vault's currency format.
     *
     * @param amount the raw amount
     * @return the formatted price string
     */
    fun format(amount: Double): String? {
        val vault = getEnabledVault()
        if (vault != null) {
            return vault.format(amount)
        }
        return amount.toString()
    }

    private fun getTicketPrice(line: Line?): Double {
        if (line == null) return 0.0
        val rule = line.priceRule
        if (rule != null) {
            return max(0.0, rule.getBasePrice())
        }
        return max(0.0, line.ticketPrice)
    }

    private fun getEstimatedMinimumPrice(line: Line?): Double {
        if (line == null) return 0.0
        val rule = line.priceRule
        if (rule != null) {
            var estimate = rule.getBasePrice()
            if (rule.getMode() == PriceRule.PricingMode.DISTANCE) {
                estimate += rule.getPerBlockRate()
            } else if (rule.getMode() == PriceRule.PricingMode.INTERVAL) {
                estimate += rule.getPerIntervalRate()
            }
            return max(0.0, estimate)
        }
        return max(0.0, line.ticketPrice)
    }

    /**
     * One-shot charge convenience method (bypasses the two-phase flow).
     * Use for direct charges that don't go through the boarding lifecycle.
     *
     * @param player the player to charge
     * @param line the line context (used for owner deposit)
     * @param priceToCharge the amount to charge
     * @return the charge result status
     */
    fun chargePrice(player: Player?, line: Line?, priceToCharge: Double): TicketChargeStatus {
        if (player == null || line == null || priceToCharge <= 0.0) {
            return TicketChargeStatus.FREE
        }
        if (!economyEnabledSupplier.asBoolean) {
            return TicketChargeStatus.ECONOMY_DISABLED
        }
        val vault = getEnabledVault()
        if (vault == null) {
            return TicketChargeStatus.VAULT_UNAVAILABLE
        }
        if (!vault.has(player, priceToCharge)) {
            return TicketChargeStatus.INSUFFICIENT_FUNDS
        }
        if (!vault.withdraw(player, priceToCharge)) {
            return TicketChargeStatus.TRANSACTION_FAILED
        }
        val owner = line.owner
        if (owner != null) {
            if (!vault.deposit(owner, priceToCharge)) {
                // Owner deposit failed: refund the passenger and report failure
                vault.deposit(player.uniqueId, priceToCharge)
                return TicketChargeStatus.TRANSACTION_FAILED
            }
        }
        return TicketChargeStatus.CHARGED
    }

    private fun getEnabledVault(): VaultIntegration? {
        val vault = vaultSupplier.get()
        return if (vault != null && vault.isEnabled) vault else null
    }
}
