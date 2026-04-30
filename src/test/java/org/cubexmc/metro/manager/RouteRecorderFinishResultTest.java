package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cubexmc.metro.manager.RouteRecorder.FinishResult.Status;
import org.junit.jupiter.api.Test;

class RouteRecorderFinishResultTest {

    @Test
    void shouldVerifyAllStatusValues() {
        assertEquals(4, Status.values().length);
        assertEquals("NOT_RECORDING", Status.NOT_RECORDING.name());
        assertEquals("TOO_FEW_POINTS", Status.TOO_FEW_POINTS.name());
        assertEquals("SAVED", Status.SAVED.name());
        assertEquals("FAILED", Status.FAILED.name());
    }
}
