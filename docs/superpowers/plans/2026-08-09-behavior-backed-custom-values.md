# Behavior-Backed Custom API Values Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immutable API-hosted behavior receivers and server routers for custom blocks/materials, entities, and particles while preserving vanilla-facing metadata and carrier fallbacks.

**Architecture:** Custom values own immutable behavior bundles. Receivers consume immutable API snapshots and return explicit decision/override plans; they never receive mutable Bukkit handles or NMS objects. Server routers retain live handles, validate plans, apply receiver overrides, and resolve `USE_DEFAULT` branches through existing host, item, carrier, and `BlockFeel` metadata. Potion types and memory keys remain metadata-only.

**Tech Stack:** Java 25, Gradle, `alkahest-api`, `paper-server`, Bukkit API, JUnit 5, existing Paper/CraftBukkit event and carrier services.

## Global Constraints

- `alkahest-api` behavior code is NMS-free; no `net.minecraft.*`, CraftBukkit, packet, or server implementation imports.
- Contexts contain immutable snapshots and request data, never mutable `Block`, `Entity`, `ItemStack`, `Location`, `World`, `Player`, mutable `BlockData`, or mutable `ItemMeta` handles.
- Results use explicit tagged states; `null` never means both “use fallback” and “suppress.”
- `DEFAULT`/`USE_DEFAULT` resolves through current host/item/carrier metadata; receivers override only explicitly selected fields.
- Native Minecraft registry IDs and holder/tag/wire insertion remain out of scope.
- Existing custom catalog registry wiring remains live and identity-preserving.
- API changes live under `dev.mintychochip.*`; server routers live under `paper-server/src/main/java/dev/mintychochip/*` or the minimal required CraftBukkit hook.
- Test commands require JDK 25 and must target observable behavior, not implementation text.

---

### Task 1: Add immutable behavior primitives and API snapshots

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/Decision.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/ValueOverride.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/Position.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/ItemStackView.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/BlockDataView.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/BlockView.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/EntityView.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/behavior/ActorView.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/behavior/BehaviorSnapshotTest.java`

**Interfaces:**
- Produces `Decision.DEFAULT|ALLOW|DENY` for operation outcomes.
- Produces `ValueOverride<T>` with exactly `USE_DEFAULT` or non-null `VALUE(T)`.
- Produces immutable views that can be used by block, entity, and particle contexts.

- [ ] **Step 1: Write failing snapshot and tri-state tests**

```java
@Test
void overrideDistinguishesDefaultFromValue() {
    assertInstanceOf(ValueOverride.UseDefault.class, ValueOverride.useDefault());
    assertEquals("x", ((ValueOverride.Value<String>) ValueOverride.value("x")).value());
}

@Test
void itemStackViewCopiesTheInputAndOutput() {
    final ItemStack source = new ItemStack(Material.STONE, 2);
    final ItemStackView view = ItemStackView.from(source);
    source.setAmount(1);
    assertEquals(2, view.amount());
    final ItemStack copy = view.copy();
    copy.setAmount(7);
    assertEquals(2, view.amount());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.behavior.BehaviorSnapshotTest'
```

Expected: FAIL because the behavior primitives and views do not exist.

- [ ] **Step 2: Implement the explicit state types**

```java
public enum Decision {
    DEFAULT,
    ALLOW,
    DENY
}

public sealed interface ValueOverride<T> permits ValueOverride.UseDefault, ValueOverride.Value {
    record UseDefault<T>() implements ValueOverride<T> {}
    record Value<T>(@NotNull T value) implements ValueOverride<T> {
        public Value {
            Objects.requireNonNull(value, "value");
        }
    }

    static <T> ValueOverride<T> useDefault() { return new UseDefault<>(); }
    static <T> ValueOverride<T> value(final T value) { return new Value<>(value); }
}
```

`ItemStackView` must clone on capture and on `copy()`. It may expose only `type()`, `amount()`, custom-key metadata, and `copy()`. `BlockDataView` stores the carrier material and serialized block-data string, never the mutable `BlockData` instance. `Position`, `BlockView`, `EntityView`, and `ActorView` are records/final immutable classes containing only documented value fields.

- [ ] **Step 3: Run the focused tests**

Run the command above. Expected: BUILD SUCCESSFUL with all `BehaviorSnapshotTest` cases passing.

- [ ] **Step 4: Commit the primitive unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/behavior alkahest-api/src/test/java/dev/mintychochip/behavior
git commit -m "Add immutable behavior context primitives"
```

---

### Task 2: Add custom block behavior contracts and plans

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockBehavior.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockPlaceContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockBreakContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockInteractContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockMoveContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BlockExplodeContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/PlacementResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/BreakResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/InteractionResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/MovementResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/ExplosionResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/DropPlan.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/ConsumePlan.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customblock/CleanupPlan.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockBehaviorTest.java`

**Interfaces:**
- Consumes Task 1 `Decision`, `ValueOverride`, `BlockView`, `ActorView`, and `ItemStackView`.
- Produces `CustomBlockBehavior.defaults()` and immutable receiver/result types for all current `CustomBlockListener` operations.

- [ ] **Step 1: Write failing receiver and fallback tests**

```java
@Test
void defaultBehaviorUsesExplicitDefaultBranches() {
    final CustomBlockBehavior behavior = CustomBlockBehavior.defaults();
    final PlacementResult result = behavior.onPlace().receive(samplePlaceContext());
    assertEquals(Decision.DEFAULT, result.decision());
    assertInstanceOf(ValueOverride.UseDefault.class, result.consumeItem());
}

@Test
void receiverResultPreservesExplicitDropSuppression() {
    final CustomBlockBehavior behavior = CustomBlockBehavior.builder()
        .onBreak(context -> BreakResult.builder()
            .decision(Decision.ALLOW)
            .drops(DropPlan.none())
            .build())
        .build();
    final BreakResult result = behavior.onBreak().receive(sampleBreakContext());
    assertEquals(DropPlan.Kind.NONE, result.drops().value().kind());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomBlockBehaviorTest'
```

Expected: FAIL because the behavior and result contracts do not exist.

- [ ] **Step 2: Implement typed contexts, receivers, and results**

`CustomBlockBehavior` must expose typed receivers with these signatures:

```java
@FunctionalInterface
public interface BlockPlaceReceiver {
    PlacementResult receive(BlockPlaceContext context);
}

@FunctionalInterface
public interface BlockBreakReceiver {
    BreakResult receive(BlockBreakContext context);
}

@FunctionalInterface
public interface BlockInteractReceiver {
    InteractionResult receive(BlockInteractContext context);
}
```

Add equivalent movement and explosion receivers. Each result must contain explicit `Decision` and `ValueOverride` fields. `DropPlan`, `ConsumePlan`, and `CleanupPlan` must use tagged variants (`DEFAULT`, `NONE`, `EXPLICIT` or equivalent) and immutable lists of `ItemStackView` values. `CustomBlockBehavior.defaults()` returns receivers that produce `DEFAULT` plans. Reject null receivers, null result fields, invalid negative XP, and invalid incompatible combinations in constructors/builders. The plan examples use private test-fixture factories such as `samplePlaceContext()` and `sampleBreakContext()`; define those helpers in each test class rather than treating them as production API.

- [ ] **Step 3: Run focused block behavior tests**

Run the command above. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit the block contract unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockBehavior.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockPlaceContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockBreakContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockInteractContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockMoveContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BlockExplodeContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/PlacementResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/BreakResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/InteractionResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/MovementResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/ExplosionResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/DropPlan.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/ConsumePlan.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CleanupPlan.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockBehaviorTest.java
git commit -m "Add custom block behavior contracts"
```

---

### Task 3: Attach behavior bundles to custom block definitions

**Files:**
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java`
- Modify: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomMaterialParityTest.java`
- Modify: `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockCatalogTest.java`

**Interfaces:**
- Consumes `CustomBlockBehavior.defaults()` and the Task 2 contracts.
- Produces `CustomBlockDefinition.behavior()` and `Builder.behavior(CustomBlockBehavior)`.

- [ ] **Step 1: Add failing definition ownership tests**

```java
@Test
void definitionOwnsAnImmutableBehaviorBundle() {
    final CustomBlockBehavior behavior = CustomBlockBehavior.builder().build();
    final CustomBlockDefinition definition = definitionBuilder().behavior(behavior).build();
    assertSame(behavior, definition.behavior());
}

@Test
void omittedBehaviorUsesMetadataFallback() {
    final CustomBlockDefinition definition = definitionBuilder().build();
    assertEquals(Decision.DEFAULT, definition.behavior().onBreak()
        .receive(sampleBreakContext(definition)).decision());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.CustomMaterialParityTest' --tests 'dev.mintychochip.customblock.CustomBlockCatalogTest'
```

Expected: FAIL because `behavior()` and `Builder.behavior(...)` do not exist.

- [ ] **Step 2: Add the behavior field and builder method**

Initialize every definition with `CustomBlockBehavior.defaults()`. Require a non-null behavior in the constructor. Do not alter `Material` metadata delegation: host/carrier remains the block-data/physical fallback, `itemMaterial` remains the item fallback, and `BlockFeel` remains the mining/explosion fallback.

- [ ] **Step 3: Run the focused API tests**

Run the command above. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit definition ownership**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockDefinition.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomMaterialParityTest.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockCatalogTest.java
git commit -m "Attach behavior bundles to custom blocks"
```

---

### Task 4: Add entity behavior contracts and attach them to definitions

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityBehavior.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customentity/EntitySpawnContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customentity/EntityApplyContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customentity/EntitySpawnResult.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/customentity/EntityApplyResult.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityDefinition.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/customentity/CustomEntityBehaviorTest.java`

**Interfaces:**
- Consumes Task 1 snapshots and Task 2 `Decision`/`ValueOverride` primitives.
- Produces `CustomEntityDefinition.behavior()`, `Builder.behavior(...)`, and default spawn/apply plans.

- [ ] **Step 1: Write failing entity behavior tests**

```java
@Test
void entityDefinitionOwnsSpawnAndApplyReceivers() {
    final CustomEntityBehavior behavior = CustomEntityBehavior.builder()
        .onSpawn(context -> EntitySpawnResult.allow())
        .onApply(context -> EntityApplyResult.defaults())
        .build();
    final CustomEntityDefinition definition = definitionBuilder().behavior(behavior).build();
    assertSame(behavior, definition.behavior());
    assertEquals(Decision.ALLOW, definition.behavior().onSpawn().receive(sampleSpawnContext()).decision());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customentity.CustomEntityBehaviorTest'
```

Expected: FAIL because the contracts do not exist.

- [ ] **Step 2: Implement immutable entity contexts/results and definition ownership**

`CustomEntityBehavior` must expose spawn and apply receivers, with optional interaction/lifecycle receivers only where an existing server hook can route them. `EntitySpawnResult` and `EntityApplyResult` use explicit decisions and `USE_DEFAULT` presentation overrides. `BlockModelHostSpec` remains the authoritative fallback for block data and transformation; behavior may override with immutable plan values.

- [ ] **Step 3: Run entity API tests**

Run the command above. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit entity behavior contracts**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityBehavior.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/EntitySpawnContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/EntityApplyContext.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/EntitySpawnResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/EntityApplyResult.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityDefinition.java \
  alkahest-api/src/test/java/dev/mintychochip/customentity/CustomEntityBehaviorTest.java
git commit -m "Add custom entity behavior contracts"
```

---

### Task 5: Add custom particle behavior and transport-neutral plans

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticleBehavior.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionContext.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionPlan.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticle.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleCatalog.java`
- Test: `alkahest-api/src/test/java/dev/mintychochip/particle/CustomParticleBehaviorTest.java`

**Interfaces:**
- Consumes Task 1 snapshots and `Decision`/`ValueOverride`.
- Produces `ParticleCatalog.register(key, dataType, behavior)`, while the existing overload creates `CustomParticleBehavior.defaults()`.
- Produces a plan containing an explicit decision, immutable transport key override, and immutable parameter map.

- [ ] **Step 1: Write failing particle behavior tests**

```java
@Test
void particleOwnsAnEmissionReceiver() {
    final CustomParticleBehavior behavior = CustomParticleBehavior.builder()
        .onEmit(context -> ParticleEmissionPlan.handled(new NamespacedKey("mintychochip", "display")))
        .build();
    final CustomParticle particle = ParticleCatalog.create()
        .register(new NamespacedKey("mintychochip", "spark"), Void.class, behavior);
    assertSame(behavior, particle.behavior());
    assertEquals(Decision.ALLOW, particle.behavior().onEmit().receive(sampleContext()).decision());
}
```

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.particle.CustomParticleBehaviorTest'
```

Expected: FAIL because behavior-aware registration and plans do not exist.

- [ ] **Step 2: Implement particle behavior and immutable emission plans**

`ParticleEmissionContext` must contain `Position`, immutable receiver identity snapshots, source identity, count/offset/extra/force values, and a captured immutable data view. Do not expose the current raw mutable receiver list or arbitrary mutable data object. `ParticleEmissionPlan` must distinguish `DEFAULT`, `ALLOW`, and `DENY`, and must reject null transport keys/parameters.

- [ ] **Step 3: Run the particle API tests**

Run the command above. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit particle behavior contracts**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticleBehavior.java \
  alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionContext.java \
  alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionPlan.java \
  alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticle.java \
  alkahest-api/src/main/java/dev/mintychochip/particle/ParticleCatalog.java \
  alkahest-api/src/test/java/dev/mintychochip/particle/CustomParticleBehaviorTest.java
git commit -m "Add custom particle behavior contracts"
```

---

### Task 6: Route custom block plans through the server lifecycle

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockBehaviorRouter.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockLifecycle.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockListener.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockProvenance.java`
- Test: `paper-server/src/test/java/dev/mintychochip/customblock/CustomBlockBehaviorRouterTest.java`
- Test: `paper-server/src/test/java/dev/mintychochip/customblock/CustomBlockBehaviorRouterTestSuite.java`
**Interfaces:**
- Consumes API contexts, receivers, and result plans from Tasks 1–3.
- Produces one router entry point per current listener operation: place, manual place, break prepare/finish, piston, and explosion.

- [x] **Step 1: Write failing server routing tests**

```java
@Test
void breakReceiverOverridesDropsWithoutMutatingTheEvent() {
    final AtomicInteger calls = new AtomicInteger();
    final CustomBlockDefinition definition = definitionWithBehavior(
        CustomBlockBehavior.builder().onBreak(context -> {
            calls.incrementAndGet();
            return BreakResult.builder()
                .decision(Decision.ALLOW)
                .drops(DropPlan.none())
                .experience(ValueOverride.value(0))
                .build();
        }).build());
    final BreakResult result = CustomBlockBehaviorRouter.breakPlan(definition, snapshotContext());
    assertEquals(1, calls.get());
    assertEquals(DropPlan.Kind.NONE, result.drops().value().kind());
}
```

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.CustomBlockBehaviorRouterTest'
```

Expected: FAIL because the router does not exist.

- [x] **Step 2: Implement snapshot construction and plan application**

The router must capture context snapshots before invoking a receiver, resolve the definition once, validate every result branch, and apply plans exactly once. `CustomBlockLifecycle` remains responsible for the existing carrier, persistence, provenance, and packet-display services; it delegates behavior decisions to the router. For every `USE_DEFAULT` branch, preserve current `CustomBlockPlacement`, `CustomBlockMining`, and `BlockFeel` behavior.

- [x] **Step 3: Run focused and existing block tests**

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.CustomBlockBehaviorRouterTest' --tests 'dev.mintychochip.customblock.*'
```

Expected: BUILD SUCCESSFUL with receiver overrides and existing carrier behavior intact.

- [x] **Step 4: Commit the block router**

```bash
git add paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockBehaviorRouter.java \
  paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockLifecycle.java \
  paper-server/src/main/java/dev/mintychochip/customblock/CustomBlockListener.java \
  paper-server/src/test/java/dev/mintychochip/customblock/CustomBlockBehaviorRouterTest.java
git commit -m "Route custom block behavior plans"
```

---

### Task 7: Route entity plans through server spawning and application

- Modify: `alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityLifecycle.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegionAccessor.java`
- Create: `paper-server/src/test/java/dev/mintychochip/customentity/CustomEntityBehaviorIntegrationTest.java`
- Create: `paper-server/src/test/java/dev/mintychochip/customentity/CustomEntityBehaviorIntegrationTestSuite.java`
**Interfaces:**
- Consumes Task 4 entity behavior contracts.
- Produces server application of spawn/apply plans with `BlockDisplay` carrier fallback.

- [x] **Step 1: Write failing entity integration tests**

```java
@Test
void customEntitySpawnUsesReceiverDecisionBeforeCarrierFallback() {
    final CustomEntityDefinition definition = definitionWithBehavior(
        CustomEntityBehavior.builder()
            .onSpawn(context -> EntitySpawnResult.deny())
            .build());
    assertThrows(IllegalStateException.class, () -> CustomEntities.spawn(testLocation(), definition));
}
```

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customentity.CustomEntityBehaviorIntegrationTest'
```

Expected: FAIL because spawn routing ignores the receiver.

- [x] **Step 2: Apply entity plans in `CustomEntityLifecycle`**

Capture `EntityView`/`ActorView` snapshots before invoking receivers. Apply explicit carrier/presentation overrides only after validation. Resolve `USE_DEFAULT` from `BlockModelHostSpec` and existing PDC identity behavior. Keep live `Entity#getType()` as the vanilla carrier and do not create an NMS entity type.

- [x] **Step 3: Run entity integration tests**

Run the command above plus the existing custom entity test suite. Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit entity routing**

```bash
git add paper-server/src/main/java/dev/mintychochip/customentity/CustomEntityLifecycle.java \
  paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegionAccessor.java \
  paper-server/src/test/java/dev/mintychochip/customentity/CustomEntityBehaviorIntegrationTest.java
git commit -m "Route custom entity behavior plans"
```

---

### Task 8: Add server particle transport dispatch

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransport.java`
- Create: `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransportRegistry.java`
- Create: `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleRouter.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java`
- Test: `paper-server/src/test/java/dev/mintychochip/particle/CustomParticleTransportTest.java`
- Test: `paper-server/src/test/java/dev/mintychochip/particle/CustomParticleTransportTestSuite.java`
**Interfaces:**
- Consumes `CustomParticle.behavior()`, `ParticleEmissionContext`, and `ParticleEmissionPlan`.
- Produces a server-installed transport registry keyed by `NamespacedKey`; no custom value is passed to `CraftParticle.createParticleParam`.

- [x] **Step 1: Write failing transport tests**

```java
@Test
void handledCustomParticleUsesTransportInsteadOfNativeConversion() {
    final AtomicInteger sends = new AtomicInteger();
    CustomParticleTransportRegistry.register(
        new NamespacedKey("mintychochip", "display"),
        plan -> sends.incrementAndGet());
    final CustomParticle particle = ParticleCatalog.global().register(
        new NamespacedKey("mintychochip", "spark"), Void.class,
        CustomParticleBehavior.builder()
            .onEmit(context -> ParticleEmissionPlan.handled(new NamespacedKey("mintychochip", "display")))
            .build());
    CustomParticleRouter.emit(sampleEmission(particle));
    assertEquals(1, sends.get());
}
```

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.particle.CustomParticleTransportTest'
```

Expected: FAIL because no custom transport dispatch exists.

- [x] **Step 2: Implement dispatch before native conversion**

`CraftWorld.spawnParticle` and the receiver-specific `CraftPlayer` path must detect custom particles before `CraftParticle.convertLegacy`/`createParticleParam`. Build an immutable emission context, invoke the particle receiver, resolve explicit plan states, and dispatch handled plans through `CustomParticleTransportRegistry`. Vanilla particles continue through the existing NMS path unchanged. Unsupported/default custom plans fail with the existing explicit catalog-only error rather than falling through to a native holder conversion.

- [x] **Step 3: Run particle transport and native-boundary tests**

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.particle.CustomParticleTransportTest' --tests 'io.papermc.paper.registry.CatalogRegistryTestSuite'
```

Expected: BUILD SUCCESSFUL; custom plans dispatch, native conversion still rejects custom particles, and vanilla particles still emit through the native path.

- [x] **Step 4: Commit particle transport**

```bash
git add paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransport.java \
  paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransportRegistry.java \
  paper-server/src/main/java/dev/mintychochip/particle/CustomParticleRouter.java \
  paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java \
  paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java \
  paper-server/src/test/java/dev/mintychochip/particle/CustomParticleTransportTest.java
git commit -m "Dispatch custom particle emission plans"
```

---

### Task 9: Update documentation and run scoped verification

**Files:**
- Modify: `docs/superpowers/specs/2026-08-09-behavior-backed-custom-values-design.md` only if implementation signatures materially differ.
- Modify: relevant Javadocs on `CustomBlockDefinition`, `CustomEntityDefinition`, `CustomParticle`, and `ParticleBuilder`.
- Add: package-level `@NullMarked` declarations for behavior API packages.
- Test: API and server behavior suites.

**Interfaces:**
- Consumes all completed behavior contracts and routers.
- Produces documentation that states supported API behavior, snapshot boundaries, explicit plan states, fallback rules, and native-only limitations.

- [x] **Step 1: Run the API behavior suite**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.behavior.*' --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.customentity.*' --tests 'dev.mintychochip.particle.*' --tests 'dev.mintychochip.registry.CatalogStaticRegistryTest'
```

Expected: BUILD SUCCESSFUL for the scoped behavior/catalog tests. The full `:alkahest-api:test` remains red only in pre-existing `dev.mintychochip.ecology`, `genetics`, `season`, and `org.bukkit.entity.memory` annotation debt (330 failures reported by `AnnotationTest` after the new behavior packages were package-null-marked).

- [x] **Step 2: Run the server behavior and registry suites**

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.customentity.*' --tests 'dev.mintychochip.particle.*' --tests 'io.papermc.paper.registry.CatalogRegistryTestSuite'
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Review exposed vanilla semantics**

Verify by source inspection and tests that:

- `Material`, `EntityType`, and `Particle` remain compile-time API types;
- contexts expose snapshots, not mutable Bukkit handles;
- results have explicit tagged fallback/suppression/value states;
- `Block#getType()`, `ItemStack#getType()`, and live carrier `Entity#getType()` remain vanilla carriers;
- native NMS conversion and holder/tag paths do not receive custom values;
- custom block/entity routers invoke receivers once and apply metadata fallback for every `USE_DEFAULT` branch.

- [x] **Step 4: Preserve the current dirty checkout and report final verification**

The implementation remains in the existing dirty checkout because behavior changes overlap pre-existing catalog/upstream edits. No broad staging, commit, merge, push, or discard was performed. Scoped API and server suites were rerun after the source review.
