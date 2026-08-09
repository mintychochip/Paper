# Unified animal genetics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every NMS `Animal` an idempotent spawn-owned genome and add a multi-locus sheep color phenotype without creating species-specific breeding engines.

**Architecture:** Keep `Genome`, meiosis, recombination, mutation, and DTOs as the shared pure engine. Add NMS-free genetics profiles keyed by Bukkit `EntityType`; the generic profile covers every animal and the sheep profile supplies three autosomal color loci. Initialize profiles from `ServerLevel.EntityCallbacks.onTrackingStart` after NBT/defaults, persist an additive profile id, assign bred children before insertion, and remove destroyed entities from the cache.

**Tech Stack:** Java 25, Gradle, JUnit 5, Paper/Minecraft NMS sources, Paper patch workflow, Bukkit API.

## Global Constraints

- Keep all new owned code under `dev.mintychochip.genetics`.
- Keep API genetics NMS-free; only `paper-server` code may import `net.minecraft.*`.
- Vanilla edits are limited to thin hooks in `Animal.java` and `ServerLevel.java`; regenerate their source patches.
- Do not modify unrelated dirty-tree registry, memory, particle, potion, benchmark, serve, or heap-dump files in the parent checkout.
- Preserve existing `GenomeCodec` JSON shape (`sex` plus `genes`); profile metadata is additive NBT.
- Keep `EntityBreedEvent` genetics metadata compatible, but never depend on an event to create or attach a genome.
- Sheep uses `MutationSettings.NONE` until semantic visual-allele mutation rules exist.
- Every production behavior change starts with a failing test and is committed with its asserting tests as one green atomic unit.

---

### Task 1: Add profile and founder-generation contracts

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/FounderAlleleFactory.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GenericGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfiles.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/model/GenomeGenerator.java`
- Create: `alkahest-api/src/test/java/dev/mintychochip/genetics/GeneticsProfilesTest.java`

**Interfaces:**
- Produces `FounderAlleleFactory.create(LocusDefinition, RandomGenerator): Allele`.
- Produces `GeneticsProfile.id(): String`, `catalog(): LocusCatalog`, `mutation(): MutationSettings`, `recombination(): RecombinationSettings`, `founder(Sex, RandomGenerator): Genome`, and `phenotype(Genome): PhenotypeSnapshot`.
- Produces `GeneticsProfiles.forEntityType(org.bukkit.entity.EntityType): GeneticsProfile`, returning the generic profile for every type except the explicitly registered sheep profile added in Task 2.
- Keeps `new GenomeGenerator(catalog, random)` behavior unchanged and adds `new GenomeGenerator(catalog, random, founderAlleles)`.

- [ ] **Step 1: Write the failing profile contract tests**

```java
@Test
void unknownAnimalTypesUseTheGenericProfile() {
    assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.COW));
    assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.PIG));
}

@Test
void customFounderFactoryControlsGeneratedAlleles() {
    final LocusDefinition locus = LocusDefinition.autosomal("marker", 1, 1, DominanceMode.COMPLETE);
    final LocusCatalog catalog = LocusCatalog.of(List.of(locus));
    final Allele marker = Allele.of("ATGAAACCC", "MARKER");
    final Genome genome = new GenomeGenerator(catalog, new Random(1), (ignored, random) -> marker)
        .generate(Sex.FEMALE);

    assertEquals("MARKER", genome.getOrNull(locus.id()).alleleA().label());
    assertEquals("MARKER", genome.getOrNull(locus.id()).alleleB().label());
}
```

- [ ] **Step 2: Run the focused API test and verify the expected red state**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.GeneticsProfilesTest'
```

Expected: compilation failure because the profile types and founder-factory constructor do not exist yet.

- [ ] **Step 3: Implement the founder strategy and generic profile**

Add the functional interface and profile contract. Update `GenomeGenerator` to store a non-null `FounderAlleleFactory`; delegate every locus allele to it; preserve the existing default factory logic for the two-argument constructor. Implement `GenericGeneticsProfile` around `DefaultGeneticsCatalog.get()`, `new PhenotypeDecoder(catalog)`, `MutationSettings.DEFAULT`, `RecombinationSettings.DEFAULT`, and the default `GenomeGenerator` constructor. Implement `GeneticsProfiles` with immutable generic access and an `EntityType` lookup that defaults to generic.

- [ ] **Step 4: Run the focused API test and verify green**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.GeneticsProfilesTest'
```

Expected: the profile and founder tests pass with zero failures.

- [ ] **Step 5: Commit the profile contract unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile \
  alkahest-api/src/main/java/dev/mintychochip/genetics/model/GenomeGenerator.java \
  alkahest-api/src/test/java/dev/mintychochip/genetics/GeneticsProfilesTest.java
git commit -m "feat(genetics): add species profile contracts"
```

---

### Task 2: Implement the multi-locus sheep profile

**Files:**
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/SheepGeneticsProfile.java`
- Create: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/SheepPhenotypeDecoder.java`
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/GeneticsProfiles.java`
- Create: `alkahest-api/src/test/java/dev/mintychochip/genetics/SheepGeneticsProfileTest.java`

**Interfaces:**
- `SheepGeneticsProfile` implements `GeneticsProfile` with id `"sheep"`.
- Its catalog exposes `sheep.base`, `sheep.dilution`, and `sheep.albinism` on chromosomes 1, 2, and 3.
- `SheepPhenotypeDecoder.decode(Genome)` returns `PhenotypeSnapshot` traits for the three resolved loci plus `sheep.color`.
- `GeneticsProfiles.forEntityType(EntityType.SHEEP)` returns the singleton sheep profile; all other types still return generic.

- [ ] **Step 1: Write deterministic failing sheep phenotype tests**

Construct genomes directly with `Genome.builder(Sex.FEMALE)` and `GeneCopy.diploid` alleles. Assert these observable rules:

```java
@Test
void diluteCarriersCanProduceGrayFromBlackParents() {
    final Genome mother = sheepGenome("BLACK/BLACK", "FULL/DILUTE", "PIGMENTED/PIGMENTED");
    final Genome father = sheepGenome("BLACK/BLACK", "FULL/DILUTE", "PIGMENTED/PIGMENTED");
    final Genome child = breedWithScriptedPicks(mother, father, false, true, false, false, true, false);

    assertEquals("GRAY", SheepGeneticsProfile.INSTANCE.phenotype(child).getOrNull("sheep.color"));
}

@Test
void albinoCarriersCanProduceWhiteRegardlessOfBasePigment() {
    final Genome mother = sheepGenome("BLACK/BLACK", "FULL/FULL", "PIGMENTED/ALBINO");
    final Genome father = sheepGenome("BROWN/BROWN", "FULL/FULL", "PIGMENTED/ALBINO");
    final Genome child = breedWithScriptedPicks(mother, father, false, false, true, false, false, true);

    assertEquals("WHITE", SheepGeneticsProfile.INSTANCE.phenotype(child).getOrNull("sheep.color"));
}

@Test
void baseDominanceAndDilutionResolveKnownDyeColors() {
    assertColor("BLACK", "FULL", "BLACK");
    assertColor("BLACK", "DILUTE", "GRAY");
    assertColor("BROWN", "FULL", "BROWN");
    assertColor("BROWN", "DILUTE", "LIGHT_GRAY");
    assertColor("RED", "FULL", "RED");
    assertColor("RED", "DILUTE", "PINK");
    assertColor("YELLOW", "FULL", "YELLOW");
    assertColor("YELLOW", "DILUTE", "ORANGE");
}
```

The helper must use real `BreedingEngine` code with `MutationSettings.NONE`; it must not mock the engine.

Use a scripted `RandomGenerator` in the test with `nextLong()` returning `0L`, `nextDouble()` returning `0.0`, and `nextBoolean()` returning the exact six gamete picks in father-then-mother chromosome order. For the gray case, use `{false, true, false, false, true, false}` so both parents pass `DILUTE`; for the white case, use `{false, false, true, false, false, true}` so both pass `ALBINO`. The helper must override the same primitive methods as the existing `AnimalGenetics.asGenerator` adapter (`nextLong`, `nextDouble`, `nextInt`, bounded `nextInt`, `nextBoolean`, and `nextFloat`) and must not use a mock.

- [ ] **Step 2: Run the sheep tests and verify the expected red state**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.SheepGeneticsProfileTest'
```

Expected: compilation failure because the sheep profile and decoder do not exist.

- [ ] **Step 3: Implement sheep loci, founder alleles, and decoder**

Create profile constants for the three autosomal loci. Use a founder factory that selects base labels uniformly (`BLACK`, `BROWN`, `RED`, `YELLOW`), selects `FULL` with probability `0.75` and `DILUTE` with `0.25`, and selects `PIGMENTED` with probability `0.90` and `ALBINO` with `0.10`. Generate functional DNA sequences with stable labels. Use `MutationSettings.NONE` and `RecombinationSettings.DEFAULT`.

Implement the decoder without relying on the generic calico rule. Resolve base by `BLACK > BROWN > RED > YELLOW`; treat `DILUTE/DILUTE` and `ALBINO/ALBINO` as recessive states; emit the exact color table from the design spec. Register the singleton profile for `EntityType.SHEEP`.

- [ ] **Step 4: Run sheep and existing API genetics tests**

Run:

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.*'
```

Expected: all existing genetics tests and the new sheep profile tests pass.

- [ ] **Step 5: Commit the sheep profile unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile \
  alkahest-api/src/test/java/dev/mintychochip/genetics/SheepGeneticsProfileTest.java
git commit -m "feat(genetics): add multi-locus sheep phenotype"
```

---

### Task 3: Make the server façade profile-aware and spawn-owned

**Files:**
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java`
- Modify: `paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java`

**Interfaces:**
- Add `AnimalGenetics.PROFILE_NBT_KEY = "MintyGenomeProfile"`.
- Add `AnimalGenetics.onAddedToWorld(Animal)` as the idempotent server insertion façade.
- Preserve `getGenome(UUID)`, `getGenome(Animal)`, `setGenome`, `roundTrip`, and generic `catalog()` compatibility.
- Add profile-aware `profile(Animal)`, `profileFor(org.bukkit.entity.EntityType)`, `phenotypeOf(Animal)`, and cache-entry handling internally without exposing NMS types from the API.
- Change `load(Animal, ValueInput)` to cache only; phenotype application happens from `onAddedToWorld`.
- Extend `PhenotypeApplier.apply(Animal, PhenotypeSnapshot)` to apply `sheep.color` through Bukkit `Sheep#setColor` before/alongside the existing cat/wolf/cow mappings.

- [ ] **Step 1: Write failing server façade tests**

Add the profile lookup assertion to `AnimalGeneticsTest`:

```java
@Test
void sheepProfileIsSelectedByEntityType() {
    assertEquals("sheep", AnimalGenetics.profileFor(EntityType.SHEEP).id());
}
```

Add the profile-persistence assertions to `AnimalGeneticsHooksPresentTest`, using its existing `readProjectFile` helper:

```java
@Test
void serverFacadePersistsProfileIdBesideGenomeJson() throws Exception {
    final String source = readProjectFile(
        "src/main/java/dev/mintychochip/genetics/AnimalGenetics.java",
        "paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java"
    );
    assertTrue(source.contains("output.putString(PROFILE_NBT_KEY"));
    assertTrue(source.contains("input.getString(PROFILE_NBT_KEY"));
}
```

This structural assertion follows the existing test style because `ValueInput`/`ValueOutput` are NMS-owned interfaces; server main compilation verifies the runtime persistence path.

Update the structural hook test to require `AnimalGenetics.onAddedToWorld` in the ServerLevel source and `AnimalGenetics.remove` in the Animal removal path. Keep the existing assertions for save/load, breeding, cancellation cleanup, and event metadata.

- [ ] **Step 2: Run the focused server tests and verify the expected red state**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.AnimalGeneticsTest' \
  --tests 'dev.mintychochip.genetics.AnimalGeneticsHooksPresentTest'
```

Expected: the new tests fail because profile selection, profile NBT, spawn ownership, and sheep application are not implemented. If compilation stops in unrelated existing Paper test sources first, record that limitation and continue with API tests plus server main compilation; do not weaken the new assertions.

- [ ] **Step 3: Implement profile-aware cache entries and NBT ordering**

Replace the UUID-to-`Genome` cache value with an internal immutable entry containing `profileId` and `Genome`, while keeping the existing public `getGenome` return type. Resolve an animal profile using `CraftEntityType.minecraftToBukkit(animal.getType())` and `GeneticsProfiles.forEntityType`.

Implement `onAddedToWorld` as:

```java
public static void onAddedToWorld(final Animal animal) {
    final GeneticsProfile profile = profile(animal);
    final Entry existing = CACHE.get(animal.getUUID());
    final RandomGenerator random = asGenerator(animal.getRandom());
    final Entry attached = existing != null && existing.profileId().equals(profile.id())
        ? existing
        : new Entry(
            profile.id(),
            profile.founder(random.nextBoolean() ? Sex.MALE : Sex.FEMALE, random)
        );
    CACHE.put(animal.getUUID(), attached);
    PhenotypeApplier.apply(animal, profile.phenotype(attached.genome()));
}
```

Use the entity's `RandomSource` adapter already present in `AnimalGenetics`. Update `save` to write both `MintyGenome` and `MintyGenomeProfile`; update `load` to decode/cache without applying. Treat missing or incompatible profile ids as legacy data and regenerate the species profile at insertion. Update `prepareBreed`, `snapshotsOf`, and profile-aware cross creation to use the parent/child profile catalog and settings. Keep the event payload as a derived snapshot only.

- [ ] **Step 4: Implement sheep appearance application**

In `PhenotypeApplier.apply`, branch on `EntityType.SHEEP`. Cast the Bukkit entity to `org.bukkit.entity.Sheep`, read `phenotype.getOrNull("sheep.color")`, convert with `DyeColor.valueOf`, and call `setColor`. Return `false` for a missing/unknown trait instead of throwing; all profile decoder outputs must use valid enum names.

- [ ] **Step 5: Run the server main compilation and focused tests**

Run:

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.*'
```

Expected: main compilation succeeds; focused tests pass if the existing test compilation baseline is available. If the known unrelated Paper test compilation failure remains, report it exactly and use the structural/API evidence instead of claiming server tests passed.

- [ ] **Step 6: Commit the profile-aware façade unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/genetics \
  paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsTest.java \
  paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java
git commit -m "feat(genetics): attach profile genomes on the server"
```

---

**Interfaces:**
- `ServerLevel.EntityCallbacks.onTrackingStart` calls `AnimalGenetics.onAddedToWorld` for every NMS `Animal` after the entity lookup accepts it, before the existing `EntityAddToWorldEvent` call.
- `Animal.onRemoval(Entity.RemovalReason)` calls `AnimalGenetics.remove(this)` for permanent destruction and delegates to `super`.
- No Bukkit event listener performs genome initialization.

- [ ] **Step 1: Extend structural tests before touching NMS sources**

Add source assertions for these exact contracts:

```java
assertTrue(serverLevelSrc.contains("AnimalGenetics.onAddedToWorld"));
assertTrue(animalSrc.contains("AnimalGenetics.remove"));
assertTrue(animalSrc.contains("onRemoval"));
```

- [ ] **Step 2: Run the hook test and verify the expected red state**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.AnimalGeneticsHooksPresentTest'
```

Expected: the assertions fail because the NMS hooks are not present. If the repository-wide test compilation fails first, preserve the failing test source and verify the source contract manually only for diagnosis; do not count the test as passing.

- [ ] **Step 3: Add the thin applied-source hooks**

Insert in `ServerLevel.EntityCallbacks.onTrackingStart`, after the entity is marked valid and before the existing Bukkit add-to-world event:

```java
if (entity instanceof net.minecraft.world.entity.animal.Animal animal) {
    dev.mintychochip.genetics.AnimalGenetics.onAddedToWorld(animal);
}
```

Insert in `Animal`:

```java
@Override
public void onRemoval(final Entity.RemovalReason reason) {
    super.onRemoval(reason);
    if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
        dev.mintychochip.genetics.AnimalGenetics.remove(this);
    }
}
```

The hook must not run in the constructor. The load hook must remain cache-only so sheep's subclass NBT color cannot overwrite the genome after it is applied.

- [ ] **Step 4: Regenerate vanilla patches**

From the isolated worktree root, run:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

Expected: the two patch files contain only the new thin genetics hooks plus the repository's existing patch content. Do not rebuild unrelated feature patches manually or edit generated patch hunks by hand.

- [ ] **Step 5: Compile the server and run structural tests**

Run:

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.AnimalGeneticsHooksPresentTest'
```

Expected: server main compilation succeeds and the structural hook assertions pass when the existing Paper test compilation is available.

- [ ] **Step 6: Commit the NMS hook unit**

```bash
git add paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java \
  paper-server/patches/sources/net/minecraft/server/level/ServerLevel.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/animal/Animal.java.patch \
  paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java
git commit -m "feat(genetics): initialize animals at NMS insertion"
```

---

### Task 5: Run final regression and inspect the finished contract

**Files:**
- Inspect only: all files changed by Tasks 1–4.
- Modify only if a test exposes a defect in the approved design.

- [ ] **Step 1: Run the complete API genetics suite**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.*'
```

Expected: all API genetics tests pass, including profile and sheep tests.

- [ ] **Step 2: Run server compilation and focused server tests**

```bash
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.*'
```

Expected: `compileJava` passes. The focused server test result must be reported exactly; an unrelated repository-wide test-source compilation failure is not evidence that the genetics implementation failed, but it prevents claiming the server tests passed.

- [ ] **Step 3: Verify patch cleanliness and API boundaries**

Run the repository whitespace check:

```bash
git diff main...HEAD --check
```

Use the repository search tool on `alkahest-api/src/main/java/dev/mintychochip/genetics` for `import net.minecraft`; expected result: no matches. Inspect the final diff to confirm only genetics files, the two vanilla hooks/patches, tests, and the approved spec/plan changed.

- [ ] **Step 4: Commit any narrowly scoped verification fix**

If and only if Task 5 exposes a defect, add a failing regression test first, fix the minimal source, rerun the affected command, and create a separate atomic commit whose message names that defect. Do not combine it with earlier commits.
