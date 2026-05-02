package org.cubexmc.metro.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.integration.MapIntegration;
import org.junit.jupiter.api.Test;

class MapIntegrationLifecycleTest {

    @Test
    void autoProviderSelectsFirstAvailableIntegration() {
        Fixtures fixtures = new Fixtures("AUTO");
        fixtures.integration("BLUEMAP", false);
        FakeMapIntegration dynmap = fixtures.integration("DYNMAP", true);
        FakeMapIntegration squaremap = fixtures.integration("SQUAREMAP", true);

        fixtures.lifecycle.enable();

        assertEquals(1, dynmap.enableCount);
        assertEquals(0, squaremap.enableCount);
        assertTrue(dynmap.enabled);
    }

    @Test
    void configuredProviderDoesNotFallBackToAnotherProvider() {
        Fixtures fixtures = new Fixtures("DYNMAP");
        FakeMapIntegration dynmap = fixtures.integration("DYNMAP", false);
        FakeMapIntegration squaremap = fixtures.integration("SQUAREMAP", true);

        fixtures.lifecycle.enable();

        assertEquals(0, dynmap.enableCount);
        assertEquals(0, squaremap.enableCount);
        assertFalse(dynmap.enabled);
    }

    @Test
    void refreshUsesSelectedAutoProvider() {
        Fixtures fixtures = new Fixtures("AUTO");
        fixtures.integration("BLUEMAP", false);
        FakeMapIntegration dynmap = fixtures.integration("DYNMAP", true);

        fixtures.lifecycle.enable();
        fixtures.lifecycle.refresh();

        assertEquals(1, dynmap.enableCount);
        assertEquals(1, dynmap.refreshCount);
    }

    @Test
    void disablingClearsActiveIntegration() {
        Fixtures fixtures = new Fixtures("AUTO");
        FakeMapIntegration bluemap = fixtures.integration("BLUEMAP", true);

        fixtures.lifecycle.enable();
        fixtures.lifecycle.disable();

        assertFalse(bluemap.enabled);
        assertEquals(1, bluemap.disableCount);
    }

    @Test
    void requestRefreshCoalescesUntilScheduledTaskRuns() {
        Fixtures fixtures = new Fixtures("AUTO");
        FakeMapIntegration bluemap = fixtures.integration("BLUEMAP", true);

        fixtures.lifecycle.requestRefresh();
        fixtures.lifecycle.requestRefresh();

        assertEquals(1, fixtures.scheduler.scheduleCount);
        assertNotNull(fixtures.scheduler.pendingTask);

        fixtures.scheduler.runPendingTask();

        assertEquals(1, bluemap.enableCount);
        assertEquals(1, bluemap.refreshCount);
    }

    @Test
    void disableCancelsQueuedRefresh() {
        Fixtures fixtures = new Fixtures("AUTO");
        FakeMapIntegration bluemap = fixtures.integration("BLUEMAP", true);

        fixtures.lifecycle.requestRefresh();
        fixtures.lifecycle.disable();
        fixtures.scheduler.runPendingTask();

        assertEquals(1, fixtures.scheduler.cancelCount);
        assertEquals(0, bluemap.enableCount);
        assertEquals(0, bluemap.refreshCount);
    }

    private static final class Fixtures {
        private final Map<String, FakeMapIntegration> integrations = new HashMap<>();
        private final FakeRefreshScheduler scheduler = new FakeRefreshScheduler();
        private final MapIntegrationLifecycle lifecycle;

        private Fixtures(String provider) {
            Metro plugin = mock(Metro.class);
            ConfigFacade configFacade = mock(ConfigFacade.class);
            when(plugin.getConfigFacade()).thenReturn(configFacade);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("MapIntegrationLifecycleTest"));
            when(configFacade.isMapIntegrationEnabled()).thenReturn(true);
            when(configFacade.getMapProvider()).thenReturn(provider);
            when(configFacade.getMapRefreshDelayTicks()).thenReturn(20L);
            this.lifecycle = new MapIntegrationLifecycle(plugin, integrations::get, scheduler);
        }

        private FakeMapIntegration integration(String provider, boolean available) {
            FakeMapIntegration integration = new FakeMapIntegration(available);
            integrations.put(provider, integration);
            return integration;
        }
    }

    private static final class FakeMapIntegration implements MapIntegration {
        private final boolean available;
        private boolean enabled;
        private int enableCount;
        private int disableCount;
        private int refreshCount;

        private FakeMapIntegration(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void enable() {
            enableCount++;
            enabled = true;
        }

        @Override
        public void disable() {
            disableCount++;
            enabled = false;
        }

        @Override
        public void refresh() {
            refreshCount++;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    private static final class FakeRefreshScheduler implements MapIntegrationLifecycle.RefreshScheduler {
        private Object pendingTaskId;
        private Runnable pendingTask;
        private int scheduleCount;
        private int cancelCount;

        @Override
        public Object schedule(Metro plugin, Runnable task, long delay, long period) {
            scheduleCount++;
            pendingTaskId = new Object();
            pendingTask = task;
            return pendingTaskId;
        }

        @Override
        public void cancel(Object taskId) {
            if (taskId != null && taskId == pendingTaskId) {
                cancelCount++;
                pendingTask = null;
                pendingTaskId = null;
            }
        }

        private void runPendingTask() {
            Runnable task = pendingTask;
            pendingTask = null;
            pendingTaskId = null;
            if (task != null) {
                task.run();
            }
        }
    }
}
