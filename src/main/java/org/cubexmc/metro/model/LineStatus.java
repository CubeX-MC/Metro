package org.cubexmc.metro.model;

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
        return name().toLowerCase();
    }

    public static LineStatus fromConfig(String value) {
        if (value == null) return NORMAL;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
