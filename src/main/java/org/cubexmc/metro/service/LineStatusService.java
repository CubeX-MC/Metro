package org.cubexmc.metro.service;

import org.bukkit.Bukkit;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.LineStatusChangeEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;

import java.util.*;

/**
 * Manages line operational status (normal, suspended, maintenance).
 * Provides methods to check, set, and broadcast line status changes.
 */
public class LineStatusService {

    private final Metro plugin;
    private final LineManager lineManager;

    public LineStatusService(Metro plugin, LineManager lineManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
    }

    /**
     * Get the current operational status of a line.
     */
    public LineStatus getStatus(Line line) {
        if (line == null) return LineStatus.NORMAL;
        return line.getLineStatus();
    }

    /**
     * Set the operational status of a line and fire the change event.
     *
     * @param line     the line to update
     * @param newStatus the new status
     * @return true if the status was changed
     */
    public boolean setStatus(Line line, LineStatus newStatus) {
        if (line == null || newStatus == null) return false;

        LineStatus oldStatus = line.getLineStatus();
        if (oldStatus == newStatus) return false;

        line.setLineStatus(newStatus);
        Bukkit.getPluginManager().callEvent(new LineStatusChangeEvent(line, oldStatus, newStatus));
        lineManager.saveConfig();
        return true;
    }

    /**
     * Check if a line is boardable (not suspended).
     */
    public boolean isBoardable(Line line) {
        return line != null && line.getLineStatus().isBoardable();
    }

    /**
     * Check if a line is suspended.
     */
    public boolean isSuspended(Line line) {
        return line != null && line.getLineStatus() == LineStatus.SUSPENDED;
    }

    /**
     * Check if a line is under maintenance.
     */
    public boolean isMaintenance(Line line) {
        return line != null && line.getLineStatus() == LineStatus.MAINTENANCE;
    }

    /**
     * Get a list of suggested alternative lines for a given line.
     * Uses the line's configured alternative route IDs.
     */
    public List<Line> getAlternativeLines(Line line) {
        if (line == null) return Collections.emptyList();

        List<String> altIds = line.getAlternativeRouteIds();
        if (altIds.isEmpty()) return Collections.emptyList();

        List<Line> alternatives = new ArrayList<>();
        for (String altId : altIds) {
            Line altLine = lineManager.getLine(altId);
            if (altLine != null) {
                alternatives.add(altLine);
            }
        }
        return alternatives;
    }

    /**
     * Get the suspension message for a line, or a default if none is set.
     */
    public String getSuspensionMessage(Line line) {
        if (line == null) return "";
        String msg = line.getSuspensionMessage();
        return msg != null ? msg : "";
    }

    /**
     * Get all lines that are currently suspended or under maintenance.
     */
    public List<Line> getNonOperatingLines() {
        List<Line> result = new ArrayList<>();
        for (Line line : lineManager.getAllLines()) {
            if (line.getLineStatus() != LineStatus.NORMAL) {
                result.add(line);
            }
        }
        return result;
    }
}
