package org.cubexmc.metro.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.junit.jupiter.api.Test;

class ChatInputManagerTest {

    @Test
    void shouldCancelPendingInputAndRunCancelCallback() {
        Metro plugin = mock(Metro.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        Player player = player();
        AtomicInteger inputCalls = new AtomicInteger();
        AtomicInteger cancelCalls = new AtomicInteger();

        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(languageManager.getMessage("chat.input_cancelled")).thenReturn("cancelled");

        ChatInputManager manager = immediateManager(plugin);
        manager.requestInput(player, "prompt", new ChatInputManager.ChatInputCallback() {
            @Override
            public void onInput(String input) {
                inputCalls.incrementAndGet();
            }

            @Override
            public void onCancel() {
                cancelCalls.incrementAndGet();
            }
        });

        AsyncPlayerChatEvent cancelEvent = chatEvent(player, "cancel");
        manager.onPlayerChat(cancelEvent);

        assertTrue(cancelEvent.isCancelled());
        verify(player).closeInventory();
        verify(player).sendMessage("prompt");
        verify(player).sendMessage("cancelled");
        org.junit.jupiter.api.Assertions.assertEquals(0, inputCalls.get());
        org.junit.jupiter.api.Assertions.assertEquals(1, cancelCalls.get());

        AsyncPlayerChatEvent laterEvent = chatEvent(player, "next");
        manager.onPlayerChat(laterEvent);
        assertFalse(laterEvent.isCancelled());
        org.junit.jupiter.api.Assertions.assertEquals(0, inputCalls.get());
        org.junit.jupiter.api.Assertions.assertEquals(1, cancelCalls.get());
    }

    @Test
    void shouldDeliverChatInputOnceAndClearPendingState() {
        Metro plugin = mock(Metro.class);
        Player player = player();
        AtomicReference<String> receivedInput = new AtomicReference<>();

        ChatInputManager manager = immediateManager(plugin);
        manager.requestInput(player, "prompt", receivedInput::set);

        AsyncPlayerChatEvent inputEvent = chatEvent(player, "Central Station");
        manager.onPlayerChat(inputEvent);

        assertTrue(inputEvent.isCancelled());
        org.junit.jupiter.api.Assertions.assertEquals("Central Station", receivedInput.get());

        AsyncPlayerChatEvent laterEvent = chatEvent(player, "ignored");
        manager.onPlayerChat(laterEvent);
        assertFalse(laterEvent.isCancelled());
        org.junit.jupiter.api.Assertions.assertEquals("Central Station", receivedInput.get());
    }

    @Test
    void shouldForgetPendingInputWhenPlayerQuits() {
        Metro plugin = mock(Metro.class);
        Player player = player();
        AtomicInteger inputCalls = new AtomicInteger();

        ChatInputManager manager = immediateManager(plugin);
        manager.requestInput(player, "prompt", input -> inputCalls.incrementAndGet());
        manager.onPlayerQuit(new PlayerQuitEvent(player, "quit"));

        AsyncPlayerChatEvent inputEvent = chatEvent(player, "late");
        manager.onPlayerChat(inputEvent);

        assertFalse(inputEvent.isCancelled());
        org.junit.jupiter.api.Assertions.assertEquals(0, inputCalls.get());
    }

    private ChatInputManager immediateManager(Metro plugin) {
        return new ChatInputManager(plugin, (ignoredPlugin, ignoredPlayer, task) -> task.run());
    }

    private AsyncPlayerChatEvent chatEvent(Player player, String message) {
        return new AsyncPlayerChatEvent(false, player, message, new HashSet<>());
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
