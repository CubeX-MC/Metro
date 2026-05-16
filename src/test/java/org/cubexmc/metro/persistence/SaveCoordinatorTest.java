package org.cubexmc.metro.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDiscardQueuedOlderSnapshotWhenNewerVersionExists() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        SaveCoordinator coordinator = new SaveCoordinator(Logger.getLogger("SaveCoordinatorTest"), executor);
        Path target = tempDir.resolve("lines.yml");

        coordinator.submitSnapshot(target, "old: true\n");
        coordinator.submitSnapshot(target, "new: true\n");
        executor.runAll();

        assertEquals("new: true\n", Files.readString(target));
        assertEquals(2L, coordinator.latestVersion(target));
        assertFalse(Files.exists(tempDir.resolve("lines.yml.tmp-1")));
        assertFalse(Files.exists(tempDir.resolve("lines.yml.tmp-2")));
    }

    @Test
    void shouldFlushPendingAsyncSave() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        SaveCoordinator coordinator = new SaveCoordinator(Logger.getLogger("SaveCoordinatorTest"), executor);
        Path target = tempDir.resolve("stops.yml");

        coordinator.submitSnapshot(target, "station: Central\n");
        executor.runAll();
        coordinator.flush(target);

        assertEquals("station: Central\n", Files.readString(target));
    }

    @Test
    void shouldWriteSynchronousSnapshot() throws IOException {
        SaveCoordinator coordinator = new SaveCoordinator(Logger.getLogger("SaveCoordinatorTest"), Runnable::run);
        Path target = tempDir.resolve("sync.yml");

        coordinator.saveNow(target, "value: 1\n");

        assertEquals("value: 1\n", Files.readString(target));
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }
}
