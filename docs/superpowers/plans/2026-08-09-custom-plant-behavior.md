# Behavior-Backed Custom Plants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional plant capability to custom block definitions so custom crop and sapling carriers receive API-owned growth and bonemeal plans without inheriting unintended vanilla carrier behavior.

**Architecture:** `CustomBlockDefinition` gains an optional `CustomPlantBehavior` and a `PLANT` host selected by `PlantHostSpec`. Immutable API contexts describe the carrier snapshot, cause, stage, actor, and climate; immutable result plans describe default, denial, no-op, single-state, or bounded structure growth. A server router invokes the receiver once, validates the plan, preserves custom identity/provenance, and applies single-state plans through Bukkit growth events or custom structures through detached block-state snapshots. Thin hooks in `CropBlock` and `SaplingBlock` claim only custom plant identities; ordinary vanilla crops and saplings continue through the existing ecology and Bukkit paths. Custom particles remain the already-implemented sibling behavior track and are regression-tested, not reimplemented here.


**Tech Stack:** Java 25, Gradle, `alkahest-api`, `paper-server`, Bukkit API, CraftBukkit growth events, Minecraft source patches, JUnit 5, Mockito where existing server tests use it.

## Global Constraints

- API code remains NMS-free: no `net.minecraft.*`, CraftBukkit, packet, or server implementation imports under `alkahest-api/src/main/java/dev/mintychochip/`.
- API contexts contain immutable snapshots and request data; receivers never receive mutable `Block`, `World`, `Location`, `Player`, `BlockData`, `BlockState`, `ServerLevel`, or `RandomSource` handles.
- `CustomBlockDefinition` remains the sole logical identity; do not add a second plant catalog, PDC key, placement lookup, or carrier identity.
- `PLANT` uses a vanilla crop or sapling carrier; `Block#getType()` and `ItemStack#getType()` remain the carrier values.
- A custom plant with the default behavior is inert for random growth and bonemeal; it does not inherit the carrier's growth algorithm.
- Carrier placement and support/survival behavior remain the fallback; this slice does not add a mutable physics/survival callback.
- Growth results use explicit tagged plans; `null` never means default, denial, or no-op.
- Structure plans are immutable, have at most 4096 changes, have no duplicate offsets, and keep every relative coordinate component within ±32.
- Native ordinary-block registration for `minecraft:wheat`, `minecraft:oak_sapling`, and similar blocks is deferred; only custom-block identities are claimed.
- Vanilla Minecraft source changes are patch-managed: edit applied `src/minecraft/java/net/minecraft/...`, then run `fixupSourcePatches` and `rebuildPatches`.
- Existing custom particle behavior/transport is a sibling dependency. Do not modify its transport boundary while implementing plants.
- Tests must defend observable behavior and use JDK 25 with the existing Gradle test tasks.
- The current checkout contains unrelated dirty work. Stage only exact new files and narrow plant hunks; never stage broad custom-block/catalog directories.

---

### Task 1: Add plant host metadata

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantKind.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantHostSpec.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockHostType.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/HostSpec.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockPlacement.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomPlantHostSpecTest.java`

**Interfaces:**
- Consumes the existing `HostSpec`, `BlockHostType`, and `CustomBlockDefinition#carrierMaterial()` contracts.
- Produces `BlockHostType.PLANT`, `PlantKind.CROP|SAPLING`, `PlantHostSpec.crop(Material)`, `PlantHostSpec.sapling(Material)`, and carrier-aware custom definitions for later behavior tasks.

- [ ] **Step 1: Write failing host and carrier tests**

```java
@Test
void cropAndSaplingFactoriesExposePlantHostMetadata() {
    final PlantHostSpec crop = PlantHostSpec.crop(Material.WHEAT);
    final PlantHostSpec sapling = PlantHostSpec.sapling(Material.OAK_SAPLING);

    assertEquals(BlockHostType.PLANT, crop.type());
    assertEquals(PlantKind.CROP, crop.kind());
    assertEquals(Material.WHEAT, crop.carrier());
    assertEquals(PlantKind.SAPLING, sapling.kind());
    assertEquals(Material.OAK_SAPLING, sapling.carrier());
}

@Test
void plantHostRejectsNullAndAirCarriers() {
    assertThrows(NullPointerException.class, () -> PlantHostSpec.crop(null));
    assertThrows(IllegalArgumentException.class, () -> PlantHostSpec.crop(Material.AIR));
}

@Test
void definitionReportsSelectedPlantCarrier() {
    final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:crop")
        .host(PlantHostSpec.crop(Material.WHEAT))
        .itemMaterial(Material.WHEAT)
        .build();

    assertEquals(BlockHostType.PLANT, definition.hostType());
    assertEquals(Material.WHEAT, definition.carrierMaterial());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomPlantHostSpecTest'
```

Expected: FAIL because the plant host type and spec do not exist.

- [ ] **Step 2: Implement host metadata and initial carrier state**

Implement the following API shape:

```java
public enum PlantKind {
    CROP,
    SAPLING
}

public record PlantHostSpec(Material carrier, PlantKind kind) implements HostSpec {
    public PlantHostSpec {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(kind, "kind");
        if (carrier == Material.AIR || carrier == Material.CAVE_AIR || carrier == Material.VOID_AIR) {
            throw new IllegalArgumentException("plant carrier must not be air");
        }
    }

    public static PlantHostSpec crop(final Material carrier) {
        return new PlantHostSpec(carrier, PlantKind.CROP);
    }

    public static PlantHostSpec sapling(final Material carrier) {
        return new PlantHostSpec(carrier, PlantKind.SAPLING);
    }

    @Override
    public BlockHostType type() {
        return BlockHostType.PLANT;
    }
}
```

Add `PLANT` to `BlockHostType`, add `PlantHostSpec` to the sealed `HostSpec` permits list, and add the `PLANT` branch to `CustomBlockDefinition#carrierMaterial()`.

In `CustomBlockPlacement.carrierData`, create the selected carrier block data and set `Ageable#setAge(0)` or `Sapling#setStage(0)` when the carrier implements those Bukkit data interfaces. Do not call NMS or inspect native classes from the API spec/host classes.

- [ ] **Step 3: Run host and existing custom-block API tests**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomPlantHostSpecTest' --tests 'dev.mintychochip.customblock.CustomBlockDefinition*'
```

Expected: BUILD SUCCESSFUL with the new host tests and existing definition tests passing.

- [ ] **Step 4: Commit the host metadata unit**

Use narrow staging because `CustomBlockDefinition.java` already contains unrelated dirty work:

```bash
git add alkahest-api/src/main/java/dev/mintychochip/customblock/PlantKind.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlantHostSpec.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockHostType.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/HostSpec.java \
  paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockPlacement.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomPlantHostSpecTest.java
git add -p alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java
git commit -m "Add custom plant host metadata"
```

---

### Task 2: Add immutable plant contexts, plans, and behavior

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthCause.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantBlockChange.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthPlan.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomPlantBehavior.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomPlantBehaviorTest.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockDefinitionPlantTest.java`

**Interfaces:**
- Consumes Task 1 `PlantHostSpec`, Task 1 host metadata, and existing behavior primitives `Decision`, `BlockView`, `BlockDataView`, `ActorView`, and `ValueOverride` conventions.
- Produces `CustomPlantBehavior.defaults()`, `PlantGrowthContext`, `PlantGrowthPlan`, `PlantGrowthResult`, and `CustomBlockDefinition#plantBehavior()` for server routing.

- [ ] **Step 1: Write failing behavior and plan tests**

```java
@Test
void defaultPlantBehaviorUsesInertFallback() {
    final PlantGrowthResult result = CustomPlantBehavior.defaults()
        .onGrowth()
        .receive(sampleContext(PlantGrowthCause.RANDOM_TICK));

    assertEquals(Decision.DEFAULT, result.decision());
    assertEquals(PlantGrowthPlan.Kind.DEFAULT, result.plan().kind());
}

@Test
void handledStateAndStructurePlansAreExplicit() {
    final BlockDataView wheat = new BlockDataView(Material.WHEAT, "minecraft:wheat[age=1]");
    final PlantGrowthPlan state = PlantGrowthPlan.state(wheat);
    final PlantGrowthPlan structure = PlantGrowthPlan.structure(
        List.of(new PlantBlockChange(0, 1, 0, new BlockDataView(Material.OAK_LOG, "minecraft:oak_log")))
    );

    assertEquals(PlantGrowthPlan.Kind.STATE, state.kind());
    assertEquals(PlantGrowthPlan.Kind.STRUCTURE, structure.kind());
}

@Test
void structurePlanRejectsDuplicateOrUnboundedChanges() {
    final PlantBlockChange change = new PlantBlockChange(
        0, 1, 0, new BlockDataView(Material.OAK_LOG, "minecraft:oak_log")
    );

    assertThrows(IllegalArgumentException.class, () ->
        PlantGrowthPlan.structure(List.of(change, change)));
    assertThrows(IllegalArgumentException.class, () ->
        PlantGrowthPlan.structure(
            List.of(new PlantBlockChange(33, 0, 0, change.data()))));
}

@Test
void definitionDefaultsPlantBehaviorWithoutChangingExistingBlockBehavior() {
    final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:plant")
        .host(PlantHostSpec.crop(Material.WHEAT))
        .itemMaterial(Material.WHEAT)
        .build();

    assertNotNull(definition.behavior());
    assertNotNull(definition.plantBehavior());
    assertEquals(Decision.DEFAULT, definition.plantBehavior().onGrowth()
        .receive(sampleContext(PlantGrowthCause.BONEMEAL)).decision());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomPlantBehaviorTest' --tests 'dev.mintychochip.customblock.CustomBlockDefinitionPlantTest'
```

Expected: FAIL because the plant result, context, behavior, and definition capability do not exist.

- [ ] **Step 2: Implement immutable contracts**

Implement these exact public shapes:

```java
public enum PlantGrowthCause {
    RANDOM_TICK,
    BONEMEAL
}

public record PlantGrowthContext(
    BlockView block,
    BlockDataView currentData,
    PlantGrowthCause cause,
    OptionalInt currentStage,
    OptionalInt maximumStage,
    Optional<ActorView> actor,
    Optional<ClimateSample> climate
) {
    public PlantGrowthContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(currentData, "currentData");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(maximumStage, "maximumStage");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(climate, "climate");
        if (currentStage.isPresent() != maximumStage.isPresent()) {
            throw new IllegalArgumentException("current and maximum stage must be present together");
        }
        if (currentStage.isPresent() && currentStage.getAsInt() > maximumStage.getAsInt()) {
            throw new IllegalArgumentException("current stage cannot exceed maximum stage");
        }
    }
}

public record PlantBlockChange(int x, int y, int z, BlockDataView data) {
    public PlantBlockChange {
        Objects.requireNonNull(data, "data");
    }
}
```

`PlantGrowthPlan` must expose `Kind.DEFAULT|NONE|STATE|STRUCTURE`, immutable state/structure accessors, `defaultPlan()`, `none()`, `state(BlockDataView)`, and `structure(List<PlantBlockChange>)`. Enforce the ±32 and 4096 limits and reject duplicate offsets in the structure factory. `state()` returns an `Optional<BlockDataView>`; custom structure plans do not carry a vanilla `TreeType` identity.

`PlantGrowthResult` contains non-null `Decision` and `PlantGrowthPlan`. `defaults()` returns `Decision.DEFAULT` with `PlantGrowthPlan.defaultPlan()`. `deny()` returns `Decision.DENY` with the default plan. `handled(PlantGrowthPlan plan)` returns `Decision.ALLOW` and rejects `DEFAULT` plans.

`CustomPlantBehavior` stores one non-null `GrowthReceiver`, exposes `defaults()`, `builder()`, `onGrowth()`, and `Builder#onGrowth`. The default receiver returns `PlantGrowthResult.defaults()`; the server interprets that as inert custom-plant fallback.

Add a non-null `plantBehavior` field to `CustomBlockDefinition`, pass it through the private constructor, expose `plantBehavior()`, default the builder to `CustomPlantBehavior.defaults()`, and add `Builder#plantBehavior`. Extend the Javadoc to say plant receivers receive snapshots and that `PLANT` host definitions do not inherit carrier growth automatically.

- [ ] **Step 3: Run API behavior and nullness tests**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomPlant*' --tests 'dev.mintychochip.customblock.CustomBlockDefinitionPlantTest' --tests 'dev.mintychochip.behavior.*'
```

Expected: BUILD SUCCESSFUL with explicit default/deny/no-op/state/structure branches and immutable context tests passing.

- [ ] **Step 4: Commit API plant contracts**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthCause.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlantBlockChange.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthPlan.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlantGrowthResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CustomPlantBehavior.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomPlantBehaviorTest.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockDefinitionPlantTest.java
git add -p alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java
git commit -m "Add custom plant behavior contracts"
```

---

### Task 3: Route and apply custom plant plans on the server

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/customblock/CustomPlantBehaviorRouter.java`
- Create: `paper-server/src/main/java/dev/mintychochip/customblock/CustomPlantLifecycle.java`
- Test: `paper-server/src/test/java/dev/mintychochip/customblock/CustomPlantBehaviorRouterTest.java`
- Test: `paper-server/src/test/java/dev/mintychochip/customblock/CustomPlantLifecycleTest.java`

**Interfaces:**
- Consumes Task 2 `CustomPlantBehavior`, `PlantGrowthContext`, `PlantGrowthResult`, `PlantGrowthPlan`, and Task 1 carrier metadata.
- Produces `CustomPlantBehaviorRouter.growthPlan(CustomBlockDefinition, PlantGrowthContext)` and server entry points:

```java
public static boolean handleRandomTick(
    ServerLevel level,
    BlockPos pos,
    BlockState state,
    RandomSource random
);

public static boolean handleBonemeal(
    ServerLevel level,
    BlockPos pos,
    BlockState state,
    RandomSource random
);
```

Both methods return `true` only when a registered custom plant claims the operation. Non-custom or non-plant blocks return `false` so the vanilla caller continues.

- [ ] **Step 1: Write failing router tests**

```java
@Test
void growthReceiverRunsOnceAndReturnsItsPlan() {
    final AtomicInteger calls = new AtomicInteger();
    final CustomBlockDefinition definition = cropDefinition(
        CustomPlantBehavior.builder()
            .onGrowth(context -> {
                calls.incrementAndGet();
                return PlantGrowthResult.handled(PlantGrowthPlan.none());
            })
            .build()
    );

    final PlantGrowthResult result = CustomPlantBehaviorRouter.growthPlan(
        definition,
        sampleContext(PlantGrowthCause.RANDOM_TICK)
    );

    assertEquals(1, calls.get());
    assertEquals(PlantGrowthPlan.Kind.NONE, result.plan().kind());
}

@Test
void nullGrowthResultsAreRejected() {
    final CustomBlockDefinition definition = cropDefinition(
        CustomPlantBehavior.builder().onGrowth(context -> null).build()
    );

    assertThrows(IllegalStateException.class, () ->
        CustomPlantBehaviorRouter.growthPlan(definition, sampleContext(PlantGrowthCause.BONEMEAL)));
}
```

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.CustomPlantBehaviorRouterTest'
```

Expected: FAIL because the router does not exist.

- [ ] **Step 2: Implement pure receiver routing**

Implement `CustomPlantBehaviorRouter` as a plan-only class:

```java
public static @NotNull PlantGrowthResult growthPlan(
    @NotNull final CustomBlockDefinition definition,
    @NotNull final PlantGrowthContext context
) {
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(context, "context");
    final PlantGrowthResult result = definition.plantBehavior().onGrowth().receive(context);
    if (result == null) {
        throw new IllegalStateException(
            "custom plant growth receiver returned null for " + definition.namespacedKey());
    }
    return result;
}
```

Do not inspect or mutate a Bukkit block in this router. It only invokes the receiver once and rejects null results.

- [ ] **Step 3: Implement lifecycle snapshots and plan application**

`CustomPlantLifecycle` must:

1. Convert `ServerLevel`/`BlockPos` into a Bukkit `Block` snapshot and resolve `CustomBlocks.of(block)`.
2. Return `false` for no identity, an unregistered definition, or a definition whose host is not `PLANT`.
3. Validate that the live carrier material matches `PlantHostSpec.carrier()` and its runtime hook family matches `PlantKind`.
4. Build `PlantGrowthContext` with `RANDOM_TICK` or `BONEMEAL`, current serialized block data, stage metadata when the Bukkit data is `Ageable`/`Sapling`, empty actor for the current NMS entry points, and the available `ClimateSample` from `CropEcology` only when the server has a `ServerLevel` sample.
5. Invoke `CustomPlantBehaviorRouter.growthPlan` once.
6. Consume the native operation for every claimed custom plant, including `DEFAULT`, `DENY`, and `NONE`.
7. Resolve `DEFAULT` as inert custom-plant fallback, return without mutation for `DENY`/`NONE`, and validate/apply explicit `STATE` or `STRUCTURE` plans before any world mutation.
8. Apply a `STATE` plan through the existing `BlockGrowEvent` path using a detached Bukkit `BlockState`; reject a state whose material differs from the selected carrier.
9. Apply a `STRUCTURE` plan by materializing detached Bukkit `BlockState` snapshots at relative positions and placing snapshots only after the complete plan validates. Do not fire `StructureGrowEvent` for an arbitrary custom structure without a truthful vanilla species, and do not modify `CustomBlocks.lookup()` for generated vanilla structure blocks.
10. Preserve the origin's custom identity and packet display, and invoke existing cleanup/provenance paths if validation or application fails after mutation begins.

The lifecycle must never pass `ServerLevel`, `BlockPos`, `BlockState`, or `RandomSource` through the API context.

- [ ] **Step 4: Run server routing and lifecycle tests**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.CustomPlantBehaviorRouterTest' --tests 'dev.mintychochip.customblock.CustomPlantLifecycleTest' --tests 'dev.mintychochip.customblock.CustomBlockLifecycleTest'
```

Expected: BUILD SUCCESSFUL; custom default growth claims the carrier without changing it, explicit state/structure plans apply once, cancelled single-state growth events leave the world unchanged, invalid structure plans apply nothing, and existing block lifecycle tests remain green.

- [ ] **Step 5: Commit server plant routing**

```bash
git add paper-server/src/main/java/dev/mintychochip/customblock/CustomPlantBehaviorRouter.java \
  paper-server/src/main/java/dev/mintychochip/customblock/CustomPlantLifecycle.java \
  paper-server/src/test/java/dev/mintychochip/customblock/CustomPlantBehaviorRouterTest.java \
  paper-server/src/test/java/dev/mintychochip/customblock/CustomPlantLifecycleTest.java
git commit -m "Route custom plant growth plans"
```

---

### Task 4: Add crop and sapling vanilla hooks

**Files:**
- Modify applied: `paper-server/src/minecraft/java/net/minecraft/world/level/block/CropBlock.java`
- Modify applied: `paper-server/src/minecraft/java/net/minecraft/world/level/block/SaplingBlock.java`
- Rebuild generated: `paper-server/patches/sources/net/minecraft/world/level/block/CropBlock.java.patch`
- Rebuild generated: `paper-server/patches/sources/net/minecraft/world/level/block/SaplingBlock.java.patch`
- Test: `paper-server/src/test/java/dev/mintychochip/customblock/CustomPlantVanillaHookTest.java`

**Interfaces:**
- Consumes Task 3 `CustomPlantLifecycle.handleRandomTick` and `handleBonemeal`.
- Produces thin hooks that claim only custom `PLANT` identities and leave ordinary crop/sapling execution byte-for-byte on the existing native/ecology/event path.

- [ ] **Step 1: Apply the current Minecraft source tree and write failing hook tests**

Run:

```bash
./gradlew applyPatches
```

Add tests that exercise the server façade with a non-custom block and assert it returns `false`, then exercise a custom plant identity and assert it returns `true` while the receiver records the correct cause:

```java
@Test
void ordinaryVanillaCarrierIsNotClaimed() {
    assertFalse(CustomPlantLifecycle.handleRandomTick(
        serverLevel, cropPosition, wheatState, randomSource));
}

@Test
void bonemealRouteUsesBonemealCauseForCustomPlant() {
    final AtomicReference<PlantGrowthCause> cause = new AtomicReference<>();
    registerCustomPlant(CustomPlantBehavior.builder()
        .onGrowth(context -> {
            cause.set(context.cause());
            return PlantGrowthResult.handled(PlantGrowthPlan.none());
        })
        .build());

    assertTrue(CustomPlantLifecycle.handleBonemeal(
        serverLevel, plantPosition, wheatState, randomSource));
    assertEquals(PlantGrowthCause.BONEMEAL, cause.get());
}
```

Expected: FAIL because the vanilla classes do not call the custom façade.

- [ ] **Step 2: Add the CropBlock hooks**

At the beginning of `CropBlock.randomTick`, before brightness, age, ecology, or growth-speed checks, add the thin custom hook:

```java
if (dev.mintychochip.customblock.CustomPlantLifecycle.handleRandomTick(level, pos, state, random)) {
    return;
}
```

The hook must run before the carrier's maximum-age gate so a custom receiver is not silently blocked by the carrier state. For non-custom blocks it returns `false`, after which all existing carrier/ecology checks run unchanged.

Add the bonemeal hook at the beginning of `growCrops`, before the existing `CropEcology.allowsForcedGrowth` call:

```java
if (level instanceof ServerLevel serverLevel
    && dev.mintychochip.customblock.CustomPlantLifecycle.handleBonemeal(
        serverLevel, pos, state, level.getRandom())) {
    return;
}
```

Keep the existing ecology and CraftBukkit `handleBlockGrowEvent` code unchanged for non-custom crops.

At the beginning of `SaplingBlock.randomTick`, before brightness, ecology, or tree-stage checks, add the same custom hook:

```java
if (dev.mintychochip.customblock.CustomPlantLifecycle.handleRandomTick(level, pos, state, random)) {
    return;
}
```

In `performBonemeal`, add the bonemeal hook before `advanceTree`:

```java
if (dev.mintychochip.customblock.CustomPlantLifecycle.handleBonemeal(level, pos, state, random)) {
    return;
}
```

Leave `advanceTree`, tree capture, and existing `StructureGrowEvent` handling unchanged for ordinary saplings.

- [ ] **Step 4: Rebuild source patches**

Run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

Expected: the generated `CropBlock.java.patch` and `SaplingBlock.java.patch` contain only the two thin `mintychochip` hook groups and the existing unrelated patch content remains intact.

- [ ] **Step 5: Run hook and regression tests**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.CustomPlantVanillaHookTest' --tests 'dev.mintychochip.customblock.CustomPlant*' --tests 'dev.mintychochip.customblock.CustomBlockLifecycleTest'
```

Expected: BUILD SUCCESSFUL; custom crop/sapling carriers claim random tick and bonemeal operations, ordinary carriers continue through ecology/native paths, and cancelled structure/state events do not partially apply.

- [ ] **Step 6: Commit vanilla hooks separately**

```bash
git add paper-server/patches/sources/net/minecraft/world/level/block/CropBlock.java.patch \
  paper-server/patches/sources/net/minecraft/world/level/block/SaplingBlock.java.patch
git commit -m "Hook custom plant growth into crop and sapling ticks"
```

Do not stage the entire `paper-server/src/minecraft` tree.

---

### Task 5: Verify particle sibling behavior and complete documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-08-09-custom-plant-behavior-design.md` only for verified implementation notes.
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java` Javadocs if the final plant accessor wording differs.
- Add or modify: plant API/server test Javadocs only where needed to document snapshot timing.
- Existing sibling verification: `paper-server/src/test/java/dev/mintychochip/particle/CustomParticleTransportTest.java` and suite; do not redesign particle transport.

**Interfaces:**
- Consumes all completed plant contracts, server routes, vanilla hooks, and the existing particle behavior/transport contract.
- Produces a final verification record proving the shared definition → immutable context → explicit plan → server adapter shape across blocks, entities, particles, and plants.

- [ ] **Step 1: Run the focused API suite**

```bash
./gradlew :alkahest-api:test \
  --tests 'dev.mintychochip.behavior.*' \
  --tests 'dev.mintychochip.customblock.*' \
  --tests 'dev.mintychochip.customentity.*' \
  --tests 'dev.mintychochip.particle.*' \
  --tests 'dev.mintychochip.registry.CatalogStaticRegistryTest'
```

Expected: BUILD SUCCESSFUL. The native ordinary-block registry remains out of scope.

- [ ] **Step 2: Run the focused server suite, including particles**

```bash
./gradlew :paper-server:test \
  --tests 'dev.mintychochip.customblock.*' \
  --tests 'dev.mintychochip.customentity.*' \
  --tests 'dev.mintychochip.particle.*' \
  --tests 'io.papermc.paper.registry.CatalogRegistryTestSuite'
```

Expected: BUILD SUCCESSFUL; custom particle transport still consumes custom emissions before native conversion and vanilla particles remain native.

- [ ] **Step 3: Inspect the public/native boundary**

Verify from source and tests that:

- API plant classes import no NMS or CraftBukkit types;
- contexts contain snapshots, `Optional` values, and serialized block data only;
- `CustomBlockDefinition` remains the identity owner and `PLANT` only selects a vanilla carrier;
- default custom plant growth is inert and does not execute the carrier's native algorithm;
- ordinary crops and saplings return `false` from the custom façade;
- custom particle routing remains before `CraftParticle.createParticleParam`;
- no custom plant or particle value is inserted into native holder/tag registries.

- [ ] **Step 4: Run the existing broad checks and record unrelated failures accurately**

Run:

```bash
./gradlew :alkahest-api:test
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

If broad checks fail in existing annotation/catalog/upstream tests, record the exact failing classes and counts. Do not attribute failures to this feature without a pre-change baseline.

- [ ] **Step 5: Commit documentation and final scoped verification**

```bash
git add docs/superpowers/specs/2026-08-09-custom-plant-behavior-design.md \
  docs/superpowers/plans/2026-08-09-custom-plant-behavior.md
git commit -m "Plan behavior-backed custom plants"
```

If the worktree still contains overlapping user edits, commit only these two new documentation files and leave unrelated changes untouched.

---

## Deferred native registry

A future plan may add a public registry keyed by native block `NamespacedKey` for ordinary crops and saplings. It should reuse `PlantGrowthContext`, `PlantGrowthPlan`, and the server façade but must preserve vanilla behavior for unregistered keys and define ordering with `CropEcology` and Bukkit growth events before implementation.
