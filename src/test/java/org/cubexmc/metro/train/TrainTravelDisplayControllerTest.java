package org.cubexmc.metro.train;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.junit.jupiter.api.Test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

class TrainTravelDisplayControllerTest {

    @Test
    void shouldShowRemainingDistanceToNextStopWhileTravelling() {
        Fixture fixture = new Fixture(20.0);

        new TrainTravelDisplayController().onTrainMoved(fixture.session);

        verify(fixture.spigot).sendMessage(eq(ChatMessageType.ACTION_BAR), fixture.captor.capture());
        String rendered = plainText(fixture.captor.getAllValues());
        org.junit.jupiter.api.Assertions.assertTrue(rendered.contains("Harbor"), rendered);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.contains("20"), rendered);
    }

    @Test
    void shouldThrottleUpdatesToConfiguredInterval() {
        Fixture fixture = new Fixture(20.0);
        TrainTravelDisplayController controller = new TrainTravelDisplayController();

        for (int move = 0; move < 12; move++) {
            controller.onTrainMoved(fixture.session);
        }

        // interval = 10: one immediate update, then one more after 10 moves
        verify(fixture.spigot, times(2)).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
    }

    @Test
    void shouldNotShowAnythingWhileDocked() {
        Fixture fixture = new Fixture(20.0);
        fixture.session.setState(TrainMovementTask.TrainState.STOPPED_AT_STATION);

        new TrainTravelDisplayController().onTrainMoved(fixture.session);

        verify(fixture.spigot, never()).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
    }

    @Test
    void shouldNotShowAnythingWhenDisabled() {
        Fixture fixture = new Fixture(20.0);
        when(fixture.configFacade.isTravelingActionbarEnabled()).thenReturn(false);

        new TrainTravelDisplayController().onTrainMoved(fixture.session);

        verify(fixture.spigot, never()).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
    }

    private static String plainText(java.util.List<BaseComponent[]> captured) {
        StringBuilder builder = new StringBuilder();
        for (BaseComponent[] components : captured) {
            for (BaseComponent component : components) {
                builder.append(component.toPlainText());
            }
        }
        return builder.toString();
    }

    private static final class Fixture {
        private final ConfigFacade configFacade = mock(ConfigFacade.class);
        private final Player.Spigot spigot = mock(Player.Spigot.class);
        private final org.mockito.ArgumentCaptor<BaseComponent[]> captor =
                org.mockito.ArgumentCaptor.forClass(BaseComponent[].class);
        private final TrainSession session;

        private Fixture(double distanceBlocks) {
            Metro plugin = mock(Metro.class);
            StopManager stopManager = mock(StopManager.class);
            LineManager lineManager = mock(LineManager.class);
            World world = mock(World.class);

            when(plugin.getConfigFacade()).thenReturn(configFacade);
            when(plugin.getStopManager()).thenReturn(stopManager);
            when(plugin.getLineManager()).thenReturn(lineManager);
            when(configFacade.isTravelingActionbarEnabled()).thenReturn(true);
            when(configFacade.getTravelingActionbar()).thenReturn("Next: {next_stop_name} {next_stop_distance}");
            when(configFacade.getTravelingActionbarInterval()).thenReturn(10);

            Stop nextStop = new Stop("B", "Harbor");
            nextStop.setStopPointLocation(new Location(world, distanceBlocks, 64, 0));
            when(stopManager.getStop("B")).thenReturn(nextStop);

            Line line = new Line("l1", "Line 1");
            line.addStop("A", -1);
            line.addStop("B", -1);

            Player passenger = mock(Player.class);
            when(passenger.isOnline()).thenReturn(true);
            when(passenger.spigot()).thenReturn(spigot);

            Minecart minecart = mock(Minecart.class);
            when(minecart.getLocation()).thenReturn(new Location(world, 0, 64, 0));

            session = new TrainSession(plugin, minecart, passenger, line, "A",
                    TrainMovementTask.TrainState.MOVING_BETWEEN_STATIONS);
        }
    }
}
