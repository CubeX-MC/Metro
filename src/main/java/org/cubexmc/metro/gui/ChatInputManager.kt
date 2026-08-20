package org.cubexmc.metro.gui

import java.util.Collections
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.cubexmc.gui.chat.AcceptResult
import org.cubexmc.gui.chat.ChatInputState
import org.cubexmc.gui.chat.ChatOutcome
import org.cubexmc.gui.chat.ModernChatBridge
import org.cubexmc.metro.Metro
import org.cubexmc.metro.util.SchedulerUtil

/**
 * 聊天提问的本插件适配层。
 *
 * 提问状态、关键字判定、两条聊天链路的去重已下沉到 `cubex-gui` 的 [ChatInputState]。
 * 这里只剩平台相关的部分：监听哪个事件、怎么回主线程、以及取消时的提示文案。
 *
 * 下沉顺带修掉一个并发问题：原来的待处理表是普通 `HashMap`，
 * 却被**异步**聊天事件线程读写；[ChatInputState] 内部用的是 `ConcurrentHashMap`。
 *
 * 本插件编译到 spigot-api，引不进 Paper 的 `AsyncChatEvent`，且它把 `net.kyori` relocate 进了
 * 自己的命名空间（编译期的 `Component` 与服务器给的不是同一个类）。因此现代事件走
 * [ModernChatBridge] 的反射注册 —— Paper 上两条链路都接住，Spigot 上自动跳过。
 */
class ChatInputManager(
    private val plugin: Metro,
    private val callbackScheduler: ChatCallbackScheduler,
) : Listener {

    constructor(plugin: Metro) : this(
        plugin,
        ChatCallbackScheduler { metro, player, task -> SchedulerUtil.entityRun(metro, player, task, 0L, -1L) },
    )

    // 提问没有超时(与下沉前一致);除 cancel 外还认本地化的“取消”。
    private val state = ChatInputState<ChatInputCallback>(cancelKeywords = { CANCEL_KEYWORDS })

    fun requestInput(player: Player, prompt: String, callback: ChatInputCallback) {
        player.closeInventory()
        player.sendMessage(prompt)
        state.open(player.uniqueId, allowClear = false, timeoutMillis = NO_TIMEOUT, payload = callback)
    }

    /**
     * 在 Paper 上补一条现代聊天事件监听;Spigot 上是空操作。
     * 由插件在注册本 Listener 之后调用一次,注销绑进插件资源栈,不需要手动清理。
     */
    fun registerModernChat() {
        val listener = ModernChatBridge.register(plugin) { player, message -> capture(player, message) }
        if (listener != null) {
            plugin.bind(Runnable { ModernChatBridge.unregister(listener) })
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (capture(event.player, event.message)) {
            event.isCancelled = true
        }
    }

    /** 返回这行是否归本插件、必须挡在公屏之外。两条聊天链路共用。 */
    private fun capture(player: Player, message: String): Boolean {
        val playerId = player.uniqueId
        return when (val result = state.accept(playerId, message)) {
            AcceptResult.NotOurs -> false
            // 同一行从另一条链路又来了一遍:吞掉但不重复回调。
            AcceptResult.AlreadyTaken -> true
            is AcceptResult.Accepted -> {
                deliver(player, playerId, result.outcome, result.payload)
                true
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        state.forget(event.player.uniqueId)
    }

    private fun deliver(
        player: Player,
        playerId: java.util.UUID,
        outcome: ChatOutcome,
        callback: ChatInputCallback,
    ) {
        when (outcome) {
            ChatOutcome.Cancelled -> {
                player.sendMessage(plugin.languageManager.getMessage("chat.input_cancelled"))
                scheduleCallback(player) {
                    state.settle(playerId)
                    callback.onCancel()
                }
            }

            is ChatOutcome.Submitted -> scheduleCallback(player) {
                state.settle(playerId)
                callback.onInput(outcome.text)
            }

            // 本插件不开放 clear、也没有超时,这两种结果到不了这里;仍然清理去重记录。
            else -> scheduleCallback(player) { state.settle(playerId) }
        }
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

    private companion object {
        const val NO_TIMEOUT = 0L

        val CANCEL_KEYWORDS: List<String> = Collections.unmodifiableList(listOf("cancel", "取消"))
    }
}
