# Provenance Persistence Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make provenance restart-safe across global records, world placements, carried entities, cache rebuilds, writer failures, and storage-root migration without introducing main-thread SQLite I/O.

**Architecture:** Keep SQLite as the durable owner for lineage, live census, collisions, and audit; keep per-dimension `SavedData` as the owner for block placement records and entity NBT as the owner for carried placement context. Make all RAM maps rebuildable, harden the writer with ordered idempotent records and spill recovery, and move the database from the primary-world path to a stable server-root path through a verified one-time migration.

**Tech Stack:** Java 25, Paper server/NMS sources, JDBC SQLite WAL, Gson spill frames, Minecraft `SavedData`, entity `ValueInput`/`ValueOutput`, JUnit 5 `@Normal` tests, Paper source-patch workflow for vanilla hooks.

## Global Constraints

- No JDBC, database query, or database transaction runs on the game thread. Initial migration/open/replay/seed runs on the dedicated provenance bootstrap executor; `ProvenanceBootstrap` waits on its readiness future before enabling provenance hooks.
- Queue-only writes are asynchronous and have a documented pre-enqueue crash window; complete forced spill frames and committed SQLite rows survive process death.
- A critical record that enters the queue or a complete spill frame is never logically dropped during normal operation or repository recovery.
- A failed repository never causes `ProvenanceWriter.process` to silently discard a critical record.
- SQLite is the sole global writer; repository methods propagate failures to the writer for retry/spill handling.
- `LiveIndex`, `LineageStore` cache, `AuditLog`, collision display ring, and collision dedupe map are caches/indexes, never durable truth.
- Existing provenance identity rules remain unchanged: UUID stamps, parent lists, split/craft/merge semantics, death semantics, and placement recovery semantics.
- Stable production code remains under `paper-server/src/main/java/dev/mintychochip/`; API changes are limited to NMS-free location contracts when required.
- Vanilla changes are thin hooks only and require `./gradlew fixupSourcePatches` followed by `./gradlew rebuildPatches`.
- Placement records remain per-dimension `SavedData`; carried records remain entity NBT; neither is duplicated into the global SQLite graph.
- Every task ends with the focused test command shown in that task before its atomic commit.

**Spec:** `docs/superpowers/specs/2026-08-10-provenance-persistence-hardening-design.md`

---

## File Structure

| File | Responsibility |
|------|----------------|
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceStorageLayout.java` | Resolve the stable server storage root and perform verified legacy-directory migration. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBootstrap.java` | Pass the server world container to the storage layout and install one locked writer. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceRepository.java` | Version SQLite schema, apply migrations, enforce ordered/idempotent writes, and expose durable reads. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceSpillJournal.java` | Write versioned forced spill frames, seize/replay them, quarantine malformed tails, and acknowledge only after commit. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceWriter.java` | Own write sequencing, queue/degraded states, retry and spill behavior, shutdown flush, and durable snapshots. |
| `paper-server/src/main/java/dev/mintychochip/provenance/LiveRecord.java` | Carry the durable live-census row used by repository and spill code. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java` | Route every live count/location transition through durable persistence and collision/audit idempotency. |
| `paper-server/src/main/java/dev/mintychochip/provenance/LiveEntry.java` | Expose safe count/location mutation snapshots for persistence. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceLocations.java` | Produce restart-stable NMS container locations and unknown locations for process-local containers. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBukkitCommand.java` | Read writer-owned durable snapshots for audit and collision commands without SQLite access. |
| `paper-server/src/main/java/dev/mintychochip/provenance/ProvenancePlacementsData.java` | Preserve per-dimension SavedData ownership and add save/reopen coverage. |
| `paper-server/src/main/java/dev/mintychochip/provenance/PlacementStore.java` | Keep production server-level access on SavedData and isolate the test-only map. |
| `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceStorageLayoutTest.java` | Verify legacy migration, checksums, resume, backup, and conflict behavior. |
| `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java` | Verify schema migration, writer retry, live ordering, audit/collision durability, and restart behavior. |
| `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceSpillJournalTest.java` | Verify versioned frames, sequence/event IDs, forced append path, replay, and corruption handling. |
| `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceLocationsTest.java` | Verify stable block-entity/entity locations and unknown fallback. |
| `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java` | Add live count persistence and placement/carry lifecycle assertions. |
| `paper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java` | Replace process-local container labels with `ProvenanceLocations`. |
| `paper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java` | Clear carried placement state when any entity is removed. |
| `paper-server/patches/sources/net/minecraft/world/level/block/entity/HopperBlockEntity.java.patch` | Rebuilt vanilla hook patch for stable hopper locations. |
| `paper-server/patches/sources/net/minecraft/world/entity/Entity.java.patch` | Rebuilt vanilla hook patch for carried-state cleanup. |
| `docs/superpowers/specs/2026-08-10-provenance-persistence-hardening-design.md` | Design authority and durability boundary. |
| `docs/superpowers/specs/2026-08-07-item-provenance-design.md` | Cross-link to the hardening design after implementation. |

---

### Task 1: Add stable storage-root resolution and verified legacy migration

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceStorageLayout.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBootstrap.java`
- Create: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceStorageLayoutTest.java`

**Interfaces:**
- Consumes: `server.getWorldContainer().toPath()` and the first loaded world's folder as a legacy candidate.
- Produces: `ProvenanceStorageLayout.resolve(Path worldContainer, @Nullable Path primaryWorld)` and `migrate()` returning the stable `Path` used by `ProvenanceWriter.install`.

- [ ] **Step 1: Write migration tests first**

Create the test class with these observable contracts:

```java
@Normal
public final class ProvenanceStorageLayoutTest {
    @TempDir
    Path tempDir;

    @Test
    public void migratesLegacyFilesWithChecksumsAndBackup() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path legacy = primaryWorld.resolve("mintychochip");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("provenance.db"), "db");
        Files.writeString(legacy.resolve("provenance.db-wal"), "wal");
        Files.writeString(legacy.resolve("provenance-spill.log"), "spill\n");
        Files.writeString(legacy.resolve("provenance-audit.jsonl"), "audit\n");

        final ProvenanceStorageLayout layout = ProvenanceStorageLayout.resolve(worldContainer, primaryWorld);
        final Path root = layout.migrate();

        assertEquals(worldContainer.resolve("mintychochip"), root);
        assertEquals("db", Files.readString(root.resolve("provenance.db")));
        assertEquals("wal", Files.readString(root.resolve("provenance.db-wal")));
        assertFalse(Files.exists(root.resolve("migration-complete.json")));

        layout.markMigrationComplete();
        assertTrue(Files.exists(root.resolve("migration-complete.json")));
        assertTrue(Files.find(
            primaryWorld,
            1,
            (path, attributes) -> path.getFileName().toString().startsWith("mintychochip.legacy-")
        ).findAny().isPresent());
    }


    @Test
    public void completedDestinationWinsOverRetainedLegacyBackup() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.writeString(root.resolve("migration-complete.json"), "{\"status\":\"complete\"}");
        Files.writeString(root.resolve("provenance.db"), "new");
        Files.createDirectories(primaryWorld.resolve("mintychochip.legacy-1"));
        Files.writeString(primaryWorld.resolve("mintychochip.legacy-1/provenance.db"), "old");

        assertEquals(root, ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate());
        assertEquals("new", Files.readString(root.resolve("provenance.db")));
    }

    @Test
    public void unresolvedTwoStoreConflictDoesNotMerge() throws Exception {
        final Path worldContainer = tempDir.resolve("server");
        final Path primaryWorld = worldContainer.resolve("world");
        final Path root = worldContainer.resolve("mintychochip");
        Files.createDirectories(root);
        Files.createDirectories(primaryWorld.resolve("mintychochip"));
        Files.writeString(root.resolve("provenance.db"), "new");
        Files.writeString(primaryWorld.resolve("mintychochip/provenance.db"), "old");

        assertThrows(
            IOException.class,
            () -> ProvenanceStorageLayout.resolve(worldContainer, primaryWorld).migrate()
        );
        assertEquals("new", Files.readString(root.resolve("provenance.db")));
        assertEquals("old", Files.readString(primaryWorld.resolve("mintychochip/provenance.db")));
    }
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: compilation fails because `ProvenanceStorageLayout` and its tests do not exist. Do not change production code before recording this failure.

- [ ] **Step 3: Implement the storage layout**

Create the class with this public shape:

```java
public final class ProvenanceStorageLayout {
    public static ProvenanceStorageLayout resolve(Path worldContainer, @Nullable Path primaryWorld);
    public Path root();
    public @Nullable Path legacyRoot();
    public Path migrate() throws IOException;
    public void markMigrationComplete() throws IOException;
    public void abortMigration() throws IOException;
}
```

Implement these exact rules:

1. `root()` is `worldContainer.resolve("mintychochip")`.
2. `legacyRoot()` is `primaryWorld.resolve("mintychochip")` when a primary world is present; otherwise it is absent through a nullable internal field.
3. `migrate()` creates an exclusive `writer.lock` parent lock before inspecting files and holds that lock until `markMigrationComplete()` or `abortMigration()`.
4. A completed `migration-complete.json` selects the destination without reading the legacy store.
5. A missing destination plus a legacy store copies `provenance.db`, `provenance.db-wal`, `provenance.db-shm`, `provenance-spill.log`, `provenance-spill.log.replay`, `provenance-audit.jsonl`, and `.1` through `.3` into a staging directory. Missing optional files are skipped; present files are copied and SHA-256 verified.
6. The staging directory is force-synced and renamed into place; `markMigrationComplete()` writes the marker and renames the legacy root to its recovery backup only after the caller successfully opens, migrates, and recovers SQLite. The first `migrate()` call returns a staging-ready root without deleting the legacy source. `abortMigration()` releases the lock and leaves both source and destination untouched.
7. A destination with no marker and a legacy root with different content throws `IOException` containing `migration-conflict`; it never merges rows.
8. A completed destination leaves a timestamped legacy backup in place.

Change `ProvenanceBootstrap.ensureInstalled` to pass `server.getWorldContainer().toPath()` as the writer root and the first world folder as the legacy candidate. `ProvenanceWriter.install` receives the already migrated root and must not derive a second world path.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; the suite reports the migration tests and existing provenance tests as passing.

- [ ] **Step 5: Commit the storage-root unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceStorageLayout.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBootstrap.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceStorageLayoutTest.java
git commit -m "feat(provenance): migrate storage to stable server root"
```

---

### Task 2: Version the SQLite schema and make repository writes recoverable

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceRepository.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/LiveRecord.java`

**Interfaces:**
- Consumes: existing unversioned SQLite tables and current `LineageNode`, `LiveRecord`, `CollisionRecord`, and `ProvenanceEvent` values.
- Produces: typed repository methods that throw `SQLException`, ordered lineage/live upserts, idempotent collision/audit inserts, schema version `2`, and `maxWriteSequence()`.

- [ ] **Step 1: Add failing schema and ordering tests**

Add these tests to `ProvenancePersistenceTest`:

```java
@Test
public void reopeningMigratesExistingSchemaAndPreservesRows() throws Exception {
    final Path db = tempDir.resolve("mintychochip/provenance.db");
    Files.createDirectories(db.getParent());
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lineage (id TEXT PRIMARY KEY, item TEXT NOT NULL, source TEXT NOT NULL, parents TEXT NOT NULL, born INTEGER NOT NULL, holder TEXT, dead INTEGER NOT NULL DEFAULT 0, death_reason TEXT, death_epoch INTEGER)");
            statement.execute("CREATE TABLE live (id TEXT PRIMARY KEY, item TEXT NOT NULL, location TEXT NOT NULL, count INTEGER NOT NULL, epoch INTEGER NOT NULL, dead INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE collisions (id TEXT NOT NULL, kind TEXT NOT NULL, existing TEXT NOT NULL, observed TEXT NOT NULL, epoch INTEGER NOT NULL)");
            statement.execute("CREATE TABLE audit (seq INTEGER PRIMARY KEY AUTOINCREMENT, epoch INTEGER NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, item TEXT, source TEXT, reason TEXT, related TEXT, holder TEXT, detail TEXT)");
        }
    }
    final UUID id = UUID.randomUUID();
    try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
        repository.upsertLineage(new LineageNode(id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 10L, "hand"), 1L);
        assertEquals(2, repository.schemaVersion());
    }
    try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
        assertTrue(repository.loadLineage(id).isPresent());
        assertEquals(2, repository.schemaVersion());
    }
}

@Test
public void olderLiveRevisionCannotOverwriteNewerRevision() throws Exception {
    final Path db = tempDir.resolve("mintychochip/provenance.db");
    final UUID id = UUID.randomUUID();
    try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
        repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:p:1", 1, 100L, false), 20L);
        repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:p:2", 4, 200L, false), 21L);
        repository.upsertLive(new LiveRecord(id, "minecraft:diamond", "player:p:1", 1, 100L, false), 20L);
        assertEquals("player:p:2", repository.loadAliveLive().getFirst().locationDisplay());
        assertEquals(4, repository.loadAliveLive().getFirst().count());
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
        repository.insertAudit(UUID.fromString("11111111-1111-1111-1111-111111111111"), event);
        repository.insertAudit(UUID.fromString("11111111-1111-1111-1111-111111111111"), event);
        assertEquals(1, repository.loadRecentCollisions(10).size());
        assertEquals(1, repository.loadRecentAudit(10).size());
    }
}
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: compilation fails because repository methods do not accept sequence/key arguments and `schemaVersion()` is absent.

- [ ] **Step 3: Implement schema migration and typed failure propagation**

Add `SCHEMA_VERSION = 2`, a transactional `migrateSchema()` called from the constructor, and these startup pragmas:

```java
statement.execute("PRAGMA journal_mode=WAL");
statement.execute("PRAGMA synchronous=FULL");
statement.execute("PRAGMA foreign_keys=ON");
statement.execute("PRAGMA busy_timeout=5000");
```

Use `PRAGMA user_version` to run migrations exactly once. Migration version `1` creates the existing four tables for a new store. Migration version `2` adds `updated_seq` to `lineage` and `live`, `event_id` to `audit`, `dedupe_key` to `collisions`, fills legacy IDs as `legacy:<seq>` and `legacy:<rowid>`, creates unique indexes, and sets `PRAGMA user_version=2` in the same transaction.

Change repository mutators from “catch, set failed, return” to “throw `SQLException`”. Keep `isFailed()` as a status snapshot set by the writer when a connection becomes unusable. Implement conditional upserts:

```sql
INSERT INTO live (id, item, location, count, epoch, updated_seq, dead)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
  item = excluded.item,
  location = excluded.location,
  count = excluded.count,
  epoch = excluded.epoch,
  updated_seq = excluded.updated_seq,
  dead = excluded.dead
WHERE excluded.updated_seq > live.updated_seq;
```

Use the same `updated_seq` guard for `lineage`. Implement `insertCollision` with `INSERT OR IGNORE` on `dedupe_key`, returning whether a row was inserted. Implement `insertAudit(UUID eventId, ProvenanceEvent event)` with `INSERT OR IGNORE` on `event_id`. Keep `loadRecentAudit` and `loadRecentCollisions` bounded to `1..10_000` rows.

Add `schemaVersion()` and `maxWriteSequence()` for tests and writer startup. `runInTransaction` accepts `Consumer<ProvenanceRepository>`, commits on success, rolls back on `SQLException` or runtime failure, restores auto-commit, and propagates the original failure.

- [ ] **Step 4: Run all persistence tests and verify they pass**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; migration, ordering, idempotency, and all pre-existing provenance tests pass.

- [ ] **Step 5: Commit the repository unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceRepository.java \
        paper-server/src/main/java/dev/mintychochip/provenance/LiveRecord.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java
git commit -m "feat(provenance): version and order durable repository writes"
```

---

### Task 3: Harden spill frames and writer failure recovery

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceSpillJournal.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceWriter.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceSpillJournalTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java`

**Interfaces:**
- Consumes: versioned repository methods from Task 2 and the existing bounded queue/install hooks.
- Produces: v2 spill records with sequence/event IDs, writer states, repository retry, durable snapshots, and no null-repository critical discard.

- [ ] **Step 1: Write failing spill and repository-recovery tests**

Add these tests:

```java
@Test
public void v2SpillRoundTripPreservesSequenceAndEventId() throws Exception {
    final Path path = tempDir.resolve("provenance-spill.log");
    final ProvenanceSpillJournal journal = new ProvenanceSpillJournal(path);
    final UUID auditId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    final UUID id = UUID.randomUUID();
    journal.appendLive(41L, new LiveRecord(id, "minecraft:stone", HAND.display(), 2, 100L, false));
    journal.appendAudit(42L, auditId, new ProvenanceEvent(
        101L, ProvenanceEventType.BIRTH, id, "minecraft:stone", ProvenanceSource.BLOCK_DROP,
        null, List.of(), HAND.display(), null
    ));

    final List<ProvenanceSpillJournal.SpillRecord> records = journal.readAll();
    assertEquals(2, records.size());
    assertEquals(41L, records.getFirst().sequence());
    assertEquals(42L, records.getLast().sequence());
    assertEquals(auditId, ((ProvenanceSpillJournal.SpillRecord.Audit) records.getLast()).eventId());
}

@Test
public void malformedFinalSpillFrameDoesNotAcknowledgePriorRecords() throws Exception {
    final Path path = tempDir.resolve("provenance-spill.log");
    final ProvenanceSpillJournal journal = new ProvenanceSpillJournal(path);
    final UUID id = UUID.randomUUID();
    journal.appendLineage(1L, new LineageNode(id, "minecraft:stone", ProvenanceSource.BLOCK_DROP, List.of(), 1L, "hand"));
    Files.writeString(path, "{\"v\":2,\"seq\":2,\"k\":\"lineage\"", StandardOpenOption.APPEND);

    final List<ProvenanceSpillJournal.SpillRecord> records = journal.readAll();
    assertEquals(1, records.size());
    assertTrue(Files.exists(path));
    journal.seizePending();
    assertTrue(Files.exists(journal.replayPath()));
}

@Test
public void repositoryFailureLeavesCriticalBatchForReplay() throws Exception {
    ProvenanceWriter.installForTest(tempDir, message -> {}, 1);
    final ItemStack stack = new ItemStack(Items.DIAMOND, 1);
    final UUID id = ItemProvenance.birth(stack, ProvenanceSource.LOOT, HAND).orElseThrow();
    ProvenanceWriter.failRepositoryForTest();
    final ItemStack second = new ItemStack(Items.DIAMOND, 1);
    final UUID secondId = ItemProvenance.birth(second, ProvenanceSource.LOOT, HAND).orElseThrow();
    assertTrue(ProvenanceWriter.status().contains("state=degraded"));
    assertTrue(ProvenanceWriter.status().contains("critical-pending="));
    ProvenanceWriter.clearInstall();

    ProvenanceWriter.install(tempDir, message -> {});
    ProvenanceWriter.flushAndClose();
    ProvenanceWriter.clearInstall();
    try (ProvenanceRepository repository = new ProvenanceRepository(tempDir.resolve("mintychochip/provenance.db"))) {
        assertTrue(repository.loadLineage(id).isPresent());
        assertTrue(repository.loadLineage(secondId).isPresent());
    }
}
```

`failRepositoryForTest()` is package-private and closes the test writer connection; it is not exposed through the public API.

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: compilation fails because v2 sequence/event overloads, writer states, and the failure-injection hook do not exist.

- [ ] **Step 3: Implement v2 spill records**

Extend the sealed spill records so every record carries `long sequence`; audit records also carry `UUID eventId`. Keep overloads used by existing tests and delegate them to sequence `0L` and a generated audit ID.

Write JSON objects with `v=2`, `seq`, `k`, and all payload fields. Use a synchronized `FileChannel` opened with `CREATE`, `WRITE`, and `APPEND`; write the complete UTF-8 line with a trailing newline and call `force(true)`. `seizePending()` moves the active file to `.replay` before parsing so new appends never mutate the seized batch.

Parsing rules are exact:

- A valid v2 frame becomes a typed record.
- A valid v1 frame becomes a typed record with sequence `0L` and a generated event ID for audit.
- A malformed final frame returns all prior complete frames and records the incomplete-tail metric.
- A malformed non-final frame throws `IOException` and leaves `.replay` unacknowledged.
- `ackSeized()` deletes only `.replay` after the repository transaction commits.

- [ ] **Step 4: Implement writer state and recovery**

Change the writer repository field from `final` to a guarded mutable handle and add:

```java
public enum State { STARTING, RUNNING, DEGRADED, DRAINING, CLOSED }

static void failRepositoryForTest();
static Optional<List<ProvenanceEvent>> recentAudit(int limit);
static Optional<List<CollisionRecord>> recentCollisions(int limit);
```

Assign a sequence in `enqueueLineage`, `enqueueLive`, and `enqueueCollision`; assign a UUID event ID in `enqueueAudit`. Seed the sequence from `repository.maxWriteSequence()` after spill recovery completes on the bootstrap executor.

Implement `processBatch` with this order:

1. Ensure the repository is open and healthy.
2. Begin one transaction.
3. Apply every item using its sequence/event ID.
4. Commit.
5. Increment written counters and update writer-owned durable audit/collision snapshots.
6. Acknowledge seized spill only after the commit returns.

When repository open, transaction, or apply fails:

1. Set state to `DEGRADED`.
2. Append every not-committed item in the batch to the active spill journal with its original sequence/event ID.
3. Close the failed connection.
4. Leave the `.replay` file untouched when the failure occurred during replay.
5. Retry opening the repository on the writer thread with 1, 2, 4, 8, and 16 second delays, capped at 16 seconds.
6. Do not call `process` while the repository is null or failed.

Queue-full critical writes use `spillCritical` and block only when the append itself fails; status records `critical-failures`. Audit writes spill during degraded storage rather than incrementing `audit-dropped`.

On production startup, construct the queue and spill journal without opening SQLite, set state `STARTING`, and submit `initializeStorage` to a dedicated `mintychochip-provenance-bootstrap` executor. `initializeStorage` calls `ProvenanceStorageLayout.migrate()`, opens/migrates SQLite, replays spill, seeds live/audit/collision snapshots, marks migration complete, attaches the repository to `LineageStore`, and completes a readiness future. `ProvenanceBootstrap` calls `installAndAwaitReady` before enabling provenance hooks; the caller waits on the future but performs no JDBC or file recovery work. Test installs use the same future and await it before assertions. On shutdown, set `DRAINING`, reject new queue ownership by spilling, drain queue and spill, close files, set `CLOSED`, and leave uncommitted spill for the next start.

Replace the status string with these fields:

```text
state=starting|running|degraded|draining|closed queue-depth=N spill-bytes=B critical-pending=N critical-failures=N written=N audit-dropped=N last-commit-ms=T store-root=... last-error=...
```

- [ ] **Step 5: Run the full provenance suite and verify it passes**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; spill round-trip, malformed-tail, recovery, queue-pressure, restart lineage, audit, and live-seed tests pass.

- [ ] **Step 6: Commit the writer unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceSpillJournal.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceWriter.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceSpillJournalTest.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java
git commit -m "fix(provenance): preserve writes across repository failure"
```

---

### Task 4: Route every live and lineage mutation through ordered persistence

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/LiveEntry.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`

**Interfaces:**
- Consumes: ordered writer APIs from Task 3.
- Produces: one mutation helper for live count/location changes and restart tests proving the final durable row matches runtime state.

- [ ] **Step 1: Add failing tests for previously uncovered count transitions**

Add these tests:

```java
@Test
public void partialSplitPersistsParentRemainingCount() throws Exception {
    ProvenanceWriter.install(tempDir, message -> {});
    final ItemStack parent = new ItemStack(Items.COBBLESTONE, 8);
    final UUID parentId = ItemProvenance.birth(parent, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
    final ItemStack child = parent.copyWithCount(3);
    parent.setCount(5);
    ItemProvenance.onSplit(parent, child);
    ProvenanceWriter.flushAndClose();
    ProvenanceWriter.clearInstall();
    ItemProvenance.clearAll();
    ProvenanceWriter.install(tempDir, message -> {});

    final LiveEntry loaded = ItemProvenance.live().get(parentId).orElseThrow();
    assertEquals(5, loaded.count());
    ProvenanceWriter.clearInstall();
}

@Test
public void consumedStackCountPersistsBeforeRestart() throws Exception {
    ProvenanceWriter.install(tempDir, message -> {});
    final ItemStack stack = new ItemStack(Items.BREAD, 5);
    final UUID id = ItemProvenance.birth(stack, ProvenanceSource.LOOT, HAND).orElseThrow();
    stack.setCount(2);
    ItemProvenance.noteConsumed(stack);
    ProvenanceWriter.flushAndClose();
    ProvenanceWriter.clearInstall();
    ItemProvenance.clearAll();
    ProvenanceWriter.install(tempDir, message -> {});

    assertEquals(2, ItemProvenance.live().get(id).orElseThrow().count());
    ProvenanceWriter.clearInstall();
}

@Test
public void staleSpillLiveUpdateCannotReplaceNewerQueueUpdate() throws Exception {
    final Path db = tempDir.resolve("mintychochip/provenance.db");
    final UUID id = UUID.randomUUID();
    try (ProvenanceRepository repository = new ProvenanceRepository(db)) {
        repository.upsertLive(new LiveRecord(id, "minecraft:stone", "player:p:9", 9, 900L, false), 90L);
        repository.upsertLive(new LiveRecord(id, "minecraft:stone", "player:p:1", 1, 100L, false), 10L);
        assertEquals("player:p:9", repository.loadAliveLive().getFirst().locationDisplay());
        assertEquals(9, repository.loadAliveLive().getFirst().count());
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: the restart assertions fail because direct `LiveEntry.setCount` calls do not enqueue a live record and `LiveEntry` has no persistence-aware mutation boundary.

- [ ] **Step 3: Add mutation helpers and replace direct writes**

Add package-private helpers to `ItemProvenance`:

```java
private static void updateLiveCount(@NotNull LiveEntry entry, int count);
private static void addLiveLocation(@NotNull LiveEntry entry, @NotNull StackLocation location);
private static void moveLiveLocation(@NotNull LiveEntry entry, @NotNull StackLocation from, @NotNull StackLocation to);
```

Each helper compares the previous value, mutates once, and calls `persistLive(entry, false)` once after the mutation. Replace every direct mutation at these current call sites:

- `observe`: count update and accepted first concrete location.
- `transfer`: count update and accepted move.
- `mintChild`: parent remaining count.
- `onSplit`: full extraction count and partial parent count.
- `noteConsumed`: remaining count.
- `rehydrateIfNeeded`: existing-entry count/location update and fresh-entry insert.

Keep `death` as the only path that removes the live entry and writes a dead live record. Keep lineage `put` calls after parent-list changes and death marking so each row receives a new writer sequence.

Add `LiveEntry.snapshot()` returning an immutable `LiveRecord` input tuple so `persistLive` reads one coherent count/location state rather than interleaving concurrent map reads:

```java
public LiveSnapshot snapshot();

public record LiveSnapshot(UUID id, String itemId, StackLocation location, int count, long bornEpochMs) {}
```

`persistLive` converts the snapshot to `LiveRecord` and lets `ProvenanceWriter.enqueueLive` assign the sequence.

- [ ] **Step 4: Run the focused provenance suite and verify it passes**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; count, split, consume, restart, and ordering tests pass.

- [ ] **Step 5: Commit the live-state unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java \
        paper-server/src/main/java/dev/mintychochip/provenance/LiveEntry.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java
git commit -m "fix(provenance): persist every live census transition"
```

---

### Task 5: Make collision and audit history durable, idempotent, and command-safe

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/AuditLog.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceWriter.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBukkitCommand.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java`

**Interfaces:**
- Consumes: repository unique collision/audit IDs and writer-owned durable snapshots from Tasks 2–3.
- Produces: deterministic collision writes, restart-safe recent-collision output, and audit/collision command reads with no repository query on the command thread.

- [ ] **Step 1: Add failing restart and snapshot tests**

Add:

```java
@Test
public void collisionsReloadIntoDurableRecentSnapshotAfterRestart() throws Exception {
    ProvenanceWriter.install(tempDir, message -> {});
    final ItemStack original = new ItemStack(Items.DIAMOND, 1);
    final UUID id = ItemProvenance.birth(original, ProvenanceSource.LOOT, HAND).orElseThrow();
    assertTrue(ItemProvenance.observe(original.copy(), StackLocation.playerSlot(PLAYER, 1)));
    ProvenanceWriter.flushAndClose();
    ProvenanceWriter.clearInstall();
    ItemProvenance.clearAll();

    ProvenanceWriter.install(tempDir, message -> {});
    final List<CollisionRecord> collisions = ProvenanceWriter.recentCollisions(10).orElseThrow();
    assertTrue(collisions.stream().anyMatch(record -> record.id().equals(id)));
    ProvenanceWriter.clearInstall();
}

@Test
public void collisionReplayDoesNotAddSecondAuditEvent() throws Exception {
    final Path db = tempDir.resolve("mintychochip/provenance.db");
    final UUID id = UUID.randomUUID();
    final CollisionRecord collision = new CollisionRecord(
        id, ProvenanceCollisionKind.DUPLICATE_LOCATION, HAND,
        StackLocation.playerSlot(PLAYER, 1), 100L
    );
    final String key = id + "|DUPLICATE_LOCATION|" + HAND.display() + "|player:" + PLAYER + ":1";
    final ProvenanceEvent event = new ProvenanceEvent(
        100L, ProvenanceEventType.COLLISION, id, "minecraft:diamond", null, null,
        List.of(), HAND.display(), "DUPLICATE_LOCATION"
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
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: the durable recent-collision snapshot is absent and repeated collision audit rows are possible.

- [ ] **Step 3: Implement deterministic collision writes and runtime-only rings**

Change `ItemProvenance.recordCollision` to build one collision key:

```java
final String key = id + "|" + kind.name() + "|" + existing.display() + "|" + observed.display();
```

Keep the bounded `COLLISIONS` ring for local display, but pass `key` to `ProvenanceWriter.enqueueCollision`. The writer calls `insertCollision`; it only writes the corresponding collision audit event when the insert returns `true`. `CollisionRecord` remains in the runtime ring only when the in-memory dedupe map accepts the event. The database unique key is the final restart-safe dedupe authority.

Add `AuditLog.appendRuntime(ProvenanceEvent event)` that updates only the hot ring. Keep `append` as the normal ring-plus-writer path. Collision handling uses `appendRuntime` followed by the combined writer collision/audit item so a replay cannot produce a second audit row.

Seed writer snapshots in the constructor:

```java
this.durableAudit = new ArrayDeque<>(repo.loadRecentAudit(16_384));
this.durableCollisions = new ArrayDeque<>(repo.loadRecentCollisions(4_096));
```

Update those snapshots only after the enclosing SQLite transaction commits. `recentAudit` and `recentCollisions` return immutable copies from these writer-owned structures; they never invoke `ProvenanceRepository` on the caller thread.

Change `/provenance collisions` to prefer `ProvenanceWriter.recentCollisions(n)` and report `durable` in the header. Change `/provenance live` to label the RAM count as `loaded live cache` and include writer state rather than implying that the cache is the complete durable census.

- [ ] **Step 4: Run the provenance suite and verify it passes**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; collision restart, collision replay, audit permanence, and existing command-facing tests pass.

- [ ] **Step 5: Commit the collision/audit unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java \
        paper-server/src/main/java/dev/mintychochip/provenance/AuditLog.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceWriter.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceBukkitCommand.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java
git commit -m "fix(provenance): make collision history restart-safe"
```

---

### Task 6: Replace process-local container locations and close carried-state gaps

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceLocations.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/PlacementStore.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ProvenancePlacementsData.java`
- Create: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceLocationsTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`
- Rebuild: `paper-server/patches/sources/net/minecraft/world/level/block/entity/HopperBlockEntity.java.patch`
- Rebuild: `paper-server/patches/sources/net/minecraft/world/entity/Entity.java.patch`

**Interfaces:**
- Consumes: `StackLocation` factories, `ServerLevel`, `BlockEntity`, `Container`, and existing placement/carry methods.
- Produces: stable block-entity container labels, unknown fallback for process-local containers, stale carry cleanup on entity load/removal, and real SavedData save/reopen coverage.

- [ ] **Step 1: Add failing stable-location and carry tests**

Add the pure helper contract:

```java
@Test
public void unknownContainerDoesNotBecomeAConcreteRestartLocation() {
    assertFalse(ProvenanceLocations.forContainer(new SimpleContainer(1), 0).isConcrete());
}

@Test
public void stableEntityLocationUsesEntityUuid() {
    final UUID entityId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    assertEquals(
        "container-entity:" + entityId + ":4",
        ProvenanceLocations.forEntityContainer(entityId, 4).display()
    );
}
```

Add to `ItemProvenanceTest`:

```java
@Test
public void loadingMissingCarriedRecordClearsStaleRuntimeEntry() {
    final UUID entityId = UUID.randomUUID();
    ItemProvenance.putCarriedForTest(
        entityId,
        new PlacementRecord(UUID.randomUUID(), "minecraft:stone", "player:test", 1L)
    );
    ItemProvenance.loadCarriedFrom(entityId, emptyValueInput());
    assertTrue(ItemProvenance.getCarried(entityId).isEmpty());
}
```

Add these imports to `ItemProvenanceTest`:

```java
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.minecraft.world.level.storage.ValueInput;
```

Add this helper to the test class:

```java
private static ValueInput emptyValueInput() {
    final ValueInput input = mock(ValueInput.class);
    when(input.getStringOr("MintyProvParent", "")).thenReturn("");
    return input;
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: compilation fails because `ProvenanceLocations` does not exist and missing carried data currently leaves the old map entry.

- [ ] **Step 3: Implement stable location helpers**

Create:

```java
public final class ProvenanceLocations {
    public static StackLocation forContainer(Container container, int slot);
    public static StackLocation forBlockEntity(BlockEntity blockEntity, int slot);
    public static StackLocation forEntityContainer(UUID entityId, int slot);
}
```

`forBlockEntity` returns `StackLocation.labeled("container:" + dimension + ":" + x + "," + y + "," + z + ":" + slot)` for a `ServerLevel` block entity. `forEntityContainer` returns `StackLocation.labeled("container-entity:" + entityId + ":" + slot)`. `forContainer` delegates to `forBlockEntity` for a block entity and returns `StackLocation.unknown()` for all other container implementations.

Update the HopperBlockEntity hook at the current `System.identityHashCode(container)` locations to call `ProvenanceLocations.forContainer(container, slot)`. Replace `"hopper-in"` with `StackLocation.unknown()` because it describes a transient handoff rather than a durable holder. Keep the copper-golem body UUID label because it is restart-stable.

Update `ItemProvenance.loadCarriedFrom` to call `CARRIED_BY_ENTITY.remove(entityId)` before checking `MintyProvParent`. Keep malformed UUID behavior as a no-op after the removal. Add a thin hook in `Entity.setRemoved` that calls `ItemProvenance.discardCarried(this.getUUID())`; duplicate explicit falling-block cleanup remains harmless and idempotent.

Keep `PlacementStore` server-level methods routed exclusively to `ProvenancePlacementsData`. Rename the string-map clearing method to `clearTestMemory` and change `ItemProvenance.clearAll()` to use that method so a runtime reset cannot be interpreted as a durable world-data wipe.

- [ ] **Step 4: Rebuild the vanilla patches**

After editing the applied Minecraft sources, run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

Expected: both tasks complete successfully and the generated HopperBlockEntity and Entity patch files contain only the new thin `// mintychochip` calls.

- [ ] **Step 5: Run location, placement, and carry tests**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; stable-location, placement codec, piston, place/break recovery, carried save/load, and entity-removal tests pass.

- [ ] **Step 6: Commit the location/carry unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceLocations.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java \
        paper-server/src/main/java/dev/mintychochip/provenance/PlacementStore.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenancePlacementsData.java \
        paper-server/src/main/java/dev/mintychochip/provenance/ProvenanceLocationsTest.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java \
        paper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java \
        paper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java \
        paper-server/patches/sources/net/minecraft/world/level/block/entity/HopperBlockEntity.java.patch \
        paper-server/patches/sources/net/minecraft/world/entity/Entity.java.patch
git commit -m "fix(provenance): use restart-stable holder locations"
```

---

### Task 7: Verify end-to-end restart, migration, and operational behavior

**Files:**
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceStorageLayoutTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceSpillJournalTest.java`
- Modify: `paper-server/src/test/java/org/bukkit/support/suite/ProvenanceTestSuite.java` only when package selection no longer discovers a new test
- Modify: `docs/superpowers/specs/2026-08-07-item-provenance-design.md`

**Interfaces:**
- Consumes: all production changes from Tasks 1–6.
- Produces: complete acceptance evidence and a cross-linked durable persistence contract.

- [ ] **Step 1: Add end-to-end acceptance tests**

Add these named tests with the exact assertions below:

```java
@Test
public void restartRebuildsLineageLiveAuditAndCollisionCaches() throws Exception {
    ProvenanceWriter.install(tempDir, message -> {});
    final ItemStack parent = new ItemStack(Items.IRON_ORE, 1);
    final UUID parentId = ItemProvenance.birth(parent, ProvenanceSource.BLOCK_DROP, HAND).orElseThrow();
    final ItemStack child = new ItemStack(Items.IRON_INGOT, 1);
    ItemProvenance.onSmelted(child, parentId, HAND);
    final UUID childId = StackStamp.readId(child).orElseThrow();
    assertTrue(ItemProvenance.observe(parent.copy(), StackLocation.playerSlot(PLAYER, 1)));
    ProvenanceWriter.flushAndClose();
    ProvenanceWriter.clearInstall();
    ItemProvenance.clearAll();

    ProvenanceWriter.install(tempDir, message -> {});
    assertTrue(ItemProvenance.lineage().walkAncestors(childId).stream().anyMatch(n -> n.id().equals(parentId)));
    assertTrue(ItemProvenance.live().contains(childId));
    assertTrue(ProvenanceWriter.recentAudit(50).orElseThrow().stream().anyMatch(e -> e.id().equals(childId)));
    assertFalse(ProvenanceWriter.recentCollisions(50).orElseThrow().isEmpty());
    ProvenanceWriter.clearInstall();
}

@Test
public void flushLeavesNoCriticalSpillWhenRepositoryHealthy() throws Exception {
    ProvenanceWriter.installForTest(tempDir, message -> {}, 2);
    for (int i = 0; i < 500; i++) {
        ItemProvenance.birth(new ItemStack(Items.COBBLESTONE, 1), ProvenanceSource.BLOCK_DROP, HAND);
    }
    ProvenanceWriter.flushAndClose();
    final Path spill = tempDir.resolve("mintychochip/provenance-spill.log");
    assertEquals(0L, Files.exists(spill) ? Files.size(spill) : 0L);
    assertFalse(Files.exists(tempDir.resolve("mintychochip/provenance-spill.log.replay")));
    ProvenanceWriter.clearInstall();
}
```

- [ ] **Step 2: Run the complete provenance suite**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: `BUILD SUCCESSFUL`; the suite includes every `@Normal` test under `dev.mintychochip.provenance`.

- [ ] **Step 3: Run a clean compile and patch verification**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew applyPatches
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: all tasks succeed; the applied Minecraft sources still contain the stable-location and carried-cleanup hooks; the provenance suite remains green after patch application.

- [ ] **Step 4: Update the core provenance design cross-link**

Under the persistence section of `docs/superpowers/specs/2026-08-07-item-provenance-design.md`, replace the current durable-store pointer with:

```markdown
- Durable global store: see `2026-08-08-provenance-durable-store-design.md` and
  `2026-08-10-provenance-persistence-hardening-design.md` (`provenance.db`,
  versioned spill recovery, stable server-root migration, durable live/collision/
  audit state, per-dimension placement SavedData, and carried entity NBT).
```

- [ ] **Step 5: Commit the verification/documentation unit**

```bash
git add paper-server/src/test/java/dev/mintychochip/provenance/ProvenancePersistenceTest.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceStorageLayoutTest.java \
        paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceSpillJournalTest.java \
        paper-server/src/test/java/org/bukkit/support/suite/ProvenanceTestSuite.java \
        docs/superpowers/specs/2026-08-07-item-provenance-design.md
git commit -m "docs(provenance): link persistence hardening contract"
```

---

## Final verification checklist

Run from the implementation worktree after the last task:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Confirm all of the following from test output and status output:

- Stable-root migration creates a checksum-backed completion marker and retains a legacy backup.
- Ambiguous old/new roots never merge automatically.
- Repository schema reports version `2` after reopening an old database.
- Repository failure leaves critical records in spill and later replay restores them.
- Older sequence numbers cannot overwrite newer live or lineage rows.
- Split, consume, transfer, rehydrate, death, and merge transitions enqueue durable live updates.
- Collision and audit replay are idempotent.
- Recent audit and collision commands use writer snapshots rather than direct command-thread SQLite reads.
- Hopper holder labels are restart-stable or `unknown`; no production provenance location uses `System.identityHashCode`.
- Placement `SavedData` and carried entity NBT still own their state and survive save/reopen tests.
- The default asynchronous crash boundary is documented in code and the design spec.
- Worktree contains only the atomic commits listed by the tasks.
