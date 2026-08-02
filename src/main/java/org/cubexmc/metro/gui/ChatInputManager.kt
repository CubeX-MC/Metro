package org.cubexmc.metro.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.SchedulerUtil
import java.util.UUID

class ChatInputManager(
    private val plugin: Metro,
    private val callbackScheduler: ChatCallbackScheduler,
) : Listener {

    constructor(plugin: Metro) : this(
        plugin,
        ChatCallbackScheduler { metro, player, task -> SchedulerUtil.entityRun(metro, player, task, 0L, -1L) },
    )

    private val pendingInputs: MutableMap<UUID, ChatInputCallback> = HashMap()

    fun requestInput(player: Player, prompt: String, callback: ChatInputCallback) {
        player.closeInventory()
        player.sendMessage(prompt)
        pendingInputs[player.uniqueId] = callback
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId

        if (!pendingInputs.containsKey(uuid)) {
            return
        }
        event.isCancelled = true
        val input = event.message
        val callback = pendingInputs.remove(uuid) ?: return

        if (input.equals("cancel", ignoreCase = true) || input.equals("取消", ignoreCase = true)) {
            player.sendMessage(plugin.languageManager.getMessage("chat.input_cancelled"))
            scheduleCallback(player) { callback.onCancel() }
            return
        }

        scheduleCallback(player) { callback.onInput(input) }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingInputs.remove(event.player.uniqueId)
    }

    interface ChatInputCallback {
        fun onInput(input: String)

        fun onCancel() {}
    }

    fun interface ChatCallbackScheduler {
        fun run(plugin: Metro, player: Player, task: Runnable)
    }

    private fun scheduleCallback(player: Player, task: Runnable) {
        callbackScheduler.run(plugin, player, task)
    }
}
