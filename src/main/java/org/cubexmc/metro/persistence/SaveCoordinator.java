package org.cubexmc.metro.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates persistent YAML saves so newer snapshots cannot be overwritten by
 * older async writes.
 */
public class SaveCoordinator {

    private static final int MAX_WRITE_ATTEMPTS = 2;

    private final Logger logger;
    private final Executor executor;
    private final Map<Path, AtomicLong> latestVersions = new ConcurrentHashMap<>();
    private final Map<Path, CompletableFuture<Void>> writeChains = new ConcurrentHashMap<>();

    public SaveCoordinator(Logger logger, Executor executor) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public long submit(Path targetFile, Supplier<String> snapshotSupplier) {
        Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        String snapshot = snapshotSupplier.get();
        return submitSnapshot(targetFile, snapshot);
    }

    public long submitSnapshot(Path targetFile, String snapshot) {
        Path normalizedTarget = normalize(targetFile);
        long version = nextVersion(normalizedTarget);

        writeChains.compute(normalizedTarget, (path, previous) -> {
            CompletableFuture<Void> base = previous == null ? CompletableFuture.completedFuture(null) : previous;
            CompletableFuture<Void> next = base.handle((ignored, previousError) -> null)
                    .thenRunAsync(() -> writeIfCurrent(path, version, snapshot), executor);
            next.whenComplete((ignored, error) -> {
                if (error != null) {
                    logger.log(Level.SEVERE, "Failed to save " + path + " at version " + version, error);
                }
                writeChains.remove(path, next);
            });
            return next;
        });

        return version;
    }

    public void saveNow(Path targetFile, String snapshot) {
        Path normalizedTarget = normalize(targetFile);
        flush(normalizedTarget);
        long version = nextVersion(normalizedTarget);
        writeSnapshot(normalizedTarget, version, snapshot);
    }

    public void flush(Path targetFile) {
        Path normalizedTarget = normalize(targetFile);
        CompletableFuture<Void> pending = writeChains.get(normalizedTarget);
        if (pending == null) {
            return;
        }
        pending.join();
    }

    public void flushAll() {
        for (CompletableFuture<Void> pending : new ArrayList<>(writeChains.values())) {
            pending.join();
        }
    }

    long latestVersion(Path targetFile) {
        AtomicLong version = latestVersions.get(normalize(targetFile));
        return version == null ? 0L : version.get();
    }

    private long nextVersion(Path targetFile) {
        return latestVersions.computeIfAbsent(targetFile, path -> new AtomicLong()).incrementAndGet();
    }

    private void writeIfCurrent(Path targetFile, long version, String snapshot) {
        if (!isCurrent(targetFile, version)) {
            logger.fine("Skipping stale save for " + targetFile + " at version " + version);
            return;
        }
        writeSnapshot(targetFile, version, snapshot);
    }

    private void writeSnapshot(Path targetFile, long version, String snapshot) {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                writeAtomically(targetFile, version, snapshot);
                return;
            } catch (IOException ex) {
                lastError = ex;
                logger.log(Level.WARNING, "Failed to save " + targetFile + " at version " + version
                        + " (attempt " + attempt + "/" + MAX_WRITE_ATTEMPTS + ")", ex);
            }
        }

        if (lastError != null) {
            logger.log(Level.SEVERE, "Giving up saving " + targetFile + " at version " + version, lastError);
        }
    }

    private void writeAtomically(Path targetFile, long version, String snapshot) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp-" + version);
        try {
            Files.writeString(tempFile, snapshot);
            if (!isCurrent(targetFile, version)) {
                Files.deleteIfExists(tempFile);
                logger.fine("Discarded stale save for " + targetFile + " at version " + version);
                return;
            }

            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                logger.fine("Atomic move is not supported for " + targetFile + "; falling back to replace existing.");
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private boolean isCurrent(Path targetFile, long version) {
        return latestVersion(targetFile) == version;
    }

    private Path normalize(Path targetFile) {
        return Objects.requireNonNull(targetFile, "targetFile").toAbsolutePath().normalize();
    }
}
