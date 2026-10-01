package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Entity;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.scheduler.CubexScheduler;
import org.cubexmc.scheduler.CubexTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TrainSchedulerTest {
    @Test
    void oneShotUsesNativeEntitySchedulerAndCancelsItsHandle() {
        Metro plugin = mock(Metro.class);
        CubexScheduler nativeScheduler = mock(CubexScheduler.class);
        CubexTask handle = mock(CubexTask.class);
        Entity entity = mock(Entity.class);
        Runnable task = mock(Runnable.class);
        when(plugin.getTaskScheduler$Metro()).thenReturn(nativeScheduler);
        when(nativeScheduler.runAtEntityLater(entity, task, 0L)).thenReturn(handle);

        TrainScheduler scheduler = new TrainScheduler(plugin);
        assertSame(handle, scheduler.entityRun(entity, task, 0L, -1L));
        scheduler.cancelAll();

        verify(nativeScheduler).runAtEntityLater(entity, task, 0L);
        verify(handle).cancel();
    }

    @Test
    void repeatingTaskKeepsLegacySchedulingSemantics() {
        Metro plugin = mock(Metro.class);
        Entity entity = mock(Entity.class);
        Runnable task = mock(Runnable.class);
        Object handle = new Object();

        try (MockedStatic<SchedulerUtil> legacy = mockStatic(SchedulerUtil.class)) {
            legacy.when(() -> SchedulerUtil.entityRun(plugin, entity, task, 0L, 1L)).thenReturn(handle);

            TrainScheduler scheduler = new TrainScheduler(plugin);
            assertSame(handle, scheduler.entityRun(entity, task, 0L, 1L));
            scheduler.cancel(handle);

            legacy.verify(() -> SchedulerUtil.entityRun(plugin, entity, task, 0L, 1L));
            legacy.verify(() -> SchedulerUtil.cancelTask(handle));
            verifyNoInteractions(plugin);
        }
    }
}
