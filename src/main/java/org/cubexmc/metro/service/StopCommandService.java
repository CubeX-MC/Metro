package org.cubexmc.metro.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Stop;

/**
 * Business operations used by stop commands.
 */
public class StopCommandService {

    private static final int MAX_ID_LENGTH = 64;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    public static final Set<String> TITLE_TYPES = Set.of("stop_continuous", "arrive_stop", "terminal_stop", "departure");
    public static final Set<String> TITLE_KEYS = Set.of("title", "subtitle", "actionbar");

    private final StopManager stopManager;

    public StopCommandService(StopManager stopManager) {
        this.stopManager = stopManager;
    }

    public enum WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_ACTION,
        INVALID_TITLE_TYPE,
        INVALID_TITLE_KEY,
        EXISTS,
        NOT_FOUND,
        FAILED,
        NOT_RAIL,
        NOT_IN_STOP
    }

    public record CreateStopResult(WriteStatus status, Stop stop) {
    }

    public record SetPointResult(WriteStatus status, float yaw) {
    }

    public CreateStopResult createStop(String id, String name, Location corner1, Location corner2, UUID ownerId) {
        if (!isValidId(id)) {
            return new CreateStopResult(WriteStatus.INVALID_ID, null);
        }
        Stop stop = stopManager.createStop(id, name, corner1, corner2, ownerId);
        return stop == null
                ? new CreateStopResult(WriteStatus.EXISTS, null)
                : new CreateStopResult(WriteStatus.SUCCESS, stop);
    }

    public WriteStatus deleteStop(String id) {
        return stopManager.deleteStop(id) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setCorners(String id, Location corner1, Location corner2) {
        return stopManager.setStopCorners(id, corner1, corner2) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public SetPointResult setPoint(String id, Stop stop, Location location, Float yaw) {
        Material type = location.getBlock().getType();
        if (!isRail(type)) {
            return new SetPointResult(WriteStatus.NOT_RAIL, 0.0f);
        }
        if (!stop.isInStop(location)) {
            return new SetPointResult(WriteStatus.NOT_IN_STOP, 0.0f);
        }

        float resolvedYaw = yaw == null ? location.getYaw() : yaw;
        WriteStatus status = stopManager.setStopPoint(id, location, resolvedYaw) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
        return new SetPointResult(status, resolvedYaw);
    }

    public WriteStatus addTransferLine(String stopId, String lineId) {
        return stopManager.addTransferLine(stopId, lineId) ? WriteStatus.SUCCESS : WriteStatus.EXISTS;
    }

    public WriteStatus removeTransferLine(String stopId, String lineId) {
        return stopManager.removeTransferLine(stopId, lineId) ? WriteStatus.SUCCESS : WriteStatus.NOT_FOUND;
    }

    public WriteStatus setCustomTitle(Stop stop, String titleType, String titleKey, String titleValue) {
        WriteStatus validation = validateTitlePath(titleType, titleKey);
        if (validation != WriteStatus.SUCCESS) {
            return validation;
        }

        Map<String, String> existing = stop.getCustomTitle(titleType);
        Map<String, String> updated = existing == null ? new HashMap<>() : new HashMap<>(existing);
        updated.put(titleKey, titleValue);
        stop.setCustomTitle(titleType, updated);
        stopManager.saveConfig();
        return WriteStatus.SUCCESS;
    }

    public WriteStatus removeCustomTitleType(Stop stop, String titleType) {
        if (!TITLE_TYPES.contains(titleType)) {
            return WriteStatus.INVALID_TITLE_TYPE;
        }
        if (!stop.removeCustomTitle(titleType)) {
            return WriteStatus.NOT_FOUND;
        }
        stopManager.saveConfig();
        return WriteStatus.SUCCESS;
    }

    public WriteStatus removeCustomTitleKey(Stop stop, String titleType, String titleKey) {
        WriteStatus validation = validateTitlePath(titleType, titleKey);
        if (validation != WriteStatus.SUCCESS) {
            return validation;
        }

        Map<String, String> existing = stop.getCustomTitle(titleType);
        if (existing == null || !existing.containsKey(titleKey)) {
            return WriteStatus.NOT_FOUND;
        }
        Map<String, String> updated = new HashMap<>(existing);
        updated.remove(titleKey);
        if (updated.isEmpty()) {
            stop.removeCustomTitle(titleType);
        } else {
            stop.setCustomTitle(titleType, updated);
        }
        stopManager.saveConfig();
        return WriteStatus.SUCCESS;
    }

    public WriteStatus renameStop(String id, String name) {
        return stopManager.setStopName(id, name) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus addAdmin(String stopId, UUID adminId) {
        return stopManager.addStopAdmin(stopId, adminId) ? WriteStatus.SUCCESS : WriteStatus.EXISTS;
    }

    public WriteStatus removeAdmin(String stopId, UUID adminId) {
        return stopManager.removeStopAdmin(stopId, adminId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus setOwner(String stopId, UUID ownerId) {
        return stopManager.setStopOwner(stopId, ownerId) ? WriteStatus.SUCCESS : WriteStatus.FAILED;
    }

    public WriteStatus updateLineLink(String action, String stopId, String lineId) {
        if ("allow".equalsIgnoreCase(action)) {
            return stopManager.allowLineLink(stopId, lineId) ? WriteStatus.SUCCESS : WriteStatus.EXISTS;
        }
        if ("deny".equalsIgnoreCase(action)) {
            return stopManager.denyLineLink(stopId, lineId) ? WriteStatus.SUCCESS : WriteStatus.NOT_FOUND;
        }
        return WriteStatus.INVALID_ACTION;
    }

    public boolean isValidId(String id) {
        return id != null
                && !id.isBlank()
                && id.length() <= MAX_ID_LENGTH
                && ID_PATTERN.matcher(id).matches();
    }

    private WriteStatus validateTitlePath(String titleType, String titleKey) {
        if (!TITLE_TYPES.contains(titleType)) {
            return WriteStatus.INVALID_TITLE_TYPE;
        }
        if (!TITLE_KEYS.contains(titleKey)) {
            return WriteStatus.INVALID_TITLE_KEY;
        }
        return WriteStatus.SUCCESS;
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }
}
