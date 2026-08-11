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
    private static final int RECENT_AUDIT_CAPACITY = 16_384;
    private static final int RECENT_COLLISION_CAPACITY = 4_096;
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
    private final @Nullable AutoCloseable storageLock;
    private final @NotNull BlockingQueue<WriteItem> queue;
    private final @NotNull AtomicBoolean running = new AtomicBoolean(true);
    private final @NotNull AtomicBoolean storageLockReleased = new AtomicBoolean();
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
        this.storePath = dir.resolve("provenance.db");
        this.spillJournal = new ProvenanceSpillJournal(dir.resolve("provenance-spill.log"));
        ProvenanceRepository repo = null;
        try {
            repo = new ProvenanceRepository(this.storePath);
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
            synchronized (this.recentAudit) {
                this.recentAudit.clear();
                this.recentAudit.addAll(repo.loadRecentAudit(RECENT_AUDIT_CAPACITY));
            }
            synchronized (this.recentCollisions) {
                this.recentCollisions.clear();
                this.recentCollisions.addAll(repo.loadRecentCollisions(RECENT_COLLISION_CAPACITY));
            }
        } catch (final RuntimeException ex) {
            lineage.attachRepository(null);
            repo.close();
            throw ex;
        }
        this.state = repo != null && !repo.isFailed() ? State.RUNNING : State.DEGRADED;
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

    public static void enqueueCollision(
        final @NotNull CollisionRecord record,
        final @NotNull String dedupeKey,
        final @NotNull UUID auditEventId,
        final @NotNull ProvenanceEvent auditEvent
    ) {
        final ProvenanceWriter w = instance;
        if (w == null) return;
        w.offerCritical(new WriteItem.Collision(w.nextSequence(), record, dedupeKey, auditEventId, auditEvent));
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
                case WriteItem.Collision collision -> this.spillJournal.appendCollision(
                    collision.sequence(),
                    collision.record(),
                    collision.auditEventId(),
                    collision.auditEvent()
                );
                case WriteItem.Audit audit -> this.spillJournal.appendAudit(audit.sequence(), audit.eventId(), audit.event());
            }
        } catch (final IOException ex) {
            this.criticalFailures.incrementAndGet();
            this.recordError("critical spill failed: " + ex.getMessage());
            try {
                if (item instanceof WriteItem.Collision collision
                    && collision.auditEventId() != null
                    && collision.auditEvent() != null) {
                    this.queue.put(new WriteItem.Audit(
                        collision.sequence(),
                        collision.auditEventId(),
                        collision.auditEvent()
                    ));
                }
                this.queue.put(item);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                this.recordError("critical write interrupted after spill failure");
            }
        }
    }
    private void drain() {

        try {
            // Recover before each poll so a queued backlog cannot starve
            // repository reopen or spill replay indefinitely.
            this.replaySpill();
            while (this.running.get()) {
                try {
                    this.attemptReopen();
                    if (System.currentTimeMillis() >= this.nextRetryMs) {
                        this.replaySpill();
                    }
                    final WriteItem first = this.queue.poll(500, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        this.processBatch(first);
                    } else {
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
        if (repo == null || repo.isFailed()) {
            this.state = State.DEGRADED;
            for (final WriteItem item : batch) this.spillCritical(item);
            return;
        }
        try {
            final List<ProcessedItem> applied = new ArrayList<>(batch.size());
            repo.runInTransaction(ignored -> {
                for (final WriteItem item : batch) {
                    applied.add(this.process(item));
                }
            });
            for (final ProcessedItem item : applied) {
                this.recordCommitted(item);
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

    private @NotNull ProcessedItem process(final WriteItem item) throws SQLException {
        final ProvenanceRepository repo = this.repository;
        if (repo == null) throw new SQLException("repository unavailable");
        return switch (item) {
            case WriteItem.Audit audit ->
                new ProcessedItem(item, false, repo.insertAudit(audit.eventId(), audit.event()));
            case WriteItem.Lineage lineage -> {
                repo.upsertLineage(lineage.node(), lineage.sequence());
                yield new ProcessedItem(item, false, false);
            }
            case WriteItem.Live live -> {
                repo.upsertLive(live.record(), live.sequence());
                yield new ProcessedItem(item, false, false);
            }
            case WriteItem.Collision collision -> {
                final boolean collisionInserted = repo.insertCollision(
                    collision.record(),
                    collision.dedupeKey()
                );
                boolean auditInserted = false;
                if (collisionInserted && collision.auditEventId() != null && collision.auditEvent() != null) {
                    auditInserted = repo.insertAudit(collision.auditEventId(), collision.auditEvent());
                }
                yield new ProcessedItem(item, collisionInserted, auditInserted);
            }
        };
    }

    private void recordCommitted(final @NotNull ProcessedItem processed) {
        this.written.incrementAndGet();
        if (processed.item() instanceof WriteItem.Audit audit) {
            if (processed.auditInserted()) {
                this.recordAuditCommitted(audit.event());
            }
        } else if (processed.item() instanceof WriteItem.Collision collision) {
            if (processed.collisionInserted()) {
                synchronized (this.recentCollisions) {
                    this.recentCollisions.addFirst(collision.record());
                    while (this.recentCollisions.size() > RECENT_COLLISION_CAPACITY) {
                        this.recentCollisions.removeLast();
                    }
                }
            }
            if (processed.auditInserted() && collision.auditEvent() != null) {
                this.recordAuditCommitted(collision.auditEvent());
            }
        }
    }

    private void recordAuditCommitted(final @NotNull ProvenanceEvent event) {
        this.appendAuditJsonl(event);
        synchronized (this.recentAudit) {
            this.recentAudit.addFirst(event);
            while (this.recentAudit.size() > RECENT_AUDIT_CAPACITY) {
                this.recentAudit.removeLast();
            }
        }
    }

    private static @NotNull String collisionDedupeKey(final @NotNull CollisionRecord record) {
        return record.id() + "|" + record.kind().name()
            + "|" + record.existingLocation().display()
            + "|" + record.observedLocation().display();
    }

    private void attemptReopen() {
        if (this.repository != null || System.currentTimeMillis() < this.nextRetryMs || this.state == State.DRAINING) {
            return;
        }
        ProvenanceRepository reopened = null;
        try {
            reopened = new ProvenanceRepository(this.storePath);
            this.repository = reopened;
            this.writeSequence = Math.max(this.writeSequence, reopened.maxWriteSequence());
            ItemProvenance.lineage().attachRepository(reopened);
            if (!this.replaySpill()) {
                if (this.repository == reopened) {
                    this.discardRepository(reopened);
                    this.state = State.DEGRADED;
                    if (System.currentTimeMillis() >= this.nextRetryMs) {
                        this.scheduleRetry();
                    }
                }
                this.recordError("spill replay unavailable after repository reopen");
                return;
            }
            this.retryDelayMs = 1_000L;
            this.nextRetryMs = 0L;
            this.state = State.RUNNING;
        } catch (final Exception ex) {
            if (reopened != null) {
                this.discardRepository(reopened);
            }
            this.state = State.DEGRADED;
            this.scheduleRetry();
            this.recordError("repository reopen failed: " + ex.getMessage());
        }
    }

    private void discardRepository(final @NotNull ProvenanceRepository repo) {
        if (this.repository == repo) {
            this.repository = null;
            ItemProvenance.lineage().attachRepository(null);
        }
        repo.close();
    }

    private void scheduleRetry() {
        this.nextRetryMs = System.currentTimeMillis() + this.retryDelayMs;
        this.retryDelayMs = Math.min(16_000L, this.retryDelayMs * 2L);
    }

    private boolean replaySpill() {
        final ProvenanceRepository repo = this.repository;
        if (repo == null) {
            return false;
        }
        if (repo.isFailed()) {
            this.discardRepository(repo);
            this.state = State.DEGRADED;
            this.scheduleRetry();
            return false;
        }
        final List<ProvenanceSpillJournal.SpillRecord> records;
        try {
            records = this.spillJournal.seizePending();
        } catch (final IOException ex) {
            this.scheduleRetry();
            this.recordError("spill read failed: " + ex.getMessage());
            return false;
        }
        if (records.isEmpty()) {
            try {
                this.spillJournal.ackSeized();
                return true;
            } catch (final IOException ex) {
                this.scheduleRetry();
                this.recordError("spill acknowledgement failed: " + ex.getMessage());
                return false;
            }
        }
        try {
            final List<ProcessedItem> applied = new ArrayList<>(records.size());
            repo.runInTransaction(ignored -> {
                for (final ProvenanceSpillJournal.SpillRecord record : records) {
                    applied.add(this.applySpill(record));
                    if (repo.isFailed()) {
                        throw new IllegalStateException("spill apply failed after SQL error");
                    }
                }
            });
            try {
                this.spillJournal.ackSeized();
            } catch (final IOException ex) {
                this.refreshRecentSnapshots(repo);
                this.scheduleRetry();
                this.recordError("spill acknowledgement failed: " + ex.getMessage());
                return false;
            }
            for (final ProcessedItem item : applied) {
                this.recordCommitted(item);
            }
            return true;
        } catch (final SQLException ex) {
            repo.markFailed();
            this.discardRepository(repo);
            this.state = State.DEGRADED;
            this.scheduleRetry();
            this.recordError("spill replay failed: " + ex.getMessage());
            return false;
        } catch (final Exception ex) {
            if (repo.isFailed()) {
                this.discardRepository(repo);
                this.state = State.DEGRADED;
            }
            this.scheduleRetry();
            this.recordError("spill replay failed: " + ex.getMessage());
            return false;
        }
    }

    private void refreshRecentSnapshots(final @NotNull ProvenanceRepository repo) {
        if (repo.isFailed()) {
            return;
        }
        synchronized (this.recentAudit) {
            this.recentAudit.clear();
            this.recentAudit.addAll(repo.loadRecentAudit(RECENT_AUDIT_CAPACITY));
        }
        synchronized (this.recentCollisions) {
            this.recentCollisions.clear();
            this.recentCollisions.addAll(repo.loadRecentCollisions(RECENT_COLLISION_CAPACITY));
        }
    }

    private @NotNull ProcessedItem applySpill(final ProvenanceSpillJournal.SpillRecord record) throws SQLException {
        final long sequence = record.sequence() == 0L ? ++this.writeSequence : record.sequence();
        this.writeSequence = Math.max(this.writeSequence, sequence);
        final WriteItem item = switch (record) {
            case ProvenanceSpillJournal.SpillRecord.Lineage lineage ->
                new WriteItem.Lineage(sequence, lineage.node());
            case ProvenanceSpillJournal.SpillRecord.Live live ->
                new WriteItem.Live(sequence, live.record());
            case ProvenanceSpillJournal.SpillRecord.Collision collision ->
                new WriteItem.Collision(
                    sequence,
                    collision.record(),
                    collisionDedupeKey(collision.record()),
                    collision.auditEventId(),
                    collision.auditEvent()
                );
            case ProvenanceSpillJournal.SpillRecord.Audit audit ->
                new WriteItem.Audit(sequence, audit.eventId(), audit.event());
        };
        return this.process(item);
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
            + " incomplete-tail=" + w.spillJournal.incompleteTailCount()
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

    private record ProcessedItem(
        @NotNull WriteItem item,
        boolean collisionInserted,
        boolean auditInserted
    ) {
    }

    private sealed interface WriteItem {
        long sequence();

        record Audit(long sequence, @NotNull UUID eventId, @NotNull ProvenanceEvent event) implements WriteItem {
        }

        record Lineage(long sequence, @NotNull LineageNode node) implements WriteItem {
        }

        record Live(long sequence, @NotNull LiveRecord record) implements WriteItem {
        }

        record Collision(
            long sequence,
            @NotNull CollisionRecord record,
            @NotNull String dedupeKey,
            @Nullable UUID auditEventId,
            @Nullable ProvenanceEvent auditEvent
        ) implements WriteItem {
            Collision(final long sequence, final @NotNull CollisionRecord record) {
                this(sequence, record, collisionDedupeKey(record), null, null);
            }
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
