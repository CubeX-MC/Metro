package org.cubexmc.metro.service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

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
        FAILED,
        STOP_NO_WORLD,
        WORLD_MISMATCH,
        CIRCULAR_INVALID_INDEX
    }

    public record AddStopResult(WriteStatus status, String lineWorld, String stopWorld) {
        static AddStopResult of(WriteStatus status) {
            return new AddStopResult(status, null, null);
        }

        static AddStopResult worldMismatch(String lineWorld, String stopWorld) {
            return new AddStopResult(WriteStatus.WORLD_MISMATCH, lineWorld, stopWorld);
        }
    }

    public record ClearRouteResult(WriteStatus status, int previousPointCount) {
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
        if (stopWorld == null || stopWorld.isBlank()) {
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

    public WriteStatus setRailProtected(String id, boolean enabled) {
        return lineManager.setLineRailProtected(id, enabled) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
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

    public boolean isValidId(String id) {
        return id != null
                && !id.isBlank()
                && id.length() <= MAX_ID_LENGTH
                && ID_PATTERN.matcher(id).matches();
    }

    public boolean isValidColor(String color) {
        return color != null
                && (LEGACY_COLOR_PATTERN.matcher(color).matches()
                || HEX_COLOR_PATTERN.matcher(color).matches());
    }
}
