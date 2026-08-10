# Native Static Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every `NATIVE_STATIC` registry key addable during the built-in bootstrap window with a typed builder, holder-safe conversion, tag participation, and an explicit client synchronization policy.

**Architecture:** Keep the existing `BuiltInRegistries.bootStrap(Runnable)` lifecycle and `PaperRegistryListenerManager` event path. Add one typed Paper entry contract per static value family, use specialized server fillers for intrusive registries, and generate all definitions/events from `RegistryEntries`. Move Paper's reference-holder lock to the end of the pre-freeze composition window; bind actual tag contents only through the normal post-freeze static tag load.

**Tech Stack:** Java 25, Gradle source generator, Paper lifecycle events, Mojang `MappedRegistry`/`Holder`, built-in registry bootstrap, static-layer `TagLoader`, JUnit 5, protocol/client synchronization adapters.

## Global Constraints

- Foundation and data-driven plans are complete first.
- The final static manifest is exactly `NATIVE_STATIC` for `GAME_EVENT`, `STRUCTURE_TYPE`, `MOB_EFFECT`, `BLOCK`, `ITEM`, `VILLAGER_PROFESSION`, `POINT_OF_INTEREST_TYPE`, `VILLAGER_TYPE`, `MAP_DECORATION_TYPE`, `MENU`, `ATTRIBUTE`, `FLUID`, `SOUND_EVENT`, `DATA_COMPONENT_TYPE`, and `GAME_RULE`.
- `ENTITY_TYPE` remains the `MERGED` catalog/native view from the catalog plan; this plan does not invent a native entity implementation for carrier-backed custom entities.
- Plugin-created keys use a non-`minecraft` namespace. Duplicate keys, duplicate object identity, invalid builder state, and unsupported client synchronization fail before registry mutation.
- All values are registered before the native registry freezes. No static registry is mutated during normal gameplay after freeze.
- The required static order is: validate builders, create values/holders, run compose handlers, lock Paper reference-holder creation, freeze/validate the NMS registry, then load and bind static-layer tag contents through the normal `TagLoader` path. `MappedRegistry.prepareTagReload` is never called while unfrozen.
- `bindBootstrappedTagsToEmpty` remains the pre-freeze holder-tag initialization step; it is not a substitute for post-freeze tag content loading.
- Blocks, items, fluids, menus, data components, and structure types use registry-specific factories. No reflective constructor or fake generic wrapper is acceptable.
- Native values that a connected client cannot decode are rejected with a registry-specific synchronization error. A resource-pack model alone does not turn a new native registry ID into a vanilla-client-compatible ID.
- No `dev.mintychochip` sources are added to the Minecraft patch tree. Only the required `BuiltInRegistries` lifecycle hook is patch-managed.

---

## File map

### Public API contracts

- **Modify:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/SoundEventRegistryEntry.java` — retain location/range fields and document native client-pack requirements.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/StructureTypeRegistryEntry.java` — typed structure codec/factory contract.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/MobEffectRegistryEntry.java` — effect category, color, particle, and attribute-modifier definition.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/BlockTypeRegistryEntry.java` — immutable block behavior/state definition.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/ItemTypeRegistryEntry.java` — immutable item properties/use definition.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/VillagerProfessionRegistryEntry.java` — profession held-job-site and requested-job-site predicates.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/PoiTypeRegistryEntry.java` — typed POI matching states and ticket capacity.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/VillagerTypeRegistryEntry.java` — biome/type association definition.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/MapDecorationTypeRegistryEntry.java` — map icon metadata and visibility policy.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/MenuTypeRegistryEntry.java` — menu factory plus client protocol declaration.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/AttributeRegistryEntry.java` — default value, min/max bounds, and client-sync metadata.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/FluidRegistryEntry.java` — source/flowing behavior and fluid properties.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/DataComponentTypeRegistryEntry.java` — NMS-free persistence, value, network, and codec adapter contract.
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/ClientSynchronizationPolicy.java` — NMS-free declaration of a native value's client decoding support.
- **Create:** `alkahest-api/src/test/java/io/papermc/paper/registry/data/StaticRegistryEntryContractTest.java` — API builder and validation coverage.

### Server fillers and native factories

- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperMobEffectRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperBlockTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperItemTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperVillagerProfessionRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperPoiTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperVillagerTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperMapDecorationTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperMenuTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperAttributeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperFluidRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperDataComponentTypeRegistryEntry.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperGameRuleRegistryEntry.java`.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperSoundEventRegistryEntry.java` — validate pack/synchronization metadata before constructing `SoundEvent`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/staticregistry/PaperStructureTypeFactory.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/staticregistry/PaperIntrusiveRegistryFactory.java` — block/item/fluid/menu registration helpers with explicit holder and global-table handling.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/staticregistry/PaperDataComponentFactory.java`.
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/staticregistry/NativeClientSynchronization.java` — validate the API policy against the active client protocol.

### Generator, lifecycle, and patch-managed hook

- **Modify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java` — add the thirteen static builder contracts and support levels; retain `GAME_EVENT` and add `SOUND_EVENT`.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java` — emit static builder implementations and explicit `NATIVE_STATIC` backend calls.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java` — emit compose/entry-add providers according to each static entry's support level.
- **Verify:** `paper-generator/src/main/java/io/papermc/generator/RegistryBootstrapper.java` — preserve both generated registry rewriters.
- **Modify through generation:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java`.
- **Modify through generation:** `alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java`.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryListenerManager.java` — use specialized factories for intrusive values and fail with the backend/client policy in errors.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java` — expose the static writable/addable view without bypassing lifecycle checks.
- **Modify:** `paper-server/src/minecraft/java/net/minecraft/core/registries/BuiltInRegistries.java` — move the Paper reference-holder lock from `createContents()` to the post-compose/pre-freeze point.

### Tests

- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryTest.java`.
- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryClientSyncTest.java`.
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java`.
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBuilderTest.java`.

---

## Task 1: Define the typed static contracts

- [ ] **Step 1: Write failing API contract tests**

Add `StaticRegistryEntryContractTest` with one test per public entry family. The tests must construct values without importing `net.minecraft.*`, assert that required fields are rejected before `build()`, and assert that every builder exposes the value's runtime-critical inputs:

- structure type: codec-backed structure factory and generation behavior;
- mob effect: category, color, optional particle, and attribute modifiers;
- block type: state definition, behavior properties, loot/occlusion flags, and client definition;
- item type: stack size/durability, rarity, food/use data, and client definition;
- villager profession/type: typed POI/job and biome associations;
- POI type: matching block states and ticket capacity;
- map decoration: icon, display name, and tracking policy;
- menu type: server factory and client protocol declaration;
- attribute: default/minimum/maximum and sync policy;
- fluid: source/flowing behavior and client fluid-state definition;
- data component: persistence, value adapter, NBT codec, and network codec;
- game rule: typed value codec, default, and change callback;
- sound event: existing location/range builder plus explicit client resource-pack declaration.

Use an API-only test fixture for each definition. The fixture must not accept a raw NMS object or an untyped `Object` payload.

- [ ] **Step 2: Run the API test and verify it fails**

```bash
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.StaticRegistryEntryContractTest'
```

Expected: compilation fails because the static entry contracts do not exist.

- [ ] **Step 3: Add immutable API models**

Create the thirteen new entry interfaces with nested `Builder` contracts extending `RegistryBuilder<T>`. Use records/sealed value types for nested definitions so a builder cannot hide a mutable NMS object. Every required constructor input must have one named setter; optional values use nullable annotations or `Optional`/`OptionalInt` rather than sentinel values.

The `StructureTypeRegistryEntry` contract must expose a NMS-free structure payload/codec abstraction that can produce a `Structure` through the server factory. It must not expose Mojang `MapCodec` in `alkahest-api`.

The `DataComponentTypeRegistryEntry` contract must carry both persistence and network codecs. A valued component without a network codec is invalid for a native registry and must be rejected rather than silently becoming server-only.

The `MenuTypeRegistryEntry`, `BlockTypeRegistryEntry`, `ItemTypeRegistryEntry`, and `FluidRegistryEntry` contracts must include a `ClientSynchronizationPolicy` declaration. A resource-pack-only declaration is not sufficient to claim a vanilla client can decode a new native ID.

- [ ] **Step 4: Add server fillers and conversion seams**

Implement each `Paper*RegistryEntry` as both the public view and its nested `PaperBuilder`, following the existing `PaperGameEventRegistryEntry` and `PaperSoundEventRegistryEntry` patterns. Builders must copy values into immutable fields, validate all required groups, and call a single specialized factory in `paper.registry.staticregistry`.

Use `Conversions` and `PaperRegistries` for existing holder references. Do not cache holders across bootstrap or reload boundaries. Each filler must name the native constructor/factory it targets in a class-level comment and throw before `registry.register` when a field cannot be represented.

- [ ] **Step 5: Run API and server compilation checks**

```bash
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.StaticRegistryEntryContractTest'
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.RegistryBuilderTest'
```

Expected: API contracts compile without NMS dependencies; server fillers compile and existing builder equality tests remain green.

- [ ] **Step 6: Commit the static contract unit**

```bash
git add \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/StructureTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/MobEffectRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/BlockTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/ItemTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/VillagerProfessionRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/PoiTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/VillagerTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/MapDecorationTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/MenuTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/AttributeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/FluidRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/DataComponentTypeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/GameRuleRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/SoundEventRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/ClientSynchronizationPolicy.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/StaticRegistryEntryContractTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperMobEffectRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperBlockTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperItemTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperVillagerProfessionRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperPoiTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperVillagerTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperMapDecorationTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperMenuTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperAttributeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperFluidRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperDataComponentTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperGameRuleRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperSoundEventRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/staticregistry
 git commit -m "feat: add typed static registry contracts"
```

## Task 2: Register simple static families through generated metadata

- [ ] **Step 1: Add generator declarations**

Update `RegistryEntries.BUILT_IN` for `STRUCTURE_TYPE`, `MOB_EFFECT`, `VILLAGER_PROFESSION`, `POINT_OF_INTEREST_TYPE`, `VILLAGER_TYPE`, `MAP_DECORATION_TYPE`, `ATTRIBUTE`, `DATA_COMPONENT_TYPE`, and `GAME_RULE` with their API builder class and server nested builder implementation. Keep `GAME_EVENT` as `WRITABLE`; mark the new additive families `ADDABLE` unless the entry contract explicitly supports entry-add modification.

Update `SOUND_EVENT` from `NONE` to `ADDABLE`, retaining `allowDirect()` and the existing serialization mapping. Every declaration also receives `.backend("NATIVE_STATIC")` from the foundation manifest.

- [ ] **Step 2: Regenerate API and server registry definitions**

Run:

```bash
./gradlew :paper-generator:rewrite
```

Inspect the generated regions in `alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java` and `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java`. Confirm that each listed key has an explicit static backend, a typed builder filler, and a compose provider. No listed static entry may still use `.build()` or `RegistryModificationApiSupport.NONE`.

- [ ] **Step 3: Implement simple native conversion**

Complete the nine `Paper*RegistryEntry` fillers using the corresponding NMS types and existing Craft wrappers. Validate:

1. key namespace and duplicate key before constructing a native value;
2. holder references against the active built-in registry;
3. codec/serializer support for values that are saved or network-synced;
4. client synchronization policy before `registerWithListeners` is called.

For `GameRule`, ensure the typed serializer and callback are installed in the NMS rule type, and ensure the generated generic argument count remains correct. For `DataComponentType`, install the adapter in `DataComponentAdapters` at the same time as the registry value so `PaperDataComponentType.of` cannot observe a component with no adapter.

- [ ] **Step 4: Test one composition per simple family**

In `StaticRegistryTest`, register a namespaced value through each generated compose provider and assert:

- lookup by `NamespacedKey` returns the corresponding Bukkit object;
- `PaperRegistries.toNms`/`fromNms` preserve the key;
- the value is present in the native registry before freeze validation;
- duplicate and invalid-namespace attempts leave the registry unchanged;
- tag lookup is available after the static-layer tag load.

- [ ] **Step 5: Commit the simple static families**

```bash
git add paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java
git commit -m "feat: open simple static registries"
```

## Task 3: Add intrusive native factories

- [ ] **Step 1: Define block and item construction boundaries**

Implement `PaperIntrusiveRegistryFactory` with separate block and item methods. A block method must create its state definition, register all intrusive block holders, and publish the block's default state before the registry can be frozen. An item method must bind its item-to-block relation and item properties without using the API catalog's carrier path.

The factory must expose no reflective constructor. It must reject a definition that would create a client-visible block/item ID the active synchronization policy cannot encode. The rejection must occur before any global table or registry mutation.

- [ ] **Step 2: Add fluid and menu factories**

Use explicit factory methods for `Fluid`/flowing-fluid pairs and `MenuType` factories. Validate that source/flowing states, fluid type references, menu factory, and client protocol declaration are mutually consistent. A menu definition that has no client decoder is rejected before registration; it is not published as a server-only menu.

Keep custom-block carrier definitions in `CustomBlocks`; they do not become native `BLOCK` or `ITEM` values. Keep custom entity carriers in `CustomEntities`; they do not become native `ENTITY_TYPE` values.

- [ ] **Step 3: Add structure-type and data-component native factories**

`PaperStructureTypeFactory` must turn the NMS-free codec/generation contract into a concrete NMS structure type and verify that its codec can round-trip the corresponding `Structure` value. It must reject a type whose generation callback cannot produce valid pieces for the selected settings.

`PaperDataComponentFactory` must install the value adapter, persistent codec, and network codec as one validated unit. A failure rolls back the adapter publication and does not register the component key.

- [ ] **Step 4: Add intrusive factory tests**

Extend `StaticRegistryTest` and add `StaticRegistryClientSyncTest` with these cases:

- a valid block/item pair registers before freeze and resolves through native holder lookup;
- invalid block state or item property fails without changing registry size or global holder tables;
- a source/flowing fluid pair round-trips through its native state codec;
- a menu with a supported client declaration registers, while a missing client decoder fails clearly;
- a structure type codec round-trips its payload;
- a data component persists through item NBT and is accepted by the network codec;
- every unsupported client synchronization case leaves the previous registry state intact.

- [ ] **Step 5: Commit intrusive static factories**

```bash
git add paper-server/src/main/java/io/papermc/paper/registry/staticregistry \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperBlockTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperItemTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperFluidRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperMenuTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureTypeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperDataComponentTypeRegistryEntry.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryClientSyncTest.java
git commit -m "feat: add intrusive static registry factories"
```

## Task 4: Correct bootstrap ordering and static tag lifecycle

- [ ] **Step 1: Add the ordering regression test**

Add a test seam around `BuiltInRegistries.bootStrap` that records these callbacks:

```text
normal built-in values
static compose handlers
Paper reference-holder lock
native freeze/validation
static-layer tag load and bind
```

Assert that a plugin builder can resolve holders created by normal bootstrap and that no plugin registration occurs after freeze. Assert that tag contents are absent from the pre-freeze phase and present after `WorldLoader` performs `TagLoader.loadTagsForExistingRegistries`.

- [ ] **Step 2: Move the patch-managed holder lock**

In `paper-server/src/minecraft/java/net/minecraft/core/registries/BuiltInRegistries.java`, remove the Paper `lockReferenceHolders` call from the `createContents()` loop. Invoke it in `freeze()` immediately after `PaperRegistryListenerManager.runFreezeListeners` and before `registry.freeze()`.

Do not replace `bindBootstrappedTagsToEmpty`; it initializes tag holder state needed before freeze. Do not call `MappedRegistry.prepareTagReload` from this hook. After editing the vanilla source, run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

- [ ] **Step 3: Make listener errors lifecycle-specific**

Update `PaperRegistryListenerManager` and `PaperRegistryAccess` so a static registration attempted outside bootstrap reports the registry key, `NATIVE_STATIC` backend, and required bootstrap phase. A registration rejected by client synchronization reports the value key and policy reason. No failure path should leave a partially registered holder or adapter.

- [ ] **Step 4: Run static lifecycle tests**

```bash
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.StaticRegistryTest' \
  --tests 'io.papermc.paper.registry.StaticRegistryClientSyncTest' \
  --tests 'io.papermc.paper.registry.RegistryBackendTest' \
  --tests 'io.papermc.paper.registry.RegistryBuilderTest'
```

Expected: all fifteen static keys have typed addable/writable metadata, compose runs before the reference-holder lock and freeze, tag binding occurs through the post-freeze static layer, and failed native registrations are atomic.

- [ ] **Step 5: Commit lifecycle and patch updates**

```bash
git add paper-server/src/minecraft/java/net/minecraft/core/registries/BuiltInRegistries.java \
  paper-server/patches/sources/net/minecraft/core/registries/BuiltInRegistries.java.patch \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryListenerManager.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryTest.java
git commit -m "fix: enforce static registry lifecycle ordering"
```

## Task 5: Static family verification and handoff

- [ ] **Step 1: Run generated-source and registry tests**

```bash
./gradlew :paper-generator:rewrite
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.*'
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.*'
```

Expected: generated definitions/events contain all fifteen static keys with `NATIVE_STATIC`, no static key uses `.build()` or `NONE`, intrusive factories pass synchronization checks, and existing data-driven registrations still pass.

- [ ] **Step 2: Run the patch verification path**

```bash
./gradlew applyPatches
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.StaticRegistryTest'
```

Expected: the rebuilt patch applies cleanly and the static bootstrap test observes the moved lock hook.

- [ ] **Step 3: Verify no catalog/native confusion**

Assert in `RegistryBackendTest` that `RegistryKey.ENTITY_TYPE` remains `MERGED`, `Registry.MATERIAL` remains catalog-backed, custom blocks/entities are not present in native NMS registries, and every static value registered through this plan is present in a native holder with a declared client policy.

- [ ] **Step 4: Commit the verification unit**

```bash
git add paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryListenerManager.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/StaticRegistryClientSyncTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBuilderTest.java
git commit -m "test: verify native static registries"
```

The catalog plan may now assume that all `NATIVE_STATIC` entries expose typed compose paths and that static holders/tags follow the corrected lifecycle order.
