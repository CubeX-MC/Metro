package org.cubexmc.metro.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

/**
 * Resolves which lines are boardable at a given stop, handles per-player
 * line-choice remembering, and selects the default line by yaw alignment.
 * <p>
 * A line is "boardable" when the stop is the line's current calling point
 * (not a terminal), the next stop has a stop-point configured, and the
 * stop's linked-line set (if non-empty) includes the line.
 */
public class LineSelectionService {

    private final LineManager lineManager;
    private final StopManager stopManager;
    private final Map<UUID, Map<String, String>> recentChoices = new ConcurrentHashMap<>();

    /**
     * @param lineManager the line manager
     * @param stopManager the stop manager
     */
    public LineSelectionService(LineManager lineManager, StopManager stopManager) {
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    /**
     * Returns the lines that can be boarded from the given stop.
     * Results are sorted by line ID.
     *
     * @param stop the stop to query
     * @return sorted, non-null list of boardable lines
     */
    public List<Line> getBoardableLines(Stop stop) {
        if (stop == null) {
            return List.of();
        }

        List<Line> lines = new ArrayList<>();
        for (Line line : lineManager.getLinesForStop(stop.getId())) {
            if (isBoardable(line, stop)) {
                lines.add(line);
            }
        }
        lines.sort(Comparator.comparing(Line::getId));
        return lines;
    }

    /**
     * Returns lines that terminate at the given stop (no next stop).
     * Results are sorted by line ID.
     *
     * @param stop the stop to query
     * @return sorted, non-null list of terminal lines
     */
    public List<Line> getTerminalLines(Stop stop) {
        if (stop == null) {
            return List.of();
        }

        List<Line> lines = new ArrayList<>();
        for (Line line : lineManager.getLinesForStop(stop.getId())) {
            if (isTerminalLine(line, stop)) {
                lines.add(line);
            }
        }
        lines.sort(Comparator.comparing(Line::getId));
        return lines;
    }

    /**
     * Resolves the default line for boarding, preferring a previously
     * remembered choice, then the line whose launch yaw best matches the
     * player's current yaw.
     *
     * @param player          the boarding player
     * @param stop            the stop to board from
     * @param clickedLocation the block the player clicked
     * @return the best-matching boardable line, or {@code null} if none
     */
    public Line resolveDefaultLine(Player player, Stop stop, Location clickedLocation) {
        List<Line> lines = getBoardableLines(stop);
        if (lines.isEmpty()) {
            return null;
        }
        lines.sort(lineComparator(player, stop, clickedLocation));
        return lines.get(0);
    }

    /**
     * Whether the player must choose a line explicitly (multiple boardable
     * lines and no remembered choice).
     *
     * @param player the boarding player
     * @param stop   the stop to board from
     * @return {@code true} if a choice GUI should be shown
     */
    public boolean requiresChoice(Player player, Stop stop) {
        List<Line> lines = getBoardableLines(stop);
        if (lines.size() <= 1) {
            return false;
        }
        return getRememberedLineId(player, stop) == null
                || lines.stream().noneMatch(line -> line.getId().equals(getRememberedLineId(player, stop)));
    }

    /**
     * Records the player's choice of line for a given stop so the same
     * line is auto-selected next time.
     *
     * @param player the boarding player
     * @param stopId the stop ID
     * @param lineId the chosen line ID
     */
    public void rememberChoice(Player player, String stopId, String lineId) {
        if (player == null || stopId == null || stopId.isEmpty() || lineId == null || lineId.isEmpty()) {
            return;
        }
        recentChoices.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(stopId, lineId);
    }

    private boolean isBoardable(Line line, Stop stop) {
        if (!isUsableLineAtStop(line, stop)) {
            return false;
        }

        String nextStopId = line.getNextStopId(stop.getId());
        if (nextStopId == null || nextStopId.isEmpty()) {
            return false;
        }

        Stop nextStop = stopManager.getStop(nextStopId);
        if (nextStop == null || nextStop.getStopPointLocation() == null) {
            return false;
        }

        return true;
    }

    private boolean isTerminalLine(Line line, Stop stop) {
        if (!isUsableLineAtStop(line, stop)) {
            return false;
        }

        String nextStopId = line.getNextStopId(stop.getId());
        return nextStopId == null || nextStopId.isEmpty();
    }

    private boolean isUsableLineAtStop(Line line, Stop stop) {
        if (line == null || stop == null || !line.containsStop(stop.getId()) || stop.getStopPointLocation() == null) {
            return false;
        }

        String lineWorld = line.getWorldName();
        if (lineWorld != null && !lineWorld.isEmpty()) {
            String stopWorld = stop.getWorldName();
            if (stopWorld == null || !lineWorld.equals(stopWorld)) {
                return false;
            }
        }

        Set<String> linkedLineIds = stop.getLinkedLineIds();
        return linkedLineIds.isEmpty() || linkedLineIds.contains(line.getId());
    }

    private Comparator<Line> lineComparator(Player player, Stop stop, Location clickedLocation) {
        String rememberedLineId = getRememberedLineId(player, stop);
        float playerYaw = resolveYaw(player, clickedLocation);
        return Comparator
                .comparing((Line line) -> !line.getId().equals(rememberedLineId))
                .thenComparingDouble(line -> yawDifference(playerYaw, stop != null ? stop.getLaunchYaw() : 0.0f))
                .thenComparing(Line::getId);
    }

    private String getRememberedLineId(Player player, Stop stop) {
        if (player == null || stop == null) {
            return null;
        }
        Map<String, String> choices = recentChoices.get(player.getUniqueId());
        return choices != null ? choices.get(stop.getId()) : null;
    }

    private float resolveYaw(Player player, Location clickedLocation) {
        if (player != null && player.getLocation() != null) {
            return player.getLocation().getYaw();
        }
        return clickedLocation != null ? clickedLocation.getYaw() : 0.0f;
    }

    private double yawDifference(float a, float b) {
        double diff = Math.abs((a - b + 360.0) % 360.0);
        return Math.min(diff, 360.0 - diff);
    }
}
