package org.cubexmc.metro.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.cubexmc.metro.Metro;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatInputManager implements Listener {

    private final Metro plugin;
    private final Map<UUID, ChatInputCallback> pendingInputs = new HashMap<>();

    public ChatInputManager(Metro plugin) {
        this.plugin = plugin;
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
                // Execute a specific cancel block if needed, or simply return.
                org.cubexmc.metro.util.SchedulerUtil.entityRun(plugin, player, () -> callback.onCancel(), 0L, -1L);
                return;
            }
            
            org.cubexmc.metro.util.SchedulerUtil.entityRun(plugin, player, () -> callback.onInput(input), 0L, -1L);
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
}