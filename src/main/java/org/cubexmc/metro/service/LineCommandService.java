package org.cubexmc.metro.service;

import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.regex.Pattern;

import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.FareRule;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.Stop;
import java.util.stream.Collectors;

/**
 * Business operations used by line commands.
 */
public class LineCommandService {

    private static final int MAX_ID_LENGTH = 64;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)&#[0-9A-F]{6}");

    private final LineManager lineManager;

    public LineCommandService(LineManager lineManager) {
        this.lineManager = lineManager;
    }

    public enum WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_COLOR,
        INVALID_VALUE,
        EXISTS,
        NOT_FOUND,
        FAILED,
        STOP_NO_WORLD,
        WORLD_MISMATCH,
        CIRCULAR_INVALID_INDEX
    }

    public static final class AddStopResult {
        private final WriteStatus status;
        private final String lineWorld;
        private final String stopWorld;

        public AddStopResult(WriteStatus status, String lineWorld, String stopWorld) {
            this.status = status;
            this.lineWorld = lineWorld;
            this.stopWorld = stopWorld;
        }

        public WriteStatus status() {
            return status;
        }

        public String lineWorld() {
            return lineWorld;
        }

        public String stopWorld() {
            return stopWorld;
        }

        static AddStopResult of(WriteStatus status) {
            return new AddStopResult(status, null, null);
        }

        static AddStopResult worldMismatch(String lineWorld, String stopWorld) {
            return new AddStopResult(WriteStatus.WORLD_MISMATCH, lineWorld, stopWorld);
        }
    }

    public static final class ClearRouteResult {
        private final WriteStatus status;
        private final int previousPointCount;

        public ClearRouteResult(WriteStatus status, int previousPointCount) {
            this.status = status;
            this.previousPointCount = previousPointCount;
        }

        public WriteStatus status() {
            return status;
        }

        public int previousPointCount() {
            return previousPointCount;
        }
    }

    public WriteStatus createLine(String id, String name, UUID ownerId) {
        if (!isValidId(id)) {
            return WriteStatus.INVALID_ID;
        }
        return lineManager.createLine(id, name, ownerId) ? WriteStatus.SUCCESS : WriteStatus.EXISTS;
    }

    public WriteStatus deleteLine(String id) {
        return lineManager.deleteLine(id) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public List<Line> listLines() {
        return lineManager.getAllLines().stream()
                .sorted(Comparator.comparing(Line::getId))
                .collect(Collectors.toList());
    }

    public WriteStatus renameLine(String id, String name) {
        return lineManager.setLineName(id, name) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setColor(String id, String color) {
        if (!isValidColor(color)) {
            return WriteStatus.INVALID_COLOR;
        }
        return lineManager.setLineColor(id, color) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setTerminusName(String id, String terminusName) {
        return lineManager.setLineTerminusName(id, terminusName) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setMaxSpeed(String id, double speed) {
        if (!Double.isFinite(speed) || speed <= 0.0) {
            return WriteStatus.INVALID_VALUE;
        }
        return lineManager.setLineMaxSpeed(id, speed) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public AddStopResult addStopToLine(Line line, Stop stop, Integer index) {
        String stopWorld = stop.getWorldName();
        String lineWorld = line.getWorldName();
        if (stopWorld == null || stopWorld.trim().isEmpty()) {
            return AddStopResult.of(WriteStatus.STOP_NO_WORLD);
        }
        if (lineWorld != null && !lineWorld.equals(stopWorld)) {
            return AddStopResult.worldMismatch(lineWorld, stopWorld);
        }

        int targetIndex = index == null ? -1 : index;
        List<String> orderedStopIds = line.getOrderedStopIds();
        if (line.isCircular() && (targetIndex < 0 || targetIndex >= orderedStopIds.size())) {
            return AddStopResult.of(WriteStatus.CIRCULAR_INVALID_INDEX);
        }

        if (!lineManager.addStopToLine(line.getId(), stop.getId(), targetIndex)) {
            return AddStopResult.of(WriteStatus.FAILED);
        }
        if (lineWorld == null) {
            lineManager.setLineWorldName(line.getId(), stopWorld);
        }
        return AddStopResult.of(WriteStatus.SUCCESS);
    }

    public WriteStatus removeStopFromLine(Line line, String stopId) {
        if (!lineManager.delStopFromLine(line.getId(), stopId)) {
            return WriteStatus.FAILED;
        }
        if (line.getOrderedStopIds().isEmpty()) {
            lineManager.setLineWorldName(line.getId(), null);
        }
        return WriteStatus.SUCCESS;
    }

    public WriteStatus addPortalToLine(Line line, Portal portal) {
        if (line == null || portal == null) {
            return WriteStatus.FAILED;
        }
        if (line.containsPortal(portal.getId())) {
            return WriteStatus.EXISTS;
        }
        return lineManager.addPortalToLine(line.getId(), portal.getId()) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus removePortalFromLine(Line line, String portalId) {
        if (line == null || portalId == null || portalId.trim().isEmpty()) {
            return WriteStatus.FAILED;
        }
        if (!line.containsPortal(portalId)) {
            return WriteStatus.NOT_FOUND;
        }
        return lineManager.delPortalFromLine(line.getId(), portalId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setRailProtected(String id, boolean enabled) {
        return lineManager.setLineRailProtected(id, enabled) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus grantAdmin(Line line, UUID adminId) {
        if (adminId == null) {
            return WriteStatus.INVALID_VALUE;
        }
        if (line.getAdmins().contains(adminId)) {
            return WriteStatus.EXISTS;
        }
        return lineManager.addLineAdmin(line.getId(), adminId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus revokeAdmin(Line line, UUID adminId) {
        if (adminId == null) {
            return WriteStatus.INVALID_VALUE;
        }
        return lineManager.removeLineAdmin(line.getId(), adminId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus transferOwner(Line line, UUID ownerId) {
        if (ownerId == null) {
            return WriteStatus.INVALID_VALUE;
        }
        return lineManager.setLineOwner(line.getId(), ownerId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public ClearRouteResult clearRoutePoints(Line line) {
        int previousCount = line.getRoutePoints().size();
        WriteStatus status = lineManager.clearLineRoutePoints(line.getId()) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
        return new ClearRouteResult(status, previousCount);
    }

    public WriteStatus cloneReverseLine(String sourceId, String newId, String stopIdSuffix, UUID ownerId) {
        if (!isValidId(newId)) {
            return WriteStatus.INVALID_ID;
        }
        return lineManager.cloneReverseLine(sourceId, newId, stopIdSuffix, ownerId)
                ? WriteStatus.SUCCESS
                : WriteStatus.FAILED;
    }

    public WriteStatus setTicketPrice(String id, double price) {
        if (!Double.isFinite(price) || price < 0.0) {
            return WriteStatus.INVALID_VALUE;
        }
        return lineManager.setLineTicketPrice(id, price) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    // =============================================================
    // Fare Rule Operations
    // =============================================================

    /**
     * Set or update the fare rule for a line.
     *
     * @param id       line ID
     * @param mode     pricing mode: "flat", "distance", or "interval"
     * @param baseFare base fare amount
     * @param perUnit  per-block rate (distance) or per-interval rate (interval)
     * @param maxFare  maximum fare cap (null = no cap, 0+ = cap)
     * @return write status
     */
    public WriteStatus setFareRule(String id, String mode, double baseFare, Double perUnit, Double maxFare) {
        if (baseFare < 0.0 || (perUnit != null && perUnit < 0.0) || (maxFare != null && maxFare < 0.0)) {
            return WriteStatus.INVALID_VALUE;
        }

        FareRule.PricingMode pricingMode;
        try {
            pricingMode = FareRule.PricingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WriteStatus.INVALID_VALUE;
        }

        Line line = lineManager.getLine(id);
        if (line == null) {
            return WriteStatus.NOT_FOUND;
        }

        FareRule rule = new FareRule(pricingMode, baseFare);
        if (pricingMode == FareRule.PricingMode.DISTANCE && perUnit != null) {
            rule.setPerBlockRate(perUnit);
        }
        if (pricingMode == FareRule.PricingMode.INTERVAL && perUnit != null) {
            rule.setPerIntervalRate(perUnit);
        }
        if (maxFare != null && maxFare > 0.0) {
            rule.setMaxFare(maxFare);
        }

        line.setFareRule(rule);
        lineManager.saveConfig();
        return WriteStatus.SUCCESS;
    }

    /**
     * Remove the fare rule from a line, reverting to legacy flat pricing.
     *
     * @param id line ID
     * @return true if successful
     */
    public boolean resetFareRule(String id) {
        Line line = lineManager.getLine(id);
        if (line == null) return false;
        line.setFareRule(null);
        lineManager.saveConfig();
        return true;
    }

    // =============================================================
    // Line Status Operations
    // =============================================================

    /**
     * Set the operational status of a line.
     *
     * @param id     line ID
     * @param status status string: "normal", "suspended", "maintenance"
     * @return write status
     */
    public WriteStatus setLineStatus(String id, String status) {
        LineStatus lineStatus = LineStatus.fromConfig(status);
        if (lineStatus == LineStatus.NORMAL && !status.equalsIgnoreCase("normal")) {
            return WriteStatus.INVALID_VALUE;
        }

        Line line = lineManager.getLine(id);
        if (line == null) {
            return WriteStatus.NOT_FOUND;
        }

        line.setLineStatus(lineStatus);
        lineManager.saveConfig();
        return WriteStatus.SUCCESS;
    }

    // =============================================================
    // Alternative Route Operations
    // =============================================================

    /**
     * Add an alternative route to a line (suggested when line is suspended).
     *
     * @param id       line ID
     * @param altLineId alternative line ID
     * @return true if added successfully
     */
    public boolean addAlternativeRoute(String id, String altLineId) {
        Line line = lineManager.getLine(id);
        if (line == null) return false;
        boolean result = line.addAlternativeRoute(altLineId);
        if (result) {
            lineManager.saveConfig();
        }
        return result;
    }

    /**
     * Remove an alternative route from a line.
     *
     * @param id       line ID
     * @param altLineId alternative line ID to remove
     * @return true if removed successfully
     */
    public boolean removeAlternativeRoute(String id, String altLineId) {
        Line line = lineManager.getLine(id);
        if (line == null) return false;
        boolean result = line.removeAlternativeRoute(altLineId);
        if (result) {
            lineManager.saveConfig();
        }
        return result;
    }

    // =============================================================
    // Suspension Message Operations
    // =============================================================

    /**
     * Set the message shown to players when trying to board a suspended line.
     *
     * @param id      line ID
     * @param message suspension message
     * @return true if set successfully
     */
    public boolean setSuspensionMessage(String id, String message) {
        Line line = lineManager.getLine(id);
        if (line == null) return false;
        line.setSuspensionMessage(message);
        lineManager.saveConfig();
        return true;
    }

    public boolean isValidId(String id) {
        return id != null
                && !id.trim().isEmpty()
                && id.length() <= MAX_ID_LENGTH
                && ID_PATTERN.matcher(id).matches();
    }

    public boolean isValidColor(String color) {
        return color != null
                && (LEGACY_COLOR_PATTERN.matcher(color).matches()
                || HEX_COLOR_PATTERN.matcher(color).matches());
    }
}
