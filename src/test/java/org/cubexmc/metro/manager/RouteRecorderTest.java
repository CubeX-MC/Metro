package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        UUID recorderId = UUID.randomUUID();
        when(lineManager.setLineRoutePoints(eq("red"), argThat(points -> points.size() == 2),
                anyLong(), eq(recorderId), any())).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Minecart cart = mock(Minecart.class);
        UUID cartId = UUID.randomUUID();
        when(cart.getUniqueId()).thenReturn(cartId);
        Minecart otherCart = mock(Minecart.class);
        when(otherCart.getUniqueId()).thenReturn(UUID.randomUUID());

        RouteRecorder recorder = new RouteRecorder(plugin);
        assertTrue(recorder.start("red", recorderId));
        recorder.sample("red", cart, new Location(world, 0.0, 64.0, 0.0));
        recorder.sample("red", cart, new Location(world, 1.0, 64.0, 0.0));
        recorder.sample("red", otherCart, new Location(world, 10.0, 64.0, 0.0));
        recorder.sample("red", cart, new Location(world, 5.0, 64.0, 0.0));

        FinishResult result = recorder.stopAndSave("red");

        assertEquals(FinishResult.Status.SAVED, result.status());
        assertEquals("red", result.lineId());
        assertEquals(2, result.pointCount());
        assertEquals(recorderId, result.recorderId());
        assertEquals(cartId, result.cartId());
        verify(lineManager).setLineRoutePoints(eq("red"), argThat(points -> points.size() == 2),
                anyLong(), eq(recorderId), eq(cartId));
    }

    @Test
    void shouldReturnRecordingMetadataWhenTerminalAutoFinishHasTooFewPoints() {
        Metro plugin = mock(Metro.class);
        UUID recorderId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Minecart cart = mock(Minecart.class);
        UUID cartId = UUID.randomUUID();
        when(cart.getUniqueId()).thenReturn(cartId);

        RouteRecorder recorder = new RouteRecorder(plugin);
        assertTrue(recorder.start("green", recorderId));
        recorder.sample("green", cart, new Location(world, 0.0, 64.0, 0.0));

        FinishResult result = recorder.finishIfRecording("green", cart);

        assertEquals(FinishResult.Status.TOO_FEW_POINTS, result.status());
        assertEquals("green", result.lineId());
        assertEquals(1, result.pointCount());
        assertEquals(recorderId, result.recorderId());
        assertEquals(cartId, result.cartId());
    }

    @Test
    void shouldExposeActiveRecorderMetadataBeforeSaving() {
        RouteRecorder recorder = new RouteRecorder(mock(Metro.class));
        UUID recorderId = UUID.randomUUID();

        assertTrue(recorder.start("blue", recorderId));

        assertEquals(recorderId, recorder.getRecordingPlayerId("blue"));
        assertEquals(0, recorder.getActivePointCount("blue"));
    }
}
