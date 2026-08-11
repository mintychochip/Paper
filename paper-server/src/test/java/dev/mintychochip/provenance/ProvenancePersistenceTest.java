package dev.mintychochip.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * Durable store contracts: restart lineage survival, collision persistence,
 * bounded writer behavior.
 */
@Normal
public class ProvenancePersistenceTest {

    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final StackLocation HAND = StackLocation.playerSlot(PLAYER, 0);

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        ItemProvenance.setEnabled(true);
        ItemProvenance.clearAll();
        ProvenanceWriter.clearInstall();
    }

    @AfterEach
    public void tearDown() {
        ProvenanceWriter.clearInstall();
        ItemProvenance.clearAll();
        ItemProvenance.setEnabled(true);
    }

    @Test
    public void restartKeepsAncestorHistory() {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack parent = new ItemStack(Items.IRON_ORE, 1);
        final UUID parentId = ItemProvenance.birth(parent, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
        final ItemStack child = new ItemStack(Items.IRON_INGOT, 1);
        ItemProvenance.onSmelted(child, parentId, StackLocation.labeled("furnace:0,64,0"));
        final UUID childId = StackStamp.readId(child).orElseThrow();
        final ItemStack persistedChild = child.copy();

        // Ensure durable writes land before simulated restart (async writer).
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        // Simulated restart: runtime state wiped, durable store stays.
        ItemProvenance.clearAll();
        ItemProvenance.lineage().clearCache();
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        ItemProvenance.rehydrate(persistedChild, HAND);

        assertTrue(
            ItemProvenance.explain(childId).stream().anyMatch(node -> node.id().equals(parentId)),
            "restart rehydration must load durable ancestry"
        );
        assertEquals(List.of(parentId), StackStamp.read(persistedChild).orElseThrow().parents());
    }

    @Test
    public void mergeSourceAndParentsPersistAndReload() throws Exception {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack target = new ItemStack(Items.COBBLESTONE, 40);
        final ItemStack source = new ItemStack(Items.COBBLESTONE, 24);
        final UUID targetId = ItemProvenance.birth(target, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
        final UUID sourceId = ItemProvenance.birth(source, ProvenanceSource.BLOCK_DROP, StackLocation.labeled("chest:test")).orElseThrow();
        final Optional<UUID> targetIdBefore = StackStamp.readId(target);
        final Optional<UUID> sourceIdBefore = StackStamp.readId(source);
        target.grow(source.getCount());
        source.setCount(0);

        assertFalse(ItemProvenance.afterContainerMerge(
            target,
            source,
            targetIdBefore,
            sourceIdBefore,
            24,
            StackLocation.labeled("chest:test"),
            HAND
        ));
        final UUID mergedId = StackStamp.readId(target).orElseThrow();

        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        try (ProvenanceRepository repository = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"))) {
            final LineageNode loaded = repository.loadLineage(mergedId).orElseThrow();
            assertEquals(ProvenanceSource.MERGE, loaded.source());
            assertEquals(List.of(targetId, sourceId), loaded.parents());
        }
    }

    @Test
    public void collisionIsPersistedAndReloadable() {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack original = new ItemStack(Items.DIAMOND, 4);
        ItemProvenance.birth(original, ProvenanceSource.LOOT, HAND).orElseThrow();
        final ItemStack duplicate = original.copy();

        assertTrue(ItemProvenance.observe(duplicate, StackLocation.playerSlot(PLAYER, 1)));
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        final ProvenanceRepository repo;
        try {
            repo = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"));
        } catch (final Exception ex) {
            throw new AssertionError("cannot reopen repository", ex);
        }
        final List<CollisionRecord> loaded = repo.loadRecentCollisions(10);
        assertFalse(loaded.isEmpty());
        assertEquals(ProvenanceCollisionKind.DUPLICATE_LOCATION, loaded.getFirst().kind());
        assertEquals(HAND, loaded.getFirst().existingLocation());
        repo.close();
    }

    @Test
    public void auditLinesAreWrittenAndValidJsonl() throws Exception {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack stack = new ItemStack(Items.COBBLESTONE, 1);
        ItemProvenance.birth(stack, ProvenanceSource.BLOCK_DROP, HAND);
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        final Path audit = tempDir.resolve("mintychochip/provenance-audit.jsonl");
        assertTrue(Files.exists(audit), "audit file must exist after flush");
        final String content = Files.readString(audit);
        assertTrue(content.contains("\"type\":\"BIRTH\""), "audit must contain the birth event");
        assertTrue(content.contains("\"id\":\"" + StackStamp.readId(stack).orElseThrow() + "\""));
        // Every line parses as JSON.
        for (final String line : content.split("\n")) {
            if (!line.isBlank()) {
                assertTrue(isJson(line), "malformed JSONL line: " + line);
            }
        }
    }

    private static boolean isJson(final String line) {
        final String trimmed = line.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.indexOf('"') == 1;
    }

    @Test
    public void writerWithoutInstallIsNoOp() {
        // No install: events must not block or throw.
        final ItemStack stack = new ItemStack(Items.COBBLESTONE, 1);
        ItemProvenance.birth(stack, ProvenanceSource.BLOCK_DROP, HAND);
        assertTrue(ItemProvenance.live().contains(StackStamp.readId(stack).orElseThrow()));
    }

    @Test
    public void liveUpsertAndLoadAliveSurvivesReopen() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        try (ProvenanceRepository repo = new ProvenanceRepository(db)) {
            repo.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:" + PLAYER + ":0", 4, 1_700_000_000_000L, false), 1L);
        }
        try (ProvenanceRepository repo = new ProvenanceRepository(db)) {
            final List<LiveRecord> alive = repo.loadAliveLive();
            assertEquals(1, alive.size());
            assertEquals(id, alive.getFirst().id());
            assertEquals(4, alive.getFirst().count());
            assertFalse(alive.getFirst().dead());
        }
    }

    @Test
    public void auditInsertAndLoadRecentSurvivesReopen() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        final ProvenanceEvent event = new ProvenanceEvent(
            1_700_000_000_000L,
            ProvenanceEventType.BIRTH,
            id,
            "minecraft:cobblestone",
            ProvenanceSource.BLOCK_DROP,
            null,
            List.of(),
            HAND.display(),
            null
        );
        try (ProvenanceRepository repo = new ProvenanceRepository(db)) {
            repo.insertAudit(UUID.randomUUID(), event);
        }
        try (ProvenanceRepository repo = new ProvenanceRepository(db)) {
            final List<ProvenanceEvent> loaded = repo.loadRecentAudit(10);
            assertEquals(1, loaded.size());
            assertEquals(ProvenanceEventType.BIRTH, loaded.getFirst().type());
            assertEquals(id, loaded.getFirst().id());
        }
    }

    @Test
    public void criticalWritesNeverDropUnderQueuePressure() throws Exception {
        // Tiny capacity forces spill path for critical lineage writes.
        ProvenanceWriter.installForTest(tempDir.resolve("mintychochip"), message -> {
        }, 4);
        final int n = 200;
        for (int i = 0; i < n; i++) {
            final ItemStack s = new ItemStack(Items.COBBLESTONE, 1);
            ItemProvenance.birth(s, ProvenanceSource.BLOCK_DROP, HAND);
        }
        ProvenanceWriter.flushAndClose();
        // Assert status before clearInstall so counters are still readable.
        final String status = ProvenanceWriter.status();
        assertFalse(status.contains("queue-dropped="),
            "status must not report critical queue drops: " + status);
        // audit-dropped is acceptable under pressure; critical must still land.
        ProvenanceWriter.clearInstall();

        try (ProvenanceRepository repo = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"))) {
            assertTrue(repo.countLineage() >= n, "all births must land in lineage, got " + repo.countLineage());
        }
    }

    @Test
    public void spillReplayRecoversAfterSimulatedCrash() throws Exception {
        final Path minty = tempDir.resolve("mintychochip");
        Files.createDirectories(minty);
        final Path spill = minty.resolve("provenance-spill.log");
        final Path replay = minty.resolve("provenance-spill.log.replay");
        final UUID id = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");

        // Write a lineage record into the active spill, then simulate a crash
        // mid-replay: content seized to .replay but never acked.
        final ProvenanceSpillJournal journal = new ProvenanceSpillJournal(spill);
        journal.appendLineage(new LineageNode(
            id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 100L, "hand"
        ));
        Files.move(spill, replay);
        assertTrue(Files.isRegularFile(replay), "simulated crash must leave unacked .replay");
        assertTrue(Files.notExists(spill) || !Files.isRegularFile(spill));

        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        assertTrue(Files.notExists(replay), ".replay must be acked after successful apply");
        try (ProvenanceRepository repo = new ProvenanceRepository(minty.resolve("provenance.db"))) {
            final Optional<LineageNode> loaded = repo.loadLineage(id);
            assertTrue(loaded.isPresent(), "seized spill must land in lineage after writer recovery");
            assertEquals(id, loaded.get().id());
            assertEquals("minecraft:stone", loaded.get().itemId());
            assertEquals(ProvenanceSource.BLOCK_DROP, loaded.get().source());
        }
    }

    @Test
    public void auditIsInSqliteAfterFlush() throws Exception {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack stack = new ItemStack(Items.COBBLESTONE, 1);
        final UUID id = ItemProvenance.birth(stack, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        try (ProvenanceRepository repo = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"))) {
            final List<ProvenanceEvent> events = repo.loadRecentAudit(20);
            assertTrue(
                events.stream().anyMatch(e -> e.id().equals(id) && e.type() == ProvenanceEventType.BIRTH),
                "birth audit must be in SQLite after flush"
            );
        }
    }

    @Test
    public void durableLiveSeedsCensusAndDetectsSecondLocation() {
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });
        final ItemStack original = new ItemStack(Items.DIAMOND, 1);
        final UUID id = ItemProvenance.birth(original, ProvenanceSource.LOOT, HAND).orElseThrow();
        ProvenanceWriter.flushAndClose();
        ItemProvenance.clearAll();
        ProvenanceWriter.clearInstall();
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });

        assertTrue(ItemProvenance.live().contains(id), "live must be seeded from DB");

        final ItemStack duplicate = original.copy();
        assertTrue(
            ItemProvenance.observe(duplicate, StackLocation.playerSlot(PLAYER, 1)),
            "second concrete location after restart must COLLISION"
        );
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
    }

    /**
     * Crash mid-spill: DB still has stale live row, unacked spill has newer location.
     * Install must replay spill synchronously before seeding LiveIndex so census is not stale.
     */
    @Test
    public void liveSeedUsesPostReplaySpillLocationNotStaleDb() throws Exception {
        final Path minty = tempDir.resolve("mintychochip");
        Files.createDirectories(minty);
        final UUID id = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000001");
        final String staleLoc = HAND.display();
        final StackLocation spillLocation = StackLocation.playerSlot(PLAYER, 5);
        final String spillLoc = spillLocation.display();

        try (ProvenanceRepository repo = new ProvenanceRepository(minty.resolve("provenance.db"))) {
            repo.upsertLive(new LiveRecord(id, "minecraft:diamond", staleLoc, 1, 100L, false), 1L);
        }

        final ProvenanceSpillJournal journal = new ProvenanceSpillJournal(minty.resolve("provenance-spill.log"));
        journal.appendLive(new LiveRecord(id, "minecraft:diamond", spillLoc, 2, 200L, false));

        // Wipe runtime census, then install: must sync-replay spill then seed.
        ItemProvenance.clearAll();
        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {
        });

        final LiveEntry seeded = ItemProvenance.live().get(id).orElseThrow(
            () -> new AssertionError("live must be seeded after install")
        );
        assertEquals(spillLoc, seeded.location().display(),
            "LiveIndex must reflect post-spill-replay location, not stale DB row");
        assertEquals(2, seeded.count(), "LiveIndex must reflect post-spill-replay count");

        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();

        // Durable store must also hold the replayed row.
        try (ProvenanceRepository repo = new ProvenanceRepository(minty.resolve("provenance.db"))) {
            final List<LiveRecord> alive = repo.loadAliveLive();
            assertEquals(1, alive.size());
            assertEquals(spillLoc, alive.getFirst().locationDisplay());
            assertEquals(2, alive.getFirst().count());
        }
    }

    @Test
    public void freshRepositoryReportsSchemaVersionTwo() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            assertEquals(2, repository.schemaVersion());
            assertEquals(0L, repository.maxWriteSequence());
        }
    }

    @Test
    public void reopeningMigratesExistingSchemaAndPreservesRows() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        Files.createDirectories(db.getParent());
        final UUID legacyId = UUID.fromString("deadbeef-0000-0000-0000-000000000001");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lineage (id TEXT PRIMARY KEY, item TEXT NOT NULL, source TEXT NOT NULL, parents TEXT NOT NULL, born INTEGER NOT NULL, holder TEXT, dead INTEGER NOT NULL DEFAULT 0, death_reason TEXT, death_epoch INTEGER)");
            statement.execute("CREATE TABLE live (id TEXT PRIMARY KEY, item TEXT NOT NULL, location TEXT NOT NULL, count INTEGER NOT NULL, epoch INTEGER NOT NULL, dead INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE collisions (id TEXT NOT NULL, kind TEXT NOT NULL, existing TEXT NOT NULL, observed TEXT NOT NULL, epoch INTEGER NOT NULL)");
            statement.execute("CREATE TABLE audit (seq INTEGER PRIMARY KEY AUTOINCREMENT, epoch INTEGER NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, item TEXT, source TEXT, reason TEXT, related TEXT, holder TEXT, detail TEXT)");
            statement.execute("INSERT INTO lineage (id, item, source, parents, born, holder) VALUES ('" + legacyId + "', 'minecraft:stone', 'LEGACY', '', 10, 'hand')");
            statement.execute("INSERT INTO live (id, item, location, count, epoch, dead) VALUES ('" + legacyId + "', 'minecraft:diamond', 'player:" + PLAYER + ":0', 4, 100, 0)");
            statement.execute("INSERT INTO collisions (id, kind, existing, observed, epoch) VALUES ('" + legacyId + "', 'DUPLICATE_LOCATION', 'hand', 'player:" + PLAYER + ":1', 100)");
        }
        final UUID id = UUID.randomUUID();
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            assertEquals(2, repository.schemaVersion());
            repository.upsertLineage(new LineageNode(id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 10L, "hand"), 1L);
        }
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            assertEquals(2, repository.schemaVersion());
            final LineageNode legacy = repository.loadLineage(legacyId).orElseThrow();
            assertEquals("minecraft:stone", legacy.itemId());
            assertEquals(ProvenanceSource.LEGACY, legacy.source());
            final LiveRecord legacyLive = repository.loadAliveLive().getFirst();
            assertEquals(legacyId, legacyLive.id());
            assertEquals(4, legacyLive.count());
            final CollisionRecord legacyCollision = new CollisionRecord(
                legacyId,
                ProvenanceCollisionKind.DUPLICATE_LOCATION,
                StackLocation.labeled("hand"),
                StackLocation.playerSlot(PLAYER, 1),
                100L
            );
            assertFalse(repository.insertCollision(
                legacyCollision,
                legacyId + "|DUPLICATE_LOCATION|hand|player:" + PLAYER + ":1"
            ));
            assertEquals(1, repository.loadRecentCollisions(10).size());
            assertTrue(repository.maxWriteSequence() >= 1L);
        }
    }

    @Test
    public void olderLiveRevisionCannotOverwriteNewerRevision() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:" + PLAYER + ":1", 1, 100L, false), 20L);
            repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:" + PLAYER + ":2", 4, 200L, false), 21L);
            repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:" + PLAYER + ":1", 1, 100L, false), 20L);
            final LiveRecord loaded = repository.loadAliveLive().getFirst();
            assertEquals("player:" + PLAYER + ":2", loaded.locationDisplay());
            assertEquals(4, loaded.count());
            assertEquals(21L, repository.maxWriteSequence());
        }
    }

    @Test
    public void olderLineageRevisionCannotOverwriteNewerRevision() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            repository.upsertLineage(new LineageNode(id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 10L, "hand"), 5L);
            final LineageNode newer = new LineageNode(id, "minecraft:cobblestone", ProvenanceSource.CRAFT, List.of(), 20L, "crafting");
            newer.markDead(ProvenanceReason.CONSUMED, 30L);
            repository.upsertLineage(newer, 6L);
            repository.upsertLineage(new LineageNode(id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 10L, "hand"), 5L);
            final LineageNode loaded = repository.loadLineage(id).orElseThrow();
            assertEquals("minecraft:cobblestone", loaded.itemId());
            assertTrue(loaded.dead());
            assertEquals(ProvenanceReason.CONSUMED, loaded.deathReason());
            assertEquals(6L, repository.maxWriteSequence());
        }
    }

    @Test
    public void duplicateCollisionAndAuditIdentifiersAreIdempotent() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        final CollisionRecord collision = new CollisionRecord(
            id,
            ProvenanceCollisionKind.DUPLICATE_LOCATION,
            HAND,
            StackLocation.playerSlot(PLAYER, 1),
            100L
        );
        final ProvenanceEvent event = new ProvenanceEvent(
            100L,
            ProvenanceEventType.COLLISION,
            id,
            "minecraft:diamond",
            null,
            null,
            List.of(),
            HAND.display(),
            "duplicate"
        );
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            assertTrue(repository.insertCollision(collision, "collision-key"));
            assertFalse(repository.insertCollision(collision, "collision-key"));
            final UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            repository.insertAudit(eventId, event);
            repository.insertAudit(eventId, event);
            assertEquals(1, repository.loadRecentCollisions(10).size());
            assertEquals(1, repository.loadRecentAudit(10).size());
        }
    }

    @Test
    public void recentReadsRespectRequestedBounds() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            for (int i = 0; i < 5; i++) {
                repository.insertAudit(UUID.randomUUID(), new ProvenanceEvent(
                    100L + i,
                    ProvenanceEventType.BIRTH,
                    UUID.randomUUID(),
                    "minecraft:diamond",
                    null,
                    null,
                    List.of(),
                    HAND.display(),
                    null
                ));
            }
            assertEquals(3, repository.loadRecentAudit(3).size());
            assertEquals(1, repository.loadRecentAudit(0).size());
            assertEquals(5, repository.loadRecentAudit(50_000).size());
        }
    }

    @Test
    public void recentAuditSupportsWriterSnapshotCapacity() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            repository.runInTransaction(r -> {
                for (int i = 0; i < 16_385; i++) {
                    r.insertAudit(UUID.randomUUID(), new ProvenanceEvent(
                        100L + i,
                        ProvenanceEventType.BIRTH,
                        UUID.randomUUID(),
                        "minecraft:diamond",
                        null,
                        null,
                        List.of(),
                        HAND.display(),
                        null
                    ));
                }
            });
            assertEquals(16_384, repository.loadRecentAudit(16_384).size());
        }
    }

    @Test
    public void transactionFailureRollsBackRestoresAutocommitAndPropagatesOriginal() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            final SQLException original = assertThrows(SQLException.class, () ->
                repository.runInTransaction(r -> {
                    r.upsertLive(new LiveRecord(UUID.randomUUID(), "minecraft:diamond", HAND.display(), 1, 100L, false), 1L);
                    throw new SQLException("boom");
                })
            );
            assertEquals("boom", original.getMessage());
            assertTrue(repository.loadAliveLive().isEmpty());
            final UUID id = UUID.randomUUID();
            repository.upsertLive(new LiveRecord(id, "minecraft:diamond", HAND.display(), 4, 100L, false), 2L);
            assertEquals(1, repository.loadAliveLive().size());
        }
    }
    @Test
    public void repositoryFailureLeavesCriticalBatchForReplay() throws Exception {
        ProvenanceWriter.installForTest(tempDir.resolve("mintychochip"), message -> {}, 1);
        final ItemStack first = new ItemStack(Items.DIAMOND, 1);
        final UUID firstId = ItemProvenance.birth(first, ProvenanceSource.LOOT, HAND).orElseThrow();
        ProvenanceWriter.failRepositoryForTest();
        final ItemStack second = new ItemStack(Items.DIAMOND, 1);
        final UUID secondId = ItemProvenance.birth(second, ProvenanceSource.LOOT, HAND).orElseThrow();
        assertTrue(ProvenanceWriter.status().contains("state=degraded"));
        assertTrue(ProvenanceWriter.status().contains("critical-pending="));
        ProvenanceWriter.clearInstall();

        ProvenanceWriter.install(tempDir.resolve("mintychochip"), message -> {});
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        try (ProvenanceRepository repository = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"))) {
            assertTrue(repository.loadLineage(firstId).isPresent());
            assertTrue(repository.loadLineage(secondId).isPresent());
        }
    }

    @Test
    public void partialSplitPersistsParentRemainingCount() throws Exception {
        final Path root = tempDir.resolve("mintychochip");
        ProvenanceWriter.install(root, message -> {});
        final ItemStack parent = new ItemStack(Items.COBBLESTONE, 8);
        final UUID parentId = ItemProvenance.birth(parent, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
        final ItemStack child = parent.copyWithCount(3);
        parent.setCount(5);
        ItemProvenance.onSplit(parent, child);
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        ItemProvenance.clearAll();
        ProvenanceWriter.install(root, message -> {});

        assertEquals(5, ItemProvenance.live().get(parentId).orElseThrow().count());
        ProvenanceWriter.clearInstall();
    }

    @Test
    public void consumedStackCountPersistsBeforeRestart() throws Exception {
        final Path root = tempDir.resolve("mintychochip");
        ProvenanceWriter.install(root, message -> {});
        final ItemStack stack = new ItemStack(Items.BREAD, 5);
        final UUID id = ItemProvenance.birth(stack, ProvenanceSource.LOOT, HAND).orElseThrow();
        stack.setCount(2);
        ItemProvenance.noteConsumed(stack);
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        ItemProvenance.clearAll();
        ProvenanceWriter.install(root, message -> {});

        assertEquals(2, ItemProvenance.live().get(id).orElseThrow().count());
        ProvenanceWriter.clearInstall();
    }

    @Test
    public void staleLowerSequenceLiveUpdateCannotReplaceNewerRevision() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            repository.upsertLive(new LiveRecord(id, "minecraft:stone", "player:p:9", 9, 900L, false), 90L);
            repository.upsertLive(new LiveRecord(id, "minecraft:stone", "player:p:1", 1, 100L, false), 10L);
            final LiveRecord loaded = repository.loadAliveLive().getFirst();
            assertEquals("player:p:9", loaded.locationDisplay());
            assertEquals(9, loaded.count());
        }
    }

    @Test
    public void collisionSpillKeepsAuditPairedInOneFrame() throws Exception {
        final Path root = tempDir.resolve("mintychochip");
        final ItemStack original = new ItemStack(Items.DIAMOND, 1);
        final UUID id = ItemProvenance.birth(original, ProvenanceSource.LOOT, HAND).orElseThrow();
        ProvenanceWriter.installForTest(root, message -> {}, 1);
        ProvenanceWriter.flushAndClose();

        assertTrue(ItemProvenance.observe(original.copy(), StackLocation.playerSlot(PLAYER, 1)));
        final ProvenanceSpillJournal journal = new ProvenanceSpillJournal(root.resolve("provenance-spill.log"));
        final List<ProvenanceSpillJournal.SpillRecord> records = journal.readAll();
        assertEquals(1, records.size());
        final ProvenanceSpillJournal.SpillRecord.Collision spill =
            assertInstanceOf(ProvenanceSpillJournal.SpillRecord.Collision.class, records.getFirst());
        assertEquals(id, spill.record().id());
        assertNotNull(spill.auditEventId());
        assertNotNull(spill.auditEvent());

        ProvenanceWriter.clearInstall();
        ItemProvenance.clearAll();
        ProvenanceWriter.install(root, message -> {});
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        try (ProvenanceRepository repository = new ProvenanceRepository(root.resolve("provenance.db"))) {
            assertEquals(1, repository.loadRecentCollisions(10).size());
            assertEquals(1, repository.loadRecentAudit(10).stream()
                .filter(event -> event.type() == ProvenanceEventType.COLLISION && event.id().equals(id))
                .count());
        }
    }

    @Test
    public void collisionsReloadIntoDurableRecentSnapshotAfterRestart() throws Exception {
        final Path root = tempDir.resolve("mintychochip");
        ProvenanceWriter.install(root, message -> {
        });
        final ItemStack original = new ItemStack(Items.DIAMOND, 1);
        final UUID id = ItemProvenance.birth(original, ProvenanceSource.LOOT, HAND).orElseThrow();
        assertTrue(ItemProvenance.observe(original.copy(), StackLocation.playerSlot(PLAYER, 1)));
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        ItemProvenance.clearAll();

        ProvenanceWriter.install(root, message -> {
        });
        final List<CollisionRecord> collisions = ProvenanceWriter.recentCollisions(10).orElseThrow();
        assertTrue(collisions.stream().anyMatch(record -> record.id().equals(id)));
        assertTrue(ItemProvenance.observe(original.copy(), StackLocation.playerSlot(PLAYER, 1)));
        ProvenanceWriter.flushAndClose();
        ProvenanceWriter.clearInstall();
        try (ProvenanceRepository repository = new ProvenanceRepository(root.resolve("provenance.db"))) {
            assertEquals(
                1,
                repository.loadRecentAudit(100).stream()
                    .filter(event -> event.type() == ProvenanceEventType.COLLISION && event.id().equals(id))
                    .count()
            );
        }
    }

    @Test
    public void collisionReplayDoesNotAddSecondAuditEvent() throws Exception {
        final Path db = tempDir.resolve("mintychochip/provenance.db");
        final UUID id = UUID.randomUUID();
        final CollisionRecord collision = new CollisionRecord(
            id,
            ProvenanceCollisionKind.DUPLICATE_LOCATION,
            HAND,
            StackLocation.playerSlot(PLAYER, 1),
            100L
        );
        final String key = id + "|DUPLICATE_LOCATION|" + HAND.display() + "|player:" + PLAYER + ":1";
        final ProvenanceEvent event = new ProvenanceEvent(
            100L,
            ProvenanceEventType.COLLISION,
            id,
            "minecraft:diamond",
            null,
            null,
            List.of(),
            HAND.display(),
            "DUPLICATE_LOCATION"
        );
        try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
            assertTrue(repository.insertCollision(collision, key));
            repository.insertAudit(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), event);
            assertFalse(repository.insertCollision(collision, key));
            repository.insertAudit(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), event);
            assertEquals(1, repository.loadRecentCollisions(10).size());
            assertEquals(1, repository.loadRecentAudit(10).size());
        }
    }

}
