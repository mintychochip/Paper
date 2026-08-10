# Provenance persistence hardening — design

**Status:** proposed
**Package:** `dev.mintychochip.provenance`
**Date:** 2026-08-10
**Depends on:** `2026-08-07-item-provenance-design.md`, `2026-08-08-provenance-durable-store-design.md`

## Goal

Make every provenance state that affects restart behavior, dupe detection, lineage explanation, or place/break recovery have an explicit durable owner. Runtime maps remain caches and indexes; they are never the only copy of correctness-critical state.

## Evidence from the current implementation

The feature already has more persistence than a memory-only map:

- `ProvenanceRepository` stores `lineage`, `live`, `collisions`, and `audit` in SQLite.
- `ProvenanceWriter` drains a bounded queue and spills critical records to `provenance-spill.log` when the queue is full.
- `LineageStore` is a bounded LRU cache with load-on-miss from SQLite.
- `LiveIndex` is seeded from the durable `live` table during writer installation.
- Block placement memory uses per-dimension `ProvenancePlacementsData` `SavedData` on real server levels.
- Falling-block and Enderman carried placement records are serialized through entity NBT hooks.
- The provenance test suite passes from the isolated planning worktree with:

```text
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
BUILD SUCCESSFUL
```

The remaining risks are persistence-boundary and recovery issues, not the absence of all durable storage:

1. The writer can log “running in-memory only” when SQLite open fails. A queued critical item then reaches `process` and is discarded because the repository is null.
2. A repository failure after a queue batch is removed can lose that batch before the spill journal is involved.
3. `ItemProvenance` mutates `LiveEntry` counts directly in split, consume, and child paths; those paths do not all call `persistLive`.
4. Queue records and spill records can be applied out of order. An older live update can overwrite a newer row because the current upsert has no monotonic revision guard.
5. `COLLISION_SEEN` and the bounded `COLLISIONS` display ring are process-memory state. The durable collision table is not used by `/provenance collisions`, and the same collision pair can be rediscovered after restart.
6. `/provenance audit` reads SQLite synchronously from the command thread instead of using a writer-owned durable snapshot.
7. The production database root is chosen from the first loaded world. A changed primary-world order can select a different database after restart.
8. Hopper locations use `System.identityHashCode(container)`, which is not stable across restarts and can produce false duplicate-location observations.
9. The schema is created with `CREATE TABLE IF NOT EXISTS` but has no explicit version or migration protocol.

## Design principles

- **One owner per state.** SQLite owns global provenance records; world `SavedData` owns block positions; entity NBT owns carried placement context; RAM owns acceleration only.
- **One writer.** Only `ProvenanceWriter` performs SQLite writes. Game-thread hooks enqueue records or append to the spill path when required by backpressure or degraded storage.
- **No silent degradation.** If SQLite is unavailable, critical writes remain queued/spilled and the writer reports a degraded state. The feature never claims durable operation while dropping records.
- **Idempotent recovery.** A database commit followed by a process crash before spill acknowledgement must replay safely without duplicate audit or collision meaning.
- **Stable locations.** A persisted location must identify a player slot, item entity, block/dimension container, or another restart-stable owner. Process-local object identity is not a durable location.
- **Explicit crash boundary.** The default asynchronous mode preserves the existing no-main-thread-JDBC behavior. A critical event is logically accepted when it enters the writer queue or a complete spill frame. Queue-only events can be lost in an abrupt process crash before the writer or spill path receives them. Spill frames and committed SQLite rows survive that boundary. The design does not claim synchronous zero-loss without deliberately adding main-thread file force for every event.

## Durable ownership

The server-owned storage root is:

```text
<server world container>/mintychochip/
  provenance.db
  provenance.db-wal
  provenance.db-shm
  provenance-spill.log
  provenance-spill.log.replay
  provenance-audit.jsonl
  provenance-audit.jsonl.1 ... .3
  migration-complete.json
  writer.lock
```

The current `<primary world>/mintychochip/` directory is a legacy source only. Migration is one-time, verified, resumable, and never merges two independently written stores.

| State | Durable owner | Runtime representation |
|---|---|---|
| Lineage and death markers | SQLite `lineage` | bounded `LineageStore` cache |
| Last-seen UUID/count/location | SQLite `live` | `LiveIndex` |
| Collision history and dedupe keys | SQLite `collisions` plus unique keys | bounded collision display ring |
| Audit history | SQLite `audit` | `AuditLog` hot ring and writer snapshot |
| Block placement parent | per-dimension `SavedData` | `PlacementStore` facade |
| Carried block placement | entity NBT | `CARRIED_BY_ENTITY` map while entity is loaded |
| Queue/recovery records | versioned spill journal | bounded writer queue |

`ItemStack` `MintyProvenance` remains the portable identity and parent hint. It is not the authoritative history store.

## Startup migration protocol

`ProvenanceStorageLayout` owns path resolution and migration. `ProvenanceBootstrap` passes `server.getWorldContainer().toPath()` as the stable root and passes the first world only as the legacy source candidate.

The protocol is:

1. Acquire an exclusive `writer.lock` with `FileChannel.tryLock()`. If another process holds it, do not install a second writer.
2. If `migration-complete.json` exists and its destination checksum set matches the current files, use the new root. Do not merge or replay the legacy backup.
3. If the new root is absent and the legacy root exists, create a staging directory beside the destination. Copy the SQLite database, `-wal`, `-shm`, active and replay spill files, and all audit rotations. Force each copied file and the staging directory, then verify size and SHA-256 against the source.
4. Atomically rename the verified staging directory to the new root when the filesystem supports it. When an atomic directory rename is unavailable, keep the staging marker and use verified per-file moves; never delete the source during this step.
5. Open the copied repository, run schema migration, replay spill, and run the repository integrity check. Only after all three succeed, write `migration-complete.json` containing source path, destination path, timestamp, and copied-file checksums.
6. Rename the legacy directory to `mintychochip.legacy-<timestamp>` and retain it as a recovery backup. The migration marker points to that backup.
7. If both roots exist without a completed marker, resume a matching staging migration. If both roots contain independently changing databases or their checksums do not match the recorded migration state, do not merge them; leave both untouched, report `migration-conflict`, and run provenance in disabled/degraded mode until an operator resolves the conflict.
8. If a completed destination exists alongside a legacy backup, the destination is authoritative. The legacy backup is never replayed into it.

The migration test suite must cover a clean legacy move, restart during staging, completed destination plus backup, and an unresolved two-store conflict.

## SQLite schema and repository

`ProvenanceRepository` uses `PRAGMA user_version` and transactional migrations. Opening the store enables:

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = FULL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

The migration baseline preserves existing rows and adds:

```sql
ALTER TABLE lineage ADD COLUMN updated_seq INTEGER NOT NULL DEFAULT 0;
ALTER TABLE live ADD COLUMN updated_seq INTEGER NOT NULL DEFAULT 0;
ALTER TABLE audit ADD COLUMN event_id TEXT;
ALTER TABLE collisions ADD COLUMN dedupe_key TEXT;

CREATE TABLE IF NOT EXISTS schema_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_event_id
    ON audit(event_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_collision_dedupe_key
    ON collisions(dedupe_key);
CREATE INDEX IF NOT EXISTS idx_live_alive
    ON live(dead, updated_seq);
CREATE INDEX IF NOT EXISTS idx_lineage_updated_seq
    ON lineage(updated_seq);
```

The migration fills legacy audit IDs as `legacy:<seq>` and legacy collision keys as `legacy:<rowid>` before creating the unique indexes. New audit writes carry a UUID event ID; collision writes carry a deterministic key built from UUID, collision kind, existing location, and observed location.

Lineage and live upserts compare `updated_seq` and ignore an older record:

```sql
... ON CONFLICT(id) DO UPDATE SET ...
WHERE excluded.updated_seq > lineage.updated_seq;
```

The repository exposes typed operations rather than swallowing SQL failures:

```java
void upsertLineage(LineageNode node, long sequence) throws SQLException;
void upsertLive(LiveRecord record, long sequence) throws SQLException;
boolean insertCollision(CollisionRecord record, String dedupeKey) throws SQLException;
void insertAudit(UUID eventId, ProvenanceEvent event) throws SQLException;
List<LiveRecord> loadAliveLive() throws SQLException;
List<CollisionRecord> loadRecentCollisions(int limit) throws SQLException;
List<ProvenanceEvent> loadRecentAudit(int limit) throws SQLException;
long maxWriteSequence() throws SQLException;
void runInTransaction(Consumer<ProvenanceRepository> work) throws SQLException;
```

A repository failure is returned to the writer. It does not permanently convert the system into a successful “in-memory” mode.

## Writer and spill protocol

`ProvenanceWriter` owns a monotonically increasing write sequence. It seeds the sequence from `maxWriteSequence()` after spill recovery and assigns a sequence to each lineage/live/collision update. Audit records use a UUID event ID; normal audit IDs are generated at enqueue time, while collision audit IDs derive from the collision dedupe key.

The version-2 spill frame is a JSON record with a sequence and discriminator:

```json
{"v":2,"seq":41,"k":"live","id":"...","item":"minecraft:diamond","location":"player:...:0","count":4,"epoch":1720000000000,"dead":false}
```

`ProvenanceSpillJournal` writes complete newline frames through a synchronized `FileChannel`, calls `force(true)` after an append, and uses the existing active-to-`.replay` seize/ack protocol. Version-1 frames remain readable with a sequence of zero and file order as their ordering source. A truncated final frame is quarantined as an incomplete durability attempt; corruption before the final frame leaves `.replay` unacknowledged and blocks normal recovery.

Writer states are explicit: `RUNNING`, `DEGRADED`, `DRAINING`, and `CLOSED`.

- Queue offer succeeds: the item is owned by the writer but is not yet crash-durable.
- Queue full: append a complete spill frame; critical append failure leaves the item in a blocking retry path and increments a critical-failure metric.
- Repository unavailable or transaction failure: stop applying new items, preserve the failed batch in the spill journal, close the failed connection, and retry repository open with bounded backoff.
- Successful transaction: acknowledge only the seized spill file after commit; update writer-owned durable audit/collision snapshots; then append the optional JSONL mirror.
- Shutdown: stop new producers, drain queue, apply spill, commit, close mirror and repository, and report an incomplete flush if the timeout expires. Remaining spill is intentionally left for next-start recovery.

No critical write is passed through `process` with a null or failed repository. Audit is also spilled while degraded; the existing audit-drop counter is reserved for an actual spill failure.

## Live and lineage state transitions

`ItemProvenance` uses one helper for every mutable live transition:

```java
private static void updateLiveCount(final LiveEntry entry, final int count) {
    if (entry.count() == count) {
        return;
    }
    entry.setCount(count);
    persistLive(entry, false);
}
```

The helper replaces direct count mutations in `observe`, `transfer`, `mintChild`, full-split handling, partial-split handling, `noteConsumed`, and rehydration. Location add/remove operations call `persistLive` exactly once after the accepted location change. Death writes a dead live row and a dead lineage row in the same logical writer batch.

Startup recovery is ordered: acquire the storage lock, open/migrate SQLite, replay spill, seed `LiveIndex`, seed the writer-owned recent audit/collision snapshots, and only then start accepting normal writes. `LineageStore` remains load-on-miss rather than loading the full graph.

## Stable location policy

Add a server-side `ProvenanceLocations` helper for NMS containers. Block entities use a stable key containing dimension identifier, block position, and slot. Entity-owned locations use entity UUID. Player slots remain `player:<uuid>:<slot>`.

The current HopperBlockEntity `System.identityHashCode(container)` labels are replaced with the helper. Generic containers that expose no restart-stable owner use `StackLocation.unknown()` rather than a process-local concrete label. The `golem_hand` and `hopper-in` labels are likewise transient and are not treated as durable collision locations; the final block/entity/player observation supplies the stable location.

This prevents a restart from turning a legitimate move into a false collision merely because a Java object received a different identity hash.

## Placement and carried state

`ProvenancePlacementsData` remains the owner for block positions. Its existing codec, dirty marking, piston move, and per-dimension storage stay in place. The plan adds a server-level round-trip test that loads the same `SavedData` through the world data storage and confirms place, piston move, break recovery, and clear behavior across a save/reopen boundary.

`saveCarriedTo` and `loadCarriedFrom` remain the entity serialization boundary. `loadCarriedFrom` first removes any stale map entry for the entity UUID, then loads a validated record. Falling-block removal paths already call `discardCarried`; the Enderman removal path must do the same when an Enderman dies or is discarded while carrying a block. Entity NBT tests cover save/load, malformed UUID, missing record clearing, placement completion, and discarded carry cleanup.

The test-only `PlacementStore` string map remains available for pure tests but is never used for a real `ServerLevel`.

## Admin and observability

`ProvenanceWriter` maintains bounded durable snapshots for recent audit and collisions. `/provenance audit` and `/provenance collisions` read those snapshots without issuing SQLite queries on the command thread. The command reports whether the snapshot is durable-only or includes pending queue entries.

`status()` includes:

```text
state=running|degraded|draining|closed
queue-depth=N
spill-bytes=B
critical-pending=N
critical-failures=N
audit-dropped=N
last-commit-ms=T
store-root=...
last-error=...
```

The status explicitly distinguishes a healthy SQLite store, a recovering spill, a migration conflict, and a disabled writer.

## Acceptance criteria

1. A legacy primary-world store migrates exactly once to the stable server root, with checksums, a completion marker, and a retained recovery backup.
2. A second startup with both a completed destination and legacy backup uses only the destination; two unresolved stores are never merged.
3. SQLite schema upgrades are idempotent and preserve all pre-existing lineage, collision, audit, and live rows.
4. A repository failure does not discard a dequeued critical batch; the batch remains in spill and is applied after recovery.
5. A stale live or lineage record cannot overwrite a newer sequence after queue/spill reordering.
6. Every live count and accepted location transition reaches the durable writer path.
7. After restart, `LiveIndex`, audit snapshots, and collision snapshots reproduce durable state without requiring the previous JVM's maps.
8. Replaying a committed spill does not duplicate audit events or collision meaning.
9. Placement `SavedData` and carried entity NBT survive their respective save/reopen boundaries.
10. `/provenance audit` and `/provenance collisions` do not perform direct SQLite reads on the server command thread.
11. The default asynchronous mode documents its queue-only crash window; committed SQLite rows and complete forced spill frames survive process death.
12. The complete provenance suite and the migration/recovery tests pass.

## Out of scope

- Synchronous file force for every game-thread provenance event.
- Replacing SQLite with a fully event-sourced ledger.
- A durable wipe command; runtime cache clearing remains separate from deleting durable history.
- Entity provenance for mobs themselves.
- Audit retention pruning.
- Hard quarantine or automatic deletion after a collision.
