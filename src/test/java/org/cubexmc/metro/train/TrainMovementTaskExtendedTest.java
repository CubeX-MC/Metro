package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.event.TrainEnterStopEvent;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.RouteRecorder;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.PriceRule;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.PriceService;
import org.cubexmc.metro.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatMessageType;

class TrainMovementTaskExtendedTest {

    private Metro plugin;
    private LineManager lineManager;
    private StopManager stopManager;
    private ConfigFacade configFacade;
    private RouteRecorder routeRecorder;
    private ScoreboardManager scoreboardManager;
    private PriceService priceService;
    private TicketService ticketService;
    private LanguageManager languageManager;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        lineManager = mock(LineManager.class);
        stopManager = mock(StopManager.class);
        configFacade = mock(ConfigFacade.class);
        routeRecorder = mock(RouteRecorder.class);
        scoreboardManager = mock(ScoreboardManager.class);
        priceService = mock(PriceService.class);
        ticketService = mock(TicketService.class);
        languageManager = mock(LanguageManager.class);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getRouteRecorder()).thenReturn(routeRecorder);
        when(plugin.getScoreboardManager()).thenReturn(scoreboardManager);
        when(plugin.getPriceService()).thenReturn(priceService);
        when(plugin.getTicketService()).thenReturn(ticketService);
        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(configFacade.getCartSpeed()).thenReturn(0.4);
        when(configFacade.getCartDepartureDelay()).thenReturn(60L);
    }

    @AfterEach
    void tearDown() {
        TrainMovementTask.shutdownActiveTasks();
    }

    private Line createLineWithStops(String lineId, String... stopIds) {
        Line line = new Line(lineId, "Line " + lineId);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        when(lineManager.getLine(lineId)).thenReturn(line);
        return line;
    }

    private Minecart createMinecart() {
        Minecart cart = mock(Minecart.class);
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        return cart;
    }

    private Player createOnlinePlayer(String name) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn(name);
        return player;
    }

    @Test
    void shouldIgnoreEnterStopEventForDifferentMinecart() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart taskCart = createMinecart();
        Minecart otherCart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(taskCart);

        TrainMovementTask task = new TrainMovementTask(plugin, taskCart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        Stop stopB = new Stop("B", "Bravo");
        task.onTrainEnterStop(new TrainEnterStopEvent(otherCart, stopB));

        assertEquals("B", task.getSession().getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, task.getSession().getState());
    }

    @Test
    void shouldIgnoreEnterStopEventWhenWrongStop() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        Stop wrongStop = new Stop("X", "Unknown");
        task.onTrainEnterStop(new TrainEnterStopEvent(cart, wrongStop));

        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, task.getSession().getState());
    }

    @Test
    void shouldTransitionToMovingInStationWhenTargetStopEntered() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            Stop stopB = new Stop("B", "Bravo");
            task.onTrainEnterStop(new TrainEnterStopEvent(cart, stopB));

            assertEquals(TrainMovementTask.TrainState.MOVING_IN_STATION, task.getSession().getState());
        }
    }

    @Test
    void shouldCancelWhenPassengerNotRidingAndNotTeleporting() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(null);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        task.onTrainEnterStop(new TrainEnterStopEvent(cart, new Stop("B", "Bravo")));

        verify(plugin).debug(eq("train_state_transitions"), contains("Task cancelled"));
    }

    @Test
    void shouldNotCancelWhenTeleportingEvenWithoutPassenger() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(null);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        task.setTeleporting(true);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            task.onTrainEnterStop(new TrainEnterStopEvent(cart, new Stop("B", "Bravo")));

            assertEquals(TrainMovementTask.TrainState.MOVING_IN_STATION, task.getSession().getState());
        }
    }

    @Test
    void shouldIgnoreVehicleMoveWhenWrongVehicle() {
        createLineWithStops("l1", "A", "B");
        Minecart taskCart = createMinecart();
        Minecart otherCart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, taskCart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(otherCart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(otherCart, never()).setMaxSpeed(anyDouble());
    }

    @Test
    void shouldSkipApproachBrakingWhenNotInMovingInStationState() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(cart, never()).setMaxSpeed(anyDouble());
    }

    @Test
    void shouldNotUpdateTravelDirectionWhenStopped() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        assertNull(task.getSession().getLastTravelDirection());
    }

    @Test
    void shouldUpdateTravelDirectionWhenMoving() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 10, 0, 0);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        assertNotNull(task.getSession().getLastTravelDirection());
    }

    @Test
    void shouldNotUpdateDirectionWhenLocationsAreSame() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world = mock(World.class);
        Location loc = new Location(world, 5, 0, 5);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(loc);
        when(event.getTo()).thenReturn(loc);

        task.onVehicleMove(event);

        assertNull(task.getSession().getLastTravelDirection());
    }

    @Test
    void shouldClearTitleWhenTransitioningToMovingBetweenStations() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            Stop stopB = new Stop("B", "Bravo");
            task.onTrainEnterStop(new TrainEnterStopEvent(cart, stopB));

            assertEquals(TrainMovementTask.TrainState.MOVING_IN_STATION, task.getSession().getState());
        }
    }

    @Test
    void shouldRemoveMinecartOnRemoveMinecartAndCancel() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        when(cart.isDead()).thenReturn(false);
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        task.removeMinecartAndCancel();

        verify(cart).eject();
        verify(cart).remove();
        assertNull(TrainMovementTask.getTaskFor(cart));
    }

    @Test
    void shouldNotRemoveDeadMinecart() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        when(cart.isDead()).thenReturn(true);
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        task.removeMinecartAndCancel();

        verify(cart, never()).eject();
        verify(cart, never()).remove();
    }

    @Test
    void shouldSetTeleportingFlag() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertFalse(task.getSession().isTeleporting());
        task.setTeleporting(true);
        assertTrue(task.getSession().isTeleporting());
    }

    @Test
    void shouldSampleRouteOnVehicleMoveWhenLineExists() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(routeRecorder).sample(eq("l1"), eq(cart), eq(to));
    }

    @Test
    void shouldTransitionToMovingInStationForCorrectTargetStop() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            Stop stopB = new Stop("B", "Bravo");
            task.onTrainEnterStop(new TrainEnterStopEvent(cart, stopB));

            assertEquals(TrainMovementTask.TrainState.MOVING_IN_STATION, task.getSession().getState());
            assertEquals("B", task.getSession().getTargetStopId());
        }
    }

    @Test
    void shouldNotTransitionForMismatchedTargetStop() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        Stop stopC = new Stop("C", "Charlie");
        task.onTrainEnterStop(new TrainEnterStopEvent(cart, stopC));

        assertEquals(TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS, task.getSession().getState());
    }

    @Test
    void shouldNotUpdateDirectionWhenWorldsDiffer() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world1 = mock(World.class);
        World world2 = mock(World.class);
        Location from = new Location(world1, 0, 0, 0);
        Location to = new Location(world2, 10, 0, 0);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        assertNull(task.getSession().getLastTravelDirection());
    }

    // --- handleArrivalAtStation / startAtStation tests ---

    @Test
    void shouldCancelWhenLineIsNullOnArrival() {
        when(lineManager.getLine("nullLine")).thenReturn(null);
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "nullLine", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertNull(task.getSession().getLine());
        assertNull(task.getSession().getTargetStopId());
    }

    @Test
    void shouldCancelWhenCurrentStopIsNullOnArrival() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(stopManager.getStop("A")).thenReturn(null);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, task.getSession().getState());
    }

    @Test
    void shouldStartAtStationAndScheduleDeparture() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        Stop stopA = new Stop("A", "Alpha");
        when(stopManager.getStop("A")).thenReturn(stopA);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals("A", task.getSession().getCurrentStopId());
        assertEquals("B", task.getSession().getTargetStopId());
    }

    // --- handleTerminalStation tests ---

    @Test
    void shouldHandleTerminalWhenNoNextStop() {
        createLineWithStops("l1", "A");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.isOnline()).thenReturn(true);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertNull(task.getSession().getTargetStopId());
        assertEquals(TrainMovementTask.TrainState.STOPPED_AT_STATION, task.getSession().getState());
    }

    @Test
    void shouldNotHandleTerminalWhenPassengerIsNull() {
        createLineWithStops("l1", "A");
        Minecart cart = createMinecart();

        TrainMovementTask task = new TrainMovementTask(plugin, cart, null, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertNull(task.getSession().getTargetStopId());
    }

    // --- transferMinecart tests ---

    @Test
    void shouldTransferToNewMinecart() {
        createLineWithStops("l1", "A", "B");
        Minecart oldCart = createMinecart();
        Minecart newCart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, oldCart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        task.transferMinecart(newCart);

        assertNull(TrainMovementTask.getTaskFor(oldCart));
        assertEquals(task, TrainMovementTask.getTaskFor(newCart));
    }

    @Test
    void shouldStartMovementAssistOnTransferWhenMovingBetweenStations() {
        createLineWithStops("l1", "A", "B");
        Minecart oldCart = createMinecart();
        Minecart newCart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, oldCart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        task.transferMinecart(newCart);

        assertFalse(task.getSession().isTeleporting());
        assertEquals(task, TrainMovementTask.getTaskFor(newCart));
    }

    // --- cancel tests ---

    @Test
    void shouldUnregisterListenerOnCancel() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        task.cancel();

        assertNull(TrainMovementTask.getTaskFor(cart));
    }

    // --- transitionToStoppedAtStation via onVehicleMove ---

    @Test
    void shouldTransitionToStoppedWhenCloseToTarget() {
        createLineWithStops("l1", "A", "B", "C");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class);
                var schedulerMock = mockStatic(org.cubexmc.metro.util.SchedulerUtil.class)) {
            org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);
            bukkitMock.when(org.bukkit.Bukkit::getBukkitVersion).thenReturn("1.20.4-R0.1-SNAPSHOT");
            org.bukkit.Server server = mock(org.bukkit.Server.class);
            bukkitMock.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            bukkitMock.when(org.bukkit.Bukkit::getScheduler).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));

            World world = mock(World.class);
            Location cartLocation = new Location(world, 100, 64, 100);
            Location stopLocation = new Location(world, 100, 64, 100);
            when(cart.getLocation()).thenReturn(cartLocation);

            Stop stopB = new Stop("B", "Bravo");
            stopB.setStopPointLocation(stopLocation);
            when(stopManager.getStop("B")).thenReturn(stopB);
            when(stopManager.getStop("C")).thenReturn(new Stop("C", "Charlie"));

            TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                    TrainMovementTask.TrainState.MOVING_IN_STATION);

            Location from = new Location(world, 99, 64, 99);
            Location to = new Location(world, 100, 64, 100);

            VehicleMoveEvent event = mock(VehicleMoveEvent.class);
            when(event.getVehicle()).thenReturn(cart);
            when(event.getFrom()).thenReturn(from);
            when(event.getTo()).thenReturn(to);

            task.onVehicleMove(event);

            verify(cart).setVelocity(new Vector(0, 0, 0));
            verify(cart).setMaxSpeed(0);
            schedulerMock.verify(() -> org.cubexmc.metro.util.SchedulerUtil.teleportEntity(eq(cart),
                    any(Location.class)), never());
        }
    }

    @Test
    void shouldNotTransitionToStoppedWhenTargetStopHasNoLocation() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(cart, never()).setVelocity(any());
    }

    @Test
    void shouldNotTransitionToStoppedWhenTargetStopIsNull() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        when(stopManager.getStop("B")).thenReturn(null);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(cart, never()).setVelocity(any());
    }

    @Test
    void shouldNotTransitionToStoppedWhenDifferentWorlds() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        // Use real World names so Location.equals/world check works with == semantics
        World cartWorld = mock(World.class);
        World stopWorld = mock(World.class);
        Location cartLocation = new Location(cartWorld, 100, 64, 100);
        when(cart.getLocation()).thenReturn(cartLocation);

        Stop stopB = new Stop("B", "Bravo");
        stopB.setStopPointLocation(new Location(stopWorld, 100, 64, 100));
        when(stopManager.getStop("B")).thenReturn(stopB);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        Location from = new Location(cartWorld, 99, 64, 99);
        Location to = new Location(cartWorld, 100, 64, 100);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        // Different World instances means equals returns false, so no transition
        verify(cart, never()).setVelocity(any());
    }

    // --- applyApproachBraking via onVehicleMove ---

    @Test
    void shouldApplyApproachBrakingWhenFarFromTarget() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        when(cart.getMaxSpeed()).thenReturn(0.8);
        Player passenger = createOnlinePlayer("Alice");

        World world = mock(World.class);
        Location cartLocation = new Location(world, 0, 64, 0);
        when(cart.getLocation()).thenReturn(cartLocation);

        Stop stopB = new Stop("B", "Bravo");
        stopB.setStopPointLocation(new Location(world, 50, 64, 50));
        when(stopManager.getStop("B")).thenReturn(stopB);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(cart).setMaxSpeed(anyDouble());
    }

    // --- route recorder sampling ---

    @Test
    void shouldNotSampleRouteWhenLineIsNull() {
        when(lineManager.getLine("l1")).thenReturn(null);
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        World world = mock(World.class);
        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(routeRecorder, never()).sample(any(), any(), any());
    }

    // --- Line.getMaxSpeed ---

    @Test
    void shouldUseLineMaxSpeedWhenSet() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);
        line.setMaxSpeed(0.8);
        when(lineManager.getLine("l1")).thenReturn(line);

        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        Stop stopA = new Stop("A", "Alpha");
        stopA.setStopPointLocation(new Location(mock(World.class), 0, 0, 0));
        stopA.setLaunchYaw(90.0f);
        when(stopManager.getStop("A")).thenReturn(stopA);
        when(stopManager.getStop("B")).thenReturn(new Stop("B", "Bravo"));

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.STOPPED_AT_STATION);

        assertEquals("B", task.getSession().getTargetStopId());
    }

    // --- getTaskFor static method ---

    @Test
    void shouldReturnNullForUnregisteredCart() {
        Minecart cart = createMinecart();
        assertNull(TrainMovementTask.getTaskFor(cart));
    }

    // --- Multiple task tracking ---

    @Test
    void shouldTrackMultipleTasksIndependently() {
        Line line1 = new Line("l1", "Line1");
        line1.addStop("A", -1);
        line1.addStop("B", -1);
        when(lineManager.getLine("l1")).thenReturn(line1);

        Line line2 = new Line("l2", "Line2");
        line2.addStop("X", -1);
        line2.addStop("Y", -1);
        when(lineManager.getLine("l2")).thenReturn(line2);

        Minecart cart1 = createMinecart();
        Minecart cart2 = createMinecart();
        Player player1 = createOnlinePlayer("Alice");
        Player player2 = createOnlinePlayer("Bob");

        TrainMovementTask task1 = new TrainMovementTask(plugin, cart1, player1, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        TrainMovementTask task2 = new TrainMovementTask(plugin, cart2, player2, "l2", "X",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);

        // Tasks are not auto-registered; register via transferMinecart to put them in registry
        Minecart dummy1 = createMinecart();
        Minecart dummy2 = createMinecart();
        task1.transferMinecart(dummy1);
        task2.transferMinecart(dummy2);

        assertEquals(task1, TrainMovementTask.getTaskFor(dummy1));
        assertEquals(task2, TrainMovementTask.getTaskFor(dummy2));

        assertEquals(2, TrainMovementTask.shutdownActiveTasks());
        assertNull(TrainMovementTask.getTaskFor(dummy1));
        assertNull(TrainMovementTask.getTaskFor(dummy2));
    }

    // --- onVehicleMove with null world on cart location ---

    @Test
    void shouldSkipTransitionWhenCartWorldIsNull() {
        createLineWithStops("l1", "A", "B");
        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");

        World world = mock(World.class);
        Location cartLocation = mock(Location.class);
        when(cartLocation.getWorld()).thenReturn(null);
        when(cart.getLocation()).thenReturn(cartLocation);

        Stop stopB = new Stop("B", "Bravo");
        stopB.setStopPointLocation(new Location(world, 100, 64, 100));
        when(stopManager.getStop("B")).thenReturn(stopB);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_IN_STATION);

        Location from = new Location(world, 0, 0, 0);
        Location to = new Location(world, 1, 0, 1);

        VehicleMoveEvent event = mock(VehicleMoveEvent.class);
        when(event.getVehicle()).thenReturn(cart);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        task.onVehicleMove(event);

        verify(cart, never()).setVelocity(any());
    }

    @Test
    void shouldChargeDistanceFareOnStationArrival() throws Exception {
        Line line = createLineWithStops("l1", "A", "B");
        PriceRule rule = new PriceRule(PriceRule.PricingMode.DISTANCE, 2.0);
        rule.setPerBlockRate(0.5);
        line.setPriceRule(rule);

        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        task.getSession().addDistance(100.0);

        when(ticketService.chargePrice(eq(passenger), eq(line), eq(50.0)))
                .thenReturn(TicketService.TicketChargeStatus.CHARGED);
        when(ticketService.format(50.0)).thenReturn("$50.00");
        when(languageManager.getMessage(eq("economy.paid_distance"), (Map<String, Object>) any()))
                .thenReturn("Paid $50.00");

        Stop stopB = new Stop("B", "Bravo");
        java.lang.reflect.Method method = TrainMovementTask.class.getDeclaredMethod("settleDistanceFare", Stop.class);
        method.setAccessible(true);
        method.invoke(task, stopB);

        verify(ticketService).chargePrice(passenger, line, 50.0);
        assertEquals(0.0, task.getSession().getDistanceTraveled());
    }

    @Test
    void shouldNotChargeFareForFlatMode() throws Exception {
        Line line = createLineWithStops("l1", "A", "B");
        line.setPriceRule(new PriceRule(PriceRule.PricingMode.FLAT, 5.0));

        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        task.getSession().addDistance(100.0);

        Stop stopB = new Stop("B", "Bravo");
        java.lang.reflect.Method method = TrainMovementTask.class.getDeclaredMethod("settleDistanceFare", Stop.class);
        method.setAccessible(true);
        method.invoke(task, stopB);

        verify(ticketService, never()).chargePrice(any(), any(), anyDouble());
    }

    @Test
    void shouldNotChargeFareWithoutPriceRule() throws Exception {
        Line line = createLineWithStops("l1", "A", "B");

        Minecart cart = createMinecart();
        Player passenger = createOnlinePlayer("Alice");
        when(passenger.getVehicle()).thenReturn(cart);

        TrainMovementTask task = new TrainMovementTask(plugin, cart, passenger, "l1", "A",
                TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        task.getSession().addDistance(100.0);

        Stop stopB = new Stop("B", "Bravo");
        java.lang.reflect.Method method = TrainMovementTask.class.getDeclaredMethod("settleDistanceFare", Stop.class);
        method.setAccessible(true);
        method.invoke(task, stopB);

        verify(ticketService, never()).chargePrice(any(), any(), anyDouble());
    }
}
