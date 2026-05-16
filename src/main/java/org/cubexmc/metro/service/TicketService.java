package org.cubexmc.metro.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.cubexmc.metro.integration.VaultIntegration;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.FareRule;

/**
 * Coordinates ticket price checks and delayed economy charges.
 */
public class TicketService {

    public enum TicketCheckStatus {
        OK,
        FREE,
        ECONOMY_DISABLED,
        VAULT_UNAVAILABLE,
        INSUFFICIENT_FUNDS
    }

    public enum TicketChargeStatus {
        CHARGED,
        FREE,
        ECONOMY_DISABLED,
        VAULT_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        TRANSACTION_FAILED
    }

    public static final class TicketCheck {
        private final TicketCheckStatus status;
        private final double price;
        private final String formattedPrice;

        public TicketCheck(TicketCheckStatus status, double price, String formattedPrice) {
            this.status = status;
            this.price = price;
            this.formattedPrice = formattedPrice;
        }

        public TicketCheckStatus getStatus() {
            return status;
        }

        public double getPrice() {
            return price;
        }

        public String getFormattedPrice() {
            return formattedPrice;
        }

        public boolean canBoard() {
            return status == TicketCheckStatus.OK
                    || status == TicketCheckStatus.FREE
                    || status == TicketCheckStatus.ECONOMY_DISABLED;
        }
    }

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

        public Player getPlayer() {
            return player;
        }

        public Line getLine() {
            return line;
        }

        public double getPrice() {
            return price;
        }

        public boolean isCharged() {
            return charged;
        }

        private void markCharged() {
            this.charged = true;
        }
    }

    private final Supplier<VaultIntegration> vaultSupplier;
    private final BooleanSupplier economyEnabledSupplier;

    public TicketService(Supplier<VaultIntegration> vaultSupplier, BooleanSupplier economyEnabledSupplier) {
        this.vaultSupplier = Objects.requireNonNull(vaultSupplier, "vaultSupplier");
        this.economyEnabledSupplier = Objects.requireNonNull(economyEnabledSupplier, "economyEnabledSupplier");
    }

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

    public TicketTransaction createTransaction(Player player, Line line) {
        return new TicketTransaction(player, line, getTicketPrice(line));
    }

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

    public String format(double amount) {
        VaultIntegration vault = getEnabledVault();
        if (vault != null) {
            return vault.format(amount);
        }
        return String.valueOf(amount);
    }

    private double getTicketPrice(Line line) {
        if (line == null) return 0.0;
        FareRule rule = line.getFareRule();
        if (rule != null) {
            return Math.max(0.0, rule.getBasePrice());
        }
        return Math.max(0.0, line.getTicketPrice());
    }

    private double getEstimatedMinimumPrice(Line line) {
        if (line == null) return 0.0;
        FareRule rule = line.getFareRule();
        if (rule != null) {
            double estimate = rule.getBasePrice();
            if (rule.getMode() == FareRule.PricingMode.DISTANCE) {
                estimate += rule.getPerBlockRate();
            } else if (rule.getMode() == FareRule.PricingMode.INTERVAL) {
                estimate += rule.getPerIntervalRate();
            }
            return Math.max(0.0, estimate);
        }
        return Math.max(0.0, line.getTicketPrice());
    }

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
