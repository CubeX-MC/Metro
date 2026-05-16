package org.cubexmc.metro.api;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.FareRule;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.PriceService;
import org.cubexmc.metro.service.LineStatusService;
import org.cubexmc.metro.service.TicketService;

import java.util.List;

/**
 * Public API for other plugins to integrate with Metro.
 * Access via {@code MetroAPI.getInstance()}.
 * <p>
 * Read queries return live model objects for convenience; mutations route
 * through command services to guarantee save, refresh, event, and permission
 * consistency.
 * <p>
 * The raw managers ({@link #getLineManager()}, {@link #getStopManager()},
 * {@link #getPlugin()}) are exposed for advanced use but are not the
 * recommended integration surface.
 */
public final class MetroAPI {

    private static MetroAPI instance;
    private final Metro plugin;

    private MetroAPI(Metro plugin) {
        this.plugin = plugin;
    }

    // =============================================================
    // Lifecycle
    // =============================================================

    public static void initialize(Metro plugin) {
        if (instance == null) {
            instance = new MetroAPI(plugin);
        }
    }

    public static MetroAPI getInstance() {
        return instance;
    }

    // =============================================================
    // Line queries
    // =============================================================

    public Line getLine(String lineId) {
        return plugin.getLineManager().getLine(lineId);
    }

    public List<Line> getAllLines() {
        return plugin.getLineManager().getAllLines();
    }

    public List<Line> getLinesForStop(String stopId) {
        return plugin.getLineManager().getLinesForStop(stopId);
    }

    // =============================================================
    // Stop queries
    // =============================================================

    public Stop getStop(String stopId) {
        return plugin.getStopManager().getStop(stopId);
    }

    public List<Stop> getAllStops() {
        return plugin.getStopManager().getAllStops();
    }

    // =============================================================
    // Line status
    // =============================================================

    public LineStatus getLineStatus(String lineId) {
        Line line = getLine(lineId);
        return line != null ? line.getLineStatus() : LineStatus.NORMAL;
    }

    public boolean setLineStatus(String lineId, LineStatus status) {
        Line line = getLine(lineId);
        if (line == null) return false;
        LineStatusService statusService = plugin.getLineStatusService();
        if (statusService == null) return false;
        return statusService.setStatus(line, status);
    }

    public boolean isLineSuspended(String lineId) {
        return getLineStatus(lineId) == LineStatus.SUSPENDED;
    }

    public boolean isLineMaintenance(String lineId) {
        return getLineStatus(lineId) == LineStatus.MAINTENANCE;
    }

    public void setSuspensionMessage(String lineId, String message) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setSuspensionMessage(message);
            plugin.getLineManager().saveConfig();
        }
    }

    // =============================================================
    // Pricing
    // =============================================================

    public FareRule getPriceRule(String lineId) {
        Line line = getLine(lineId);
        return line != null ? line.getFareRule() : null;
    }

    public void setPriceRule(String lineId, FareRule rule) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setFareRule(rule);
            plugin.getLineManager().saveConfig();
        }
    }

    public double calculatePrice(String lineId, String entryStopId, String exitStopId,
                                  double distanceBlocks, int intervals) {
        Line line = getLine(lineId);
        Stop entryStop = getStop(entryStopId);
        Stop exitStop = getStop(exitStopId);
        if (line == null || entryStop == null || exitStop == null) return 0.0;

        PriceService priceService = plugin.getPriceService();
        if (priceService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }

        return priceService.calculatePrice(line, entryStop, exitStop, distanceBlocks, intervals,
                entryStop.getStopPointLocation() != null ? entryStop.getStopPointLocation().getWorld() : null);
    }

    public double getEstimatedPrice(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return 0.0;
        PriceService priceService = plugin.getPriceService();
        if (priceService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }
        return priceService.getEstimatedPrice(line);
    }

    public String getPriceDescription(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return "Free";
        PriceService priceService = plugin.getPriceService();
        if (priceService == null) return String.valueOf(Math.max(0.0, line.getTicketPrice()));
        return priceService.getPriceDescription(line);
    }

    // =============================================================
    // Ticketing
    // =============================================================

    public TicketService.TicketCheck checkCanBoard(Player player, String lineId) {
        Line line = getLine(lineId);
        if (line == null) {
            return new TicketService.TicketCheck(
                    TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS, 0, "0");
        }
        return plugin.getTicketService().checkCanBoard(player, line);
    }

    // =============================================================
    // Advanced / unstable access
    // =============================================================

    public LineManager getLineManager() {
        return plugin.getLineManager();
    }

    public StopManager getStopManager() {
        return plugin.getStopManager();
    }

    public Metro getPlugin() {
        return plugin;
    }
}
