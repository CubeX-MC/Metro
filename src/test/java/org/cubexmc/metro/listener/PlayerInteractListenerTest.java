package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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
import org.cubexmc.metro.gui.GuiManager;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.LineSelectionService;
import org.cubexmc.metro.service.TicketService;
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

    @Test
    void shouldOpenLineChoiceWhenStopHasMultipleBoardableLines() throws Exception {
        Fixtures fixtures = new Fixtures();
        Stop stop = fixtures.stopWithPoint("central");
        Line red = fixtures.line("red", "central", "east");
        Line blue = fixtures.line("blue", "central", "west");
        when(fixtures.lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(blue, red));
        when(fixtures.lineSelectionService.requiresChoice(fixtures.player, stop)).thenReturn(true);

        invokeHandleStopPoint(fixtures.listener, fixtures.player, stop);

        verify(fixtures.guiManager).openLineBoardingChoice(fixtures.player, stop, 0);
    }

    @Test
    void shouldCancelRailClickDuringInteractionCooldown() throws Exception {
        Fixtures fixtures = new Fixtures();
        Block rail = fixtures.block(Material.POWERED_RAIL, new Location(null, 8, 64, 8));
        PlayerInteractEvent event = fixtures.event(Action.RIGHT_CLICK_BLOCK, rail);
        when(fixtures.player.hasPermission("metro.stop.create")).thenReturn(false);
        putLastInteractTime(fixtures.listener, fixtures.player.getUniqueId(), System.currentTimeMillis());

        fixtures.listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(fixtures.stopManager, never()).getBestStopContainingLocation(eq(rail.getLocation()), eq(0.0f));
    }

    @Test
    void shouldRejectSelectedLineWhenPlayerCannotUseMetro() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.player.hasPermission("metro.use")).thenReturn(false);
        when(fixtures.languageManager.getMessage("interact.no_permission")).thenReturn("no ride permission");

        fixtures.listener.boardSelectedLine(fixtures.player, "central", "red");

        verify(fixtures.player).sendMessage("no ride permission");
        verify(fixtures.stopManager, never()).getStop(anyString());
        verify(fixtures.lineManager, never()).getLine(anyString());
    }

    @Test
    void shouldNotifyWhenSelectedStopOrLineDoesNotExist() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.player.hasPermission("metro.use")).thenReturn(true);
        when(fixtures.stopManager.getStop("missing")).thenReturn(null);
        when(fixtures.lineManager.getLine("red")).thenReturn(fixtures.line("red", "central", "east"));
        when(fixtures.languageManager.getMessage("interact.stop_no_line")).thenReturn("no line");

        fixtures.listener.boardSelectedLine(fixtures.player, "missing", "red");

        verify(fixtures.player).sendMessage("no line");
    }

    @Test
    void shouldNotifyWhenSelectedStopHasNoStopPoint() {
        Fixtures fixtures = new Fixtures();
        Stop stop = new Stop("central", "Central");
        when(fixtures.player.hasPermission("metro.use")).thenReturn(true);
        when(fixtures.stopManager.getStop("central")).thenReturn(stop);
        when(fixtures.lineManager.getLine("red")).thenReturn(fixtures.line("red", "central", "east"));
        when(fixtures.languageManager.getMessage("interact.stop_no_point")).thenReturn("no point");

        fixtures.listener.boardSelectedLine(fixtures.player, "central", "red");

        verify(fixtures.player).sendMessage("no point");
    }

    @Test
    void shouldNotifyTerminalStopWhenSelectedLineIsNoLongerBoardable() {
        Fixtures fixtures = new Fixtures();
        Stop stop = fixtures.stopWithPoint("central");
        Line terminalLine = fixtures.line("red", "central");
        when(fixtures.player.hasPermission("metro.use")).thenReturn(true);
        when(fixtures.stopManager.getStop("central")).thenReturn(stop);
        when(fixtures.lineManager.getLine("red")).thenReturn(terminalLine);
        when(fixtures.lineSelectionService.getBoardableLines(stop)).thenReturn(List.of());
        when(fixtures.lineManager.getLinesForStop("central")).thenReturn(List.of(terminalLine));
        when(fixtures.languageManager.getMessage("interact.terminal_stop")).thenReturn("terminal");

        fixtures.listener.boardSelectedLine(fixtures.player, "central", "red");

        verify(fixtures.player).sendMessage("terminal");
    }

    @Test
    void shouldSendVaultUnavailableWhenTicketPrecheckFails() {
        Fixtures fixtures = new Fixtures();
        Stop stop = fixtures.stopWithPoint("central");
        Line paidLine = fixtures.line("red", "central", "east");
        paidLine.setTicketPrice(12.5);
        when(fixtures.player.hasPermission("metro.use")).thenReturn(true);
        when(fixtures.stopManager.getStop("central")).thenReturn(stop);
        when(fixtures.lineManager.getLine("red")).thenReturn(paidLine);
        when(fixtures.lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(paidLine));
        when(fixtures.ticketService.checkCanBoard(fixtures.player, paidLine))
                .thenReturn(new TicketService(() -> null, () -> true).checkCanBoard(fixtures.player, paidLine));
        when(fixtures.languageManager.getMessage("economy.vault_unavailable")).thenReturn("vault unavailable");

        fixtures.listener.boardSelectedLine(fixtures.player, "central", "red");

        verify(fixtures.lineSelectionService).rememberChoice(fixtures.player, "central", "red");
        verify(fixtures.player).sendMessage("vault unavailable");
        verify(fixtures.ticketService, never()).createTransaction(fixtures.player, paidLine);
    }

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final ConfigFacade configFacade = mock(ConfigFacade.class);
        private final LanguageManager languageManager = mock(LanguageManager.class);
        private final SelectionManager selectionManager = mock(SelectionManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final LineManager lineManager = mock(LineManager.class);
        private final LineSelectionService lineSelectionService = mock(LineSelectionService.class);
        private final GuiManager guiManager = mock(GuiManager.class);
        private final TicketService ticketService = mock(TicketService.class);
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
            when(plugin.getLineManager()).thenReturn(lineManager);
            when(plugin.getLineSelectionService()).thenReturn(lineSelectionService);
            when(plugin.getGuiManager()).thenReturn(guiManager);
            when(plugin.getTicketService()).thenReturn(ticketService);
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

        private Stop stopWithPoint(String id) {
            Stop stop = new Stop(id, "Stop " + id);
            stop.setStopPointLocation(new Location(null, 10, 64, 10));
            return stop;
        }

        private Line line(String id, String... stopIds) {
            Line line = new Line(id, "Line " + id);
            for (String stopId : stopIds) {
                line.addStop(stopId, -1);
            }
            return line;
        }
    }

    private static void invokeHandleStopPoint(PlayerInteractListener listener, Player player, Stop stop) throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod("handleStopPoint", Player.class, Stop.class);
        method.setAccessible(true);
        method.invoke(listener, player, stop);
    }

    @SuppressWarnings("unchecked")
    private static void putLastInteractTime(PlayerInteractListener listener, UUID playerId, long value) throws Exception {
        Field field = PlayerInteractListener.class.getDeclaredField("lastInteractTime");
        field.setAccessible(true);
        ((Map<UUID, Long>) field.get(listener)).put(playerId, value);
    }
}
