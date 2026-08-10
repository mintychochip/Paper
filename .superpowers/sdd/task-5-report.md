# Task 5 Report: All Breedable Genetics Lifecycle Paths

## Scope

Task 5 wires common ageable persistence/removal, supported insertion attachment, villager breeding preparation, and preserves Animal child preparation. Work was performed only in `/home/jlo/dev/paper/.worktrees/unified-animal-genetics`.

## RED

After adding `BreedableLifecycleHooksPresentTest` and updating `AnimalGeneticsHooksPresentTest` for the new common-hook contract, the mandated structural suite was run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

The suite compiled and ran, then failed as expected before the vanilla hooks were implemented:

```text
48 tests completed, 7 failed
```

The failures were the missing `AgeableMob` save/load/removal hooks, missing `VillagerMakeLove` preparation, Animal's duplicated save/load/removal calls, and missing Villager insertion branch. The existing Animal-only insertion assertion remained satisfied, confirming the RED failures were targeted at the Task 5 delta.

## Implementation

### Owned server façade

- Added `AnimalGenetics.prepareVillagerBreed(Villager, Villager, Villager): boolean`.
- Resolves the villager `BreedPlan`, obtains parent genomes, supplies the biome-derived villager type as the `BreedContext` environmental variant, caches the child genome before insertion, and returns `false` on disabled genetics, unresolved profiles/plans, empty crosses, or runtime resolution failures so vanilla child creation continues unchanged.
- Added only the required `BreedContext`, `BreedPlan`, `Villager`, and `VillagerType` imports.

### Thin patch-managed vanilla hooks

- `AgeableMob.java`: calls `AnimalGenetics.save(this, output)` immediately after `super.addAdditionalSaveData(output)`; calls `AnimalGenetics.load(this, input)` immediately after `super.readAdditionalSaveData(input)`; adds common `onRemoval` cleanup using the pre-existing permanent-removal predicate (`KILLED`/`DISCARDED`) after `super.onRemoval(reason)`.
- `Animal.java`: removed the former duplicate genetics save/load/removal hooks; retained `AnimalGenetics.prepareBreed` and the existing cancellation cleanup/event metadata path.
- `ServerLevel.java`: after accepted entity lookup and `valid = true`, attaches genetics only for `Animal` and `Villager` entities.
- `VillagerMakeLove.java`: calls `AnimalGenetics.prepareVillagerBreed(source, target, child)` after vanilla child creation/positioning and before `addFreshEntityWithPassengers(child)`.

### Structural tests

- Added `BreedableLifecycleHooksPresentTest` covering all six required source assertions, supported insertion branches, Animal child preparation retention, and absence of duplicate Animal persistence/removal hooks.
- Updated `AnimalGeneticsHooksPresentTest` so common persistence/removal assertions target `AgeableMob`; patch documentation now checks the moved hooks in `AgeableMob.java.patch` and asserts Animal no longer owns them.

## Patch regeneration

Ran the exact required commands:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

Both commands completed successfully. Regeneration produced the intended thin patch changes:

- `paper-server/patches/sources/net/minecraft/world/entity/AgeableMob.java.patch`
- `paper-server/patches/sources/net/minecraft/world/entity/animal/Animal.java.patch`
- `paper-server/patches/sources/net/minecraft/server/level/ServerLevel.java.patch`
- `paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java.patch` (new)
- `paper-server/patches/features/0003-Entity-Activation-Range-2.0.patch` (regenerated patch index metadata for changed source blob hashes)

A scoped search of these source patches shows only thin `dev.mintychochip.genetics.AnimalGenetics` calls and `mintychochip` marker comments; the façade implementation remains under `paper-server/src/main/java/dev/mintychochip/genetics/` and is not copied into the patch tree.

## GREEN

Ran the mandated compile and focused suite after patch regeneration:

```bash
./gradlew :paper-server:compileJava
```

```text
BUILD SUCCESSFUL
```

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

```text
BUILD SUCCESSFUL
28 actionable tasks: 5 executed, 23 up-to-date
```

For final verification after the last whitespace-only cleanup in the owned façade, ran both commands together:

```bash
./gradlew :paper-server:compileJava && ./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Both tasks completed with `BUILD SUCCESSFUL`. The focused suite completed successfully with no failing tests.

## Changed files

- `paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java`
- `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsHooksPresentTest.java`
- `paper-server/src/test/java/dev/mintychochip/genetics/BreedableLifecycleHooksPresentTest.java`
- `paper-server/patches/sources/net/minecraft/world/entity/AgeableMob.java.patch`
- `paper-server/patches/sources/net/minecraft/world/entity/animal/Animal.java.patch`
- `paper-server/patches/sources/net/minecraft/server/level/ServerLevel.java.patch`
- `paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java.patch`
- `paper-server/patches/features/0003-Entity-Activation-Range-2.0.patch` (regenerated source-patch index metadata)

## Self-review

- Common save/load/removal calls occur once in `AgeableMob`; Animal no longer duplicates them.
- Removal behavior preserves the prior Animal-only permanent predicate, so chunk unload/player unload/dimension change do not discard the cache prematurely.
- Server insertion attachment is restricted to Animal and Villager; unsupported AgeableMob subclasses are not attached merely by inheritance.
- Villager preparation is fail-safe and does not veto or alter vanilla child creation when profile resolution fails.
- Animal `prepareBreed` remains in `spawnChildFromBreeding`, including existing event metadata and cancellation cleanup.
- All patch-managed hooks are thin, marked `mintychochip`, and regenerated through the required patch workflow.
- No formatter, linter, or project-wide test suite was run.

## Concerns

- The focused proof is structural plus compilation; no live-world Villager breeding simulation is part of the existing `GeneticsTestSuite`.
- The generated feature patch contains only source blob/index metadata updates required after patch regeneration; its semantic source changes are limited to the four intended Minecraft classes.
