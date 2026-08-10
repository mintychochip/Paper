# Data-Driven Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open the currently closed datapack/worldgen registries—biomes, structures, and animal sound variants—through typed builders, holder-safe composition, tags, and network codecs.

**Architecture:** Extend the existing delayed `MappedRegistry` load path. API data interfaces describe NMS-free values; server `Paper*RegistryEntry` implementations convert those builders to holders and NMS records. Generator metadata emits writable registry definitions and lifecycle providers. The registry loader composes plugin values before its existing tag bind/freeze/validation sequence.

**Tech Stack:** Java 25, Gradle source generator, Paper registry events, Mojang codecs, `MappedRegistry`, JUnit 5, server integration tests.

## Global Constraints

- Foundation plan `2026-08-08-dynamic-registry-foundation.md` is complete first.
- Use `RegistryBackendKind.NATIVE_DATA` for every registry in this plan.
- Plugin keys are namespaced and are registered before the data registry layer is frozen.
- Tag contents use the existing `RegistryLoadTask.registerTags`/`TagLoader` path; do not call `MappedRegistry.prepareTagReload` on an unfrozen registry.
- Holders referenced by builders come from `Conversions`, never from a stale registry snapshot.
- `BIOME` and `STRUCTURE` values must be available before dependent worldgen lookup providers are built; existing generated chunks are not retroactively changed.
- Do not modify `dev.mintychochip` packages or add code under the Minecraft patch tree.

---

## File map

### API

- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/WolfSoundVariantRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/CatSoundVariantRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/ChickenSoundVariantRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/CowSoundVariantRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/PigSoundVariantRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/BiomeRegistryEntry.java`
- **Create:** `alkahest-api/src/main/java/io/papermc/paper/registry/data/StructureRegistryEntry.java`
- **Modify:** generated `alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java` through the generator.
- **Create:** `alkahest-api/src/test/java/io/papermc/paper/registry/data/SoundVariantRegistryEntryTest.java`
- **Create:** `alkahest-api/src/test/java/io/papermc/paper/registry/data/BiomeRegistryEntryTest.java`
- **Create:** `alkahest-api/src/test/java/io/papermc/paper/registry/data/StructureRegistryEntryTest.java`

### Server conversions

- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperWolfSoundVariantRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperCatSoundVariantRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperChickenSoundVariantRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperCowSoundVariantRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperPigSoundVariantRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperBiomeRegistryEntry.java`
- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureRegistryEntry.java`
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java` through generation.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryListenerManager.java` only where the existing buildable metadata dispatch needs to recognize the new entries.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/data/util/Conversions.java` only if a new DTO needs an existing holder conversion exposed; do not duplicate holder conversion logic.

### Generator and lifecycle

- **Modify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java` — add builder implementation metadata for the seven entries.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java` — preserve explicit backend calls while emitting `.writable(...)`.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java` — emit providers for every non-`NONE` data-driven builder.
- **Verify:** `paper-server/src/minecraft/java/net/minecraft/resources/ResourceManagerRegistryLoadTask.java` — retain the order: element registration, holder locking, compose listeners, tag loading, tag binding, then outer registry freeze.

### Tests

- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryTest.java`
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBuilderTest.java`
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java`
- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryReloadTest.java`

---

## Task 1: Open the five sound-variant registries

The NMS records are:

- `WolfSoundVariant(WolfSoundSet adultSounds, WolfSoundSet babySounds)` with ambient, death, growl, hurt, pant, whine, and step sounds;
- `CatSoundVariant(CatSoundSet adultSounds, CatSoundSet babySounds)` with ambient, stray ambient, hiss, hurt, death, eat, beg-for-food, purr, and purreow sounds;
- `ChickenSoundVariant(ChickenSoundSet adultSounds, ChickenSoundSet babySounds)` with ambient, hurt, death, and step sounds;
- `PigSoundVariant(PigSoundSet adultSounds, PigSoundSet babySounds)` with ambient, hurt, death, step, and eat sounds;
- `CowSoundVariant` with ambient, hurt, death, and step sounds.

All sound fields are `TypedKey<Sound>` in the API and `Holder<SoundEvent>` in NMS.

- [ ] **Step 1: Write the failing API compilation tests**

Add API tests under `alkahest-api/src/test/java/io/papermc/paper/registry/data/SoundVariantRegistryEntryTest.java` that instantiate the builders with typed sound keys and assert the builder exposes every NMS codec field. The test must include one adult/baby value for wolf and one single set for cow.

Use the existing `RegistryKey.typedKey(String)` helper so the test does not depend on an undeclared generated key class:

```java
private static TypedKey<Sound> sound(final String key) {
    return RegistryKey.SOUND_EVENT.typedKey(key);
}

@Test
void wolfBuilderAcceptsEverySoundField() {
    WolfSoundVariantRegistryEntry.Builder builder = WolfSoundVariantRegistryEntry.builder();
    builder.adultSounds(set -> set
        .ambientSound(sound("entity.wolf.ambient"))
        .deathSound(sound("entity.wolf.death"))
        .growlSound(sound("entity.wolf.growl"))
        .hurtSound(sound("entity.wolf.hurt"))
        .pantSound(sound("entity.wolf.pant"))
        .whineSound(sound("entity.wolf.whine"))
        .stepSound(sound("entity.wolf.step")));
    assertNotNull(builder);
}
```

- [ ] **Step 2: Run the API test and verify it fails**

```bash
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.SoundVariantRegistryEntryTest'
```

Expected: compilation failure because the five entry interfaces and their sound-key builders do not exist.

- [ ] **Step 3: Add typed API entry contracts**

Create one interface per NMS record. Each interface extends `RegistryBuilder<T>` through its nested `Builder`. Use `TypedKey<Sound>` for every sound reference and require all fields in `build()`.

The cow contract must include:

```java
public interface CowSoundVariantRegistryEntry {
    TypedKey<Sound> ambientSound();
    TypedKey<Sound> hurtSound();
    TypedKey<Sound> deathSound();
    TypedKey<Sound> stepSound();

    interface Builder extends CowSoundVariantRegistryEntry, RegistryBuilder<Cow.SoundVariant> {
        Builder ambientSound(TypedKey<Sound> key);
        Builder hurtSound(TypedKey<Sound> key);
        Builder deathSound(TypedKey<Sound> key);
        Builder stepSound(TypedKey<Sound> key);
    }
}
```

The four adult/baby variants use nested `SoundSet`/`SoundSetBuilder` interfaces with the exact fields listed above. Expose `builder()` factories matching existing data-entry patterns.

- [ ] **Step 4: Add server fillers and NMS conversion**

Each `Paper*SoundVariantRegistryEntry` must implement the API view and nested builder. Convert each `TypedKey<Sound>` with:

```java
private Holder<SoundEvent> sound(final TypedKey<Sound> key) {
    return this.conversions.getReferenceHolder(PaperRegistries.toNms(key));
}
```

Construct the exact NMS record (`new WolfSoundVariant(...)`, `new CatSoundVariant(...)`, etc.) and expose references back with `PaperRegistries.fromNms(holder.unwrapKey().orElseThrow())`. Do not store `SoundEvent` objects directly in the API builder.

- [ ] **Step 5: Register generator metadata and regenerate**

Add `.writableApiRegistryBuilder(...)` entries for `WOLF_SOUND_VARIANT`, `CAT_SOUND_VARIANT`, `CHICKEN_SOUND_VARIANT`, `COW_SOUND_VARIANT`, and `PIG_SOUND_VARIANT` in `RegistryEntries.DATA_DRIVEN`, and add `.delayed()` to all five entries so their buildable metadata is installed on the delayed data-loader path. Run:

```bash
./gradlew :paper-generator:rewrite
```

Expected: generated `PaperRegistries.java` uses `.backend(RegistryBackendKind.NATIVE_DATA).writable(...)` and generated `RegistryEvents.java` contains five providers.

- [ ] **Step 6: Run builder equality and event tests**

```bash
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.DataDrivenRegistryTest' \
  --tests 'io.papermc.paper.registry.RegistryBuilderTest'
```

Expected: vanilla sound-variant entries round-trip through each builder; compose registration adds a namespaced entry; a missing sound holder fails before registry mutation.

- [ ] **Step 7: Commit the sound-variant unit**

```bash
git add alkahest-api/src/main/java/io/papermc/paper/registry/data/*SoundVariantRegistryEntry.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/SoundVariantRegistryEntryTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/Paper*SoundVariantRegistryEntry.java \
  paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryTest.java \
  paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java
git commit -m "feat: open animal sound variant registries"
```

## Task 2: Add a typed biome definition and worldgen composition

- [ ] **Step 1: Define the NMS-free biome data model**

Create `BiomeRegistryEntry` with immutable nested values for the exact `Biome.DIRECT_CODEC` fields:

```java
record Climate(boolean hasPrecipitation, float temperature, TemperatureModifier temperatureModifier, float downfall) {}
record Effects(int waterColor, Integer foliageColor, Integer dryFoliageColor, Integer grassColor, GrassColorModifier grassColorModifier) {}
```

Add typed values for mob-spawn settings and generation settings that use `RegistryKeySet`/`TypedKey` references for biomes, placed features, carvers, structures, and entity types. The builder must require climate, effects, mob spawns, and generation settings before `build()`; optional colors use `OptionalInt`/nullable API annotations and never magic sentinel values.

Expose `TemperatureModifier` (`NONE`, `FROZEN`) and `GrassColorModifier` (`NONE`, `DARK_FOREST`, `SWAMP`) as API enums whose serialized names match Mojang codecs.

- [ ] **Step 2: Test builder completeness and codec-shaped validation**

Create API tests for missing required fields, valid default modifiers, color omission, and typed holder references. Run:

```bash
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.BiomeRegistryEntryTest'
```

Expected: the missing-required-field test fails before implementation and passes after the builder is complete.

- [ ] **Step 3: Implement `PaperBiomeRegistryEntry`**

Convert the DTOs to `Biome.BiomeBuilder`, `BiomeSpecialEffects`, `MobSpawnSettings`, `BiomeGenerationSettings`, and `EnvironmentAttributeMap`. Obtain all registry references from the `Conversions` lookup for the active registry snapshot. `build()` must throw an `IllegalStateException` naming every missing required group.

Use `Biome.DIRECT_CODEC` in the equality test to confirm that a built value serializes and decodes with the same registry lookup.

- [ ] **Step 4: Make `BIOME` writable in generator metadata**

Add `.writableApiRegistryBuilder(BiomeRegistryEntry.Builder.class, "PaperBiomeRegistryEntry.PaperBuilder")` to the `BIOME` entry while retaining `.delayed()`. Regenerate and verify the generated compose provider is present.

- [ ] **Step 5: Verify worldgen timing and registration**

In `DataDrivenRegistryTest`, register `test:temperate` during the compose callback, then assert:

```java
Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
assertNotNull(registry.get(NamespacedKey.fromString("test:temperate")));
```

Verify the registration occurs before the lookup provider used by the worldgen layer is frozen. Do not load a world chunk in this unit test; the reload smoke test covers active-world boundaries.

- [ ] **Step 6: Commit the biome unit**

```bash
git add alkahest-api/src/main/java/io/papermc/paper/registry/data/BiomeRegistryEntry.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/BiomeRegistryEntryTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperBiomeRegistryEntry.java \
  paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryTest.java \
  paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java
git commit -m "feat: open biome registry composition"
```

## Task 3: Add typed structure composition

`Structure` is abstract and its codec dispatches through `STRUCTURE_TYPE`. This task supports plugin values using an existing registered structure type and makes the type dependency explicit. A custom structure type/codec is supplied by the native-static plan before a plugin can use one.

- [ ] **Step 1: Define `StructureRegistryEntry`**

Expose typed fields for `Structure.StructureSettings`: a `RegistryKeySet<Biome>`, spawn overrides keyed by `MobCategory`, `GenerationStep.Decoration`, and `TerrainAdjustment`. The builder also takes a `TypedKey<StructureType>` to select the existing codec dispatch target.

```java
interface Builder extends StructureRegistryEntry, RegistryBuilder<Structure> {
    Builder structureType(TypedKey<StructureType> type);
    Builder biomes(RegistryKeySet<Biome> biomes);
    Builder step(Decoration step);
    Builder terrainAdjustment(TerrainAdjustment adjustment);
    Builder spawnOverride(MobCategory category, SpawnOverride override);
}
```

- [ ] **Step 2: Implement NMS conversion and dependency checks**

`PaperStructureRegistryEntry` must resolve the structure type and biome holders from the active `Conversions` lookup. Reject an empty biome set, missing structure type, invalid spawn category, or unresolved configured feature before creating the NMS structure. Use the selected `StructureType.codec()` to encode/decode the final value.

- [ ] **Step 3: Register and test the structure builder**

Add generator metadata and run `./gradlew :paper-generator:rewrite`. Add a compose test that uses a vanilla structure type and a custom namespaced key. Assert that the registry contains the custom holder and that the codec round trip retains its settings. Add a separate assertion that an unknown structure type fails without adding the key.

- [ ] **Step 4: Commit the structure unit**

```bash
git add alkahest-api/src/main/java/io/papermc/paper/registry/data/StructureRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureRegistryEntry.java \
  paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryTest.java \
  paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java
git commit -m "feat: open structure registry composition"
```

## Task 4: Verify tags, reload safety, and final data-driven coverage

- [ ] **Step 1: Add reload declaration replay**

Create `DataDrivenRegistryReloadTest` around the existing `ReloadableServerRegistries`/worldgen reload harness. Register one sound variant and one biome declaration, reload a resource manager with an added datapack tag, and assert the new registry snapshot contains:

1. the vanilla value;
2. the plugin value;
3. the datapack tag bound to holders from the new snapshot;
4. no holder object reused from the previous snapshot.

- [ ] **Step 2: Verify tag order**

Assert the loader sequence remains:

```text
register normal values
lock references
run compose listeners
load tag files
bind tags
freeze/validate/publish
```

The test must fail if compose runs after tag binding or if plugin holders are retained across reload.

- [ ] **Step 3: Run all data-driven tests**

```bash
./gradlew :alkahest-api:test --tests 'io.papermc.paper.registry.data.*'
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.*'
```

Expected: all existing writable registry tests pass, all seven formerly closed entries have `Buildable` metadata, every generated event provider exists, and reload tests pass.

- [ ] **Step 4: Verify generated sources and commit**

```bash
./gradlew :paper-generator:rewrite
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.*'
git status --short
git add paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/RegistryEventsRewriter.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/WolfSoundVariantRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/CatSoundVariantRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/ChickenSoundVariantRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/CowSoundVariantRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/PigSoundVariantRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/BiomeRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/data/StructureRegistryEntry.java \
  alkahest-api/src/main/java/io/papermc/paper/registry/event/RegistryEvents.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/SoundVariantRegistryEntryTest.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/BiomeRegistryEntryTest.java \
  alkahest-api/src/test/java/io/papermc/paper/registry/data/StructureRegistryEntryTest.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryListenerManager.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperWolfSoundVariantRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperCatSoundVariantRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperChickenSoundVariantRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperCowSoundVariantRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperPigSoundVariantRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperBiomeRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/PaperStructureRegistryEntry.java \
  paper-server/src/main/java/io/papermc/paper/registry/data/util/Conversions.java \
  paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/DataDrivenRegistryReloadTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBuilderTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java
git commit -m "test: verify data-driven registry lifecycle"
```

The generated-output diff must contain only registry definitions/events required by this plan. No `fixupSourcePatches` or `rebuildPatches` is needed unless a vanilla file under `src/minecraft/java` was actually changed.
