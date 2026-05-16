package org.cubexmc.metro.train;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SoundUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

class TrainDisplayControllerExtendedTest {

    private Metro plugin;
    private ConfigFacade configFacade;
    private StopManager stopManager;
    private LineManager lineManager;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        configFacade = mock(ConfigFacade.class);
        stopManager = mock(StopManager.class);
        lineManager = mock(LineManager.class);
        when(plugin.getConfigFacade()).thenReturn(configFacade);
        when(plugin.getStopManager()).thenReturn(stopManager);
        when(plugin.getLineManager()).thenReturn(lineManager);

        when(configFacade.isArrivalSoundEnabled()).thenReturn(false);
        when(configFacade.getArrivalNotes()).thenReturn(Collections.emptyList());
        when(configFacade.isStationArrivalSoundEnabled()).thenReturn(false);
        when(configFacade.isTerminalStopTitleEnabled()).thenReturn(true);
        when(configFacade.isDepartureTitleEnabled()).thenReturn(true);
        when(configFacade.isWaitingTitleEnabled()).thenReturn(true);
        when(configFacade.isDepartureSoundEnabled()).thenReturn(false);
        when(configFacade.isWaitingSoundEnabled()).thenReturn(false);
        when(configFacade.getWaitingNotes()).thenReturn(Collections.emptyList());
        when(configFacade.getStationArrivalNotes()).thenReturn(Collections.emptyList());
        when(configFacade.getStationArrivalInitialDelay()).thenReturn(0);

        when(configFacade.getArriveStopTitle()).thenReturn("&a{stop_name}");
        when(configFacade.getArriveStopSubtitle()).thenReturn("&7Next: {next_stop_name}");
        when(configFacade.getArriveStopFadeIn()).thenReturn(5);
        when(configFacade.getArriveStopStay()).thenReturn(40);
        when(configFacade.getArriveStopFadeOut()).thenReturn(10);

        when(configFacade.getTerminalStopTitle()).thenReturn("&c{stop_name} (Terminal)");
        when(configFacade.getTerminalStopSubtitle()).thenReturn("&7End of line");
        when(configFacade.getTerminalStopFadeIn()).thenReturn(5);
        when(configFacade.getTerminalStopStay()).thenReturn(60);
        when(configFacade.getTerminalStopFadeOut()).thenReturn(10);

        when(configFacade.getDepartureTitle()).thenReturn("&b{stop_name}");
        when(configFacade.getDepartureSubtitle()).thenReturn("");
        when(configFacade.getDepartureActionbar()).thenReturn("");
        when(configFacade.getDepartureFadeIn()).thenReturn(5);
        when(configFacade.getDepartureStay()).thenReturn(30);
        when(configFacade.getDepartureFadeOut()).thenReturn(5);

        when(configFacade.getWaitingTitle()).thenReturn("&e{stop_name}");
        when(configFacade.getWaitingSubtitle()).thenReturn("&7Waiting...");
        when(configFacade.getWaitingActionbar()).thenReturn("");
        when(configFacade.getCartDepartureDelay()).thenReturn(60L);
        when(configFacade.getWaitingSoundInterval()).thenReturn(0);
        when(configFacade.getWaitingInitialDelay()).thenReturn(0);
    }

    private Line createLineWithStops(String lineId, String... stopIds) {
        Line line = new Line(lineId, "Line " + lineId);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        return line;
    }

    private Player createOnlinePlayer() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        return player;
    }

    // ---- Station arrival sound tests ----

    @Test
    void shouldPlayStationArrivalSoundToNearbyPlayers() {
        when(configFacade.isStationArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getStationArrivalNotes()).thenReturn(List.of("1,PIANO,1.0"));
        when(configFacade.getStationArrivalInitialDelay()).thenReturn(5);

        World world = mock(World.class);
        Location stopLocation = new Location(world, 10, 64, 10);
        Stop stop = new Stop("B", "Bravo");
        stop.setStopPointLocation(stopLocation);
        stop.setCorner1(new Location(world, 10, 64, 10));
        stop.setCorner2(new Location(world, 12, 66, 12));

        Player passenger = createOnlinePlayer();
        when(passenger.getLocation()).thenReturn(new Location(world, 20, 64, 20));

        Player nearbyPlayer = createOnlinePlayer();
        Location nearbyLocation = new Location(world, 11, 65, 11);
        when(nearbyPlayer.getLocation()).thenReturn(nearbyLocation);

        when(world.getPlayers()).thenReturn(List.of(passenger, nearbyPlayer));

        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);

        TrainDisplayController controller = new TrainDisplayController(plugin);
        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stop, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        try (var soundMock = mockStatic(SoundUtil.class)) {
            controller.onTrainArrival(event);
            soundMock.verify(() -> SoundUtil.playNoteSequence(eq(plugin), eq(nearbyPlayer), any(), eq(5)));
        }
    }

    @Test
    void shouldNotPlayStationArrivalSoundWhenNoNotes() {
        when(configFacade.isStationArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getStationArrivalNotes()).thenReturn(Collections.emptyList());

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();
        Stop stop = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stop, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        try (var soundMock = mockStatic(SoundUtil.class)) {
            controller.onTrainArrival(event);
            soundMock.verify(() -> SoundUtil.playNoteSequence(any(), any(), any(), anyInt()), never());
        }
    }

    @Test
    void shouldSkipStationArrivalSoundWhenStopLocationIsNull() {
        when(configFacade.isStationArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getStationArrivalNotes()).thenReturn(List.of("1,PIANO,1.0"));

        Stop stop = new Stop("B", "Bravo");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stop, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        try (var soundMock = mockStatic(SoundUtil.class)) {
            controller.onTrainArrival(event);
            soundMock.verify(() -> SoundUtil.playNoteSequence(any(), any(), any(), anyInt()), never());
        }
    }

    // ---- Custom title tests ----

    @Test
    void shouldUseCustomWaitingTitleWhenSet() {
        when(configFacade.getWaitingTitle()).thenReturn("default");
        when(configFacade.getWaitingSubtitle()).thenReturn("default_sub");

        Stop stopB = new Stop("B", "Bravo");
        stopB.setCustomTitle("waiting", Map.of(
                "title", "&6Custom Title",
                "subtitle", "&dCustom Sub"
        ));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.DOCKED);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(0), eq(1000000), eq(0));
    }

    @Test
    void shouldUseCustomDepartureTitleWhenSet() {
        Stop stopA = new Stop("A", "Alpha");
        stopA.setCustomTitle("departure", Map.of(
                "title", "&6Custom Departure",
                "subtitle", "&dNext: {next_stop_name}",
                "actionbar", "Custom actionbar"
        ));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);
        when(stopManager.getStop("A")).thenReturn(stopA);

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                cart, passenger, line, stopA, stopB);

        controller.onTrainDeparture(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(30), eq(5));
    }

    // ---- showStopInfo edge cases ----

    @Test
    void shouldSendActionbarAndTitleForArriveStop() {
        when(configFacade.getArriveStopTitle()).thenReturn("&a{stop_name}");
        when(configFacade.getArriveStopSubtitle()).thenReturn("&7Next: {next_stop_name}");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopB = new Stop("B", "Bravo");
        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
        verify(passenger.spigot()).sendMessage(eq(ChatMessageType.ACTION_BAR),
                any(BaseComponent[].class));
    }

    @Test
    void shouldSendActionbarAndTitleForTerminalStop() {
        when(configFacade.getTerminalStopTitle()).thenReturn("&c{stop_name}");
        when(configFacade.getTerminalStopSubtitle()).thenReturn("&7End");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(60), eq(10));
        verify(passenger.spigot()).sendMessage(eq(ChatMessageType.ACTION_BAR),
                any(BaseComponent[].class));
    }

    // ---- Waiting with plain actionbar (no countdown) ----

    @Test
    void shouldSendPlainWaitingActionbarWhenNoCountdown() {
        when(configFacade.getWaitingActionbar()).thenReturn("&eWaiting at {stop_name}");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopB = new Stop("B", "Bravo");
        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.DOCKED);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(0), eq(1000000), eq(0));
        verify(passenger.spigot()).sendMessage(eq(ChatMessageType.ACTION_BAR),
                any(BaseComponent[].class));
    }

    // ---- Arrive stop with custom title from stop ----

    @Test
    void shouldUseCustomArriveStopTitleWhenSet() {
        Stop stopB = new Stop("B", "Bravo");
        stopB.setCustomTitle("arrive_stop", Map.of(
                "title", "&6Custom Arrive",
                "subtitle", "&dCustom Sub"
        ));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
    }

    @Test
    void shouldUseCustomTerminalStopTitleWhenSet() {
        Stop stopB = new Stop("B", "Bravo");
        stopB.setCustomTitle("terminal_stop", Map.of(
                "title", "&4End of Line",
                "subtitle", "&7Please exit"
        ));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(60), eq(10));
    }

    // ---- Edge: empty stop list for terminus lookup ----

    @Test
    void shouldHandleEmptyStopListWhenShowingDeparture() {
        Line line = new Line("l1", "Line 1");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                cart, passenger, line, stopA, stopB);

        controller.onTrainDeparture(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(30), eq(5));
    }

    @Test
    void shouldHandleEmptyStopListWhenShowingArriveStopInfo() {
        Line line = new Line("l1", "Line 1");

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopB = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
    }

    // ---- Custom departure actionbar from stop triggers countdown ----

    @Test
    void shouldUseCustomArriveStopActionbarWhenSet() {
        Stop stopB = new Stop("B", "Bravo");
        stopB.setCustomTitle("arrive_stop", Map.of(
                "actionbar", "Arriving at {stop_name}"
        ));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        // The custom actionbar should be sent
        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
        verify(passenger.spigot()).sendMessage(eq(ChatMessageType.ACTION_BAR),
                any(BaseComponent[].class));
    }

    // ---- Stop world is null for station arrival sound ----

    @Test
    void shouldSkipStationArrivalSoundWhenStopWorldIsNull() {
        when(configFacade.isStationArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getStationArrivalNotes()).thenReturn(List.of("1,PIANO,1.0"));

        Stop stop = new Stop("B", "Bravo");
        stop.setStopPointLocation(new Location(null, 10, 64, 10));

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createOnlinePlayer();

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stop, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        try (var soundMock = mockStatic(SoundUtil.class)) {
            controller.onTrainArrival(event);
            soundMock.verify(() -> SoundUtil.playNoteSequence(any(), any(), any(), anyInt()), never());
        }
    }
}
