package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.RouteRecorder.FinishResult;
import org.junit.jupiter.api.Test;

class RouteRecorderTest {

    @Test
    void shouldSampleOneCartAndSaveOnlyDistantPoints() {
        LineManager lineManager = mock(LineManager.class);
        Metro plugin = mock(Metro.class);
        when(plugin.getLineManager()).thenReturn(lineManager);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RouteRecorderTest"));
        when(lineManager.setLineRoutePoints(eq("red"), argThat(points -> points.size() == 2))).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Minecart cart = mock(Minecart.class);
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        Minecart otherCart = mock(Minecart.class);
        when(otherCart.getUniqueId()).thenReturn(UUID.randomUUID());

        RouteRecorder recorder = new RouteRecorder(plugin);
        assertTrue(recorder.start("red"));
        recorder.sample("red", cart, new Location(world, 0.0, 64.0, 0.0));
        recorder.sample("red", cart, new Location(world, 1.0, 64.0, 0.0));
        recorder.sample("red", otherCart, new Location(world, 10.0, 64.0, 0.0));
        recorder.sample("red", cart, new Location(world, 5.0, 64.0, 0.0));

        FinishResult result = recorder.stopAndSave("red");

        assertEquals(FinishResult.Status.SAVED, result.status());
        assertEquals(2, result.pointCount());
        verify(lineManager).setLineRoutePoints(eq("red"), argThat(points -> points.size() == 2));
    }
}
