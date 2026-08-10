# Genetics Admin Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add read-only `/genetics inspect [selector]` and `/genetics profile <entity-type|generic>` commands that expose live genotype, phenotype, and truthful profile metadata to authorized operators.

**Architecture:** A server-only Bukkit `Command` resolves one ageable target, reads the existing cache without creating a founder, and delegates profile/locus metadata to a small formatter/description helper. A one-time bootstrap registers the command beside the existing mintychochip bootstraps. The API genome/profile model remains unchanged except for public sheep label constants needed to expose already-defined metadata without duplicating it in server code.

**Tech Stack:** Java 25, Bukkit `Command`, Adventure `Component`, NMS `AgeableMob`, Mockito/JUnit 5, Gradle Paper server tests.

## Global Constraints

- Keep implementation under `paper-server/src/main/java/dev/mintychochip/genetics/`; only the minimal command bootstrap call belongs in the existing `CraftServer` integration area.
- Do not add NMS types to `alkahest-api`.
- Inspection must use a non-creating cache lookup and must not mutate entities, NBT, phenotype state, or cache state.
- Operators bypass `mintychochip.genetics`; all other senders require that permission.
- Profile output must never infer labels/ranges from absent metadata; print `labels: not enumerated` when unavailable.
- Follow existing `ProvenanceBukkitCommand` and `ProvenanceBootstrap` output/registration conventions.
- No mutation, reroll, clone, GUI, or world-wide scan command.

---

### Task 1: Add metadata and pure inspection coverage

**Files:**
- Modify: `alkahest-api/src/main/java/dev/mintychochip/genetics/profile/SheepGeneticsProfile.java`
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/GeneticsCatalogDescriptions.java`
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/GeneticsInspection.java`
- Test: `paper-server/src/test/java/dev/mintychochip/genetics/GeneticsInspectionTest.java`

**Interfaces:**
- `SheepGeneticsProfile` exposes immutable `BASE_ORDER`, `DILUTION_LABELS`, and `ALBINISM_LABELS` values already used by its decoder/founder logic.
- `GeneticsCatalogDescriptions.describe(GeneticsProfile, LocusDefinition)` returns an immutable description containing `List<String> labels` and nullable `String range`; an empty labels list and null range mean the value is not enumerated.
- `GeneticsInspection.inspect(AgeableMob)` returns `Optional<GeneticsInspection.Snapshot>` without creating a genome. `Snapshot` contains the entity UUID, resolved Bukkit `EntityType`, profile, genome, and decoded phenotype.

- [ ] **Step 1: Write failing tests for non-creating inspection and truthful metadata**

Test these observable contracts:

```java
@Test
void missingGenomeDoesNotCreateOne() {
    final UUID id = UUID.randomUUID();
    final AgeableMob mob = mock(AgeableMob.class);
    when(mob.getUUID()).thenReturn(id);
    doReturn(EntityTypes.SHEEP).when(mob).getType();

    assertTrue(GeneticsInspection.inspect(mob).isEmpty());
    assertNull(AnimalGenetics.getGenome(id));
}

@Test
void existingGenomeReturnsProfileAndPhenotype() {
    final UUID id = UUID.randomUUID();
    final AgeableMob mob = mock(AgeableMob.class);
    when(mob.getUUID()).thenReturn(id);
    doReturn(EntityTypes.SHEEP).when(mob).getType();
    final Genome genome = SheepGeneticsProfile.INSTANCE.founder(Sex.FEMALE, new Random(4L));
    AnimalGenetics.setGenome(id, genome);

    final GeneticsInspection.Snapshot snapshot = GeneticsInspection.inspect(mob).orElseThrow();
    assertEquals(EntityType.SHEEP, snapshot.entityType());
    assertEquals("sheep", snapshot.profile().id());
    assertEquals(genome, snapshot.genome());
    assertNotNull(snapshot.phenotype().getOrNull(SheepGeneticsProfile.COLOR_KEY));
}

@Test
void genericCatalogHasNoInventedAlleleList() {
    final GeneticsProfile generic = GeneticsProfiles.generic();
    final LocusDefinition coat = generic.catalog().require(LocusId.of("coat"));

    assertTrue(GeneticsCatalogDescriptions.describe(generic, coat).labels().isEmpty());
    assertNull(GeneticsCatalogDescriptions.describe(generic, coat).range());
}

@Test
void knownProfileMetadataIsEnumeratedFromExistingConstants() {
    final GeneticsProfile equine = EquineGeneticsProfile.HORSE;
    final var speed = GeneticsCatalogDescriptions.describe(equine, EquineGeneticsProfile.SPEED);
    assertEquals("0.1125 - 0.3375", speed.range());
    assertEquals(EquineGeneticsProfile.COLORS,
        GeneticsCatalogDescriptions.describe(equine, EquineGeneticsProfile.COLOR).labels());
}
```

Call `AnimalGenetics.clearCache()` in `@AfterEach` so tests cannot leak UUID entries.

- [ ] **Step 2: Run the focused tests and verify the expected RED state**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.GeneticsInspectionTest'
```

Expected: compilation/test failure because `GeneticsInspection` and `GeneticsCatalogDescriptions` do not yet exist.

- [ ] **Step 3: Implement the minimal metadata and inspection helpers**

`GeneticsInspection.inspect` must:

1. read `AnimalGenetics.getGenome(ageable.getUUID())`;
2. return `Optional.empty()` when absent;
3. resolve `AnimalGenetics.profile(ageable)` only after a genome exists;
4. decode with `profile.phenotype(genome)`;
5. return the snapshot without calling `getOrCreate`, `ensureEntry`, `setGenome`, or `PhenotypeApplier`.

`GeneticsCatalogDescriptions` must use existing public profile constants and `VariantLabelSets` for known labels. It must describe:

- variant label lists through `VariantLabelSets.labelsFor(profile.id())`;
- sheep base/dilution/albinism labels;
- equine color, markings, and speed/jump/health ranges;
- llama color and strength range;
- panda labels;
- villager type labels;
- no labels/ranges for generic and empty loci unless the source model exposes them.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same Gradle command. Expected: all tests pass with zero failures/errors.

- [ ] **Step 5: Commit the metadata/inspection unit**

```bash
git add alkahest-api/src/main/java/dev/mintychochip/genetics/profile/SheepGeneticsProfile.java \
  paper-server/src/main/java/dev/mintychochip/genetics/GeneticsCatalogDescriptions.java \
  paper-server/src/main/java/dev/mintychochip/genetics/GeneticsInspection.java \
  paper-server/src/test/java/dev/mintychochip/genetics/GeneticsInspectionTest.java
git diff --cached --check
git commit -m "feat(genetics): expose read-only inspection metadata"
```

---

### Task 2: Add the Bukkit command and command behavior tests

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/GeneticsBukkitCommand.java`
- Test: `paper-server/src/test/java/dev/mintychochip/genetics/GeneticsBukkitCommandTest.java`

**Interfaces:**
- `GeneticsBukkitCommand extends org.bukkit.command.Command` with aliases `genotype` and `genes`.
- Permission: `mintychochip.genetics`; operators bypass it.
- Subcommands: `inspect`, `profile`.
- `execute(sender, label, args)` returns `true` for all handled/error paths and never lets selector parsing exceptions escape.

- [ ] **Step 1: Write failing command tests**

Cover:

- non-operator without permission receives `No permission.`;
- no arguments prints both subcommands in usage;
- console with `/genetics inspect` receives a selector-required error;
- `/genetics profile sheep` prints the profile ID, `sheep.base`, `sheep.dilution`, and `sheep.albinism`;
- `/genetics profile generic` prints `coat`, `vitality`, `mt-vigor`, and `labels: not enumerated`;
- invalid entity type prints an actionable error;
- a missing genome inspection reports `no genome attached` and leaves the cache empty;
- a non-ageable selector target is rejected;
- a valid inspection prints allele labels, DNA sequences, and decoded phenotype values.

Use mocked `CommandSender`/`Player` and `PlainTextComponentSerializer` to assert message text. Keep selector resolution isolated so tests do not require a running server.

- [ ] **Step 2: Run the focused command test and verify RED**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.GeneticsBukkitCommandTest'
```

Expected: compilation/test failure because `GeneticsBukkitCommand` does not yet exist.

- [ ] **Step 3: Implement command execution and formatting**

Implement:

- `execute` permission gate and subcommand dispatch;
- `inspect` target resolution:
  - no selector requires `Player`, then calls `getTargetEntity(32, false)`;
  - selector uses `Bukkit.selectEntities(sender, selector)` and requires exactly one result;
  - selected Bukkit entity must be a `CraftEntity` whose handle is `AgeableMob`;
  - `GeneticsInspection.inspect` is used for the non-creating lookup;
- inspect output with entity type/UUID/profile/sex, locus metadata, allele A/B labels and `sequence.asString()`, and phenotype traits;
- profile output from `GeneticsProfiles.forEntityType`, catalog metadata, mutation settings, recombination settings, optional labels/ranges, and `no loci` for empty profiles;
- `tabComplete` for `inspect`, `profile`, entity-type names, and no unsafe suggestions for selectors.

Catch selector parsing/runtime errors and report them as command messages. Do not call any mutating genetics API.

- [ ] **Step 4: Run command tests and verify GREEN**

Run the focused command test again. Expected: all command behavior tests pass.

- [ ] **Step 5: Commit the command unit**

```bash
git add paper-server/src/main/java/dev/mintychochip/genetics/GeneticsBukkitCommand.java \
  paper-server/src/test/java/dev/mintychochip/genetics/GeneticsBukkitCommandTest.java
git diff --cached --check
git commit -m "feat(genetics): add admin inspection commands"
```

---

### Task 3: Register the command at server startup

**Files:**
- Create: `paper-server/src/main/java/dev/mintychochip/genetics/GeneticsBootstrap.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java` at the existing mintychochip bootstrap block
- Test: `paper-server/src/test/java/dev/mintychochip/genetics/GeneticsAdminBootstrapTest.java`

**Interfaces:**
- `GeneticsBootstrap.ensureInstalled(Server)` is synchronized/idempotent and registers `new GeneticsBukkitCommand()` once through `Bukkit.getCommandMap().register("mintychochip", ...)`.
- Startup registration must not initialize or create any genomes.

- [ ] **Step 1: Write failing registration coverage**

Assert the CraftServer source contains the genetics bootstrap call in the same startup block as the existing customblock, customentity, and provenance bootstraps. Assert `GeneticsBootstrap.ensureInstalled` is idempotent by invoking it twice with a mocked server and verifying command registration occurs once.

- [ ] **Step 2: Run the focused registration test and verify RED**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.GeneticsAdminBootstrapTest'
```

Expected: failure because `GeneticsBootstrap` and its CraftServer call are absent.

- [ ] **Step 3: Implement bootstrap and minimal CraftServer integration**

Follow `ProvenanceBootstrap` exactly: guard with a static `installed` flag, register the command once, log success/failure, and keep exceptions from aborting server startup. Add only the genetics call and `// mintychochip` comments to the existing block.

- [ ] **Step 4: Run focused registration and command tests**

Run:

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.GeneticsAdminBootstrapTest' \
  --tests 'dev.mintychochip.genetics.GeneticsBukkitCommandTest'
```

Expected: all pass.

- [ ] **Step 5: Commit registration**

```bash
git add paper-server/src/main/java/dev/mintychochip/genetics/GeneticsBootstrap.java \
  paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java \
  paper-server/src/test/java/dev/mintychochip/genetics/GeneticsAdminBootstrapTest.java
git diff --cached --check
git commit -m "feat(genetics): register admin command at startup"
```

---

### Task 4: Full verification

**Files:**
- No source changes expected.

- [ ] **Step 1: Run command-focused tests**

```bash
./gradlew :paper-server:test --tests 'dev.mintychochip.genetics.GeneticsAdmin*' \
  --tests 'dev.mintychochip.genetics.GeneticsBukkitCommandTest'
```

Expected: all focused command/inspection tests pass.

- [ ] **Step 2: Compile the server**

```bash
./gradlew :paper-server:compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the complete genetics suite**

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.*'
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Expected: both builds succeed with zero failures/errors.

- [ ] **Step 4: Check the final worktree**

```bash
git diff --check
git status --short
git log --oneline -8
```

Expected: no whitespace errors, no untracked/generated source files, and only the atomic genetics observability commits above added after the prior branch head.
