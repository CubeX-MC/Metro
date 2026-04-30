package org.cubexmc.metro.train;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.junit.jupiter.api.Test;

class TrainTaskStarterTest {

    @Test
    void shouldReturnWhenLineDoesNotExist() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        Minecart minecart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(lineManager.getLine("missing")).thenReturn(null);

        TrainTaskStarter.start(plugin, minecart, passenger, "missing", "A");

        verify(minecart, never()).remove();
    }

    @Test
    void shouldRemoveMinecartWhenPassengerIsNullAndCartIsValid() {
        Metro plugin = pluginWithLine();
        Minecart minecart = mock(Minecart.class);
        when(minecart.isValid()).thenReturn(true);

        TrainTaskStarter.start(plugin, minecart, null, "red", "A");

        verify(minecart).remove();
    }

    @Test
    void shouldRemoveMinecartWhenPassengerIsOffline() {
        Metro plugin = pluginWithLine();
        Minecart minecart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(minecart.isValid()).thenReturn(true);
        when(passenger.isOnline()).thenReturn(false);

        TrainTaskStarter.start(plugin, minecart, passenger, "red", "A");

        verify(minecart).remove();
    }

    @Test
    void shouldRemoveMinecartWhenPassengerIsNotInThatCart() {
        Metro plugin = pluginWithLine();
        Minecart minecart = mock(Minecart.class);
        Minecart otherCart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(minecart.isValid()).thenReturn(true);
        when(passenger.isOnline()).thenReturn(true);
        when(passenger.getVehicle()).thenReturn(otherCart);

        TrainTaskStarter.start(plugin, minecart, passenger, "red", "A");

        verify(minecart).remove();
    }

    @Test
    void shouldNotRemoveInvalidMinecartWhenPassengerIsInvalid() {
        Metro plugin = pluginWithLine();
        Minecart minecart = mock(Minecart.class);
        Player passenger = mock(Player.class);
        when(minecart.isValid()).thenReturn(false);
        when(passenger.isOnline()).thenReturn(false);

        TrainTaskStarter.start(plugin, minecart, passenger, "red", "A");

        verify(minecart, never()).remove();
    }

    private Metro pluginWithLine() {
        Metro plugin = mock(Metro.class);
        LineManager lineManager = mock(LineManager.class);
        Line line = new Line("red", "Red");
        line.addStop("A", -1);
        line.addStop("B", -1);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(lineManager.getLine("red")).thenReturn(line);
        return plugin;
    }
}
