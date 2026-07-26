package org.cubexmc.metro.util

import org.bukkit.NamespacedKey
import org.cubexmc.metro.Metro

/**
 * Shared constants used across runtime components.
 */
object MetroConstants {
    const val METRO_MINECART_NAME: String = "MetroMinecart"
    const val SCOREBOARD_OBJECTIVE: String = "metro"

    // PDC key for identifying metro minecarts
    private var minecartKey: NamespacedKey? = null

    // PDC key for identifying items that belong to a Metro GUI and must never
    // end up in a player inventory
    private var guiItemKey: NamespacedKey? = null

    @JvmStatic
    fun initialize(plugin: Metro) {
        if (minecartKey == null) {
            minecartKey = NamespacedKey(plugin, "is_metro")
        }
        if (guiItemKey == null) {
            guiItemKey = NamespacedKey(plugin, "gui_item")
        }
    }

    @JvmStatic
    fun getMinecartKey(): NamespacedKey? = minecartKey

    @JvmStatic
    fun getGuiItemKey(): NamespacedKey? = guiItemKey
}
