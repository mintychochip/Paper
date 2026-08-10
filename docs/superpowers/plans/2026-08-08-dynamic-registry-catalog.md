# Catalog and Merged Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open the catalog-backed and merged registry surfaces—particles, potions, memory keys, `Material`, and custom `EntityType`—with stable namespaced identity, atomic publication, deterministic iteration, and explicit rejection at native-only boundaries.

**Architecture:** Follow the fork's existing `Material`/`EntityType` interface-plus-vanilla-enum pattern. Each catalog owns an immutable published snapshot and validates keys before swapping the snapshot. Server registry adapters merge native vanilla values with catalog values without inserting catalog values into NMS registries. `ENTITY_TYPE` remains merged; custom entities keep their carrier/persistent-identity implementation.

**Tech Stack:** Java 25, Bukkit/Paper API interfaces, immutable/atomic catalog snapshots, `Registry.NotARegistry`, server `PaperRegistryAccess`, existing custom-block/custom-entity lifecycle, JUnit 5, server smoke tests.

## Global Constraints

- Foundation, data-driven, and static plans are complete first.
- `PARTICLE_TYPE`, `POTION`, and `MEMORY_MODULE_TYPE` use `CATALOG`; `ENTITY_TYPE` uses `MERGED`; `Registry.MATERIAL` is an additional catalog/merged surface outside `RegistryKey`.
- Catalog custom values have stable non-`minecraft` `NamespacedKey`s and deterministic iteration. Duplicate keys and duplicate value identity fail before publication.
- Catalog publication is atomic: readers observe either the previous complete snapshot or the new complete snapshot. A failed registration does not mutate the previous snapshot.
- Catalog values never receive native NMS registry IDs by accident. `CraftRegistry.bukkitToMinecraft` and registry-specific value converters, native tag APIs, network codecs, native spawn APIs, and other native-only paths reject catalog values with a clear backend/identity message.
- Native vanilla values remain available through the merged views and preserve their existing static constants and native holder/tag behavior.
- `ENTITY_TYPE` custom values remain carrier-backed `CustomEntityDefinition`s. This plan does not pretend a carrier is a native `net.minecraft.world.entity.EntityType`.
- The public enum migrations for `Particle` and `PotionType` follow the existing fork pattern: `VanillaParticle`/`VanillaPotionType` own generated enum constants; `Particle`/`PotionType` are keyed interfaces that re-export constants and provide vanilla-only `values()`/`valueOf()` helpers. Enum-only external switch bytecode cannot be preserved; the API must document the migration.
- The current public `RegistryKey` catalog does not include NMS-only `LOOT_TABLE`, `ITEM_MODIFIER`, or `PREDICATE`. This plan does not invent hidden keys or claim reloadable support for them. The manifest keeps `NATIVE_RELOADABLE` available for a future explicitly promoted key and asserts that no current public key silently uses it.
- Do not modify unrelated Paper APIs or add `dev.mintychochip` code to the Minecraft patch tree.

---

## File map

### Particle and potion API migrations

- **Create:** `alkahest-api/src/main/java/org/bukkit/VanillaParticle.java` — generated vanilla particle enum moved from `Particle`.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/Particle.java` — keyed extensible interface, vanilla constant re-exports, vanilla-only compatibility helpers.
- **Create:** `alkahest-api/src/main/java/org/bukkit/ParticleRegistry.java` — atomic custom particle catalog and typed registration factory.
- **Create:** `alkahest-api/src/main/java/org/bukkit/VanillaPotionType.java` — generated vanilla potion enum moved from `PotionType`.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/potion/PotionType.java` — keyed extensible interface, vanilla constant re-exports, vanilla-only compatibility helpers.
- **Create:** `alkahest-api/src/main/java/org/bukkit/potion/PotionTypeRegistry.java` — atomic custom potion catalog and typed registration factory.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/Registry.java` — point API-only registry fields at live catalog-aware views.
- **Modify:** `alkahest-api/src/main/java/com/destroystokyo/paper/ParticleBuilder.java` — preserve interface-based particles and reject catalog values where NMS conversion is required.

### Memory, material, and entity catalogs

- **Create:** `alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKeyRegistry.java` — atomic memory-key catalog and creation validation.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKey.java` — delegate generated/native values and custom creation to `MemoryKeyRegistry`.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/MaterialRegistry.java` — immutable deterministic custom snapshot and collision-aware merge.
- **Modify:** `alkahest-api/src/main/java/org/bukkit/EntityTypeRegistry.java` — immutable deterministic custom snapshot and native/catalog identity queries.
- **Modify:** `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockCatalog.java` — copy-on-write publication and vanilla-key collision checks.
- **Modify:** `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlocks.java` — validate/register through the catalog snapshot boundary.
- **Modify:** `alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityCatalog.java` — copy-on-write publication and vanilla-key collision checks.
- **Modify:** `alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntities.java` — validate/register through the catalog snapshot boundary.

### Server catalog adapters and manifest

- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java` — generic native-plus-catalog snapshot view.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java` — remove the enum-only bound and delegate catalog values without native conversion.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/EntityTypeRegistry.java` — preserve native tags while merging custom entities.
- **Modify through generation:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java` — explicit `CATALOG`/`MERGED` metadata and live suppliers.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java` — expose catalog-aware registry views and backend-specific errors.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/RegistryBackendKind.java` only if the foundation's visibility/accessor contract needs catalog identity helpers.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java` — retain API-only entries and annotate `CATALOG` versus `MERGED`.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java` — emit catalog suppliers without native registry assumptions.

### Tests

- **Create:** `alkahest-api/src/test/java/org/bukkit/ParticleRegistryTest.java`.
- **Create:** `alkahest-api/src/test/java/org/bukkit/potion/PotionTypeRegistryTest.java`.
- **Create:** `alkahest-api/src/test/java/org/bukkit/entity/memory/MemoryKeyRegistryTest.java`.
- **Modify:** `alkahest-api/src/test/java/org/bukkit/MaterialRegistryTest.java`.
- **Modify:** `alkahest-api/src/test/java/org/bukkit/EntityTypeRegistryTest.java`.
- **Modify:** `alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockCatalogTest.java`.
- **Modify:** `alkahest-api/src/test/java/dev/mintychochip/customentity/CustomEntityCatalogTest.java`.
- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/CatalogRegistryTest.java`.
- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/MergedRegistryTest.java`.
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java`.

---

## Task 1: Build the atomic catalog publication boundary

- [ ] **Step 1: Write failing catalog invariants**

Add API tests that register values concurrently with reads and assert these invariants:

1. a snapshot contains either all entries before publication or all entries after publication;
2. a duplicate key fails without changing size, iteration, or lookup;
3. a `minecraft` custom key fails before publication;
4. iteration order is native values followed by custom values sorted by full namespaced key;
5. a failed builder/definition leaves the prior snapshot unchanged.

Add `CatalogRegistryTest` for the server adapter and assert that native values resolve to native entries, catalog values resolve to catalog entries, and the native value-conversion adapter rejects the latter.

- [ ] **Step 2: Run the focused tests and verify the boundary is missing**

```bash
./gradlew :alkahest-api:test \
  --tests 'org.bukkit.MaterialRegistryTest' \
  --tests 'org.bukkit.EntityTypeRegistryTest' \
  --tests 'dev.mintychochip.customblock.CustomBlockCatalogTest'
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.CatalogRegistryTest'
```

Expected: the new atomic/snapshot assertions fail against the current mutable insertion-order catalogs and no generic server catalog adapter exists.

- [ ] **Step 3: Implement immutable catalog snapshots**

Add a private immutable snapshot representation to `CustomBlockCatalog`, `CustomEntityCatalog`, `ParticleRegistry`, `PotionTypeRegistry`, and `MemoryKeyRegistry`. Use an atomic reference or synchronized compare-and-publish boundary; do not expose a mutable backing map through `all()` or `asMap()`.

Validate the full key collision set before publication: existing catalog values, vanilla values in the merged view, and any reserved key policy for the catalog. Keep registration order deterministic by sorting custom entries by `NamespacedKey.toString()` at publication.

- [ ] **Step 4: Implement `PaperCatalogRegistry`**

Create a server view that accepts a native `Registry<V>` supplier and a catalog snapshot supplier. Its `get`, iteration, size, `keyStream`, and identity query must be snapshot-consistent. Its tag methods delegate only to the native registry and report catalog values as native-tag-ineligible.

Add explicit methods or package-private predicates for `isNative(value)` and `isCatalog(value)`. Use those predicates in the native value-conversion adapters, native tag conversion, and any NMS-backed registry adapter rather than relying on `instanceof` against one custom implementation.

- [ ] **Step 5: Run the atomic publication tests**

```bash
./gradlew :alkahest-api:test \
  --tests 'org.bukkit.MaterialRegistryTest' \
  --tests 'org.bukkit.EntityTypeRegistryTest' \
  --tests 'dev.mintychochip.customblock.CustomBlockCatalogTest' \
  --tests 'dev.mintychochip.customentity.CustomEntityCatalogTest'
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.CatalogRegistryTest'
```

Expected: duplicate/namespace failures are atomic, iteration is deterministic, and the server adapter never exposes a catalog value as a native holder.

- [ ] **Step 6: Commit the publication boundary**

```bash
git add paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java \
  paper-server/src/test/java/io/papermc/paper/registry/CatalogRegistryTest.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockCatalog.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlocks.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityCatalog.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntities.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockCatalogTest.java \
  alkahest-api/src/test/java/dev/mintychochip/customentity/CustomEntityCatalogTest.java
git commit -m "feat: publish catalog registries atomically"
```

## Task 2: Open the particle catalog without native-ID confusion

- [ ] **Step 1: Move generated vanilla particles to `VanillaParticle`**

Move the generated enum body and all particle data-type metadata from `Particle.java` to `VanillaParticle.java`. Make `VanillaParticle` implement the new `Particle` interface. Retain the generated source markers so the next upstream rewrite updates `VanillaParticle`, not a hand-maintained duplicate.

Expose on `Particle`:

- all existing constant names as fields of type `Particle`;
- `getKey()`, `getDataType()`, and `builder()` behavior;
- static `values()` and `valueOf(String)` helpers that are explicitly vanilla-only;
- a `isVanilla()` identity predicate.

Update `ParticleBuilder` and any API call site to accept the interface. Do not change particle option classes or their validation.

- [ ] **Step 2: Add `ParticleRegistry` registration**

Implement `ParticleRegistry.register(NamespacedKey, Class<?>)` with non-`minecraft` validation and a private immutable `Particle` implementation. The returned value must expose the key and data type and must work with the public particle builder. Registration must reject duplicate vanilla/custom keys and publish atomically.

Document that a custom particle has catalog identity only. It cannot be passed to an NMS particle packet or converted through `RegistryKey.PARTICLE_TYPE` as a native holder unless a separate native client implementation is installed.

- [ ] **Step 3: Adapt server lookup and generated metadata**

Change `PaperSimpleRegistry`/`PaperCatalogRegistry` so `particleType()` merges `VanillaParticle` values resolved from `BuiltInRegistries.PARTICLE_TYPE` with `ParticleRegistry` values. Update the generated `PARTICLE_TYPE` entry to `apiOnly(CATALOG, ...)` and keep its registry view live after catalog publication.

Add a native conversion guard that throws `IllegalArgumentException("particle ... is catalog-backed and has no native NMS holder")` for a custom value. Native vanilla particles continue to use the existing NMS mapping and tags.

- [ ] **Step 4: Test particle behavior**

`ParticleRegistryTest` must cover:

- vanilla constants and `VanillaParticle.values()` remain unchanged;
- a custom namespaced particle is visible through `ParticleRegistry` and `Registry.PARTICLE_TYPE`;
- custom iteration is deterministic and atomic;
- duplicate/`minecraft` keys fail before publication;
- custom particle builder metadata round-trips;
- native packet conversion rejects the custom value while vanilla conversion succeeds.

- [ ] **Step 5: Commit the particle unit**

```bash
git add alkahest-api/src/main/java/org/bukkit/Particle.java \
  alkahest-api/src/main/java/org/bukkit/VanillaParticle.java \
  alkahest-api/src/main/java/org/bukkit/ParticleRegistry.java \
  alkahest-api/src/main/java/com/destroystokyo/paper/ParticleBuilder.java \
  alkahest-api/src/test/java/org/bukkit/ParticleRegistryTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java \
  paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java
git commit -m "feat: open particle catalog"
```

## Task 3: Open the potion catalog without enum-only assumptions

- [ ] **Step 1: Move generated vanilla potions to `VanillaPotionType`**

Move the generated constants and vanilla `Bukkit.getUnsafe().getInternalPotionData` behavior from `PotionType.java` to `VanillaPotionType.java`. Make `VanillaPotionType` implement `PotionType`. Keep `PotionType.getByEffect`, `values`, and `valueOf` as compatibility helpers with documented vanilla/catalog iteration semantics; no custom value is silently cast to an enum.

Preserve `FeatureDependant`, potion-effect accessors, deprecated effect methods, and all generated source markers. A custom potion implementation must provide the same observable effect metadata and feature-flag behavior without using the vanilla enum supplier.

- [ ] **Step 2: Add typed `PotionTypeRegistry` registration**

Implement a registration builder requiring a namespaced key, immutable potion effects, upgrade/extension flags, maximum level, and feature flag policy. Validate effect types and duplicate keys before atomically publishing a custom `PotionType` implementation.

The catalog value is a logical Bukkit potion type only. It is not inserted into `BuiltInRegistries.POTION`, cannot be assigned a native potion ID, and cannot be serialized through a native potion holder.

- [ ] **Step 3: Adapt server lookup and conversions**

Change `PaperSimpleRegistry.potion()` to merge `VanillaPotionType`/native NMS potion values with `PotionTypeRegistry` values. Update generated API-only metadata to use `CATALOG`. Ensure `CraftPotionEffectType` and item potion serializers reject catalog potion types at the native boundary with the backend-specific error.

- [ ] **Step 4: Test potion behavior**

`PotionTypeRegistryTest` must cover vanilla helper compatibility, custom effect metadata, feature flags, deterministic iteration, duplicate/namespace validation, atomic publication, and rejection by native potion serialization. Include a regression for `getByEffect` so it does not assume `PotionType.values()` contains catalog entries.

- [ ] **Step 5: Commit the potion unit**

```bash
git add alkahest-api/src/main/java/org/bukkit/potion/PotionType.java \
  alkahest-api/src/main/java/org/bukkit/potion/VanillaPotionType.java \
  alkahest-api/src/main/java/org/bukkit/potion/PotionTypeRegistry.java \
  alkahest-api/src/test/java/org/bukkit/potion/PotionTypeRegistryTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java
git commit -m "feat: open potion catalog"
```

## Task 4: Formalize memory, material, and merged entity views

- [ ] **Step 1: Add custom memory-key creation**

Create `MemoryKeyRegistry.create(NamespacedKey, Class<T>)` and make `MemoryKey` delegate registration and lookup to it. Keep generated static constants and deprecated constants intact. Require a non-`minecraft` namespace for custom keys, reject duplicate keys, and return immutable `values()` snapshots.

Update `Registry.MEMORY_MODULE_TYPE` and the generated `MEMORY_MODULE_TYPE` API-only supplier to read the live catalog. Mark custom memory keys as catalog-backed; native Brain/memory-module code must reject them unless a native implementation is explicitly added later.

- [ ] **Step 2: Make material publication deterministic and atomic**

Update `MaterialRegistry` and `CustomBlockCatalog` so `Registry.MATERIAL` returns vanilla non-legacy materials followed by custom definitions sorted by full key. Reject a custom key colliding with any vanilla `VanillaMaterial` before `CustomBlocks` publishes it. Preserve carrier semantics: `Block#getType()` and `ItemStack#getType()` remain vanilla carriers, while custom identity remains PDC/lookup-backed.

Add `MaterialRegistry.isNative(Material)`/`isCatalog(Material)` and use them in native item/block conversions. Native-only tag and packet APIs reject custom materials rather than attempting a fake NMS `Block` or `Item` ID.

- [ ] **Step 3: Make entity publication deterministic and merged**

Update `EntityTypeRegistry`, `CustomEntityCatalog`, and `CustomEntities` so iteration is vanilla values followed by custom definitions sorted by key. Preserve `vanilla()` for the server tag-aware wrapper. Native entity tags remain native-only; custom definitions use their namespaced catalog identity.

Update `PaperSimpleRegistry.entityType()` and server `EntityTypeRegistry` to use the shared catalog snapshot boundary. Ensure `EntityType.getByKey` resolves custom values, while `EntityType.fromId` and native spawn/network paths remain vanilla-only and reject catalog values clearly.

- [ ] **Step 4: Test memory/material/entity semantics**

Add or extend tests for:

- custom memory creation, duplicate/namespace rejection, snapshot iteration, and native Brain rejection;
- material merge order, custom block identity across item/world lookup, duplicate vanilla collision, and native conversion rejection;
- entity merge order, custom identity persistence through the existing carrier path, native-only tags, and rejection by `fromId`/native spawn conversion;
- registry size/iteration snapshots under concurrent reads and registration.

- [ ] **Step 5: Commit the merged/catalog unit**

```bash
git add alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKey.java \
  alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKeyRegistry.java \
  alkahest-api/src/main/java/org/bukkit/Registry.java \
  alkahest-api/src/main/java/org/bukkit/MaterialRegistry.java \
  alkahest-api/src/main/java/org/bukkit/EntityTypeRegistry.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockCatalog.java \
  alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlocks.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntityCatalog.java \
  alkahest-api/src/main/java/dev/mintychochip/customentity/CustomEntities.java \
  alkahest-api/src/test/java/org/bukkit/entity/memory/MemoryKeyRegistryTest.java \
  alkahest-api/src/test/java/org/bukkit/MaterialRegistryTest.java \
  alkahest-api/src/test/java/org/bukkit/EntityTypeRegistryTest.java \
  alkahest-api/src/test/java/dev/mintychochip/customblock/CustomBlockCatalogTest.java \
  alkahest-api/src/test/java/dev/mintychochip/customentity/CustomEntityCatalogTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java \
  paper-server/src/main/java/io/papermc/paper/registry/EntityTypeRegistry.java
 git commit -m "feat: formalize merged catalog registries"
```

## Task 5: Complete backend adapters and verify the public catalog

- [ ] **Step 1: Wire generated API-only metadata**

Update `RegistryEntries.API_ONLY` and regenerate. The generated server manifest must contain:

```text
PARTICLE_TYPE      -> CATALOG
POTION             -> CATALOG
MEMORY_MODULE_TYPE -> CATALOG
ENTITY_TYPE        -> MERGED
```

Use live suppliers for all four views. Do not create a second backend map or infer backend from the concrete registry class.

- [ ] **Step 2: Add backend-specific access checks**

Update `PaperRegistryAccess` and `PaperRegistries` so:

- catalog/merged keys return their live registry view from `getRegistry`;
- `getWritableRegistry` rejects catalog/merged keys with the backend and the native-holder limitation;
- native value conversion accepts only native values and reports the catalog key when rejecting a custom value;
- tag queries on merged entity/material views are explicitly native-only;
- no custom catalog value is retained as a native holder across any registry reload or server restart.

- [ ] **Step 3: Assert reloadable scope explicitly**

Add a manifest test that enumerates all current `RegistryKey` values and asserts:

1. every key has one backend;
2. only `PARTICLE_TYPE`, `POTION`, and `MEMORY_MODULE_TYPE` are `CATALOG`;
3. only `ENTITY_TYPE` is `MERGED`;
4. no current key is `NATIVE_RELOADABLE`;
5. promotion of `LOOT_TABLE`, `ITEM_MODIFIER`, or `PREDICATE` would require a new typed `RegistryKey` and a replay-on-reload declaration path.

This is a scope assertion, not a hidden implementation of NMS-only registries.

- [ ] **Step 4: Run catalog and registry tests**

```bash
./gradlew :alkahest-api:test \
  --tests 'org.bukkit.ParticleRegistryTest' \
  --tests 'org.bukkit.potion.PotionTypeRegistryTest' \
  --tests 'org.bukkit.entity.memory.MemoryKeyRegistryTest' \
  --tests 'org.bukkit.MaterialRegistryTest' \
  --tests 'org.bukkit.EntityTypeRegistryTest' \
  --tests 'dev.mintychochip.customblock.*' \
  --tests 'dev.mintychochip.customentity.*'
./gradlew :paper-generator:rewrite
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.CatalogRegistryTest' \
  --tests 'io.papermc.paper.registry.MergedRegistryTest' \
  --tests 'io.papermc.paper.registry.RegistryBackendTest' \
  --tests 'io.papermc.paper.registry.*'
```

Expected: catalog values resolve through the public views, native-only paths reject them, merged entity/material iteration is deterministic, and every public `RegistryKey` has an explicit backend.

- [ ] **Step 5: Run the end-to-end catalog smoke scenario**

Launch the server with one custom particle, potion, memory key, material, and entity definition. Exercise lookup, iteration, native-boundary rejection, custom item/block/entity persistence, and server restart. Confirm that vanilla values retain native holder IDs and tags while custom values retain only their namespaced catalog identity.

- [ ] **Step 6: Commit the catalog verification unit**

```bash
git add paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/CatalogRegistryTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/MergedRegistryTest.java
git commit -m "test: verify catalog and merged registries"
```

The final smoke matrix may now treat every current public `RegistryKey` as having a tested backend and a documented native/catalog identity policy.
