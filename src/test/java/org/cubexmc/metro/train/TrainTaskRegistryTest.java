package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Minecart;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrainTaskRegistryTest {

    @AfterEach
    void clearRegistry() {
        TrainTaskRegistry.shutdownActiveTasks();
    }

    @Test
    void shouldRegisterGetAndUnregisterTaskByMinecartUuid() {
        Minecart minecart = mockMinecart(UUID.randomUUID());
        TrainMovementTask task = mock(TrainMovementTask.class);

        TrainTaskRegistry.register(minecart, task);

        assertEquals(task, TrainTaskRegistry.get(minecart));
        TrainTaskRegistry.unregister(minecart);
        assertNull(TrainTaskRegistry.get(minecart));
    }

    @Test
    void shouldTransferTaskToNewMinecart() {
        Minecart oldCart = mockMinecart(UUID.randomUUID());
        Minecart newCart = mockMinecart(UUID.randomUUID());
        TrainMovementTask task = mock(TrainMovementTask.class);

        TrainTaskRegistry.register(oldCart, task);
        TrainTaskRegistry.transfer(oldCart, newCart, task);

        assertNull(TrainTaskRegistry.get(oldCart));
        assertEquals(task, TrainTaskRegistry.get(newCart));
    }

    @Test
    void shouldDeduplicateTasksDuringShutdown() {
        Minecart firstCart = mockMinecart(UUID.randomUUID());
        Minecart secondCart = mockMinecart(UUID.randomUUID());
        TrainMovementTask task = mock(TrainMovementTask.class);

        TrainTaskRegistry.register(firstCart, task);
        TrainTaskRegistry.register(secondCart, task);

        assertEquals(1, TrainTaskRegistry.shutdownActiveTasks());
        verify(task).removeMinecartAndCancel();
        assertNull(TrainTaskRegistry.get(firstCart));
        assertNull(TrainTaskRegistry.get(secondCart));
    }

    @Test
    void shouldUseEntitySchedulerCleanupDuringFoliaShutdown() {
        Metro plugin = mock(Metro.class);
        Minecart firstCart = mockMinecart(UUID.randomUUID());
        Minecart secondCart = mockMinecart(UUID.randomUUID());
        TrainMovementTask task = mock(TrainMovementTask.class);

        TrainTaskRegistry.register(firstCart, task);
        TrainTaskRegistry.register(secondCart, task);

        assertEquals(1, TrainTaskRegistry.shutdownActiveTasks(plugin, true));

        verify(task).removeMinecartAndCancelOnEntityScheduler();
        verify(task, never()).removeMinecartAndCancel();
        assertNull(TrainTaskRegistry.get(firstCart));
        assertNull(TrainTaskRegistry.get(secondCart));
    }

    private Minecart mockMinecart(UUID uuid) {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getUniqueId()).thenReturn(uuid);
        return minecart;
    }
}
