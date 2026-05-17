package org.cubexmc.metro.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.cubexmc.metro.integration.VaultIntegration;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.PriceRule;

/**
 * Coordinates ticket price checks and delayed economy charges.
 * <p>
 * The flow is: {@link #checkCanBoard} → {@link #createTransaction} →
 * {@link #charge}. This two-phase design (check before boarding, charge
 * on spawn) avoids locking economy funds during the spawn delay.
 */
public class TicketService {

    /**
     * Result of a pre-boarding balance check.
     */
    public enum TicketCheckStatus {
        /** Player has sufficient funds to board. */
        OK,
        /** No charge applies for this line, boarding is free. */
        FREE,
        /** The in-game economy is disabled in config. */
        ECONOMY_DISABLED,
        /** Vault is not available; cannot check balance. */
        VAULT_UNAVAILABLE,
        /** Player does not have enough money for the fare. */
        INSUFFICIENT_FUNDS
    }

    /**
     * Result of a delayed charge attempt at minecart spawn time.
     */
    public enum TicketChargeStatus {
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
        /** Vault withdrawal returned {@code false} for an unknown reason. */
        TRANSACTION_FAILED
    }

    /**
     * The result of {@link #checkCanBoard}.
     */
    public static final class TicketCheck {
        private final TicketCheckStatus status;
        private final double price;
        private final String formattedPrice;

        /**
         * @param status         the check status
         * @param price          the raw price amount
         * @param formattedPrice the Vault-formatted price string
         */
        public TicketCheck(TicketCheckStatus status, double price, String formattedPrice) {
            this.status = status;
            this.price = price;
            this.formattedPrice = formattedPrice;
        }

        /** @return the check status */
        public TicketCheckStatus getStatus() { return status; }

        /** @return the raw price amount */
        public double getPrice() { return price; }

        /** @return the Vault-formatted price string */
        public String getFormattedPrice() { return formattedPrice; }

        /**
         * Whether the player is allowed to board (status is
         * {@link TicketCheckStatus#OK}, {@link TicketCheckStatus#FREE},
         * or {@link TicketCheckStatus#ECONOMY_DISABLED}).
         *
         * @return {@code true} if boarding is permitted
         */
        public boolean canBoard() {
            return status == TicketCheckStatus.OK
                    || status == TicketCheckStatus.FREE
                    || status == TicketCheckStatus.ECONOMY_DISABLED;
        }
    }

    /**
     * A delayed charge ticket created by {@link #createTransaction}.
     * The actual withdrawal happens later via {@link TicketService#charge}.
     */
    public static final class TicketTransaction {
        private final Player player;
        private final Line line;
        private final double price;
        private boolean charged;

        private TicketTransaction(Player player, Line line, double price) {
            this.player = Objects.requireNonNull(player, "player");
            this.line = Objects.requireNonNull(line, "line");
            this.price = Math.max(0.0, price);
        }

        /** @return the player being charged */
        public Player getPlayer() { return player; }

        /** @return the line being boarded */
        public Line getLine() { return line; }

        /** @return the price to charge */
        public double getPrice() { return price; }

        /** @return whether the charge has already been applied */
        public boolean isCharged() { return charged; }

        private void markCharged() { this.charged = true; }
    }

    private final Supplier<VaultIntegration> vaultSupplier;
    private final BooleanSupplier economyEnabledSupplier;

    /**
     * @param vaultSupplier          supplies the current Vault integration
     * @param economyEnabledSupplier whether the in-game economy is enabled
     */
    public TicketService(Supplier<VaultIntegration> vaultSupplier, BooleanSupplier economyEnabledSupplier) {
        this.vaultSupplier = Objects.requireNonNull(vaultSupplier, "vaultSupplier");
        this.economyEnabledSupplier = Objects.requireNonNull(economyEnabledSupplier, "economyEnabledSupplier");
    }

    /**
     * Checks whether a player can board the given line (balance check).
     * This is the first phase of the two-phase boarding flow.
     *
     * @param player the boarding player
     * @param line   the line to board
     * @return a {@link TicketCheck} describing the result
     */
    public TicketCheck checkCanBoard(Player player, Line line) {
        double price = getEstimatedMinimumPrice(line);
        String formattedPrice = format(price);
        if (!economyEnabledSupplier.getAsBoolean()) {
            return new TicketCheck(TicketCheckStatus.ECONOMY_DISABLED, price, formattedPrice);
        }
        if (price <= 0.0) {
            return new TicketCheck(TicketCheckStatus.FREE, price, formattedPrice);
        }

        VaultIntegration vault = getEnabledVault();
        if (vault == null) {
            return new TicketCheck(TicketCheckStatus.VAULT_UNAVAILABLE, price, formattedPrice);
        }
        if (!vault.has(player, price)) {
            return new TicketCheck(TicketCheckStatus.INSUFFICIENT_FUNDS, price, formattedPrice);
        }
        return new TicketCheck(TicketCheckStatus.OK, price, formattedPrice);
    }

    /**
     * Creates a delayed-charge transaction for the player and line.
     * The actual withdrawal happens later via {@link #charge}.
     *
     * @param player the boarding player
     * @param line   the line being boarded
     * @return a new, uncharged transaction
     */
    public TicketTransaction createTransaction(Player player, Line line) {
        return new TicketTransaction(player, line, getTicketPrice(line));
    }

    /**
     * Executes the actual economy withdrawal (second phase).
     * This is called at minecart spawn time, after the boardability
     * check in {@link #checkCanBoard}.
     *
     * @param transaction the transaction created by {@link #createTransaction}
     * @return the charge result status
     */
    public TicketChargeStatus charge(TicketTransaction transaction) {
        if (transaction == null) {
            return TicketChargeStatus.TRANSACTION_FAILED;
        }
        if (!economyEnabledSupplier.getAsBoolean()) {
            return TicketChargeStatus.ECONOMY_DISABLED;
        }
        if (transaction.getPrice() <= 0.0) {
            return TicketChargeStatus.FREE;
        }
        if (transaction.isCharged()) {
            return TicketChargeStatus.CHARGED;
        }

        VaultIntegration vault = getEnabledVault();
        if (vault == null) {
            return TicketChargeStatus.VAULT_UNAVAILABLE;
        }
        if (!vault.has(transaction.getPlayer(), transaction.getPrice())) {
            return TicketChargeStatus.INSUFFICIENT_FUNDS;
        }
        if (!vault.withdraw(transaction.getPlayer(), transaction.getPrice())) {
            return TicketChargeStatus.TRANSACTION_FAILED;
        }

        transaction.markCharged();
        UUID owner = transaction.getLine().getOwner();
        if (owner != null) {
            vault.deposit(owner, transaction.getPrice());
        }
        return TicketChargeStatus.CHARGED;
    }

    /**
     * Formats a price amount using Vault's currency format.
     *
     * @param amount the raw amount
     * @return the formatted price string
     */
    public String format(double amount) {
        VaultIntegration vault = getEnabledVault();
        if (vault != null) {
            return vault.format(amount);
        }
        return String.valueOf(amount);
    }

    private double getTicketPrice(Line line) {
        if (line == null) return 0.0;
        PriceRule rule = line.getPriceRule();
        if (rule != null) {
            return Math.max(0.0, rule.getBasePrice());
        }
        return Math.max(0.0, line.getTicketPrice());
    }

    private double getEstimatedMinimumPrice(Line line) {
        if (line == null) return 0.0;
        PriceRule rule = line.getPriceRule();
        if (rule != null) {
            double estimate = rule.getBasePrice();
            if (rule.getMode() == PriceRule.PricingMode.DISTANCE) {
                estimate += rule.getPerBlockRate();
            } else if (rule.getMode() == PriceRule.PricingMode.INTERVAL) {
                estimate += rule.getPerIntervalRate();
            }
            return Math.max(0.0, estimate);
        }
        return Math.max(0.0, line.getTicketPrice());
    }

    /**
     * One-shot charge convenience method (bypasses the two-phase flow).
     * Use for direct charges that don't go through the boarding lifecycle.
     *
     * @param player        the player to charge
     * @param line          the line context (used for owner deposit)
     * @param priceToCharge the amount to charge
     * @return the charge result status
     */
    public TicketChargeStatus chargePrice(Player player, Line line, double priceToCharge) {
        if (player == null || line == null || priceToCharge <= 0.0) {
            return TicketChargeStatus.FREE;
        }
        if (!economyEnabledSupplier.getAsBoolean()) {
            return TicketChargeStatus.ECONOMY_DISABLED;
        }
        VaultIntegration vault = getEnabledVault();
        if (vault == null) {
            return TicketChargeStatus.VAULT_UNAVAILABLE;
        }
        if (!vault.has(player, priceToCharge)) {
            return TicketChargeStatus.INSUFFICIENT_FUNDS;
        }
        if (!vault.withdraw(player, priceToCharge)) {
            return TicketChargeStatus.TRANSACTION_FAILED;
        }
        UUID owner = line.getOwner();
        if (owner != null) {
            vault.deposit(owner, priceToCharge);
        }
        return TicketChargeStatus.CHARGED;
    }

    private VaultIntegration getEnabledVault() {
        VaultIntegration vault = vaultSupplier.get();
        return vault != null && vault.isEnabled() ? vault : null;
    }
}
