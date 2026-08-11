package dev.mintychochip.provenance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Durable provenance store (SQLite).
 *
 * <p>Holds the permanent lineage graph, collision trail, live census, and audit
 * event log. The live census is durable and seeds runtime {@link LiveIndex} on
 * restart; audit is the permanent event ledger.
 */
public final class ProvenanceRepository implements AutoCloseable {

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_RECENT_ROWS = 16_384;

    private final Connection connection;
    private volatile boolean failed;

    public ProvenanceRepository(final @NotNull java.nio.file.Path dbPath) throws SQLException {
        Objects.requireNonNull(dbPath, "dbPath");
        final java.io.File parent = dbPath.toFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final Connection opened = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.connection = opened;
        try {
            this.applyStartupPragmas();
            this.migrateSchema();
            this.normalizeCollisionKeys();
        } catch (final SQLException ex) {
            try {
                opened.close();
            } catch (final SQLException ignored) {
                ex.addSuppressed(ignored);
            }
            throw ex;
        } catch (final RuntimeException ex) {
            try {
                opened.close();
            } catch (final SQLException ignored) {
                ex.addSuppressed(ignored);
            }
            throw ex;
        }
    }

    public boolean isFailed() {
        return this.failed;
    }

    /** Mark the connection unusable after a writer-observed storage failure. */
    synchronized void markFailed() {
        this.failed = true;
    }

    public synchronized void upsertLineage(final @NotNull LineageNode node, final long sequence) throws SQLException {
        final String sql = """
            INSERT INTO lineage (id, item, source, parents, born, holder, dead, death_reason, death_epoch, updated_seq)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                item = excluded.item,
                source = excluded.source,
                parents = excluded.parents,
                born = excluded.born,
                holder = excluded.holder,
                dead = excluded.dead,
                death_reason = excluded.death_reason,
                death_epoch = excluded.death_epoch,
                updated_seq = excluded.updated_seq
            WHERE excluded.updated_seq > lineage.updated_seq
            """;
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, node.id().toString());
            ps.setString(2, node.itemId());
            ps.setString(3, node.source().name());
            ps.setString(4, node.parents().stream().map(UUID::toString).collect(Collectors.joining(",")));
            ps.setLong(5, node.bornEpochMs());
            ps.setString(6, node.bornHolder());
            ps.setInt(7, node.dead() ? 1 : 0);
            ps.setString(8, node.dead() ? node.deathReason().name() : null);
            ps.setLong(9, node.dead() ? node.deathEpochMs() : 0L);
            ps.setLong(10, sequence);
            ps.executeUpdate();
        }
    }

    public synchronized @NotNull Optional<LineageNode> loadLineage(final @NotNull UUID id) {
        if (this.failed) {
            return Optional.empty();
        }
        try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT item, source, parents, born, holder, dead, death_reason, death_epoch FROM lineage WHERE id = ?"
        )) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(toNode(id, rs));
            }
        } catch (final SQLException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("lineage load", ex);
            return Optional.empty();
        }
    }

    public synchronized boolean insertCollision(
        final @NotNull CollisionRecord record,
        final @NotNull String dedupeKey
    ) throws SQLException {
        try (PreparedStatement ps = this.connection.prepareStatement(
            "INSERT OR IGNORE INTO collisions (id, kind, existing, observed, epoch, dedupe_key) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, record.id().toString());
            ps.setString(2, record.kind().name());
            ps.setString(3, record.existingLocation().display());
            ps.setString(4, record.observedLocation().display());
            ps.setLong(5, record.epochMs());
            ps.setString(6, dedupeKey);
            return ps.executeUpdate() == 1;
        }
    }

    public synchronized @NotNull List<CollisionRecord> loadRecentCollisions(final int limit) {
        if (this.failed) {
            return List.of();
        }
        final List<CollisionRecord> out = new ArrayList<>();
        try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT id, kind, existing, observed, epoch FROM collisions ORDER BY epoch DESC LIMIT ?"
        )) {
            ps.setInt(1, Math.max(1, Math.min(limit, MAX_RECENT_ROWS)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CollisionRecord(
                        UUID.fromString(rs.getString("id")),
                        ProvenanceCollisionKind.valueOf(rs.getString("kind")),
                        parseLocationDisplay(rs.getString("existing")),
                        parseLocationDisplay(rs.getString("observed")),
                        rs.getLong("epoch")
                    ));
                }
            }
        } catch (final SQLException | IllegalArgumentException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("collision load", ex);
        }
        return out;
    }

    public synchronized void upsertLive(final @NotNull LiveRecord record, final long sequence) throws SQLException {
        final String sql = """
            INSERT INTO live (id, item, location, count, epoch, updated_seq, dead)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                item = excluded.item,
                location = excluded.location,
                count = excluded.count,
                epoch = excluded.epoch,
                updated_seq = excluded.updated_seq,
                dead = excluded.dead
            WHERE excluded.updated_seq > live.updated_seq
            """;
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, record.id().toString());
            ps.setString(2, record.itemId());
            ps.setString(3, record.locationDisplay());
            ps.setInt(4, record.count());
            ps.setLong(5, record.epochMs());
            ps.setLong(6, sequence);
            ps.setInt(7, record.dead() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public synchronized @NotNull List<LiveRecord> loadAliveLive() {
        if (this.failed) {
            return List.of();
        }
        final List<LiveRecord> out = new ArrayList<>();
        try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT id, item, location, count, epoch, dead FROM live WHERE dead = 0"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LiveRecord(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("item"),
                        rs.getString("location"),
                        rs.getInt("count"),
                        rs.getLong("epoch"),
                        rs.getInt("dead") != 0
                    ));
                }
            }
        } catch (final SQLException | IllegalArgumentException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("live load", ex);
        }
        return out;
    }

    public synchronized boolean insertAudit(
        final @NotNull UUID eventId,
        final @NotNull ProvenanceEvent event
    ) throws SQLException {
        try (PreparedStatement ps = this.connection.prepareStatement(
            "INSERT OR IGNORE INTO audit (epoch, kind, id, item, source, reason, related, holder, detail, event_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setLong(1, event.epochMs());
            ps.setString(2, event.type().name());
            ps.setString(3, event.id().toString());
            ps.setString(4, event.itemId());
            ps.setString(5, event.source() != null ? event.source().name() : null);
            ps.setString(6, event.reason() != null ? event.reason().name() : null);
            ps.setString(7, event.related().isEmpty()
                ? null
                : event.related().stream().map(UUID::toString).collect(Collectors.joining(",")));
            ps.setString(8, event.holder());
            ps.setString(9, event.detail());
            ps.setString(10, eventId.toString());
            return ps.executeUpdate() == 1;
        }
    }

    public synchronized @NotNull List<ProvenanceEvent> loadRecentAudit(final int limit) {
        if (this.failed) {
            return List.of();
        }
        final List<ProvenanceEvent> out = new ArrayList<>();
        try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT epoch, kind, id, item, source, reason, related, holder, detail FROM audit ORDER BY seq DESC LIMIT ?"
        )) {
            ps.setInt(1, Math.max(1, Math.min(limit, MAX_RECENT_ROWS)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toEvent(rs));
                }
            }
        } catch (final SQLException | IllegalArgumentException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("audit load", ex);
        }
        return out;
    }

    /** Return the current user_version, or zero when the read fails. */
    public synchronized int schemaVersion() {
        if (this.failed) {
            return 0;
        }
        try {
            return this.queryUserVersion();
        } catch (final SQLException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("schema version load", ex);
            return 0;
        }
    }

    /** Return the highest durable lineage/live write sequence, or zero when empty. */
    public synchronized long maxWriteSequence() {
        if (this.failed) {
            return 0L;
        }
        try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT MAX(updated_seq) FROM (SELECT updated_seq FROM lineage UNION ALL SELECT updated_seq FROM live)"
        ); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (final SQLException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("max write sequence load", ex);
            return 0L;
        }
    }

    /** Row count for tests / ops (`SELECT COUNT(*) FROM lineage`). */
    public synchronized long countLineage() {
        if (this.failed) {
            return 0L;
        }
        try (PreparedStatement ps = this.connection.prepareStatement("SELECT COUNT(*) FROM lineage");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (final SQLException ex) {
            this.failed = true;
            ProvenanceWriter.reportStorageError("lineage count", ex);
            return 0L;
        }
    }

    @FunctionalInterface
    public interface RepositoryWork {
        void accept(@NotNull ProvenanceRepository repository) throws SQLException;
    }

    /**
     * Run work inside a single SQLite transaction (writer-thread batching).
     * Nested calls reuse the outer transaction.
     */
    public synchronized void runInTransaction(final @NotNull RepositoryWork work) throws SQLException {
        Objects.requireNonNull(work, "work");
        if (!this.connection.getAutoCommit()) {
            work.accept(this);
            return;
        }
        this.connection.setAutoCommit(false);
        Throwable failure = null;
        try {
            work.accept(this);
            this.connection.commit();
        } catch (final SQLException | RuntimeException | Error ex) {
            failure = ex;
            try {
                this.connection.rollback();
            } catch (final SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
        } finally {
            try {
                this.connection.setAutoCommit(true);
            } catch (final SQLException restoreFailure) {
                this.failed = true;
                if (failure == null) {
                    failure = restoreFailure;
                } else {
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
        if (failure instanceof SQLException ex) {
            throw ex;
        }
        if (failure instanceof RuntimeException ex) {
            throw ex;
        }
        if (failure instanceof Error ex) {
            throw ex;
        }
    }

    private void applyStartupPragmas() throws SQLException {
        try (Statement st = this.connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void migrateSchema() throws SQLException {
        final int version = this.queryUserVersion();
        if (version >= SCHEMA_VERSION) {
            return;
        }
        this.connection.setAutoCommit(false);
        Throwable failure = null;
        try {
            if (version < 1) {
                this.createSchemaV1();
            }
            if (version < 2) {
                this.migrateToV2();
            }
            this.setUserVersion(SCHEMA_VERSION);
            this.connection.commit();
        } catch (final SQLException | RuntimeException | Error ex) {
            failure = ex;
            try {
                this.connection.rollback();
            } catch (final SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }

        } finally {
            try {
                this.connection.setAutoCommit(true);
            } catch (final SQLException restoreFailure) {
                if (failure == null) {
                    failure = restoreFailure;
                } else {
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
        if (failure instanceof SQLException ex) {
            throw ex;
        }
        if (failure instanceof RuntimeException ex) {
            throw ex;
        }
        if (failure instanceof Error ex) {
            throw ex;
        }
    }
    private void normalizeCollisionKeys() throws SQLException {
        if (this.queryUserVersion() < 2) {
            return;
        }
        this.runInTransaction(repository -> {
            try (Statement statement = this.connection.createStatement()) {
                statement.executeUpdate("""
                    DELETE FROM collisions
                    WHERE rowid NOT IN (
                        SELECT MIN(rowid)
                        FROM collisions
                        GROUP BY id, kind, existing, observed
                    )
                    """);
                statement.executeUpdate("""
                    UPDATE collisions
                    SET dedupe_key = id || '|' || kind || '|' || existing || '|' || observed
                    """);
            }
        });
    }

    private void createSchemaV1() throws SQLException {
        try (Statement st = this.connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS lineage (
                    id TEXT PRIMARY KEY,
                    item TEXT NOT NULL,
                    source TEXT NOT NULL,
                    parents TEXT NOT NULL,
                    born INTEGER NOT NULL,
                    holder TEXT,
                    dead INTEGER NOT NULL DEFAULT 0,
                    death_reason TEXT,
                    death_epoch INTEGER
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS collisions (
                    id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    existing TEXT NOT NULL,
                    observed TEXT NOT NULL,
                    epoch INTEGER NOT NULL
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_collisions_id ON collisions(id)");
            st.execute("""
                CREATE TABLE IF NOT EXISTS live (
                    id TEXT PRIMARY KEY,
                    item TEXT NOT NULL,
                    location TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    epoch INTEGER NOT NULL,
                    dead INTEGER NOT NULL DEFAULT 0
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    epoch INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    id TEXT NOT NULL,
                    item TEXT,
                    source TEXT,
                    reason TEXT,
                    related TEXT,
                    holder TEXT,
                    detail TEXT
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_epoch ON audit(epoch)");
        }
    }

    private void migrateToV2() throws SQLException {
        try (Statement st = this.connection.createStatement()) {
            st.execute("ALTER TABLE lineage ADD COLUMN updated_seq INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE live ADD COLUMN updated_seq INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE collisions ADD COLUMN dedupe_key TEXT NOT NULL DEFAULT ''");
            st.execute("ALTER TABLE audit ADD COLUMN event_id TEXT NOT NULL DEFAULT ''");
            st.execute("UPDATE lineage SET updated_seq = rowid");
            st.execute("UPDATE live SET updated_seq = rowid");
            st.execute("UPDATE collisions SET dedupe_key = 'legacy:' || rowid");
            st.execute("UPDATE audit SET event_id = 'legacy:' || seq");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_collisions_dedupe_key ON collisions(dedupe_key)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_event_id ON audit(event_id)");
        }
    }

    private int queryUserVersion() throws SQLException {
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setUserVersion(final int version) throws SQLException {
        try (Statement st = this.connection.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        }
    }

    private static @NotNull ProvenanceEvent toEvent(final ResultSet rs) throws SQLException {
        final String relatedRaw = rs.getString("related");
        final List<UUID> related = new ArrayList<>();
        if (relatedRaw != null && !relatedRaw.isEmpty()) {
            for (final String part : relatedRaw.split(",")) {
                try {
                    related.add(UUID.fromString(part));
                } catch (final IllegalArgumentException ignored) {
                    // skip bad related id
                }
            }
        }
        final String sourceRaw = rs.getString("source");
        final String reasonRaw = rs.getString("reason");
        return new ProvenanceEvent(
            rs.getLong("epoch"),
            ProvenanceEventType.valueOf(rs.getString("kind")),
            UUID.fromString(rs.getString("id")),
            rs.getString("item"),
            sourceRaw != null ? ProvenanceSource.valueOf(sourceRaw) : null,
            reasonRaw != null ? ProvenanceReason.valueOf(reasonRaw) : null,
            List.copyOf(related),
            rs.getString("holder"),
            rs.getString("detail")
        );
    }

    private static @NotNull LineageNode toNode(final UUID id, final ResultSet rs) throws SQLException {
        final String parentsRaw = rs.getString("parents");
        final List<UUID> parents = new ArrayList<>();
        if (parentsRaw != null && !parentsRaw.isEmpty()) {
            for (final String part : parentsRaw.split(",")) {
                try {
                    parents.add(UUID.fromString(part));
                } catch (final IllegalArgumentException ignored) {
                    // skip bad parent
                }
            }
        }
        final boolean dead = rs.getInt("dead") != 0;
        final LineageNode node = new LineageNode(
            id,
            rs.getString("item"),
            ProvenanceSource.valueOf(rs.getString("source")),
            List.copyOf(parents),
            rs.getLong("born"),
            rs.getString("holder")
        );
        if (dead) {
            node.markDead(ProvenanceReason.valueOf(rs.getString("death_reason")), rs.getLong("death_epoch"));
        }
        return node;
    }

    /** Parse a stored location display string back into a {@link StackLocation}. */
    static @NotNull StackLocation parseLocationDisplay(final String raw) {
        if (raw == null) {
            return StackLocation.unknown();
        }
        if (raw.startsWith("player:") && raw.indexOf(':', 7) > 7) {
            final int sep = raw.indexOf(':', 7);
            try {
                return StackLocation.playerSlot(UUID.fromString(raw.substring(7, sep)), Integer.parseInt(raw.substring(sep + 1)));
            } catch (final IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (raw.startsWith("item_entity:")) {
            try {
                return StackLocation.itemEntity(UUID.fromString(raw.substring("item_entity:".length())));
            } catch (final IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (raw.equals("unknown")) {
            return StackLocation.unknown();
        }
        return StackLocation.labeled(raw);
    }

    private void exReport(final String context, final SQLException ex) {
        this.failed = true;
        ProvenanceWriter.reportStorageError(context, ex);
    }

    @Override
    public synchronized void close() {
        try {
            this.connection.close();
        } catch (final SQLException ignored) {
            // already closing
        }
    }
}
