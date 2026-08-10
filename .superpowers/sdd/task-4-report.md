# Task 4 report: server founder capture and ageable genetics

## Implementation summary

Implemented server-side founder capture under `dev.mintychochip.genetics` without changing vanilla hooks:

- Added the `FounderCapture` adapter seam and `FounderCaptures` dispatcher in `paper-server`.
- Added direct state capture for axolotl, sheep, horse, panda, llama, villager, and all registered variant-profile species (cat, chicken, cow, mooshroom, fox, frog, pig, rabbit, and wolf).
- Captured semantic labels are written as identical diploid copies. Unsupported or unreadable state falls back to the profile founder generator rather than producing an incompatible genome.
- Expanded `AnimalGenetics` profile, persistence, cache, phenotype, and creation overloads from `Animal` to `AgeableMob` while preserving existing Animal overloads.
- Cache entries validate both profile ID and phenotype compatibility. Profile mismatches and incompatible loci are discarded and regenerated/captured before application.
- Added `PhenotypeApplier.apply(AgeableMob, Genome)` while retaining all existing Animal entry points.
- Added focused tests for adapter capture and ageable persistence/profile/phenotype behavior.

## TDD evidence

### RED

Initial focused compile/test command:

```text
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Observed before the server implementation:

```text
compileTestJava FAILED
error: cannot find symbol: FounderCaptures
error: incompatible types: AgeableMob cannot be converted to Animal
error: incompatible methods for AnimalGenetics.load/save/profile/phenotypeOf
```

The new tests therefore failed at compilation because founder capture and the ageable-aware server overloads did not yet exist.

### GREEN

Server test compilation:

```text
./gradlew :paper-server:compileTestJava
```

Observed output:

```text
BUILD SUCCESSFUL in 7s
23 actionable tasks: 6 executed, 17 up-to-date
```

Focused suite:

```text
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

Observed output:

```text
BUILD SUCCESSFUL in 8s
28 actionable tasks: 5 executed, 23 up-to-date
```

No formatter, linter, or project-wide suite was run, per task scope.

## Changed files

- `paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java`
- `paper-server/src/main/java/dev/mintychochip/genetics/FounderCapture.java`
- `paper-server/src/main/java/dev/mintychochip/genetics/FounderCaptures.java`
- `paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java`
- `paper-server/src/test/java/dev/mintychochip/genetics/AnimalGeneticsTest.java`
- `paper-server/src/test/java/dev/mintychochip/genetics/FounderCaptureTest.java`

## Self-review

- Confirmed NMS/CraftBukkit access is confined to `paper-server`; API profile sources were not changed.
- Confirmed existing Animal overloads remain available and generic persistence keys remain `MintyGenome` and `MintyGenomeProfile`.
- Confirmed persistence only caches on load and writes both genome/profile keys on save.
- Confirmed founder state is duplicated into both diploid copies by `fromLabels`.
- Confirmed cache reuse requires matching profile ID and successful phenotype decoding; invalid or mismatched entries are regenerated before application.
- Confirmed focused suite passes after implementation.
- `git diff --cached --check` passed before commit.

## Commit

`54f996a4c Add ageable founder capture adapters`

## Concerns

- Variant and villager registry values are read through a small reflection helper because their NMS holder/value types differ across entity families; failures intentionally use profile founder fallback.
- Vanilla lifecycle hook edits are intentionally excluded from this task.
