package dev.mintychochip.provenance;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Server;

/**
 * Installs the provenance admin command and the durable writer. Safe to call
 * repeatedly; the writer is installed once per server process.
 */
public final class ProvenanceBootstrap {

    private static final ExecutorService BOOTSTRAP_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "mintychochip-provenance-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean installed;

    private ProvenanceBootstrap() {
    }

    public static synchronized void ensureInstalled(final Server server) {
        if (installed) {
            return;
        }
        final AtomicReference<ProvenanceWriter> writerReference = new AtomicReference<>();
        try {
            Bukkit.getCommandMap().register("mintychochip", new ProvenanceBukkitCommand());
            final java.io.File worldContainer = server.getWorldContainer();
            if (worldContainer != null) {
                final java.nio.file.Path worldContainerPath = worldContainer.toPath();
                java.nio.file.Path primaryWorld = null;
                if (!server.getWorlds().isEmpty()) {
                    final org.bukkit.World primary = server.getWorlds().getFirst();
                    if (primary != null && primary.getWorldFolder() != null) {
                        primaryWorld = primary.getWorldFolder().toPath();
                    }
                }
                final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainerPath, primaryWorld);
                final CountDownLatch bootstrapFinished = new CountDownLatch(1);
                final CompletableFuture<Void> readiness = CompletableFuture.runAsync(
                    () -> {
                        try {
                            writerReference.set(initialize(layout));
                        } finally {
                            bootstrapFinished.countDown();
                        }
                    },
                    BOOTSTRAP_EXECUTOR
                );
                awaitReadiness(readiness, bootstrapFinished);
            }
            Bukkit.getLogger().info("[mintychochip] item provenance tracking enabled (/provenance)");
            Runtime.getRuntime().addShutdownHook(new Thread(ProvenanceWriter::flushAndClose, "mintychochip-provenance-flush"));
            installed = true;
        } catch (final Exception ex) {
            final ProvenanceWriter writer = writerReference.get();
            if (writer != null) {
                ProvenanceWriter.clearInstall(writer);
            }
            Bukkit.getLogger().warning("[mintychochip] failed to register /provenance: " + ex.getMessage());
        }
    }

    private static ProvenanceWriter initialize(final ProvenanceStorageLayout layout) {
        ProvenanceWriter writer = null;
        try {
            final java.nio.file.Path storageRoot = layout.migrate();
            final AutoCloseable writerLock = layout.retainMigrationLock();
            writer = ProvenanceWriter.installLocked(storageRoot, message -> Bukkit.getLogger().info(message), writerLock);
            layout.markMigrationCompleteForWriter();
            return writer;
        } catch (final IOException | RuntimeException | Error ex) {
            if (writer != null) {
                ProvenanceWriter.clearInstall(writer);
            }
            try {
                layout.abortMigration();
            } catch (final IOException abortFailure) {
                ex.addSuppressed(abortFailure);
            }
            throw new IllegalStateException("provenance bootstrap failed", ex);
        }
    }

    private static void awaitReadiness(
        final CompletableFuture<Void> readiness,
        final CountDownLatch bootstrapFinished
    ) {
        try {
            readiness.get();
        } catch (final InterruptedException interruptedWait) {
            boolean interrupted = true;
            while (interrupted) {
                try {
                    bootstrapFinished.await();
                    interrupted = false;
                } catch (final InterruptedException ignored) {
                    // Wait for initialize() to finish before allowing a retry.
                }
            }
            try {
                readiness.join();
            } catch (final CompletionException completion) {
                Thread.currentThread().interrupt();
                final Throwable cause = completion.getCause();
                throw new IllegalStateException(
                    "provenance bootstrap failed",
                    cause == null ? completion : cause
                );
            }
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            throw new IllegalStateException("provenance bootstrap failed", cause == null ? ex : cause);
        }
    }
}
