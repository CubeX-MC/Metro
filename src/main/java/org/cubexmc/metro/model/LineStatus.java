package org.cubexmc.metro.model;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Locale;

/**
 * Represents the operational status of a metro line.
 */
public enum LineStatus {
    /** Line is operating normally */
    NORMAL,
    /** Line is temporarily suspended (e.g., maintenance, incident) */
    SUSPENDED,
    /** Line is under maintenance (banners shown, may still operate) */
    MAINTENANCE;

    public boolean isBoardable() {
        return this == NORMAL || this == MAINTENANCE;
    }

    public String getConfigKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LineStatus fromConfig(String value) {
        if (value == null) return NORMAL;
        String normalized = value.trim();
        if (normalized.isEmpty()) return NORMAL;
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Logger.getGlobal().log(Level.WARNING,
                    "Invalid line status in config: '" + value + "', defaulting to NORMAL");
            return NORMAL;
        }
    }
}
