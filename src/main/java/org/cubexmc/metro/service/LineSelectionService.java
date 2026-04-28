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
 * Resolves stable, boardable line choices for a stop.
 */
public class LineSelectionService {

    private final LineManager lineManager;
    private final StopManager stopManager;
    private final Map<UUID, Map<String, String>> recentChoices = new ConcurrentHashMap<>();

    public LineSelectionService(LineManager lineManager, StopManager stopManager) {
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

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

    public Line resolveDefaultLine(Player player, Stop stop, Location clickedLocation) {
        List<Line> lines = getBoardableLines(stop);
        if (lines.isEmpty()) {
            return null;
        }
        lines.sort(lineComparator(player, stop, clickedLocation));
        return lines.get(0);
    }

    public boolean requiresChoice(Player player, Stop stop) {
        List<Line> lines = getBoardableLines(stop);
        if (lines.size() <= 1) {
            return false;
        }
        return getRememberedLineId(player, stop) == null
                || lines.stream().noneMatch(line -> line.getId().equals(getRememberedLineId(player, stop)));
    }

    public void rememberChoice(Player player, String stopId, String lineId) {
        if (player == null || stopId == null || stopId.isEmpty() || lineId == null || lineId.isEmpty()) {
            return;
        }
        recentChoices.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(stopId, lineId);
    }

    private boolean isBoardable(Line line, Stop stop) {
        if (line == null || stop == null || !line.containsStop(stop.getId()) || stop.getStopPointLocation() == null) {
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
