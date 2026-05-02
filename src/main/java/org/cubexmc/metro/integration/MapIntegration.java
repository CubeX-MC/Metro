package org.cubexmc.metro.integration;

public interface MapIntegration {
    default boolean isAvailable() {
        return true;
    }

    void enable();

    void disable();

    void refresh();

    boolean isEnabled();
}
