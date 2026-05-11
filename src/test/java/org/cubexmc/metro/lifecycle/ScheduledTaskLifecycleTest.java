package org.cubexmc.metro.lifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.junit.jupiter.api.Test;

class ScheduledTaskLifecycleTest {

    @Test
    void shouldSkipLegacyMinecartWorldScanOnFolia() {
        Fixtures fixtures = new Fixtures(true);

        fixtures.lifecycle.start();

        verify(fixtures.plugin).getLogger();
        assertScheduleCount(fixtures.scheduler, 1);
    }

    @Test
    void shouldScheduleLegacyMinecartMigrationOutsideFolia() {
        Fixtures fixtures = new Fixtures(false);

        fixtures.lifecycle.start();

        assertScheduleCount(fixtures.scheduler, 2);
    }

    @Test
    void autoSaveTaskProcessesLineStopAndPortalSaves() {
        Fixtures fixtures = new Fixtures(true);

        fixtures.lifecycle.start();
        fixtures.scheduler.runTask(0);

        verify(fixtures.lineManager).processAsyncSave();
        verify(fixtures.stopManager).processAsyncSave();
        verify(fixtures.portalManager).processAsyncSave();
    }

    @Test
    void shutdownCancelsScheduledTasks() {
        Fixtures fixtures = new Fixtures(false);

        fixtures.lifecycle.start();
        fixtures.lifecycle.shutdown();

        assertScheduleCount(fixtures.scheduler, 2);
        org.junit.jupiter.api.Assertions.assertEquals(2, fixtures.scheduler.cancelled.size());
    }

    private static void assertScheduleCount(FakeTaskScheduler scheduler, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, scheduler.tasks.size());
    }

    private static final class Fixtures {
        private final Metro plugin = mock(Metro.class);
        private final LineManager lineManager = mock(LineManager.class);
        private final StopManager stopManager = mock(StopManager.class);
        private final PortalManager portalManager = mock(PortalManager.class);
        private final FakeTaskScheduler scheduler = new FakeTaskScheduler();
        private final ScheduledTaskLifecycle lifecycle;

        private Fixtures(boolean folia) {
            when(plugin.getLogger()).thenReturn(Logger.getLogger("ScheduledTaskLifecycleTest"));
            this.lifecycle = new ScheduledTaskLifecycle(plugin, lineManager, stopManager, portalManager,
                    scheduler, folia);
        }
    }

    private static final class FakeTaskScheduler implements ScheduledTaskLifecycle.TaskScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<Object> cancelled = new ArrayList<>();

        @Override
        public Object schedule(Metro plugin, Runnable task, long delay, long period) {
            tasks.add(task);
            return task;
        }

        @Override
        public void cancel(Object taskId) {
            cancelled.add(taskId);
        }

        private void runTask(int index) {
            tasks.get(index).run();
        }
    }
}
