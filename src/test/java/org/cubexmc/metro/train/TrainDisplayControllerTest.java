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

class TrainDisplayControllerTest {

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
        when(configFacade.getDepartureFadeIn()).thenReturn(5);
        when(configFacade.getDepartureStay()).thenReturn(30);
        when(configFacade.getDepartureFadeOut()).thenReturn(5);
        when(configFacade.getDepartureActionbar()).thenReturn("");

        when(configFacade.getWaitingTitle()).thenReturn("&e{stop_name}");
        when(configFacade.getWaitingSubtitle()).thenReturn("&7Waiting...");
        when(configFacade.getWaitingActionbar()).thenReturn("");
        when(configFacade.getCartDepartureDelay()).thenReturn(60L);
    }

    private Line createLineWithStops(String lineId, String... stopIds) {
        Line line = new Line(lineId, "Line " + lineId);
        for (String stopId : stopIds) {
            line.addStop(stopId, -1);
        }
        return line;
    }

    private Player createPlayerWithSpigot() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        return player;
    }

    @Test
    void shouldSkipArrivalWhenPassengerIsNull() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Stop stop = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, null, line, stop, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(configFacade, never()).getArriveStopTitle();
    }

    @Test
    void shouldSkipArrivalWhenPassengerOffline() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(false);
        Stop stop = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stop, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldShowArriveStopInfoWhenEnteringNonTerminal() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");
        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
    }

    @Test
    void shouldShowTerminalStopInfoWhenEnteringTerminal() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(60), eq(10));
    }

    @Test
    void shouldShowWaitingInfoWhenDockedAtNonTerminal() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();
        when(passenger.getVehicle()).thenReturn(cart);

        Stop stopB = new Stop("B", "Bravo");
        Stop stopC = new Stop("C", "Charlie");
        when(stopManager.getStop("C")).thenReturn(stopC);
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.DOCKED);

        controller.onTrainArrival(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(0), eq(1000000), eq(0));
    }

    @Test
    void shouldNotShowWaitingInfoWhenDockedAtTerminal() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.DOCKED);

        controller.onTrainArrival(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), eq(0), eq(1000000), eq(0));
    }

    @Test
    void shouldSkipDepartureWhenPassengerOffline() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(passenger.isOnline()).thenReturn(false);

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                cart, passenger, line, stopA, stopB);

        controller.onTrainDeparture(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldShowDepartureInfoWhenEnabled() {
        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");
        when(stopManager.getStop("B")).thenReturn(stopB);
        when(stopManager.getStop("A")).thenReturn(stopA);

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                cart, passenger, line, stopA, stopB);

        controller.onTrainDeparture(event);

        verify(passenger).sendTitle(anyString(), anyString(), eq(5), eq(30), eq(5));
    }

    @Test
    void shouldNotShowDepartureTitleWhenDisabled() {
        when(configFacade.isDepartureTitleEnabled()).thenReturn(false);
        TrainDisplayController controller = new TrainDisplayController(plugin);

        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");

        MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                cart, passenger, line, stopA, stopB);

        controller.onTrainDeparture(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldNotShowTerminalTitleWhenDisabled() {
        when(configFacade.isTerminalStopTitleEnabled()).thenReturn(false);
        TrainDisplayController controller = new TrainDisplayController(plugin);

        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);

        controller.onTrainArrival(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldNotShowWaitingTitleWhenDisabled() {
        when(configFacade.isWaitingTitleEnabled()).thenReturn(false);
        TrainDisplayController controller = new TrainDisplayController(plugin);

        Line line = createLineWithStops("l1", "A", "B", "C");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");

        MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                cart, passenger, line, stopB, false, MetroTrainArrivalEvent.ArrivalType.DOCKED);

        controller.onTrainArrival(event);

        verify(passenger, never()).sendTitle(anyString(), anyString(), eq(0), eq(1000000), eq(0));
    }

    @Test
    void shouldPlayArrivalSoundWhenEnabled() {
        when(configFacade.isArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getArrivalNotes()).thenReturn(List.of("1,PIANO,1.0"));
        when(configFacade.getArrivalInitialDelay()).thenReturn(0);

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");

        try (var soundMock = mockStatic(SoundUtil.class)) {
            MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                    cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);
            controller.onTrainArrival(event);

            soundMock.verify(() -> SoundUtil.playNoteSequence(eq(plugin), eq(passenger), any(), eq(0)));
        }
    }

    @Test
    void shouldPlayDepartureSoundWhenEnabled() {
        when(configFacade.isDepartureSoundEnabled()).thenReturn(true);
        when(configFacade.getDepartureNotes()).thenReturn(List.of("1,PIANO,1.0"));
        when(configFacade.getDepartureInitialDelay()).thenReturn(0);

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopA = new Stop("A", "Alpha");
        Stop stopB = new Stop("B", "Bravo");

        try (var soundMock = mockStatic(SoundUtil.class)) {
            MetroTrainDepartureEvent event = new MetroTrainDepartureEvent(
                    cart, passenger, line, stopA, stopB);
            controller.onTrainDeparture(event);

            soundMock.verify(() -> SoundUtil.playNoteSequence(eq(plugin), eq(passenger), any(), eq(0)));
        }
    }

    @Test
    void shouldSkipArrivalSoundWhenNoNotesConfigured() {
        when(configFacade.isArrivalSoundEnabled()).thenReturn(true);
        when(configFacade.getArrivalNotes()).thenReturn(Collections.emptyList());

        TrainDisplayController controller = new TrainDisplayController(plugin);
        Line line = createLineWithStops("l1", "A", "B");
        Minecart cart = mock(Minecart.class);
        Player passenger = createPlayerWithSpigot();

        Stop stopB = new Stop("B", "Bravo");

        try (var soundMock = mockStatic(SoundUtil.class)) {
            MetroTrainArrivalEvent event = new MetroTrainArrivalEvent(
                    cart, passenger, line, stopB, true, MetroTrainArrivalEvent.ArrivalType.ENTERING);
            controller.onTrainArrival(event);

            soundMock.verify(() -> SoundUtil.playNoteSequence(any(), any(), any(), anyInt()),
                    never());
        }
    }
}
