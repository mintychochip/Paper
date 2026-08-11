package dev.mintychochip.provenance;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    public enum State { STARTING, RUNNING, DEGRADED, DRAINING, CLOSED }

    private final @NotNull Path auditPath;
    private final @NotNull Path storePath;
    private final @NotNull ProvenanceSpillJournal spillJournal;
    private volatile @Nullable ProvenanceRepository repository;
    private final @NotNull BlockingQueue<WriteItem> queue;
    private final @NotNull AtomicBoolean running = new AtomicBoolean(true);
    private final @NotNull Thread thread;
    private final @NotNull AtomicLong auditDropped = new AtomicLong();
    private final @NotNull AtomicLong written = new AtomicLong();
    private final @NotNull AtomicLong criticalFailures = new AtomicLong();
    private final @NotNull Deque<ProvenanceEvent> recentAudit = new ArrayDeque<>();
    private final @NotNull Deque<CollisionRecord> recentCollisions = new ArrayDeque<>();
    private volatile State state = State.STARTING;
    private long writeSequence;
    private volatile @Nullable String lastError;
    private volatile long lastErrorLogMs;
    private volatile long lastCommitMs;
    private volatile long nextRetryMs;
    private long retryDelayMs = 1_000L;
    private @Nullable BufferedWriter auditWriter;
    private long auditBytes;
    private int itemsSinceFlush;
    private final @NotNull Consumer<String> logger;

    private ProvenanceWriter(final @NotNull Path worldFolder, final @NotNull Consumer<String> logger) {
        this(worldFolder, logger, QUEUE_CAPACITY);
    }

    private ProvenanceWriter(
        final @NotNull Path worldFolder,
        final @NotNull Consumer<String> logger,
        final int queueCapacity
    ) {
        this.logger = logger;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
        final Path dir = worldFolder.resolve("mintychochip");
        try {
            Files.createDirectories(dir);
        } catch (final IOException ex) {
            this.lastError = "cannot create " + dir + ": " + ex.getMessage();
        }
        this.auditPath = dir.resolve("provenance-audit.jsonl");
        this.storePath = dir.resolve("provenance.db");
        this.spillJournal = new ProvenanceSpillJournal(dir.resolve("provenance-spill.log"));
        ProvenanceRepository repo = null;
        try {
            repo = new ProvenanceRepository(this.storePath);
        } catch (final Exception ex) {
            this.lastError = "provenance store failed to open: " + ex.getMessage();
            logger.accept("[mintychochip] WARN provenance store unavailable, running in-memory only: " + ex.getMessage());
        }
        this.repository = repo;
        this.writeSequence = repo != null ? repo.maxWriteSequence() : 0L;
        LineageStore lineage = ItemProvenance.lineage();
        lineage.attachRepository(repo);

        // Recover unacked spill on the install thread so the live seed below sees
        // post-replay DB state (async drain would only update SQLite, leaving a
        // stale census after crash with pending live spill rows).
        this.replaySpill();

        // Seed in-memory live census from durable last-seen rows after spill recover.
        if (repo != null) {
            for (final LiveRecord row : repo.loadAliveLive()) {
                final StackLocation loc = ProvenanceRepository.parseLocationDisplay(row.locationDisplay());
                ItemProvenance.live().put(new LiveEntry(row.id(), row.itemId(), loc, row.count(), row.epochMs()));
            }
            synchronized (this.recentAudit) {
                this.recentAudit.addAll(repo.loadRecentAudit(256));
            }
            synchronized (this.recentCollisions) {
                this.recentCollisions.addAll(repo.loadRecentCollisions(256));
            }
        }
        this.state = repo != null && !repo.isFailed() ? State.RUNNING : State.DEGRADED;
        this.thread = new Thread(this::drain, "mintychochip-provenance-writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public static synchronized void install(final @NotNull Path worldFolder, final @NotNull Consumer<String> logger) {
        if (instance != null) {
            return;
        }
        instance = new ProvenanceWriter(worldFolder, logger);
        final Path audit = instance.auditPath;
        final String store = instance.repository != null ? "sqlite" : "in-memory";
        logger.accept("[mintychochip] provenance audit → " + audit + " (store: " + store + ")");
    }

    /**
     * Test hook: install with a tiny memory queue so spill under pressure is
     * exercised without flooding tens of thousands of events.
     */
    public static synchronized void installForTest(
        final @NotNull Path worldFolder,
        final @NotNull Consumer<String> logger,
        final int queueCapacity
    ) {
        if (instance != null) {
            return;
        }
        instance = new ProvenanceWriter(worldFolder, logger, queueCapacity);
        final Path audit = instance.auditPath;
        final String store = instance.repository != null ? "sqlite" : "in-memory";
        logger.accept("[mintychochip] provenance audit → " + audit + " (store: " + store + ", test-capacity=" + queueCapacity + ")");
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
        if (w == null) return;
        w.offerAudit(new WriteItem.Audit(w.nextSequence(), UUID.randomUUID(), event));
    }

    public static void enqueueLineage(final @NotNull LineageNode node) {
        final ProvenanceWriter w = instance;
        if (w == null) return;
        w.offerCritical(new WriteItem.Lineage(w.nextSequence(), node));
    }

    public static void enqueueLive(final @NotNull LiveRecord record) {
        final ProvenanceWriter w = instance;
        if (w == null) return;
        w.offerCritical(new WriteItem.Live(w.nextSequence(), record));
    }

    public static void enqueueCollision(final @NotNull CollisionRecord record) {
        final ProvenanceWriter w = instance;
        if (w == null) return;
        w.offerCritical(new WriteItem.Collision(w.nextSequence(), record));
    }

    private synchronized long nextSequence() {
        return ++this.writeSequence;
    }

    public static void reportStorageError(final @NotNull String context, final @NotNull Exception ex) {
        final ProvenanceWriter w = instance;
        if (w == null) {
            return;
        }
        w.recordError(context + ": " + ex.getMessage());
    }

    private void offerCritical(final WriteItem item) {
        if (this.running.get() && this.state != State.DRAINING && this.queue.offer(item)) return;
        this.spillCritical(item);
    }

    private void offerAudit(final WriteItem.Audit item) {
        if (this.state != State.DEGRADED && this.running.get() && this.queue.offer(item)) return;
        try {
            this.spillJournal.appendAudit(item.sequence(), item.eventId(), item.event());
        } catch (final IOException ex) {
            this.auditDropped.incrementAndGet();
            this.recordError("audit spill failed: " + ex.getMessage());
        }
    }

    private void spillCritical(final WriteItem item) {
        try {
            switch (item) {
                case WriteItem.Lineage lineage -> this.spillJournal.appendLineage(lineage.sequence(), lineage.node());
                case WriteItem.Live live -> this.spillJournal.appendLive(live.sequence(), live.record());
                case WriteItem.Collision collision -> this.spillJournal.appendCollision(collision.sequence(), collision.record());
                case WriteItem.Audit audit -> this.spillJournal.appendAudit(audit.sequence(), audit.eventId(), audit.event());
            }
        } catch (final IOException ex) {
            this.criticalFailures.incrementAndGet();
            this.recordError("critical spill failed: " + ex.getMessage());
            try {
                this.queue.put(item);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                this.recordError("critical write interrupted after spill failure");
            }
        }
    }
    private void drain() {
        while (this.running.get()) {
            try {
                final WriteItem first = this.queue.poll(500, TimeUnit.MILLISECONDS);
                if (first != null) {
                    this.processBatch(first);
                } else {
                    this.attemptReopen();
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
    }

    private void processBatch(final @NotNull WriteItem first) {
        final List<WriteItem> batch = new ArrayList<>(BATCH_MAX);
        batch.add(first);
        this.queue.drainTo(batch, BATCH_MAX - 1);
        final ProvenanceRepository repo = this.repository;
        if (repo == null || repo.isFailed()) {
            this.state = State.DEGRADED;
            for (final WriteItem item : batch) this.spillCritical(item);
            return;
        }
        try {
            repo.runInTransaction(ignored -> {
                for (final WriteItem item : batch) this.process(item);
            });
            for (final WriteItem item : batch) {
                this.written.incrementAndGet();
                if (item instanceof WriteItem.Audit audit) {
                    this.appendAuditJsonl(audit.event());
                    synchronized (this.recentAudit) {
                        this.recentAudit.addFirst(audit.event());
                        while (this.recentAudit.size() > 256) this.recentAudit.removeLast();
                    }
                } else if (item instanceof WriteItem.Collision collision) {
                    synchronized (this.recentCollisions) {
                        this.recentCollisions.addFirst(collision.record());
                        while (this.recentCollisions.size() > 256) this.recentCollisions.removeLast();
                    }
                }
            }
            this.lastCommitMs = System.currentTimeMillis();
            this.state = State.RUNNING;
        } catch (final SQLException ex) {
            this.state = State.DEGRADED;
            repo.markFailed();
            repo.close();
            this.repository = null;
            this.nextRetryMs = 0L;
            for (final WriteItem item : batch) this.spillCritical(item);
            this.recordError("repository write failed: " + ex.getMessage());
        }
    }

    private void process(final WriteItem item) throws SQLException {
        final ProvenanceRepository repo = this.repository;
        if (repo == null) throw new SQLException("repository unavailable");
        switch (item) {
            case WriteItem.Audit audit -> repo.insertAudit(audit.eventId(), audit.event());
            case WriteItem.Lineage lineage -> repo.upsertLineage(lineage.node(), lineage.sequence());
            case WriteItem.Live live -> repo.upsertLive(live.record(), live.sequence());
            case WriteItem.Collision collision -> repo.insertCollision(collision.record(), collisionDedupeKey(collision.record()));
        }
    }

    private static @NotNull String collisionDedupeKey(final @NotNull CollisionRecord record) {
        return record.id() + "|" + record.kind().name()
            + "|" + record.existingLocation().display()
            + "|" + record.observedLocation().display();
    }

    private void attemptReopen() {
        if (this.repository != null || System.currentTimeMillis() < this.nextRetryMs || this.state == State.DRAINING) return;
        try {
            final ProvenanceRepository reopened = new ProvenanceRepository(this.storePath);
            this.repository = reopened;
            this.writeSequence = Math.max(this.writeSequence, reopened.maxWriteSequence());
            ItemProvenance.lineage().attachRepository(reopened);
            this.retryDelayMs = 1_000L;
            this.state = State.RUNNING;
            this.replaySpill();
        } catch (final Exception ex) {
            this.state = State.DEGRADED;
            this.nextRetryMs = System.currentTimeMillis() + this.retryDelayMs;
            this.retryDelayMs = Math.min(16_000L, this.retryDelayMs * 2L);
            this.recordError("repository reopen failed: " + ex.getMessage());
        }
    }

    private void replaySpill() {
        final ProvenanceRepository repo = this.repository;
        // Do not seize while the store is unavailable — leave spill / .replay intact.
        if (repo == null || repo.isFailed()) {
            return;
        }
        final List<ProvenanceSpillJournal.SpillRecord> records;
        try {
            records = this.spillJournal.seizePending();
        } catch (final IOException ex) {
            this.recordError("spill read failed: " + ex.getMessage());
            return;
        }
        if (records.isEmpty()) {
            // Empty seized file (e.g. blank lines) must still be acked so recovery can advance.
            try {
                this.spillJournal.ackSeized();
            } catch (final IOException ignored) {
                // nothing outstanding
            }
            return;
        }
        try {
            repo.runInTransaction(ignored -> {
                for (final ProvenanceSpillJournal.SpillRecord record : records) {
                    this.applySpill(record);
                }
            });
            this.spillJournal.ackSeized();
        } catch (final SQLException ex) {
            repo.markFailed();
            this.recordError("spill replay failed: " + ex.getMessage());
            // Leave .replay for the next attempt; never ack on failure.
        } catch (final IOException ex) {
            this.recordError("spill replay failed: " + ex.getMessage());
            // Leave .replay for the next attempt; never ack on failure.
        }
    }

    private void applySpill(final ProvenanceSpillJournal.SpillRecord record) throws SQLException {
        final long sequence = record.sequence() == 0L ? ++this.writeSequence : record.sequence();
        this.writeSequence = Math.max(this.writeSequence, sequence);
        switch (record) {
            case ProvenanceSpillJournal.SpillRecord.Lineage lineage ->
                this.process(new WriteItem.Lineage(sequence, lineage.node()));
            case ProvenanceSpillJournal.SpillRecord.Live live ->
                this.process(new WriteItem.Live(sequence, live.record()));
            case ProvenanceSpillJournal.SpillRecord.Collision collision ->
                this.process(new WriteItem.Collision(sequence, collision.record()));
            case ProvenanceSpillJournal.SpillRecord.Audit audit ->
                this.process(new WriteItem.Audit(sequence, audit.eventId(), audit.event()));
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
            this.logger.accept("[mintychochip] WARN provenance storage: " + message);
        }
    }

    public void shutdown() {
        this.state = State.DRAINING;
        this.running.set(false);
        try {
            this.thread.join(SHUTDOWN_JOIN_MS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        this.state = State.CLOSED;
    }

    static void failRepositoryForTest() {
        final ProvenanceWriter w = instance;
        if (w == null) return;
        final ProvenanceRepository repo = w.repository;
        w.repository = null;
        w.state = State.DEGRADED;
        if (repo != null) repo.close();
    }

    public static @NotNull String status() {
        final ProvenanceWriter w = instance;
        if (w == null) return "not installed";
        final String error = w.lastError == null ? "none" : w.lastError;
        return "state=" + w.state.name().toLowerCase()
            + " queue-depth=" + w.queue.size()
            + " spill-bytes=" + w.spillJournal.sizeBytes()
            + " critical-pending=" + w.queue.stream().filter(i -> !(i instanceof WriteItem.Audit)).count()
            + " critical-failures=" + w.criticalFailures.get()
            + " written=" + w.written.get()
            + " audit-dropped=" + w.auditDropped.get()
            + " last-commit-ms=" + w.lastCommitMs
            + " store-root=" + w.auditPath.getParent()
            + " last-error=" + error;
    }

    public static Optional<List<ProvenanceEvent>> recentAudit(final int n) {
        final ProvenanceWriter w = instance;
        if (w == null) return Optional.empty();
        synchronized (w.recentAudit) {
            return Optional.of(List.copyOf(w.recentAudit).subList(0, Math.min(Math.max(0, n), w.recentAudit.size())));
        }
    }

    public static Optional<List<CollisionRecord>> recentCollisions(final int n) {
        final ProvenanceWriter w = instance;
        if (w == null) return Optional.empty();
        synchronized (w.recentCollisions) {
            return Optional.of(List.copyOf(w.recentCollisions).subList(0, Math.min(Math.max(0, n), w.recentCollisions.size())));
        }
    }

    // -------------------------------------------------------------------------
    // Queue payloads
    // -------------------------------------------------------------------------

    private sealed interface WriteItem {
        long sequence();

        record Audit(long sequence, @NotNull UUID eventId, @NotNull ProvenanceEvent event) implements WriteItem {
        }

        record Lineage(long sequence, @NotNull LineageNode node) implements WriteItem {
        }

        record Live(long sequence, @NotNull LiveRecord record) implements WriteItem {
        }

        record Collision(long sequence, @NotNull CollisionRecord record) implements WriteItem {
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
