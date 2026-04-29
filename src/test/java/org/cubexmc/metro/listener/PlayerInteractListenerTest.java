package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.junit.jupiter.api.Test;

class PlayerInteractListenerTest {

    @Test
    void shouldSetFirstSelectionCornerWithConfiguredTool() {
        Fixtures fixtures = new Fixtures();
        PlayerInteractEvent event = fixtures.event(Action.LEFT_CLICK_BLOCK, fixtures.selectionBlock);
        when(fixtures.languageManager.getMessage(eq("selection.corner1_set"), anyMap())).thenReturn("corner one");

        fixtures.listener.onPlayerInteract(event);

        verify(fixtures.selectionManager).setCorner1(fixtures.player, fixtures.selectionLocation);
        verify(fixtures.player).sendMessage("corner one");
        verify(event).setCancelled(true);
    }

    @Test
    void shouldSetSecondSelectionCornerWithConfiguredTool() {
        Fixtures fixtures = new Fixtures();
        PlayerInteractEvent event = fixtures.event(Action.RIGHT_CLICK_BLOCK, fixtures.selectionBlock);
        when(fixtures.languageManager.getMessage(eq("selection.corner2_set"), anyMap())).thenReturn("corner two");

        fixtures.listener.onPlayerInteract(event);

        verify(fixtures.selectionManager).setCorner2(fixtures.player, fixtures.selectionLocation);
        verify(fixtures.player).sendMessage("corner two");
        verify(event).setCancelled(true);
    }

    @Test
    void shouldIgnoreRightClickedRailsWhenPlayerCannotUseMetro() {
        Fixtures fixtures = new Fixtures();
        Block rail = fixtures.block(Material.POWERED_RAIL, new Location(null, 8, 64, 8));
        PlayerInteractEvent event = fixtures.event(Action.RIGHT_CLICK_BLOCK, rail);
        when(fixtures.player.hasPermission("metro.stop.create")).thenReturn(false);
        when(fixtures.player.hasPermission("metro.use")).thenReturn(false);

        fixtures.listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
        verify(fixtures.stopManager).getBestStopContainingLocation(eq(rail.getLocation()), eq(0.0f));
    }

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final ConfigFacade configFacade = mock(ConfigFacade.class);
        private final LanguageManager languageManager = mock(LanguageManager.class);
        private final SelectionManager selectionManager = mock(SelectionManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final Player player = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final Location playerLocation = new Location(null, 0, 64, 0);
        private final Location selectionLocation = new Location(null, 1, 65, 2);
        private final Block selectionBlock = block(Material.STONE, selectionLocation);
        private final PlayerInteractListener listener;

        private Fixtures() {
            playerLocation.setYaw(0.0f);
            when(plugin.getConfigFacade()).thenReturn(configFacade);
            when(plugin.getLanguageManager()).thenReturn(languageManager);
            when(plugin.getSelectionManager()).thenReturn(selectionManager);
            when(plugin.getStopManager()).thenReturn(stopManager);
            when(configFacade.getSelectionTool()).thenReturn(Material.GOLDEN_SHOVEL);
            when(configFacade.getInteractCooldown()).thenReturn(2000L);
            when(configFacade.getMinecartPendingTimeout()).thenReturn(60000L);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getInventory()).thenReturn(inventory);
            when(player.getLocation()).thenReturn(playerLocation);
            when(player.hasPermission("metro.admin")).thenReturn(false);
            when(player.hasPermission("metro.stop.create")).thenReturn(true);
            when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.GOLDEN_SHOVEL));
            this.listener = new PlayerInteractListener(plugin, false);
        }

        private PlayerInteractEvent event(Action action, Block clickedBlock) {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getAction()).thenReturn(action);
            when(event.getClickedBlock()).thenReturn(clickedBlock);
            when(event.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.HAND);
            return event;
        }

        private Block block(Material material, Location location) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(material);
            when(block.getLocation()).thenReturn(location);
            return block;
        }
    }
}
