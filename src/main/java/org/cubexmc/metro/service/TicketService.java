package org.cubexmc.metro.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.cubexmc.metro.integration.VaultIntegration;
import org.cubexmc.metro.model.FareRule;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

/**
 * Coordinates ticket price checks and delayed economy charges.
 * Supports flat-rate, distance-based, and interval-based pricing via FareRule.
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
        double price = getTicketPrice(line);
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

    /**
     * Get the estimated boarding price for a line, considering FareRule if set.
     * If the line has a FareRule, returns the base fare (minimum possible).
     */
    private double getTicketPrice(Line line) {
        if (line == null) return 0.0;
        FareRule rule = line.getFareRule();
        if (rule != null) {
            // For display: use baseFare as the minimum price
            return Math.max(0.0, rule.getBaseFare());
        }
        return Math.max(0.0, line.getTicketPrice());
    }

    /**
     * Calculate the actual fare based on FareRule, distance, intervals, and world time.
     * Falls back to legacy flat ticket price if no FareRule is set.
     *
     * @param line       the line
     * @param entryStop  the boarding stop
     * @param exitStop   the alighting stop
     * @param distanceBlocks distance traveled in blocks
     * @param intervals  number of stop intervals passed
     * @param world      the world (for game time)
     * @return the calculated fare
     */
    public double calculateFare(Line line, Stop entryStop, Stop exitStop,
                                 double distanceBlocks, int intervals, World world) {
        if (line == null) return 0.0;
        FareRule rule = line.getFareRule();
        if (rule == null) {
            return Math.max(0.0, line.getTicketPrice());
        }
        // Count intervals if not provided
        if (intervals <= 0 && entryStop != null && exitStop != null) {
            intervals = countStopIntervals(line, entryStop.getId(), exitStop.getId());
        }
        if (intervals <= 0) intervals = 1;
        long gameTime = world != null ? world.getTime() : 6000;
        return rule.calculatePrice(distanceBlocks, intervals, gameTime);
    }

    /**
     * Charge a fare based on actual distance traveled (deferred per-block charging).
     * Used when a player arrives at a stop (not the initial boarding charge).
     *
     * @param player       the player
     * @param line         the line
     * @param fareToCharge the fare to charge
     * @return the charge status
     */
    public TicketChargeStatus chargeFare(Player player, Line line, double fareToCharge) {
        if (player == null || line == null || fareToCharge <= 0.0) {
            return TicketChargeStatus.FREE;
        }
        if (!economyEnabledSupplier.getAsBoolean()) {
            return TicketChargeStatus.ECONOMY_DISABLED;
        }
        VaultIntegration vault = getEnabledVault();
        if (vault == null) {
            return TicketChargeStatus.VAULT_UNAVAILABLE;
        }
        if (!vault.has(player, fareToCharge)) {
            return TicketChargeStatus.INSUFFICIENT_FUNDS;
        }
        if (!vault.withdraw(player, fareToCharge)) {
            return TicketChargeStatus.TRANSACTION_FAILED;
        }
        UUID owner = line.getOwner();
        if (owner != null) {
            vault.deposit(owner, fareToCharge);
        }
        return TicketChargeStatus.CHARGED;
    }

    private int countStopIntervals(Line line, String entryStopId, String exitStopId) {
        if (line == null || entryStopId == null || exitStopId == null) return 0;
        java.util.List<String> stopIds = line.getOrderedStopIds();
        int entryIndex = stopIds.indexOf(entryStopId);
        int exitIndex = stopIds.indexOf(exitStopId);
        if (entryIndex == -1 || exitIndex == -1) return 0;
        if (line.isCircular()) {
            int forwardDist = (exitIndex - entryIndex + stopIds.size()) % stopIds.size();
            int backwardDist = (entryIndex - exitIndex + stopIds.size()) % stopIds.size();
            return Math.min(forwardDist, backwardDist);
        }
        if (exitIndex <= entryIndex) return 0;
        return exitIndex - entryIndex;
    }

    private VaultIntegration getEnabledVault() {
        VaultIntegration vault = vaultSupplier.get();
        return vault != null && vault.isEnabled() ? vault : null;
    }
}
