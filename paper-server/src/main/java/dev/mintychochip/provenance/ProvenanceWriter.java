package dev.mintychochip.provenance;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single-writer durable sink for provenance: SQLite repository writes plus a
 * rotating JSONL audit export, drained off the server thread.
 *
 * <p>Critical writes (lineage, live, collision) never drop: when the memory
 * queue is full they append to {@link ProvenanceSpillJournal}. Audit may drop
 * only if both the queue and spill fail. Storage failures are surfaced through
 * {@link #status()} and rate-limited logs.
 */
public final class ProvenanceWriter {

    private static final int QUEUE_CAPACITY = 8_192;
    private static final int BATCH_MAX = 64;
    private static final long MAX_AUDIT_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ROTATIONS = 3;
    private static final long ERROR_LOG_INTERVAL_MS = 30_000L;
    private static final long SHUTDOWN_JOIN_MS = 10_000L;

    private static volatile @Nullable ProvenanceWriter instance;

    private final @NotNull Path auditPath;
    private final @NotNull ProvenanceSpillJournal spillJournal;
    private final @Nullable ProvenanceRepository repository;
    private final @Nullable AutoCloseable storageLock;
    private final @NotNull BlockingQueue<WriteItem> queue;
    private final @NotNull AtomicBoolean running = new AtomicBoolean(true);
    private final @NotNull AtomicBoolean storageLockReleased = new AtomicBoolean();
    private final @NotNull Thread thread;
    private final @NotNull AtomicLong auditDropped = new AtomicLong();
    private final @NotNull AtomicLong written = new AtomicLong();
    private long writeSequence;
    private volatile @Nullable String lastError;
    private volatile long lastErrorLogMs;
    private @Nullable BufferedWriter auditWriter;
    private long auditBytes;
    private int itemsSinceFlush;
    private final @NotNull Consumer<String> logger;

    private ProvenanceWriter(final @NotNull Path storageRoot, final @NotNull Consumer<String> logger) {
        this(storageRoot, logger, QUEUE_CAPACITY, null);
    }

    private ProvenanceWriter(
        final @NotNull Path storageRoot,
        final @NotNull Consumer<String> logger,
        final int queueCapacity
    ) {
        this(storageRoot, logger, queueCapacity, null);
    }

    private ProvenanceWriter(
        final @NotNull Path storageRoot,
        final @NotNull Consumer<String> logger,
        final int queueCapacity,
        final @Nullable AutoCloseable storageLock
    ) {
        this.storageLock = storageLock;
        this.logger = logger;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
        final Path dir = storageRoot;
        try {
            Files.createDirectories(dir);
        } catch (final IOException ex) {
            throw new IllegalStateException("cannot create " + dir, ex);
        }
        this.auditPath = dir.resolve("provenance-audit.jsonl");
        this.spillJournal = new ProvenanceSpillJournal(dir.resolve("provenance-spill.log"));

        final ProvenanceRepository repo;
        try {
            repo = new ProvenanceRepository(dir.resolve("provenance.db"));
        } catch (final Exception ex) {
            throw new IllegalStateException("provenance store failed to open: " + ex.getMessage(), ex);
        }
        this.repository = repo;
        this.writeSequence = repo != null ? repo.maxWriteSequence() : 0L;
        LineageStore lineage = ItemProvenance.lineage();
        lineage.attachRepository(repo);
        try {
            // Recover unacked spill on the install thread so the live seed below sees
            // post-replay DB state (async drain would only update SQLite, leaving a
            // stale census after crash with pending live spill rows).
            if (!this.replaySpill()) {
                throw new IllegalStateException("provenance spill recovery failed: " + this.lastError);
            }

            // Seed in-memory live census from durable last-seen rows after spill recover.
            for (final LiveRecord row : repo.loadAliveLive()) {
                final StackLocation loc = ProvenanceRepository.parseLocationDisplay(row.locationDisplay());
                ItemProvenance.live().put(new LiveEntry(row.id(), row.itemId(), loc, row.count(), row.epochMs()));
            }
        } catch (final RuntimeException ex) {
            lineage.attachRepository(null);
            repo.close();
            throw ex;
        }

        this.thread = new Thread(this::drain, "mintychochip-provenance-writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public static synchronized void install(final @NotNull Path storageRoot, final @NotNull Consumer<String> logger) {
        if (instance != null) {
            return;
        }
        final ProvenanceWriter writer = new ProvenanceWriter(storageRoot, logger);
        instance = writer;
        try {
            logger.accept("[mintychochip] provenance audit → " + writer.auditPath + " (store: sqlite)");
        } catch (final RuntimeException | Error ex) {
            instance = null;
            writer.shutdown();
            ItemProvenance.lineage().attachRepository(null);
            throw ex;
        }
    }

    static synchronized @NotNull ProvenanceWriter installLocked(
        final @NotNull Path storageRoot,
        final @NotNull Consumer<String> logger,
        final @NotNull AutoCloseable storageLock
    ) {
        if (instance != null) {
            try {
                storageLock.close();
            } catch (final Exception closeFailure) {
                throw new IllegalStateException("cannot release provenance storage lock", closeFailure);
            }
            throw new IllegalStateException("provenance writer is already installed");
        }
        final ProvenanceWriter writer;
        try {
            writer = new ProvenanceWriter(storageRoot, logger, QUEUE_CAPACITY, storageLock);
        } catch (final RuntimeException | Error ex) {
            try {
                storageLock.close();
            } catch (final Exception closeFailure) {
                ex.addSuppressed(closeFailure);
            }
            throw ex;
        }
        instance = writer;
        try {
            logger.accept("[mintychochip] provenance audit → " + writer.auditPath + " (store: sqlite)");
        } catch (final RuntimeException | Error ex) {
            instance = null;
            writer.shutdown();
            ItemProvenance.lineage().attachRepository(null);
            throw ex;
        }
        return writer;
    }

    /**
     * Test hook: install with a tiny memory queue so spill under pressure is
     * exercised without flooding tens of thousands of events.
     */
    public static synchronized void installForTest(
        final @NotNull Path storageRoot,
        final @NotNull Consumer<String> logger,
        final int queueCapacity
    ) {
        if (instance != null) {
            return;
        }
        final ProvenanceWriter writer = new ProvenanceWriter(storageRoot, logger, queueCapacity);
        instance = writer;
        try {
            logger.accept("[mintychochip] provenance audit → " + writer.auditPath + " (store: sqlite, test-capacity=" + queueCapacity + ")");
        } catch (final RuntimeException | Error ex) {
            instance = null;
            writer.shutdown();
            ItemProvenance.lineage().attachRepository(null);
            throw ex;
        }
    }

    /** Test hook: stop the writer and detach. */
    public static synchronized void clearInstall() {
        final ProvenanceWriter writer = instance;
        instance = null;
        if (writer != null) {
            writer.shutdown();
        }
        ItemProvenance.lineage().attachRepository(null);
    }

    static synchronized void clearInstall(final @NotNull ProvenanceWriter expected) {
        if (instance != expected) {
            return;
        }
        instance = null;
        expected.shutdown();
        ItemProvenance.lineage().attachRepository(null);
    }

    /** Flush and close the installed writer (server shutdown). Safe to call repeatedly. */
    public static void flushAndClose() {
        final ProvenanceWriter w = instance;
        if (w != null) {
            w.shutdown();
        }
    }

    public static boolean isInstalled() {
        return instance != null;
    }

    public static void enqueueAudit(final @NotNull ProvenanceEvent event) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.offerAudit(new WriteItem.Audit(event));
    }

    public static void enqueueLineage(final @NotNull LineageNode node) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.offerCritical(new WriteItem.Lineage(node));
    }

    public static void enqueueLive(final @NotNull LiveRecord record) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.offerCritical(new WriteItem.Live(record));
    }

    public static void enqueueCollision(final @NotNull CollisionRecord record) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.offerCritical(new WriteItem.Collision(record));
    }

    public static void reportStorageError(final @NotNull String context, final @NotNull Exception ex) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.recordError(context + ": " + ex.getMessage());
    }

    private void offerCritical(final WriteItem item) {
        if (this.running.get() && this.queue.offer(item)) {
            return;
        }
        // Queue full or shutting down: never drop critical — spill (or block as last resort).
        this.spillCritical(item);
    }

    private void offerAudit(final WriteItem.Audit item) {
        if (this.running.get() && this.queue.offer(item)) {
            return;
        }
        try {
            this.spillJournal.appendAudit(item.event());
        } catch (final IOException ex) {
            this.auditDropped.incrementAndGet();
            this.recordError("audit spill failed: " + ex.getMessage());
        }
    }

    private void spillCritical(final WriteItem item) {
        try {
            switch (item) {
                case WriteItem.Lineage lineage -> this.spillJournal.appendLineage(lineage.node());
                case WriteItem.Live live -> this.spillJournal.appendLive(live.record());
                case WriteItem.Collision collision -> this.spillJournal.appendCollision(collision.record());
                case WriteItem.Audit ignored -> throw new IllegalStateException("audit is not critical");
            }
        } catch (final IOException ex) {
            this.recordError("critical spill failed: " + ex.getMessage());
            // Absolute last resort: block until the writer accepts (must not drop critical).
            try {
                this.queue.put(item);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                this.recordError("critical write interrupted after spill failure");
            }
        }
    }

    private void drain() {
        try {
            // Recover any pre-crash spill before accepting new work as committed.
            this.replaySpill();
            while (this.running.get()) {
                try {
                    final WriteItem first = this.queue.poll(500, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        this.processBatch(first);
                    } else {
                        this.replaySpill();
                        this.flushAudit();
                    }
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Throwable t) {
                    this.recordError("writer failure: " + t);
                }
            }
            // Final drain on close: memory queue then remaining spill.
            try {
                WriteItem item;
                while ((item = this.queue.poll()) != null) {
                    this.processBatch(item);
                }
                this.replaySpill();
            } catch (final Throwable t) {
                this.recordError("final drain failure: " + t);
            }
            this.flushAudit();
            this.closeAudit();
            final ProvenanceRepository repo = this.repository;
            if (repo != null) {
                repo.close();
            }
        } finally {
            this.releaseStorageLock();
        }
    }

    private void releaseStorageLock() {
        final AutoCloseable lock = this.storageLock;
        if (lock == null || !this.storageLockReleased.compareAndSet(false, true)) {
            return;
        }
        try {
            lock.close();
        } catch (final Exception ex) {
            this.recordError("storage lock release failed: " + ex.getMessage());
        }
    }

    private void processBatch(final @NotNull WriteItem first) {
        final List<WriteItem> batch = new ArrayList<>(BATCH_MAX);
        batch.add(first);
        this.queue.drainTo(batch, BATCH_MAX - 1);
        final ProvenanceRepository repo = this.repository;
        try {
            if (repo != null && !repo.isFailed() && batch.size() > 1) {
                repo.runInTransaction(ignored -> {
                    for (final WriteItem item : batch) {
                        this.process(item);
                    }
                });
            } else {
                for (final WriteItem item : batch) {
                    this.process(item);
                }
            }
        } catch (final SQLException ex) {
            if (repo != null) {
                repo.markFailed();
            }
            this.recordError("repository write failed: " + ex.getMessage());
        }
    }

    private void process(final WriteItem item) throws SQLException {
        final ProvenanceRepository repo = this.repository;
        switch (item) {
            case WriteItem.Audit audit -> {
                if (repo != null) {
                    repo.insertAudit(UUID.randomUUID(), audit.event());
                }
                this.appendAuditJsonl(audit.event());
            }
            case WriteItem.Lineage lineage -> {
                if (repo != null) {
                    repo.upsertLineage(lineage.node(), ++this.writeSequence);
                }
            }
            case WriteItem.Live live -> {
                if (repo != null) {
                    repo.upsertLive(live.record(), ++this.writeSequence);
                }
            }
            case WriteItem.Collision collision -> {
                if (repo != null) {
                    repo.insertCollision(collision.record(), collisionDedupeKey(collision.record()));
                }
            }
        }
        this.written.incrementAndGet();
    }

    private static @NotNull String collisionDedupeKey(final @NotNull CollisionRecord record) {
        return record.id() + "|" + record.kind().name()
            + "|" + record.existingLocation().display()
            + "|" + record.observedLocation().display();
    }

    private boolean replaySpill() {
        final ProvenanceRepository repo = this.repository;
        if (repo == null || repo.isFailed()) {
            return false;
        }
        final List<ProvenanceSpillJournal.SpillRecord> records;
        try {
            records = this.spillJournal.seizePending();
        } catch (final IOException ex) {
            this.recordError("spill read failed: " + ex.getMessage());
            return false;
        }
        if (records.isEmpty()) {
            try {
                this.spillJournal.ackSeized();
                return true;
            } catch (final IOException ex) {
                this.recordError("spill acknowledgement failed: " + ex.getMessage());
                return false;
            }
        }
        try {
            repo.runInTransaction(ignored -> {
                for (final ProvenanceSpillJournal.SpillRecord record : records) {
                    this.applySpill(record);
                    if (repo.isFailed()) {
                        throw new IllegalStateException("spill apply failed after SQL error");
                    }
                }
            });
            this.spillJournal.ackSeized();
            return true;
        } catch (final SQLException ex) {
            repo.markFailed();
            this.recordError("spill replay failed: " + ex.getMessage());
            return false;
        } catch (final Exception ex) {
            this.recordError("spill replay failed: " + ex.getMessage());
            return false;
        }
    }

    private void applySpill(final ProvenanceSpillJournal.SpillRecord record) throws SQLException {
        switch (record) {
            case ProvenanceSpillJournal.SpillRecord.Lineage lineage ->
                this.process(new WriteItem.Lineage(lineage.node()));
            case ProvenanceSpillJournal.SpillRecord.Live live ->
                this.process(new WriteItem.Live(live.record()));
            case ProvenanceSpillJournal.SpillRecord.Collision collision ->
                this.process(new WriteItem.Collision(collision.record()));
            case ProvenanceSpillJournal.SpillRecord.Audit audit ->
                this.process(new WriteItem.Audit(audit.event()));
        }
    }

    private void appendAuditJsonl(final @NotNull ProvenanceEvent event) {
        if (this.auditWriter == null) {
            try {
                this.auditWriter = Files.newBufferedWriter(
                    this.auditPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                );
                this.auditBytes = Files.size(this.auditPath);
            } catch (final IOException ex) {
                this.recordError("cannot open audit file: " + ex.getMessage());
                this.auditWriter = null;
                // JSONL is a mirror only; DB insert already attempted. Do not count as audit-dropped.
                return;
            }
        }
        final String line = toJsonLine(event);
        try {
            this.auditWriter.write(line);
            this.auditWriter.newLine();
            this.auditBytes += line.length() + 1L;
            if (++this.itemsSinceFlush >= 100) {
                this.flushAudit();
            }
            if (this.auditBytes >= MAX_AUDIT_BYTES) {
                this.rotateAudit();
            }
        } catch (final IOException ex) {
            this.recordError("audit write failed: " + ex.getMessage());
            this.closeAudit();
        }
    }

    private void rotateAudit() {
        this.flushAudit();
        this.closeAudit();
        for (int i = MAX_ROTATIONS - 1; i >= 1; i--) {
            final Path from = Path.of(this.auditPath + "." + i);
            final Path to = Path.of(this.auditPath + "." + (i + 1));
            try {
                Files.deleteIfExists(to);
                if (Files.exists(from)) {
                    Files.move(from, to);
                }
            } catch (final IOException ex) {
                this.recordError("audit rotation failed: " + ex.getMessage());
            }
        }
        final Path first = Path.of(this.auditPath + ".1");
        try {
            Files.deleteIfExists(first);
            Files.move(this.auditPath, first);
        } catch (final IOException ex) {
            this.recordError("audit rotation failed: " + ex.getMessage());
        }
        this.auditBytes = 0;
    }

    private void flushAudit() {
        final BufferedWriter writer = this.auditWriter;
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            this.itemsSinceFlush = 0;
        } catch (final IOException ex) {
            this.recordError("audit flush failed: " + ex.getMessage());
            this.closeAudit();
        }
    }

    private void closeAudit() {
        final BufferedWriter writer = this.auditWriter;
        this.auditWriter = null;
        if (writer != null) {
            try {
                writer.close();
            } catch (final IOException ignored) {
                // already broken
            }
        }
    }

    private void recordError(final @NotNull String message) {
        this.lastError = message;
        final long now = System.currentTimeMillis();
        if (now - this.lastErrorLogMs > ERROR_LOG_INTERVAL_MS) {
            this.lastErrorLogMs = now;
            try {
                this.logger.accept("[mintychochip] WARN provenance storage: " + message);
            } catch (final RuntimeException | Error ignored) {
                // Logging must not interrupt storage recovery or lock release.
            }
        }
    }

    public void shutdown() {
        this.running.set(false);
        try {
            this.thread.join(SHUTDOWN_JOIN_MS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public static @NotNull String status() {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return "not installed";
        }
        final String error = w.lastError == null ? "none" : w.lastError;
        return "queue-depth=" + w.queue.size()
            + " spill-bytes=" + w.spillJournal.sizeBytes()
            + " written=" + w.written.get()
            + " audit-dropped=" + w.auditDropped.get()
            + " store=" + (w.repository != null && !w.repository.isFailed() ? "sqlite" : "in-memory")
            + " last-error=" + error;
    }

    /**
     * Recent durable audit events (newest-first) when the SQLite store is healthy.
     * Empty when the writer is not installed or the repository is unavailable/failed —
     * callers should fall back to {@link AuditLog#latest(int)} (chronological).
     */
    public static Optional<List<ProvenanceEvent>> recentAudit(final int n) {
        final ProvenanceWriter w = instance;
        if (w == null || w.repository == null || w.repository.isFailed()) {
            return Optional.empty();
        }
        return Optional.of(w.repository.loadRecentAudit(n));
    }

    // -------------------------------------------------------------------------
    // Queue payloads
    // -------------------------------------------------------------------------

    private sealed interface WriteItem {
        record Audit(@NotNull ProvenanceEvent event) implements WriteItem {
        }

        record Lineage(@NotNull LineageNode node) implements WriteItem {
        }

        record Live(@NotNull LiveRecord record) implements WriteItem {
        }

        record Collision(@NotNull CollisionRecord record) implements WriteItem {
        }
    }

    // -------------------------------------------------------------------------
    // JSONL export
    // -------------------------------------------------------------------------

    private static @NotNull String toJsonLine(final ProvenanceEvent event) {
        final StringBuilder sb = new StringBuilder(128);
        sb.append("{\"t\":").append(event.epochMs());
        sb.append(",\"type\":\"").append(event.type().name()).append('"');
        sb.append(",\"id\":\"").append(event.id()).append('"');
        if (event.itemId() != null) {
            sb.append(",\"item\":\"").append(escape(event.itemId())).append('"');
        }
        if (event.source() != null) {
            sb.append(",\"source\":\"").append(event.source().name()).append('"');
        }
        if (event.reason() != null) {
            sb.append(",\"reason\":\"").append(event.reason().name()).append('"');
        }
        if (!event.related().isEmpty()) {
            sb.append(",\"related\":[");
            boolean first = true;
            for (final UUID u : event.related()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(u).append('"');
            }
            sb.append(']');
        }
        if (event.holder() != null) {
            sb.append(",\"location\":\"").append(escape(event.holder())).append('"');
        }
        if (event.detail() != null) {
            sb.append(",\"detail\":\"").append(escape(event.detail())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static @NotNull String escape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Keep the list import used by future tooling (repository queries). */
    static @NotNull List<String> auditFields() {
        return List.of("t", "type", "id", "item", "source", "reason", "related", "location", "detail");
    }
}
