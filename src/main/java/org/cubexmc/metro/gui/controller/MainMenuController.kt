package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.GuiHolder

class MainMenuController(private val plugin: Metro) {

    fun handleClick(player: Player, holder: GuiHolder, slot: Int) {
        when (slot) {
            SLOT_LINE_MANAGE -> plugin.guiManager.openLineList(player, 0, false, holder.snapshot())
            SLOT_STOP_MANAGE -> plugin.guiManager.openStopList(player, 0, false, holder.snapshot())
            else -> Unit
        }
    }

    private companion object {
        const val SLOT_LINE_MANAGE = 11
        const val SLOT_STOP_MANAGE = 15
    }
}
