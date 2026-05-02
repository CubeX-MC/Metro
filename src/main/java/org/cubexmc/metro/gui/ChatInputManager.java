package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatInputManager implements Listener {

    private final Metro plugin;
    private final ChatCallbackScheduler callbackScheduler;
    private final Map<UUID, ChatInputCallback> pendingInputs = new HashMap<>();

    public ChatInputManager(Metro plugin) {
        this(plugin, (metro, player, task) -> SchedulerUtil.entityRun(metro, player, task, 0L, -1L));
    }

    ChatInputManager(Metro plugin, ChatCallbackScheduler callbackScheduler) {
        this.plugin = plugin;
        this.callbackScheduler = callbackScheduler;
    }

    public void requestInput(Player player, String prompt, ChatInputCallback callback) {
        player.closeInventory();
        player.sendMessage(prompt);
        pendingInputs.put(player.getUniqueId(), callback);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (pendingInputs.containsKey(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage();
            ChatInputCallback callback = pendingInputs.remove(uuid);
            
            if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("取消")) {
                player.sendMessage(plugin.getLanguageManager().getMessage("chat.input_cancelled"));
                scheduleCallback(player, callback::onCancel);
                return;
            }
            
            scheduleCallback(player, () -> callback.onInput(input));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    public interface ChatInputCallback {
        void onInput(String input);
        default void onCancel() {}
    }

    @FunctionalInterface
    interface ChatCallbackScheduler {
        void run(Metro plugin, Player player, Runnable task);
    }

    private void scheduleCallback(Player player, Runnable task) {
        callbackScheduler.run(plugin, player, task);
    }
}
