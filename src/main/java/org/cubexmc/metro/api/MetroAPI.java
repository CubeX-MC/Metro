package org.cubexmc.metro.api;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.FareRule;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.FareService;
import org.cubexmc.metro.service.LineStatusService;
import org.cubexmc.metro.service.TicketService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Public API for other plugins to integrate with Metro.
 * Access via {@code MetroAPI.getInstance()}.
 */
public class MetroAPI {

    private static MetroAPI instance;
    private final Metro plugin;

    private MetroAPI(Metro plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the API instance. Called internally by Metro on enable.
     */
    public static void initialize(Metro plugin) {
        instance = new MetroAPI(plugin);
    }

    /**
     * Get the MetroAPI instance.
     *
     * @return the singleton API instance, or null if Metro is not loaded
     */
    public static MetroAPI getInstance() {
        return instance;
    }

    // =============================================================
    // Line Information
    // =============================================================

    /**
     * Get a line by its ID.
     *
     * @param lineId the line identifier
     * @return the Line, or null if not found
     */
    public Line getLine(String lineId) {
        return plugin.getLineManager().getLine(lineId);
    }

    /**
     * Get all registered lines.
     */
    public List<Line> getAllLines() {
        return plugin.getLineManager().getAllLines();
    }

    /**
     * Get all lines that serve a specific stop.
     */
    public List<Line> getLinesForStop(String stopId) {
        return plugin.getLineManager().getLinesForStop(stopId);
    }

    // =============================================================
    // Stop Information
    // =============================================================

    /**
     * Get a stop by its ID.
     */
    public Stop getStop(String stopId) {
        return plugin.getStopManager().getStop(stopId);
    }

    /**
     * Get all stops.
     */
    public List<Stop> getAllStops() {
        return plugin.getStopManager().getAllStops();
    }

    // =============================================================
    // Line Status & Suspension
    // =============================================================

    /**
     * Get the operational status of a line.
     */
    public LineStatus getLineStatus(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return LineStatus.NORMAL;
        return line.getLineStatus();
    }

    /**
     * Set the operational status of a line.
     *
     * @param lineId the line identifier
     * @param status the new status
     * @return true if the status was changed
     */
    public boolean setLineStatus(String lineId, LineStatus status) {
        LineStatusService statusService = plugin.getLineStatusService();
        if (statusService == null) return false;
        Line line = getLine(lineId);
        if (line == null) return false;
        return statusService.setStatus(line, status);
    }

    /**
     * Check if a line is suspended.
     */
    public boolean isLineSuspended(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return false;
        return line.getLineStatus() == LineStatus.SUSPENDED;
    }

    /**
     * Check if a line is in maintenance mode.
     */
    public boolean isLineMaintenance(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return false;
        return line.getLineStatus() == LineStatus.MAINTENANCE;
    }

    /**
     * Set the suspension message for a line.
     */
    public void setSuspensionMessage(String lineId, String message) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setSuspensionMessage(message);
            plugin.getLineManager().saveConfig();
        }
    }

    /**
     * Get alternative route suggestions for a suspended/maintenance line.
     */
    public List<Line> getAlternativeRoutes(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return Collections.emptyList();
        return line.getAlternativeRouteIds().stream()
                .map(plugin.getLineManager()::getLine)
                .filter(l -> l != null)
                .collect(java.util.stream.Collectors.toList());
    }

    // =============================================================
    // Fare & Pricing
    // =============================================================

    /**
     * Get the fare configuration for a line.
     */
    public FareRule getFareRule(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return null;
        return line.getFareRule();
    }

    /**
     * Set the fare rule for a line.
     */
    public void setFareRule(String lineId, FareRule rule) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setFareRule(rule);
            plugin.getLineManager().saveConfig();
        }
    }

    /**
     * Calculate the expected fare for a ride between two stops on a line.
     *
     * @param lineId     the line identifier
     * @param entryStopId the boarding stop
     * @param exitStopId  the alighting stop
     * @return the calculated fare, or 0 if the calculation fails
     */
    public double calculateFare(String lineId, String entryStopId, String exitStopId) {
        Line line = getLine(lineId);
        Stop entryStop = getStop(entryStopId);
        Stop exitStop = getStop(exitStopId);
        if (line == null || entryStop == null || exitStop == null) return 0.0;

        FareService fareService = plugin.getFareService();
        if (fareService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }

        return fareService.calculateFare(line, entryStop, exitStop, 0, 0,
                entryStop.getStopPointLocation() != null ? entryStop.getStopPointLocation().getWorld() : null);
    }

    /**
     * Get the estimated minimum fare for boarding a line.
     */
    public double getEstimatedFare(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return 0.0;

        FareService fareService = plugin.getFareService();
        if (fareService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }
        return fareService.getEstimatedPrice(line);
    }

    /**
     * Get a description of the pricing model for a line.
     */
    public String getPriceDescription(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return "Free";
        FareService fareService = plugin.getFareService();
        if (fareService == null) return String.valueOf(Math.max(0.0, line.getTicketPrice()));
        return fareService.getPriceDescription(line);
    }

    // =============================================================
    // Ticketing & Economy
    // =============================================================

    /**
     * Check if a player can afford to board a line.
     *
     * @return TicketCheck with the result
     */
    public TicketService.TicketCheck checkCanBoard(Player player, String lineId) {
        Line line = getLine(lineId);
        if (line == null) {
            return new TicketService.TicketCheck(
                    TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS, 0, "0");
        }
        return plugin.getTicketService().checkCanBoard(player, line);
    }

    // =============================================================
    // Managers (for advanced use)
    // =============================================================

    /**
     * Get the LineManager.
     */
    public LineManager getLineManager() {
        return plugin.getLineManager();
    }

    /**
     * Get the StopManager.
     */
    public StopManager getStopManager() {
        return plugin.getStopManager();
    }

    /**
     * Get the raw Metro plugin instance (for advanced access).
     */
    public Metro getPlugin() {
        return plugin;
    }
}
