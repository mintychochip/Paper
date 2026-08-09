# All Breedable Genetics Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit vanilla-parity genetics profiles and lifecycle coverage for every current breedable mob, including horse/donkey/mule and villager/happy-ghast reproduction paths.

**Architecture:** Keep one NMS-free genome/meiosis engine and expand `GeneticsProfiles` into a complete explicit registry. Reusable variant and empty profiles cover common species; custom profiles cover sheep, pandas, equines, llamas, and villagers. The server facade captures vanilla founder state, persists profile IDs, applies phenotypes after insertion, and prepares children before insertion for both Animal and Villager breeding.

**Tech Stack:** Java 25, Gradle, JUnit 5, Bukkit/Paper API, Minecraft NMS sources, Paper source patch workflow.

## Global Constraints

- All owned code remains under `dev.mintychochip.*`.
- API profile code stays NMS-free under `alkahest-api/src/main/java/dev/mintychochip/genetics/`.
- Server adapters and NMS bridges stay under `paper-server/src/main/java/dev/mintychochip/genetics/`.
- Vanilla edits are limited to thin hooks under `paper-server/src/minecraft/java/net/minecraft/...` and must regenerate source patches.
- Every listed standard breedable type, `MULE`, and `HAPPY_GHAST` has an explicit profile entry.
- `GenomeCodec` JSON remains `sex` plus `genes`; `MintyGenomeProfile` is additive NBT.
- Profiles without inherited vanilla traits use explicit empty catalogs; do not invent unrelated gameplay mechanics.
- Existing generic genome/cross helpers and legacy cat/wolf/cow mappings remain compatible.
- Each production behavior change starts with a failing test and ends with focused green verification.
- Skip formatters, linters, and project-wide test suites during individual tasks; run focused verification once at the end.

---

### Task 1: Build the complete profile registry and coverage contract

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/BreedableEntityTypes.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/EmptyGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/VariantGeneticsProfile.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfiles.java`
- Create: `alkahest-api/src/test/java/dev/mintychochip/genetics/BreedableProfilesCoverageTest.java`

**Interfaces:**
- `BreedableEntityTypes.parentBreedingTypes(): Set<EntityType>` returns exactly `ARMADILLO`, `AXOLOTL`, `BEE`, `CAMEL`, `CHICKEN`, `CAT`, `COW`, `MOOSHROOM`, `DONKEY`, `HORSE`, `LLAMA`, `OCELOT`, `FOX`, `FROG`, `GOAT`, `HOGLIN`, `NAUTILUS`, `PANDA`, `PIG`, `RABBIT`, `SHEEP`, `SNIFFER`, `STRIDER`, `TURTLE`, `WOLF`, and `VILLAGER`.
- `BreedableEntityTypes.explicitTypes(): Set<EntityType>` returns the parent set plus `MULE` and `HAPPY_GHAST`.
- `GeneticsProfiles.isExplicit(EntityType): boolean` reports registry membership.
- `GeneticsProfiles.forEntityType(EntityType): GeneticsProfile` returns an explicit profile for every explicit type and the existing generic profile otherwise.
- `VariantGeneticsProfile.of(String id, String locusKey, List<String> labels)` creates an autosomal multiallelic profile whose founder labels are selected uniformly and whose phenotype exposes the resolved label at `locusKey`.
- `EmptyGeneticsProfile.of(String id)` creates a profile with an empty `LocusCatalog`, valid founder generation, and an empty `PhenotypeSnapshot`.

- [ ] **Step 1: Write the failing registry coverage tests**

```java
@Test
void everyBreedableTypeHasAnExplicitProfile() {
    for (final EntityType type : BreedableEntityTypes.explicitTypes()) {
        final GeneticsProfile profile = GeneticsProfiles.forEntityType(type);
        assertTrue(GeneticsProfiles.isExplicit(type));
        assertNotEquals(GeneticsProfiles.generic(), profile);
        assertFalse(profile.id().isBlank());
        assertNotNull(profile.catalog());
        assertNotNull(profile.mutation());
        assertNotNull(profile.recombination());
        assertNotNull(profile.phenotype(profile.founder(Sex.FEMALE, new Random(1L))));
    }
}

@Test
void unknownTypesRetainGenericFallback() {
    assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.POLAR_BEAR));
}
```

Add a parameterized test that constructs every `VariantGeneticsProfile` with one label and asserts the decoded trait key/value. Add an empty-profile test that generates, crosses, and codec-round-trips a genome without requiring any locus.

- [ ] **Step 2: Run the focused coverage tests and verify the expected red state**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.BreedableProfilesCoverageTest'
```

Expected: compilation or assertion failure because the explicit registry and profile implementations do not yet exist.

- [ ] **Step 3: Implement registry primitives and explicit base entries**

Implement immutable `LinkedHashSet`/`LinkedHashMap` registries so iteration order is stable. Register all explicit types immediately. Use existing species profiles for sheep and generic profile fallback only for non-explicit types; use `VariantGeneticsProfile` for the variant families and `EmptyGeneticsProfile` for the no-trait entries until custom profiles replace those entries in later tasks.

`VariantGeneticsProfile` must create one locus with `DominanceMode.COMPLETE`, generate labeled functional alleles, and decode a deterministic visible label. `EmptyGeneticsProfile.phenotype` must return `new PhenotypeSnapshot(List.of())`.

- [ ] **Step 4: Run the coverage tests and verify green**

Run the same focused command. Expected: all registry, variant, and empty-profile assertions pass.

- [ ] **Step 5: Commit the registry unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile \
  alkahest-api/src/test/java/dev/mintychochip/genetics/BreedableProfilesCoverageTest.java
git commit -m "feat(genetics): register every breedable profile"
```

---

### Task 2: Implement vanilla variant profiles and appearance adapters

**Files:**
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfiles.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/VariantLabelSets.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java`
- Modify: `alkahest-api/src/test/java/dev/mintychochip/genetics/BreedableProfilesCoverageTest.java`
- Create: `paper-server/src/test/java/dev/mintychochip/genetics/VariantPhenotypeApplierTest.java`

**Interfaces:**
- `VariantLabelSets` exposes immutable current labels for axolotl, cat, chicken, cow, mooshroom, fox, frog, pig, rabbit, and wolf.
- `PhenotypeApplier.apply(AgeableMob, PhenotypeSnapshot): boolean` accepts the common ageable base; the existing Animal overload may delegate to it.
- Each variant profile emits one trait whose key is `<species>.variant` and whose value is the selected registry label.

- [ ] **Step 1: Write failing API tests for all variant profile labels**

For each variant type, obtain the explicit profile, build a founder with a deterministic random source, decode its phenotype, and assert the trait key is present and its label belongs to that profile's label set. Add direct decoder tests for one representative label from each family.

- [ ] **Step 2: Run the variant API tests and verify red**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.BreedableProfilesCoverageTest'
```

Expected: failures because the registry still points to placeholder profiles and the variant label sets/adapters are absent.

- [ ] **Step 3: Replace placeholders with variant profiles**

Register explicit variant profiles with stable IDs and labels. Preserve `SHEEP` as `SheepGeneticsProfile.INSTANCE`. Keep legacy generic `coat` decoding unchanged for old NBT.

- [ ] **Step 4: Write failing server adapter tests**

Use the existing structural/test-support style to assert `PhenotypeApplier` contains branches for `AXOLOTL`, `CAT`, `CHICKEN`, `COW`, `MOOSHROOM`, `FOX`, `FROG`, `PIG`, `RABBIT`, `SHEEP`, and `WOLF`. Add direct adapter tests where Bukkit dummy entities support the setter; otherwise assert invalid/missing labels return `false` without throwing.

- [ ] **Step 5: Implement variant appearance adapters**

Extend `PhenotypeApplier` with explicit branches using the Bukkit setters/variant enums: `Axolotl#setVariant`, `Cat#setCatType`, `Chicken#setVariant`, `Cow#setVariant`, `MushroomCow#setVariant`, `Fox#setFoxType`, `Frog#setVariant`, `Pig#setVariant`, `Rabbit#setRabbitType`, `Sheep#setColor`, and `Wolf#setVariant`. Unknown labels must return `false`.

- [ ] **Step 6: Run focused API and server adapter tests**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.BreedableProfilesCoverageTest'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

- [ ] **Step 7: Commit variant profiles and adapters**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile \
  alkahest-api/src/test/java/dev/mintychochip/genetics/BreedableProfilesCoverageTest.java \
  paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java \
  paper-server/src/test/java/dev/mintychochip/genetics/VariantPhenotypeApplierTest.java
git commit -m "feat(genetics): add breedable variant profiles"
```

---

### Task 3: Implement sheep, panda, equine, llama, and villager profile contracts

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/PandaGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/PandaPhenotypeDecoder.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/EquineGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/LlamaGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/VillagerGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/BreedPlan.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/BreedContext.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfile.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfiles.java`
- Create: `alkahest-api/src/test/java/dev/mintychochip/genetics/SpecializedBreedableProfilesTest.java`

**Interfaces:**
- `BreedContext` contains parent `EntityType`s, child `EntityType`, and an optional environmental variant string for villager type resolution.
- `BreedPlan` contains `familyProfile()`, `childProfile()`, and `breed(Genome, Genome, RandomGenerator, BreedContext): Optional<Genome>`.
- `GeneticsProfiles.resolveBreed(EntityType parentA, EntityType parentB, EntityType child): Optional<BreedPlan>` resolves ordinary same-family crosses, horse/donkey → mule, and villager breeding.
- `GeneticsProfile` gains a default `breed(Genome, Genome, RandomGenerator, BreedContext)` that delegates to `BreedingEngine` using the profile catalog/settings; custom profiles override it only where vanilla inheritance needs a special rule.

- [ ] **Step 1: Write failing specialized-profile tests**

Cover these observable contracts:

```java
@Test
void pandaRecessiveGenesResolveLikeVanilla() { /* BROWN/BROWN -> BROWN; BROWN/NORMAL -> NORMAL */ }

@Test
void horseAndDonkeyResolveToMulePlan() {
    final BreedPlan plan = GeneticsProfiles.resolveBreed(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE).orElseThrow();
    assertEquals("mule", plan.childProfile().id());
}

@Test
void villagerTypeUsesEnvironmentOrParentAlleles() { /* deterministic context rolls */ }

@Test
void equineNumericTraitUsesReflectedVanillaFormula() { /* min, max, midpoint, bound reflection */ }
```

Use real `BreedingEngine` for discrete loci and a scripted `RandomGenerator` for deterministic quantitative and villager cases.

- [ ] **Step 2: Run specialized tests and verify red**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.SpecializedBreedableProfilesTest'
```

Expected: compilation failure because `BreedPlan`, specialized profiles, and profile-aware breeding do not exist.

- [ ] **Step 3: Add profile-aware breeding defaults and context**

Add the default `GeneticsProfile.breed` method and immutable `BreedContext`/`BreedPlan`. `GeneticsProfiles.resolveBreed` must reject unrelated parent/child combinations and select the shared equine family for horse/donkey/mule.

- [ ] **Step 4: Implement panda profile**

Create `panda.main` and `panda.hidden` autosomal loci with labels `NORMAL`, `LAZY`, `WORRIED`, `PLAYFUL`, `BROWN`, `WEAK`, and `AGGRESSIVE`. Decode a non-recessive main gene directly; decode a recessive main gene only when it equals the hidden gene; otherwise return `NORMAL`. Mark `BROWN` and `WEAK` recessive.

- [ ] **Step 5: Implement equine and llama profiles**

Expose horse/donkey/mule shared loci for color/markings where applicable and bounded numeric speed/jump/health traits. Implement exact bounded vanilla `createOffspringAttribute` arithmetic using three random doubles and one-reflection bound handling. Add llama color and bounded strength loci.

- [ ] **Step 6: Implement villager profile**

Expose `villager.type` as a multiallelic locus. The custom breed method uses one deterministic roll: below `0.50` choose the environmental type supplied by `BreedContext`; `[0.50, 0.75)` choose parent A's type; otherwise choose parent B's type. Profession is not encoded.

- [ ] **Step 7: Run specialized tests and verify green**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.SpecializedBreedableProfilesTest'
```

- [ ] **Step 8: Commit specialized profile contracts**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile \
  alkahest-api/src/test/java/dev/mintychochip/genetics/SpecializedBreedableProfilesTest.java
git commit -m "feat(genetics): add specialized breedable profiles"
```

---

### Task 4: Add server founder capture and profile-aware cache behavior

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/FounderCapture.java`
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/FounderCaptures.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java`
- Create: `paper-server/src/test/java/dev/mintychochip/genetics/FounderCaptureTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsTest.java`

**Interfaces:**
- `FounderCapture.capture(AgeableMob entity, GeneticsProfile profile, Sex sex, RandomGenerator random): Genome` returns a profile genome seeded from current vanilla state.
- `FounderCaptures.capture(...)` dispatches explicit adapters by Bukkit `EntityType` and delegates to `profile.founder` when no state adapter exists.
- `AnimalGenetics.onAddedToWorld(AgeableMob)` is idempotent and stores an immutable `{profileId, genome}` cache entry.
- `AnimalGenetics.profile(AgeableMob)` resolves the Bukkit type; `phenotypeOf(AgeableMob, Genome)` uses the resolved profile.

- [ ] **Step 1: Write failing founder/caching tests**

Test profile capture for a representative variant, sheep color, horse numeric state, and empty profile fallback. Add structural assertions that `load` caches without applying and that save writes both `NBT_KEY` and `PROFILE_NBT_KEY`.

- [ ] **Step 2: Run focused server tests and verify red**

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Expected: compilation failures for `FounderCapture` and ageable-aware methods, or structural assertion failures before implementation.

- [ ] **Step 3: Implement founder capture adapters**

Use Bukkit/NMS state reads for variant enums, sheep dye color, panda genes, horse attributes/color/markings, llama color/strength, and villager type. Build diploid copies with the captured semantic label/value duplicated into both copies so first attachment preserves current vanilla state while later children can recombine.

- [ ] **Step 4: Widen cache and phenotype methods**

Refactor the private cache entry to retain profile ID plus genome. Widen `save`, `load`, `onAddedToWorld`, `profile`, and `phenotypeOf` to `AgeableMob`; retain existing Animal overload behavior. On profile mismatch or missing required loci, capture current state rather than applying the incompatible genome.

- [ ] **Step 5: Run server compilation and focused suite**

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

- [ ] **Step 6: Commit founder capture and cache behavior**

```bash
git add paper-server/src/main/java/dev/mintychochip/genetics \
  paper-server/src/test/java/dev/mintychochip/genetics/FounderCaptureTest.java \
  paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsTest.java
git commit -m "feat(genetics): capture vanilla breedable founder state"
```

---

### Task 5: Move persistence/removal and insertion hooks to all supported ageables

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/AgeableMob.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java`
- Modify or create: `paper-server/src/test/java/dev/mintychochip/genetics/BreedableLifecycleHooksPresentTest.java`

**Interfaces:**
- `AgeableMob` calls `AnimalGenetics.save/load/remove` exactly once through the common lifecycle.
- `ServerLevel.EntityCallbacks.onTrackingStart` calls `AnimalGenetics.onAddedToWorld` for `Animal` and `Villager` entities.
- `VillagerMakeLove` calls `AnimalGenetics.prepareVillagerBreed(source, target, child)` before `addFreshEntityWithPassengers`.
- `Animal.spawnChildFromBreeding` continues to use `AnimalGenetics.prepareBreed` for Animal children.

- [ ] **Step 1: Write failing structural hook tests**

Assert source contains:

```java
assertTrue(ageableSource.contains("AnimalGenetics.save"));
assertTrue(ageableSource.contains("AnimalGenetics.load"));
assertTrue(ageableSource.contains("AnimalGenetics.remove"));
assertTrue(serverLevelSource.contains("AnimalGenetics.onAddedToWorld"));
assertTrue(villagerLoveSource.contains("AnimalGenetics.prepareVillagerBreed"));
assertTrue(animalSource.contains("AnimalGenetics.prepareBreed"));
```

Also assert Animal no longer contains duplicate genetics save/load/removal calls after the hooks move to AgeableMob.

- [ ] **Step 2: Run structural tests and verify red**

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Expected: structural assertions fail because the common hooks and Villager path are not yet patched.

- [ ] **Step 3: Patch AgeableMob common lifecycle**

Call `AnimalGenetics.save(this, output)` after `super.addAdditionalSaveData`, `AnimalGenetics.load(this, input)` after `super.readAdditionalSaveData`, and `AnimalGenetics.remove(this)` after `super.onRemoval(reason)` only for permanent reasons inside the façade. Remove the former Animal-only persistence/removal calls.

- [ ] **Step 4: Patch ServerLevel insertion**

After entity lookup acceptance and `valid = true`, call the façade for `Animal` and `Villager` only. Do not attach unsupported water ageables merely because they extend `AgeableMob`.

- [ ] **Step 5: Patch VillagerMakeLove and retain Animal child preparation**

After `getBreedOffspring` creates the villager child and before the world insertion, call the profile-aware villager preparation method. The method caches the child genome and returns without changing vanilla child creation when profile resolution fails.

- [ ] **Step 6: Regenerate source patches**

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

Inspect the regenerated patch files and ensure only thin `mintychochip` hunks changed.

- [ ] **Step 7: Compile and run lifecycle tests**

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

- [ ] **Step 8: Commit ageable lifecycle integration**

```bash
git add paper-server/patches/sources/net/minecraft \
  paper-server/src/test/java/dev/mintychochip/genetics \
  paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java
git commit -m "feat(genetics): wire all breedable lifecycle paths"
```

---

### Task 6: Apply custom phenotype adapters and special breeding plans

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java`
- Create: `paper-server/src/test/java/dev/mintychochip/genetics/SpecialBreedLifecycleTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java`

**Interfaces:**
- `AnimalGenetics.prepareBreed(Animal, Animal, AgeableMob): @Nullable BreedPrep` uses `GeneticsProfiles.resolveBreed` and the family profile's `breed` method.
- `AnimalGenetics.prepareVillagerBreed(Villager, Villager, Villager): boolean` uses villager type context and caches the child before insertion.
- `PhenotypeApplier.apply(AgeableMob, PhenotypeSnapshot): boolean` applies every explicit visible profile trait and returns `false` for missing/invalid data.

- [ ] **Step 1: Write failing special-path tests**

Cover horse/donkey/mule child profile selection, villager child cache creation before insertion, happy-ghast founder attachment without parents, and deterministic application of horse attributes, llama strength, panda genes, and villager type.

- [ ] **Step 2: Run special-path tests and verify red**

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Expected: failures for missing special resolver and adapters.

- [ ] **Step 3: Implement profile-aware Animal breeding**

Replace same-profile-only checks with `GeneticsProfiles.resolveBreed(parentTypeA, parentTypeB, childType)`. Use `BreedPlan.familyProfile().breed` to create the child genome, cache it under `BreedPlan.childProfile().id()`, and preserve `BreedGenetics` metadata for ordinary Animal events.

- [ ] **Step 4: Implement Villager and Happy Ghast paths**

Use current villager biome/type context to construct `BreedContext`. Cache the child before `addFreshEntityWithPassengers`. For Happy Ghast, let insertion call the explicit founder capture without attempting a parent cross.

- [ ] **Step 5: Implement numeric and special adapters**

Apply horse/donkey/mule attributes through the existing Bukkit/NMS attribute instances, llama color/strength, panda main/hidden genes, and villager type. Keep all invalid labels non-throwing and preserve vanilla defaults when no profile trait exists.

- [ ] **Step 6: Run server compilation and special suite**

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

- [ ] **Step 7: Commit special breeding and phenotype adapters**

```bash
git add paper-server/src/main/java/dev/mintychochip/genetics \
  paper-server/src/test/java/dev/mintychochip/genetics
git commit -m "feat(genetics): apply special breedable phenotypes"
```

---

### Task 7: Run complete profile verification and close the plan

**Files:**
- Modify only tests/docs if a concrete verification gap is found.

- [ ] **Step 1: Run all focused API profile tests**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.*'
```

Expected: all existing genetics tests plus the complete registry, variant, specialized, and empty-profile tests pass.

- [ ] **Step 2: Run server main compilation after final patch regeneration**

```bash
./gradlew :paper-server:compileJava
```

Expected: BUILD SUCCESSFUL with regenerated vanilla patches applied.

- [ ] **Step 3: Run the server genetics suite**

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Expected: all Animal, Villager, profile, adapter, migration, and structural lifecycle tests pass.

- [ ] **Step 4: Inspect final repository state**

```bash
git diff --check
git status --short
git log --oneline -8
```

Expected: no uncommitted source changes, no patch drift outside the intended thin hooks, and one atomic commit per completed task.

- [ ] **Step 5: Report exact coverage**

Report the explicit profile set, special reproduction adapters, commands run, test counts, and any remaining non-breedable ageables intentionally excluded from the completeness contract.
