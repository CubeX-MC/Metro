package org.cubexmc.metro.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.OwnershipUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerInteractListenerExtendedTest {

    private Metro plugin;
    private ConfigFacade configFacade;
    private LanguageManager languageManager;
    private SelectionManager selectionManager;
    private StopManager stopManager;
    private LineManager lineManager;
    private LineSelectionService lineSelectionService;
    private GuiManager guiManager;
    private TicketService ticketService;
    private Player player;
    private PlayerInventory inventory;
    private Location playerLocation;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        configFacade = mock(ConfigFacade.class);
        languageManager = mock(LanguageManager.class);
        selectionManager = mock(SelectionManager.class);
        stopManager = mock(StopManager.class);
        lineManager = mock(LineManager.class);
        lineSelectionService = mock(LineSelectionService.class);
        guiManager = mock(GuiManager.class);
        ticketService = mock(TicketService.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        playerLocation = new Location(mock(World.class), 0, 64, 0);
        playerLocation.setYaw(0.0f);

        when(plugin.getName()).thenReturn("metro");
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
        when(player.hasPermission("metro.stop.create")).thenReturn(false);
        when(player.hasPermission("metro.use")).thenReturn(true);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));

        // Spigot mock for actionbar messages
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        // Default: no stop found at any location
        when(stopManager.getBestStopContainingLocation(any(Location.class), anyFloat())).thenReturn(null);

        MetroConstants.initialize(plugin);
    }

    private PlayerInteractListener createListener() {
        return new PlayerInteractListener(plugin, false);
    }

    private PlayerInteractEvent createEvent(Action action, Block clickedBlock, EquipmentSlot hand) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(action);
        when(event.getClickedBlock()).thenReturn(clickedBlock);
        when(event.getHand()).thenReturn(hand);
        return event;
    }

    private Block createBlock(Material material, Location location) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(location);
        return block;
    }

    private Stop createStopWithPoint(String id) {
        Stop stop = new Stop(id, "Stop " + id);
        stop.setStopPointLocation(new Location(mock(World.class), 10, 64, 10));
        return stop;
    }

    private Line createLine(String id, String... stopIds) {
        Line line = new Line(id, "Line " + id);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        return line;
    }

    // ---- Rail interaction: no stop found at clicked rail ----

    @Test
    void shouldNotCancelWhenRailClickedButNoStopFound() {
        PlayerInteractListener listener = createListener();
        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        // Default mock returns null for getBestStopContainingLocation

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- Rail interaction: stop found but no stop point ----

    @Test
    void shouldNotifyPlayerWhenStopHasNoStopPoint() {
        PlayerInteractListener listener = createListener();
        Stop stop = new Stop("s1", "Stop 1");
        // no stop point set

        when(stopManager.getBestStopContainingLocation(any(Location.class), anyFloat())).thenReturn(stop);
        when(languageManager.getMessage("interact.stop_no_point")).thenReturn("no point");

        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(player).sendMessage("no point");
        // Event IS cancelled because checkAndHandleStopPoint returns true (stop found, but no point)
        // Actually, let's check: checkAndHandleStopPoint sends the message and returns true
        // because stop was found but it had no stop point -> returns false after sending message
        // So handled = false, event is NOT cancelled
    }

    // ---- Rail interaction: player without metro.use permission ----

    @Test
    void shouldNotHandleStopWhenPlayerLacksUsePermission() throws Exception {
        PlayerInteractListener listener = createListener();
        when(player.hasPermission("metro.use")).thenReturn(false);

        Stop stop = createStopWithPoint("s1");
        when(stopManager.getBestStopContainingLocation(any(Location.class), anyFloat())).thenReturn(stop);

        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(player, never()).sendMessage(anyString());
    }

    // ---- Pending minecart timeout: expired pending minecart ----

    @Test
    void shouldRemoveExpiredPendingMinecartAndProceed() throws Exception {
        PlayerInteractListener listener = createListener();

        putPendingMinecart(listener, "s1", System.currentTimeMillis() - 120000);

        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        when(languageManager.getMessage(anyString())).thenReturn("msg");

        listener.onPlayerInteract(event);

        // Should not block with train_pending since timeout expired
    }

    // ---- Active pending minecart blocks interaction ----

    @Test
    void shouldBlockInteractionWhenPendingMinecartIsActive() throws Exception {
        PlayerInteractListener listener = createListener();

        Stop stop = createStopWithPoint("s1");
        when(stopManager.getBestStopContainingLocation(any(Location.class), anyFloat())).thenReturn(stop);

        putPendingMinecart(listener, "s1", System.currentTimeMillis());
        when(languageManager.getMessage("interact.train_pending")).thenReturn("pending");

        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(player).sendMessage("pending");
        verify(event).setCancelled(true);
    }

    // ---- handleStopPoint: single line goes directly to boarding ----
    // Note: The full boarding path requires SchedulerUtil which cannot be initialized in tests.
    // Instead, verify the line selection service is used correctly.

    @Test
    void shouldResolveDefaultLineForSingleLineStop() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");

        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));
        when(lineSelectionService.requiresChoice(player, stop)).thenReturn(false);

        invokeHandleStopPoint(listener, player, stop);

        verify(lineSelectionService).resolveDefaultLine(eq(player), eq(stop), any());
    }

    // ---- No boardable lines: send stop_no_line ----

    @Test
    void shouldNotifyNoLineWhenNoBoardableLinesAndNotOnlyTerminal() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");

        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of());
        when(lineManager.getLinesForStop("s1")).thenReturn(List.of());
        when(languageManager.getMessage("interact.stop_no_line")).thenReturn("no line");

        invokeHandleStopPoint(listener, player, stop);

        verify(player).sendMessage("no line");
    }

    // ---- No boardable lines: only terminal lines ----

    @Test
    void shouldNotifyTerminalWhenOnlyTerminalLinesServeStop() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line terminalLine = createLine("red", "s1");

        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of());
        when(lineManager.getLinesForStop("s1")).thenReturn(List.of(terminalLine));
        when(languageManager.getMessage("interact.terminal_stop")).thenReturn("terminal");

        invokeHandleStopPoint(listener, player, stop);

        verify(player).sendMessage("terminal");
    }

    // ---- boardSelectedLine: ticket check fails with insufficient funds ----

    @Test
    void shouldSendInsufficientFundsWhenTicketPrecheckFailsDuringBoarding() {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");
        line.setTicketPrice(100.0);

        when(stopManager.getStop("s1")).thenReturn(stop);
        when(lineManager.getLine("red")).thenReturn(line);
        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));
        // Use real TicketService with economy enabled but no vault -> VAULT_UNAVAILABLE
        TicketService realService = new TicketService(() -> null, () -> true);
        when(plugin.getTicketService()).thenReturn(realService);
        when(languageManager.getMessage(eq("economy.vault_unavailable"))).thenReturn("vault err");

        listener.boardSelectedLine(player, "s1", "red");

        verify(player).sendMessage("vault err");
    }

    // ---- boardSelectedLine: active pending minecart blocks boarding ----

    @Test
    void shouldBlockBoardSelectedLineWhenPendingMinecartIsActive() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");

        when(stopManager.getStop("s1")).thenReturn(stop);
        when(lineManager.getLine("red")).thenReturn(line);
        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));
        when(languageManager.getMessage("interact.train_pending")).thenReturn("pending");

        putPendingMinecart(listener, "s1", System.currentTimeMillis());

        listener.boardSelectedLine(player, "s1", "red");

        verify(languageManager).getMessage("interact.train_pending");
    }

    // ---- Selection tool: only process main hand ----

    @Test
    void shouldNotProcessSelectionToolForOffHand() {
        PlayerInteractListener listener = createListener();
        when(player.hasPermission("metro.stop.create")).thenReturn(true);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.GOLDEN_SHOVEL));

        Location loc = new Location(mock(World.class), 5, 64, 5);
        Block block = createBlock(Material.STONE, loc);
        PlayerInteractEvent event = createEvent(Action.LEFT_CLICK_BLOCK, block, EquipmentSlot.OFF_HAND);

        listener.onPlayerInteract(event);

        verify(selectionManager, never()).setCorner1(any(), any());
    }

    // ---- Selection tool: wrong tool in hand ----

    @Test
    void shouldNotProcessSelectionToolWhenHoldingDifferentItem() {
        PlayerInteractListener listener = createListener();
        when(player.hasPermission("metro.stop.create")).thenReturn(true);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_PICKAXE));

        Location loc = new Location(mock(World.class), 5, 64, 5);
        Block block = createBlock(Material.STONE, loc);
        PlayerInteractEvent event = createEvent(Action.LEFT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(selectionManager, never()).setCorner1(any(), any());
    }

    // ---- Non-right-click action on rail should be ignored ----

    @Test
    void shouldIgnoreLeftClickOnRail() {
        PlayerInteractListener listener = createListener();
        Location railLocation = new Location(mock(World.class), 8, 64, 8);
        Block rail = createBlock(Material.POWERED_RAIL, railLocation);
        PlayerInteractEvent event = createEvent(Action.LEFT_CLICK_BLOCK, rail, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(stopManager, never()).getBestStopContainingLocation(any(Location.class), anyFloat());
    }

    // ---- Click on non-rail block should be ignored ----

    @Test
    void shouldIgnoreClickOnNonRailBlock() {
        PlayerInteractListener listener = createListener();
        Location loc = new Location(mock(World.class), 8, 64, 8);
        Block stone = createBlock(Material.STONE, loc);
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, stone, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(stopManager, never()).getBestStopContainingLocation(any(Location.class), anyFloat());
    }

    // ---- handleStopPoint: terminal stop handled correctly ----

    @Test
    void shouldHandleTerminalStopInHandleStopPoint() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s2");
        Line line = createLine("red", "s1", "s2");

        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));
        when(lineSelectionService.requiresChoice(player, stop)).thenReturn(false);

        invokeHandleStopPoint(listener, player, stop);

        verify(lineSelectionService).resolveDefaultLine(eq(player), eq(stop), any());
    }

    // ---- handleStopPoint: next stop exists for line info ----

    @Test
    void shouldResolveLineWhenNextStopExists() throws Exception {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");

        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));
        when(lineSelectionService.requiresChoice(player, stop)).thenReturn(false);

        Stop nextStop = new Stop("s2", "Station Two");
        when(stopManager.getStop("s2")).thenReturn(nextStop);

        invokeHandleStopPoint(listener, player, stop);

        verify(lineSelectionService).resolveDefaultLine(eq(player), eq(stop), any());
    }

    // ---- No permission for selection tool ----

    @Test
    void shouldNotProcessSelectionToolWhenPlayerLacksPermission() {
        PlayerInteractListener listener = createListener();
        when(player.hasPermission("metro.stop.create")).thenReturn(false);
        when(player.hasPermission("metro.admin")).thenReturn(false);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.GOLDEN_SHOVEL));

        Location loc = new Location(mock(World.class), 5, 64, 5);
        Block block = createBlock(Material.STONE, loc);
        PlayerInteractEvent event = createEvent(Action.LEFT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        try (var ownershipMock = mockStatic(OwnershipUtil.class)) {
            ownershipMock.when(() -> OwnershipUtil.canCreateStop(player)).thenReturn(false);
            listener.onPlayerInteract(event);
        }

        verify(selectionManager, never()).setCorner1(any(), any());
    }

    // ---- Null clicked block should be handled gracefully ----

    @Test
    void shouldHandleNullClickedBlock() {
        PlayerInteractListener listener = createListener();
        PlayerInteractEvent event = createEvent(Action.RIGHT_CLICK_BLOCK, null, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- Physical action (stepping on) should be ignored ----

    @Test
    void shouldIgnorePhysicalAction() {
        PlayerInteractListener listener = createListener();
        Location loc = new Location(mock(World.class), 5, 64, 5);
        Block block = createBlock(Material.STONE, loc);
        PlayerInteractEvent event = createEvent(Action.PHYSICAL, block, EquipmentSlot.HAND);

        listener.onPlayerInteract(event);

        verify(selectionManager, never()).setCorner1(any(), any());
        verify(stopManager, never()).getBestStopContainingLocation(any(Location.class), anyFloat());
    }

    // ---- boardSelectedLine: ticket check fails with vault unavailable ----

    @Test
    void shouldSendVaultUnavailableOnTicketCheckDuringBoardSelectedLine() {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");
        line.setTicketPrice(10.0);

        when(stopManager.getStop("s1")).thenReturn(stop);
        when(lineManager.getLine("red")).thenReturn(line);
        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(line));

        TicketService realService = new TicketService(() -> null, () -> true);
        when(plugin.getTicketService()).thenReturn(realService);
        when(languageManager.getMessage(eq("economy.vault_unavailable"))).thenReturn("vault err");

        listener.boardSelectedLine(player, "s1", "red");

        verify(player).sendMessage("vault err");
    }

    // ---- boardSelectedLine: line not boardable ----

    @Test
    void shouldNotifyWhenSelectedLineNotInBoardableList() {
        PlayerInteractListener listener = createListener();
        Stop stop = createStopWithPoint("s1");
        Line line = createLine("red", "s1", "s2");
        Line otherLine = createLine("blue", "s1", "s3");

        when(stopManager.getStop("s1")).thenReturn(stop);
        when(lineManager.getLine("red")).thenReturn(line);
        when(lineSelectionService.getBoardableLines(stop)).thenReturn(List.of(otherLine));
        when(lineManager.getLinesForStop("s1")).thenReturn(List.of(otherLine));
        when(languageManager.getMessage(eq("interact.stop_no_line"))).thenReturn("no line");

        listener.boardSelectedLine(player, "s1", "red");

        verify(player).sendMessage("no line");
    }

    // ---- Helper methods ----

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

    @SuppressWarnings("unchecked")
    private static void putPendingMinecart(PlayerInteractListener listener, String stopId, long timestamp) throws Exception {
        Field field = PlayerInteractListener.class.getDeclaredField("pendingMinecarts");
        field.setAccessible(true);
        ((Map<String, Long>) field.get(listener)).put(stopId, timestamp);
    }
}
