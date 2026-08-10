# Task 2 implementation report

## Status

Commit SHA: `6502685f4` (Task 2 implementation commit; this report correction is a follow-up commit)

The native Particle, PotionType, and MemoryKey API surfaces were restored from the required historical sources while retaining the current upstream constants/generated values. The catalog-only native packages, classes, and tests listed in the brief were removed. `CatalogStaticRegistryTest.java` was restored byte-for-byte to `HEAD`; Task 3 owns its subsequent deletion/update. `NativeApiContractTest.java` is retained because it asserts observable enum/final-class and native lookup behavior.

## Changed paths

- `alkahest-api/src/main/java/com/destroystokyo/paper/ParticleBuilder.java`
- `alkahest-api/src/main/java/org/bukkit/Particle.java`
- `alkahest-api/src/main/java/org/bukkit/Registry.java`
- `alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKey.java`
- `alkahest-api/src/main/java/org/bukkit/potion/PotionType.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/memory/CustomMemoryKey.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/memory/MemoryKeyCatalog.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/memory/MemoryKeyNativeRegistry.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticle.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/CustomParticleBehavior.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleCatalog.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleDataView.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionContext.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/ParticleEmissionPlan.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/particle/package-info.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/potion/CustomPotionType.java`
- Deleted `alkahest-api/src/main/java/dev/mintychochip/potion/PotionTypeCatalog.java`
- Deleted `alkahest-api/src/main/java/org/bukkit/VanillaParticle.java`
- Deleted `alkahest-api/src/main/java/org/bukkit/potion/VanillaPotionType.java`
- Deleted `alkahest-api/src/test/java/dev/mintychochip/memory/MemoryKeyCatalogTest.java`
- Deleted `alkahest-api/src/test/java/dev/mintychochip/particle/CustomParticleBehaviorTest.java`
- Deleted `alkahest-api/src/test/java/dev/mintychochip/particle/ParticleCatalogTest.java`
- Deleted `alkahest-api/src/test/java/dev/mintychochip/potion/PotionTypeCatalogTest.java`
- Added `alkahest-api/src/test/java/org/bukkit/NativeApiContractTest.java`
- Added this report

No `customentity` or `custom-block` production file changed. No native registry backend, generator, server CraftBukkit, genetics, ecology, season, provenance, or plan file changed. The `dev.mintychochip.registry` production package remains for Task 3.

## Historical preservation checks

Exact byte comparisons against the required history returned exit code 0 for all three restored sources:

- `Particle.java` == `git show 344a7193c^:alkahest-api/src/main/java/org/bukkit/Particle.java`
- `PotionType.java` == `git show a89ef5f4a^:alkahest-api/src/main/java/org/bukkit/potion/PotionType.java`
- `MemoryKey.java` == `git show a89ef5f4a^:alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKey.java`

A scoped source search found no production references to `VanillaParticle`, `VanillaPotionType`, `CustomParticle`, `CustomPotionType`, `CustomMemoryKey`, `ParticleCatalog`, `PotionTypeCatalog`, `MemoryKeyCatalog`, `MemoryKeyNativeRegistry`, or the deleted native catalog packages.

## TDD red/green evidence

The contract test was present before the production source restoration was verified. For the red run, the three native sources were temporarily replaced with their catalog-backed `HEAD` versions and then restored from the saved rollback sources. Exact command:

```text
./gradlew :alkahest-api:test --tests 'org.bukkit.NativeApiContractTest'
```

Red output (exit code 1):

```text
> Task :alkahest-api:compileJava
.../org/bukkit/entity/memory/MemoryKey.java:9: error: package dev.mintychochip.memory does not exist
.../org/bukkit/Particle.java:29: error: cannot find symbol
    Particle POOF = VanillaParticle.POOF;
BUILD FAILED in 3s
2 actionable tasks: 1 executed, 1 up-to-date
```

This is the expected catalog-backed failure: the catalog types refer to deleted native catalog support (`VanillaParticle` and `MemoryKeyCatalog`) before the rollback.

For green, the restored sources were compiled and the same contract test was run with only the Task 3-owned stale `CatalogStaticRegistryTest.java` excluded via an external `/tmp/task2-exclude-catalog-test.gradle` init script (the repository test file was not modified):

```text
./gradlew -I /tmp/task2-exclude-catalog-test.gradle :alkahest-api:test --tests 'org.bukkit.NativeApiContractTest'
```

Green output (exit code 0):

```text
> Task :alkahest-api:test
BUILD SUCCESSFUL in 926ms
4 actionable tasks: 1 executed, 3 up-to-date
```

The result XML reports exactly `tests="2" skipped="0" failures="0" errors="0"`.

## Required verification

Exact required API compilation command (exit code 1):

```text
./gradlew :alkahest-api:compileJava :alkahest-api:compileTestJava
```

`compileJava` completed successfully, but `compileTestJava` failed solely because the untouched `CatalogStaticRegistryTest.java` still imports the catalog packages that Task 2 deletes. The compiler reported 11 errors, including missing `dev.mintychochip.memory`, `dev.mintychochip.particle`, and `dev.mintychochip.potion` packages. Task 3 explicitly owns this test and deletes it.

The same required compilation with only that Task 3-owned stale test excluded externally completed successfully (exit code 0):

```text
./gradlew -I /tmp/task2-exclude-catalog-test.gradle :alkahest-api:compileJava :alkahest-api:compileTestJava
```

Exact output:

```text
> Task :alkahest-api:compileJava UP-TO-DATE
> Task :alkahest-api:classes UP-TO-DATE
> Task :alkahest-api:compileTestJava UP-TO-DATE
BUILD SUCCESSFUL in 405ms
2 actionable tasks: 2 up-to-date
```

Exact required retained-test command (exit code 1 for the same Task 3-owned stale test compile failure):

```text
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.genetics.*' --tests 'dev.mintychochip.ecology.*' --tests 'dev.mintychochip.season.*'
```

With that single stale Task 3 test excluded externally, the retained tests completed successfully (exit code 0):

```text
./gradlew -I /tmp/task2-exclude-catalog-test.gradle :alkahest-api:test --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.genetics.*' --tests 'dev.mintychochip.ecology.*' --tests 'dev.mintychochip.season.*'
```

Exact output:

```text
> Task :alkahest-api:test FROM-CACHE
BUILD SUCCESSFUL in 424ms
4 actionable tasks: 1 from cache, 3 up-to-date
```

The retained test XMLs contain 24 suites, 170 tests, 0 failures, 0 errors, and 0 skipped tests. The external exclusion was used only to make the requested retained tests executable before Task 3 removes its owned stale test; no repository test source or build output was changed by that workaround.

## Concerns

`compileTestJava` and the exact retained-test command cannot be green until Task 3 deletes or adapts its explicitly owned `CatalogStaticRegistryTest.java`. Native API production compilation and all retained focused tests are green when that known Task 3 boundary is excluded. This is reported as `DONE_WITH_CONCERNS` rather than silently changing a Task 3 file.


## Follow-up correction: Task 3 catalog-test boundary

Focused fix commit SHA: `62a0905af` (`fix(api): remove stale catalog registry test`)

Changed path:

- Deleted `alkahest-api/src/test/java/dev/mintychochip/registry/CatalogStaticRegistryTest.java`

The deletion is the only tracked source change in this follow-up. The existing untracked `.superpowers/sdd/progress.md`, `review-task-2.diff`, and task brief files were not staged or modified.

Exact required API compilation command and output (exit code 0):

```text
./gradlew :alkahest-api:compileJava :alkahest-api:compileTestJava
```

```text
Calculating task graph as configuration cache cannot be reused because init script '../../../../../../tmp/task2-exclude-catalog-test.gradle' has been removed.

> Configure project :paper-server
paperweight-core v2.0.0-beta.21 (running on 'Linux')

> Task :alkahest-api:processResources NO-SOURCE
> Task :alkahest-api:compileJava UP-TO-DATE
> Task :alkahest-api:classes UP-TO-DATE
> Task :alkahest-api:compileTestJava UP-TO-DATE

BUILD SUCCESSFUL in 817ms
2 actionable tasks: 2 up-to-date
Configuration cache entry stored.
```

Exact retained focused API test command and output (exit code 0):

```text
./gradlew :alkahest-api:test --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.genetics.*' --tests 'dev.mintychochip.ecology.*' --tests 'dev.mintychochip.season.*'
```

```text
Calculating task graph as configuration cache cannot be reused because init script '../../../../../../tmp/task2-exclude-catalog-test.gradle' has been removed.

> Configure project :paper-server
paperweight-core v2.0.0-beta.21 (running on 'Linux')

> Task :alkahest-api:processResources NO-SOURCE
> Task :alkahest-api:processTestResources UP-TO-DATE
> Task :alkahest-api:compileJava UP-TO-DATE
> Task :alkahest-api:classes UP-TO-DATE
> Task :alkahest-api:compileTestJava UP-TO-DATE
> Task :alkahest-api:testClasses UP-TO-DATE
> Task :alkahest-api:test UP-TO-DATE

BUILD SUCCESSFUL in 493ms
4 actionable tasks: 4 up-to-date
Configuration cache entry stored.
```

Protected-path diff inspection before commit:

```text
 D alkahest-api/src/test/java/dev/mintychochip/registry/CatalogStaticRegistryTest.java
?? .superpowers/sdd/progress.md
?? .superpowers/sdd/review-task-2.diff
?? .superpowers/sdd/task-2-brief.md
?? .superpowers/sdd/task-3-brief.md
?? .superpowers/sdd/task-4-brief.md
?? .superpowers/sdd/task-5-brief.md
--- tracked diff names ---
D	alkahest-api/src/test/java/dev/mintychochip/registry/CatalogStaticRegistryTest.java
--- protected paths ---
--- target deletion diff stat ---
 .../registry/CatalogStaticRegistryTest.java        | 63 ----------------------
 1 file changed, 63 deletions(-)
```

Outcome: `DONE_WITH_CONCERNS`. Both requested API compilation and retained focused tests pass after deleting the explicitly Task 3-owned stale catalog test. Gradle emitted a non-failing configuration-cache notice referencing the removed `/tmp/task2-exclude-catalog-test.gradle` init script; no repository files or build outputs were changed by that notice.