# Item Provenance Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make migrated item transitions preserve vanilla behavior while maintaining durable, collision-safe provenance state from verified post-operation stacks.

**Architecture:** Keep `ItemProvenance` as the server integration façade, but move count changes and merge validation behind focused transition methods. Treat `StackLocation.TRANSIENT` as a non-authoritative hint, then migrate high-risk hooks in small families with behavior-level regression tests.

**Tech Stack:** Java 25, Paper/Alkahest applied NMS sources, JUnit 5, Mockito, Gradle Paper test suites, SQLite-backed existing provenance writer.

## Global Constraints

- Normal Minecraft item success, cancellation, counts, and results MUST remain unchanged.
- Owned implementation stays under `paper-server/src/main/java/dev/mintychochip/provenance/`; only thin NMS hooks belong in `paper-server/src/minecraft/java/net/minecraft/`.
- Any NMS edit MUST be followed by `./gradlew fixupSourcePatches` and `./gradlew rebuildPatches`.
- No synchronous JDBC or file forcing on the game thread.
- Unknown and transient holders MUST NOT create duplicate-location collision evidence or become authoritative durable locations.
- A failed transition MUST leave vanilla stacks untouched and MUST NOT fabricate lineage.
- Each production change and its tests form one green atomic commit.

---

### Task 1: Make transient locations non-authoritative

**Files:**
- Modify: `alkahest-api/src/main/java/dev/mintychochip/provenance/StackLocation.java:86-89`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/provenance/LiveEntry.java:50-76`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceInvariantTest.java`

**Interfaces:**
- Consumes: existing `StackLocation.LocationKind` values.
- Produces: `StackLocation.isConcrete()` returns `true` only for `PLAYER_SLOT` and `ITEM_ENTITY`; existing `LiveEntry` location methods automatically exclude `TRANSIENT` and `UNKNOWN`.

- [ ] **Step 1: Add failing transient-location tests**

Add tests that birth or observe one stamped stack at two labels and assert no collision and no authoritative live location:

```java
@Test
public void transientLocationsCannotCreateCollisionEvidence() {
    final ItemStack stack = new ItemStack(Items.DIAMOND, 4);
    ItemProvenance.birth(stack, ProvenanceSource.LOOT, StackLocation.labeled("incoming")).orElseThrow();

    assertEquals(StackLocation.unknown(), ItemProvenance.live()
        .get(StackStamp.readId(stack).orElseThrow()).orElseThrow().location());
    assertFalse(ItemProvenance.observe(stack.copy(), StackLocation.labeled("menu-slot:0")));
    assertTrue(ItemProvenance.collisions().isEmpty());
}
```

Also add direct assertions:

```java
assertFalse(StackLocation.labeled("cursor").isConcrete());
assertFalse(StackLocation.unknown().isConcrete());
assertTrue(StackLocation.playerSlot(PLAYER, 0).isConcrete());
assertTrue(StackLocation.itemEntity(UUID.randomUUID()).isConcrete());
```

- [ ] **Step 2: Verify the regression fails**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ProvenanceInvariantTest.transientLocationsCannotCreateCollisionEvidence'
```

Expected: FAIL because `TRANSIENT` currently returns concrete and is stored in the live census.

- [ ] **Step 3: Restrict concrete locations**

Replace `StackLocation.isConcrete()` with:

```java
public boolean isConcrete() {
    return this.kind == LocationKind.PLAYER_SLOT || this.kind == LocationKind.ITEM_ENTITY;
}
```

Update its Javadoc to say concrete means restart-stable and suitable for collision evidence. Keep `display()` unchanged so transient labels remain useful in audit and explain output.

- [ ] **Step 4: Run focused and complete tests**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ProvenanceInvariantTest'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the location semantics**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/provenance/StackLocation.java \
  paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceInvariantTest.java
git commit -m "fix(provenance): exclude transient holders from collisions"
```

---

### Task 2: Route all live-count updates through persistence

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java:1009-1017`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/inventory/AbstractContainerMenu.java:518-527`
- Modify generated patch: `paper-server/patches/sources/net/minecraft/world/inventory/AbstractContainerMenu.java.patch`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`

**Interfaces:**
- Consumes: `ItemProvenance.live()` and existing writer installation used by persistence tests.
- Produces: public `ItemProvenance.updateLiveCount(UUID id, int count)`; this is the only hook-facing live-count mutation method and delegates to the existing entry helper.

- [ ] **Step 1: Add a failing count-transition test**

Install the existing test writer fixture used elsewhere in `ItemProvenanceTest`, birth a stack, call the new UUID/count API, flush the writer, and assert both in-memory and repository live counts equal the new value. Use the repository setup helpers already present later in the file; do not introduce a second fixture convention.

Core assertions:

```java
ItemProvenance.updateLiveCount(id, 2);
assertEquals(2, ItemProvenance.live().get(id).orElseThrow().count());
assertEquals(2, repository.loadAliveLive().stream()
    .filter(record -> record.id().equals(id))
    .findFirst().orElseThrow().count());
```

- [ ] **Step 2: Verify the new API is absent**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest.liveCountTransitionPersists'
```

Expected: test compilation fails because `updateLiveCount(UUID, int)` does not exist.

- [ ] **Step 3: Add the hook-facing count method**

Add beside the existing package-private entry overload:

```java
public static void updateLiveCount(final @NotNull UUID id, final int count) {
    if (!enabled || count < 0) {
        return;
    }
    LIVE.get(id).ifPresent(entry -> updateLiveCount(entry, count));
}
```

The method does not create an entry or resurrect a dead UUID. Zero is permitted only as observed state; existing death paths remain responsible for retirement.

- [ ] **Step 4: Replace the direct menu mutation**

In the drag remainder branch replace:

```java
ItemProvenance.live().get(dragParent.get()).ifPresent(e -> e.setCount(remainingAfterDrag));
```

with:

```java
ItemProvenance.updateLiveCount(dragParent.get(), remainingAfterDrag);
```

Do not change drag distribution or item arithmetic.

- [ ] **Step 5: Rebuild the NMS patch and verify**

Run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest.liveCountTransitionPersists'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: patch rebuild and both test commands succeed.

- [ ] **Step 6: Commit durable drag count updates**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java \
  paper-server/src/minecraft/java/net/minecraft/world/inventory/AbstractContainerMenu.java \
  paper-server/patches/sources/net/minecraft/world/inventory/AbstractContainerMenu.java.patch \
  paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java
git commit -m "fix(provenance): persist menu drag remainder counts"
```

---

### Task 3: Validate merges from captured pre-state and actual post-state

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/provenance/MergeTransition.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java:651-705`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceInvariantTest.java`

**Interfaces:**
- Consumes: `StackStamp.readId`, `ItemProvenance.ensure`, existing `afterContainerMerge` semantics.
- Produces:

```java
public record MergeTransition(
    UUID targetId,
    UUID sourceId,
    String itemId,
    int targetCountBefore,
    int sourceCountBefore,
    StackLocation targetLocation,
    StackLocation sourceLocation
) {
    public static Optional<MergeTransition> capture(
        ItemStack target,
        ItemStack source,
        StackLocation targetLocation,
        StackLocation sourceLocation
    );

    public boolean matches(ItemStack targetAfter, ItemStack sourceAfter);
    public int amountMoved(ItemStack targetAfter, ItemStack sourceAfter);
}
```

`ItemProvenance.applyMerge(MergeTransition, ItemStack, ItemStack)` applies lineage only when `matches` is true. Keep `afterContainerMerge` temporarily as an internal delegate only until all callers migrate; do not create a public compatibility shim.

- [ ] **Step 1: Add failing merge-transition contract tests**

Cover full and partial merges, no movement, target/source arithmetic mismatch, item-type change, and same-UUID duplicate merge. Representative test:

```java
final MergeTransition transition = MergeTransition.capture(target, source, HAND, CHEST).orElseThrow();
target.grow(5);
source.shrink(5);
assertTrue(transition.matches(target, source));
assertEquals(5, transition.amountMoved(target, source));
assertFalse(ItemProvenance.applyMerge(transition, target, source));
```

For mismatch, grow the target by five and shrink the source by four; assert `matches` and `applyMerge` are false, both original live identities remain, and no `MERGE` lineage node appears.

- [ ] **Step 2: Verify transition type is absent**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest.mergeTransitionRejectsMismatchedCounts'
```

Expected: test compilation fails because `MergeTransition` and `applyMerge` do not exist.

- [ ] **Step 3: Implement immutable merge capture**

Implement `MergeTransition` as a final record. `capture` rejects empty stacks, calls `ItemProvenance.ensure` for both stacks, stores pre-counts and `ItemProvenance.itemId(target)`, and returns empty when item IDs differ. `matches` requires:

```java
int targetGrowth = targetAfter.getCount() - this.targetCountBefore;
int sourceShrink = this.sourceCountBefore - sourceAfter.getCount();
return targetGrowth > 0
    && targetGrowth == sourceShrink
    && ItemProvenance.itemId(targetAfter).equals(this.itemId)
    && (sourceAfter.isEmpty() || ItemProvenance.itemId(sourceAfter).equals(this.itemId))
    && StackStamp.readId(targetAfter).filter(this.targetId::equals).isPresent()
    && (sourceAfter.isEmpty() || StackStamp.readId(sourceAfter).filter(this.sourceId::equals).isPresent());
```

- [ ] **Step 4: Implement merge application**

Add:

```java
public static boolean applyMerge(
    final @NotNull MergeTransition transition,
    final @NotNull ItemStack targetAfter,
    final @NotNull ItemStack sourceAfter
) {
    if (!enabled || !transition.matches(targetAfter, sourceAfter)) {
        return false;
    }
    return afterContainerMerge(
        targetAfter,
        sourceAfter,
        Optional.of(transition.targetId()),
        Optional.of(transition.sourceId()),
        transition.amountMoved(targetAfter, sourceAfter),
        transition.sourceLocation(),
        transition.targetLocation()
    );
}
```

Keep the existing lineage/death logic unchanged in this task.

- [ ] **Step 5: Run merge and full suite tests**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest' \
  --tests 'dev.mintychochip.provenance.ProvenanceInvariantTest'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: both commands succeed.

- [ ] **Step 6: Commit the merge gateway**

```bash
git add paper-server/src/main/java/dev/mintychochip/provenance/MergeTransition.java \
  paper-server/src/main/java/dev/mintychochip/provenance/ItemProvenance.java \
  paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java \
  paper-server/src/test/java/dev/mintychochip/provenance/ProvenanceInvariantTest.java
git commit -m "feat(provenance): validate merge transitions"
```

---

### Task 4: Migrate inventory and item-entity merge hooks

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/player/Inventory.java:313-360`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/item/ItemEntity.java:319-346`
- Modify generated patches:
  - `paper-server/patches/sources/net/minecraft/world/entity/player/Inventory.java.patch`
  - `paper-server/patches/sources/net/minecraft/world/entity/item/ItemEntity.java.patch`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`

**Interfaces:**
- Consumes: `MergeTransition.capture(...)`, `ItemProvenance.applyMerge(...)` from Task 3.
- Produces: inventory and item-entity merges no longer calculate or pass `amountMoved` independently.

- [ ] **Step 1: Add interaction-shaped regression tests**

Add tests for a partial inventory merge and a partial item-entity merge using the same call order as the hooks: capture, mutate, apply. Assert target `MERGE` parents, source remainder identity/count, old target death, and no collision. Add a no-op case where the target is full and assert no lineage changes.

- [ ] **Step 2: Verify tests fail before hook migration where observable**

Run the new focused tests. Expected: contract tests pass through the Task 3 gateway, establishing the migration target; inspect current hooks to confirm they still call `afterContainerMerge` directly before editing.

- [ ] **Step 3: Migrate `Inventory.addResource`**

Capture one nullable transition before mutation:

```java
final MergeTransition provenanceMerge = mergingIntoExisting
    ? MergeTransition.capture(itemStackInSlot, itemStack, targetLocation, sourceLocation).orElse(null)
    : null;
```

After mutation call `ItemProvenance.applyMerge(provenanceMerge, itemStackInSlot, remainingView)` when non-null. Preserve the existing transfer path for insertion into an empty slot.

- [ ] **Step 4: Migrate `ItemEntity.merge`**

Capture before vanilla merge using item-entity target/source locations, perform the vanilla merge, then call `applyMerge` with the resulting stacks. Remove local UUID optionals, source-count arithmetic, and moved-count calculation made redundant by the snapshot.

- [ ] **Step 5: Rebuild patches and verify**

Run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: patch rebuild and both test commands succeed.

- [ ] **Step 6: Commit migrated merge hooks**

```bash
git add paper-server/src/minecraft/java/net/minecraft/world/entity/player/Inventory.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/item/ItemEntity.java \
  paper-server/patches/sources/net/minecraft/world/entity/player/Inventory.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/item/ItemEntity.java.patch \
  paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java
git commit -m "refactor(provenance): use verified merge snapshots"
```

---

### Task 5: Migrate container and menu merge hooks

**Files:**
- Modify NMS sources containing direct `afterContainerMerge` calls, identified with:
  `paper-server/src/minecraft/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java`,
  `paper-server/src/minecraft/java/net/minecraft/world/inventory/Slot.java`,
  `paper-server/src/minecraft/java/net/minecraft/world/inventory/AbstractContainerMenu.java`,
  `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java`,
  and `paper-server/src/minecraft/java/net/minecraft/world/item/component/BundleContents.java` when present in the applied tree.
- Modify corresponding generated files under `paper-server/patches/sources/net/minecraft/`.
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/ItemProvenanceTest.java`
- Test: `paper-server/src/test/java/dev/mintychochip/provenance/CraftingMenuProvenanceTest.java`

**Interfaces:**
- Consumes: `MergeTransition.capture(...)` and `ItemProvenance.applyMerge(...)`.
- Produces: all remaining merge hooks use the same verified pre/post contract; no direct NMS caller invokes `afterContainerMerge`.

- [ ] **Step 1: Inventory exact remaining callers**

Use repository search for `afterContainerMerge(` under `paper-server/src/minecraft/java`. Record each caller in the commit body or review notes. Do not migrate unrelated birth, split, or transform hooks.

- [ ] **Step 2: Add regression cases for each distinct merge shape**

Add one test per behavior family: container partial merge, slot/cursor full merge, drag distribution into an occupied slot, copper-golem partial deposit, and bundle insertion. Assertions cover item counts, source remainder identity, ordered parents, and absence of false collision.

- [ ] **Step 3: Migrate each caller mechanically**

At each site:

1. Construct stable locations when the owner and slot are known; otherwise use a descriptive transient label.
2. Capture `MergeTransition` immediately before the vanilla count mutation.
3. Perform the vanilla operation unchanged.
4. Call `ItemProvenance.applyMerge` with the actual post-operation stacks.
5. Delete UUID optionals and moved-count arithmetic used only by provenance.

Never move provenance capture before a cancellable Bukkit event whose cancellation prevents the mutation.

- [ ] **Step 4: Confirm no direct merge callers remain**

Search `paper-server/src/minecraft/java` for `afterContainerMerge(`. Expected: zero callers. The method may remain package-private inside `ItemProvenance` as the gateway implementation.

- [ ] **Step 5: Rebuild and run integration suites**

Run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
./gradlew :paper-server:test --tests 'dev.mintychochip.provenance.ItemProvenanceTest' \
  --tests 'dev.mintychochip.provenance.CraftingMenuProvenanceTest'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
```

Expected: no patch drift and all tests succeed.

- [ ] **Step 6: Commit the remaining merge migration**

Stage only the searched merge-hook sources, their generated patches, and the behavior tests:

```bash
git commit -m "refactor(provenance): unify container merge tracking"
```

---

### Task 6: Verify runtime item behavior

**Files:**
- Modify only if runtime verification reveals a reproducible defect; production fix and regression test stay in one separate atomic commit.

**Interfaces:**
- Consumes: completed transition changes.
- Produces: direct runtime evidence for representative item interactions.

- [ ] **Step 1: Build the runnable server artifact**

Run:

```bash
./gradlew createPaperclipJar
```

Expected: `BUILD SUCCESSFUL` and an Alkahest paperclip jar under the project build outputs.

- [ ] **Step 2: Launch the local server through the harness process manager**

Use the existing accepted EULA workdir and the produced jar. Wait for the server-ready log line before interaction. Do not run the server as a blocking shell command.

- [ ] **Step 3: Exercise representative transitions**

Using the existing operator account or console commands, exercise:

1. give two compatible stacks and merge them in inventory;
2. split a stack, drop the child, and pick it up;
3. shift-click a craft result into a partially occupied stack;
4. move items through a hopper into a partially occupied container slot;
5. place and break a tracked block stack.

After each interaction, use `/provenance inspect` on the resulting stack and `/provenance collisions`. Expected: counts and items match vanilla results, lineage explains the transition, and no collision appears for legitimate moves.

- [ ] **Step 4: Run final automated verification**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
./gradlew createPaperclipJar
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 5: Review repository state**

Run `git status --short` and inspect staged/unstaged diffs. Expected: clean worktree, except intentional local runtime files remain ignored. Confirm each commit contains one green concern and generated patches correspond only to edited NMS files.
