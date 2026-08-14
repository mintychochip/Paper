# Item provenance quality — design

**Status:** accepted
**Package:** `dev.mintychochip.provenance`
**Date:** 2026-08-14
**Depends on:** `2026-08-07-item-provenance-design.md`, `2026-08-08-provenance-durable-store-design.md`, `2026-08-10-provenance-persistence-hardening-design.md`

## Goal

Improve item provenance gameplay correctness without changing normal Minecraft item behavior. A provenance hook must not lose an item, reject a valid interaction, invent lineage, produce a false collision, or leave the live census inconsistent with the resulting stack state.

Correctness is the primary target. Hook coverage, operational diagnostics, and hot-path cost are addressed where they directly support correctness.

## Existing feature inventory

The current system already provides:

- A UUID in `CUSTOM_DATA` under `MintyProvenance` for each live stack identity.
- Sources for block drops and recovery, crafting, splitting, merging, smelting, trades, loot, entity drops, gifts, and special recipes.
- Parent UUID lists and durable lineage explanation.
- A live UUID/count/location census with duplicate-location and duplicate-merge collision records.
- Placement memory that follows pistons, falling blocks, and Endermen.
- SQLite-backed lineage, live, audit, and collision state; ordered asynchronous writes and spill recovery.
- Hooks across inventory/container merges, menus, crafting and processors, item entities, loot, equipment, bundles, block placement, consumption, and destruction.
- `/provenance` inspection, history, collision, audit, status, and synthetic-dupe facilities.

The feature is broad. Its quality risk is not a missing core model; it is that many independent NMS hooks reproduce transition logic and transient-location choices. That makes cancellation ordering, quantity changes, lineage, and live-location updates inconsistent at interaction boundaries.

## Evidence and current risks

Current hooks use a mixture of `birth`, `birthIfAbsent`, `ensure`, `observe`, `transfer`, `mintChild`, `afterContainerMerge`, and direct live-entry mutation. Several hooks independently capture UUIDs, calculate moved counts, mutate vanilla stacks, and then repair provenance state.

Concrete risks visible in the current source include:

1. Menu drag handling writes a live count through `LiveEntry.setCount` instead of the durable transition helper.
2. Some holder labels are transient or underspecified, such as `player:<uuid>` without a slot, `menu-slot:<index>`, `player-inv-incoming`, `golem_hand`, and coordinate-only dispenser labels. These must not participate in durable duplicate-location decisions.
3. Merge hooks require callers to calculate moved counts and supply pre-merge identities consistently. A caller error can retire the wrong identity or create incorrect lineage while the underlying item transfer succeeds.
4. Transformation hooks are distributed across menu and block-entity implementations, so event cancellation and preflight ordering must be validated per mutation family.
5. The current test suite strongly covers the pure engine and persistence, but fewer tests exercise actual NMS interaction boundaries and cancellation paths.

## Decision

Use an invariant-owned transition gateway for mutation families, while preserving the existing public DTOs, durable writer, and thin vanilla-hook policy.

The gateway is server-internal. It records immutable pre-state before a vanilla operation and applies provenance from actual post-state only after the operation succeeds. Existing hooks migrate by mutation family rather than through a repository-wide rewrite.

A hook-by-hook repair would produce smaller initial edits but retain duplicated semantics. An automatic full-world census could detect some damage after the fact, but cannot reconstruct trustworthy lineage and would impose uncontrolled tick cost. A bounded reconciliation command is useful only as an operator diagnostic and repair tool.

## Transition contract

The gateway recognizes these logical transitions:

| Transition | Identity rule | Quantity rule |
|---|---|---|
| Birth | Mint one UUID | Result count becomes live count |
| Move | Preserve UUID | Count must equal actual destination stack count |
| Split | Preserve parent UUID; mint partial child | Parent and child post-counts must equal pre-count |
| Merge | Mint one derived target from ordered target/source parents | Target growth equals source shrink |
| Transform | Mint one result from ordered input parents | Inputs use their actual post-counts; emptied inputs die |
| Consume | Preserve UUID while non-empty; retire at zero | Live count equals post-count |
| Destroy | Retire UUID | No live location remains |
| Park | Preserve or ensure UUID at a stable holder | Count unchanged except for the vanilla operation |
| Observe | Preserve UUID; compare stable simultaneous holders | Unknown/transient holders cannot collide |

Every transition has an immutable pre-state and a post-state application step. The pre-state contains only data required to verify the transition: stamp, item identifier, count, and stable location when available. The post-state is read from the actual stacks after the vanilla operation.

## Invariants

1. Provenance never changes whether vanilla accepts, cancels, or completes an item operation.
2. A cancelled or failed operation produces no lineage, death, count, or location mutation.
3. A move preserves identity and replaces its stable location; it is not an observation at a second simultaneous holder.
4. A partial split mints exactly one child identity; a full extraction moves the original identity.
5. A merge mints exactly one derived target identity after positive target growth and matching source shrink.
6. A same-UUID merge records `DUPLICATE_MERGE` and does not mint a laundering identity.
7. A transform records ordered, de-duplicated input identities and updates each input from its observed post-state.
8. Every accepted live-count or stable-location change enters the existing durable writer path exactly once.
9. Unknown and transient locations are bookkeeping hints only and never evidence of simultaneous possession.
10. Hooks do not guess when pre/post state violates the expected transition. They leave item behavior intact, emit a bounded diagnostic, and avoid fabricating lineage.

## Components

### Transition snapshots

Add small immutable server-side snapshot types scoped to the transition gateway. They capture stamp UUID, item identifier, count, and location. Snapshots do not retain mutable `ItemStack` references beyond the immediate operation boundary.

### Mutation-family operations

Expose focused operations rather than one generic event object:

- capture/apply split
- capture/apply merge
- capture/apply transform inputs
- apply move/park/consume/destroy

The existing `ItemProvenance` façade remains the integration point. Internally it delegates common state changes to one live-transition helper so hooks cannot bypass persistence.

### Location classification

Extend the server-side location helpers so every location is explicitly stable or transient.

Stable locations include player UUID plus slot, item-entity UUID, entity UUID plus equipment slot, and dimension/block-position/container slot. Transient locations include cursor, incoming transfer, machine work buffer, golem hand, and unknown menu staging. Transient locations may appear in audit text but cannot create `DUPLICATE_LOCATION` records or be persisted as authoritative restart locations.

### Diagnostics and reconciliation

Add bounded transition-invariant diagnostics to provenance status. Store a count and recent fixed-capacity records; do not log every repeated violation.

Add an explicit admin reconciliation operation for loaded players and loaded item entities. It reports missing stamps, dead UUIDs still present, count disagreement, and simultaneous stable holders. Repair mode may mint a legacy replacement for missing/dead stamps and persist observed counts. It must not invent parent lineage. Automatic world-wide scanning is out of scope.

## Migration sequence

Migrate the smallest high-risk families first:

1. Remove direct live-entry count mutation from menu drag handling and route it through the durable count transition.
2. Classify transient locations and prevent them from becoming collision evidence or durable authoritative holders.
3. Introduce a tested merge transition snapshot and migrate container/item-entity merge hooks that currently duplicate moved-count arithmetic.
4. Introduce transform snapshots for crafting and processing cancellation/preflight boundaries.
5. Add bounded reconciliation only after transition invariants are reliable.

Each family remains a separate atomic change with its own regression tests. Paperweight mappings/NMS bindings are used only if the applied source lacks a required pre/post hook; owned server code remains normal source and vanilla changes remain thin patch-managed hooks.

## Error handling

- Empty or disabled stacks remain no-ops.
- Missing stamps at legitimate economy boundaries use the existing `LEGACY` birth path.
- A dead UUID found on a live stack receives a replacement identity through existing rehydration semantics; the retired identity is not resurrected.
- Quantity mismatch or unexpected item-type change records an invariant diagnostic and performs no speculative lineage mutation.
- Writer degradation follows the existing spill/recovery contract; transition code does not add synchronous game-thread JDBC or file forcing.
- Diagnostic or provenance failure never discards, duplicates, or blocks the vanilla item operation.

## Testing

### Contract tests

Cover:

- move identity continuity;
- partial and full split conservation;
- full and partial merge conservation and ordered parents;
- same-UUID merge collision without laundering;
- transformation input post-count and death handling;
- cancelled/failed operation producing no provenance mutation;
- transient locations not creating collisions;
- every count transition reaching the durable writer;
- stale and dead UUID rehydration behavior.

### NMS integration tests

Exercise representative boundaries:

- cursor and drag distribution;
- shift-click craft preflight and rejected transfer;
- partial inventory, hopper, menu-slot, bundle, and item-entity merges;
- creative slot replacement and rejected creative drop;
- furnace/campfire and workstation transforms;
- use remainder and fuel remainder identity handoff;
- block placement/recovery and carried entity save/load.

Tests assert observable stack counts, identities, lineage, live records, and collision records—not source-hook presence.

### Runtime verification

Run the complete `org.bukkit.support.suite.ProvenanceTestSuite`. Then launch a local Alkahest server and exercise at least split/merge, shift-click craft, hopper transfer, item drop/pickup, and placement/break recovery while checking `/provenance inspect`, `status`, and collisions.

Performance measurement is required only for a changed hot path. The transition gateway must avoid retaining stack copies or allocating general-purpose event maps per mutation.

## Acceptance criteria

1. Normal item interactions retain their vanilla success, cancellation, counts, and results with provenance enabled.
2. Menu drag no longer bypasses durable live-count persistence.
3. Transient holders cannot produce durable false duplicate-location collisions.
4. Migrated merge hooks derive provenance from verified pre/post state and reject inconsistent arithmetic without altering items.
5. Failed or cancelled migrated operations produce no provenance state transition.
6. Every accepted migrated count/location transition reaches the existing ordered writer exactly once.
7. Regression tests cover the changed interaction boundaries and fail for the prior defect.
8. The complete provenance test suite passes.
9. Representative interactions work on a running local Alkahest server.

## Out of scope

- Hard quarantine or automatic deletion of colliding items.
- Entity provenance for mobs.
- Synchronous durability for every item mutation.
- Automatic full-world or per-tick census scans.
- Replacing SQLite or the existing writer/spill protocol.
- Broad refactoring of unrelated Paper or Moonrise code.
