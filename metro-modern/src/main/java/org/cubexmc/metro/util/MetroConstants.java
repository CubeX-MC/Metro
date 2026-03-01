package org.cubexmc.metro.util;

import org.bukkit.NamespacedKey;
import org.cubexmc.metro.Metro;

/**
 * Shared constants used across runtime components.
 */
public final class MetroConstants {

    private MetroConstants() {
    }

    public static final String METRO_MINECART_NAME = "MetroMinecart";
    public static final String SCOREBOARD_OBJECTIVE = "metro";

    // PDC key for identifying metro minecarts
    private static NamespacedKey minecartKey;

    public static void initialize(Metro plugin) {
        if (minecartKey == null) {
            minecartKey = new NamespacedKey(plugin, "is_metro");
        }
    }

    public static NamespacedKey getMinecartKey() {
        return minecartKey;
    }
}
