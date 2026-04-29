package org.cubexmc.metro.integration;

public interface MapIntegration {
    void enable();

    void disable();

    void refresh();

    boolean isEnabled();
}
