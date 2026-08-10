# Catalog/API Rollback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the experimental catalog-backed Bukkit API surfaces and their server/CraftBukkit wiring for now, restoring the native enum and registry behavior where it was replaced, while retaining the intentional custom-block Material/carrier system and unrelated Alkahest features.

**Architecture:** Roll the work back by subsystem rather than reverting commits wholesale. The catalog commits interleave custom blocks, custom entities, particle/potion/memory experiments, and later genetics changes; each affected current file must therefore be restored to the appropriate historical shape and then re-applied selectively. Native Bukkit enum/registry contracts return to their pre-experiment forms, custom blocks continue to use vanilla carriers plus the deliberate Material/catalog surface, and the native registry backend infrastructure remains intact.

**Tech Stack:** Java 25, Gradle, Paper/CraftBukkit API and server modules, generated Bukkit sources, JUnit 5, Git history/worktrees.

## Global Constraints

- Implement in a fresh isolated worktree from `HEAD` (`bed6398ec`) using the `using-git-worktrees` workflow. The current parent checkout contains user-owned staged and unstaged test edits; do not reset, stage, or overwrite them.
- Do not use a broad `git revert`. Use the history anchors below as source material and make a coordinated source-level rollback.
- Preserve custom blocks: `dev.mintychochip.customblock`, its vanilla carrier placement/break behavior, stamped item identity, `BlockFeel`, `CustomBlockCatalog`, `CustomBlocks`, the `Material`/`VanillaMaterial` split, `Registry.MATERIAL`, and the Minecraft `/give` custom-block patches.
- Remove the experimental custom-entity identity system, including its fake first-class `EntityType` values, carrier-spawn API, registry merge, bootstrap, and server behavior package. This is separate from the retained custom-block carrier system.
- Remove the experimental `dev.mintychochip.particle`, `dev.mintychochip.potion`, `dev.mintychochip.memory`, and generic `dev.mintychochip.registry` API packages, plus all server wiring that exists only to expose those packages.
- Preserve `dev.mintychochip.behavior` contracts that are still consumed by custom-block behavior and plant behavior. Delete only behavior implementations/tests that become unreachable with custom entities and custom particles.
- Preserve genetics additions, especially the genetics fields and methods in `org.bukkit.event.entity.EntityBreedEvent`; do not restore that file wholesale from an older commit.
- Preserve ecology, seasons, provenance DTOs/server engine, custom-block behavior/plant APIs, native registry backend metadata, `WritableCraftRegistry`, `PaperRegistryListenerManager`, `PaperRegistries`, and unrelated upstream/Paper changes.
- Do not edit `paper-server/src/minecraft/java` or regenerate patches for this rollback. Existing custom-block patches under `paper-server/patches/sources/net/minecraft/commands/arguments/item/` remain unchanged. If implementation unexpectedly requires a vanilla edit, stop and handle that as a separately reviewed patch change.

## History anchors and intended baselines

Use these commits with `git show`, `git log --follow`, and `git diff` during implementation; paths changed from `paper-api` to `alkahest-api` at `398535552`, so resolve the path at the selected commit before copying a historical blob.

- `14966f566^` (`81675e4b9`): pre-custom-entity Bukkit `EntityType` enum and pre-custom-entity `Particle` enum shape. It also predates the broad custom-entity registry wiring.
- `280a5a6ad`: intentional custom-block Material support (`BlockFeel`, custom Material parity, and carrier placement) to retain.
- `d6650e1cd`: pre-generic-catalog server/API registry layer. It retains custom block/entity merged views but uses map suppliers directly; its native particle, potion, and memory registry paths are useful references.
- `a89ef5f4a^`: original enum `PotionType` and final-class generated `MemoryKey` immediately before the broad potion/memory catalog migration.
- `344a7193c^`: original enum `Particle` immediately before the particle catalog migration.
- `e23836df7`: introduces the custom particle behavior/catalog package and the generic `CustomCatalog` abstraction.
- `866aad848`: introduces the custom memory/potion catalog packages, generic catalog registry, static Bukkit catalog views, and associated tests.
- `ec89bec40`: changes server catalog adapters to consume the generic `CustomCatalog` interface.

---

## 1. Establish an isolated rollback baseline

**Files/areas:** worktree metadata; all source and test paths listed in later tasks.

- [ ] Create or enter an isolated worktree from `HEAD` with a branch dedicated to this rollback; verify the parent checkout's dirty files remain outside the worktree.
- [ ] Record the baseline commit (`bed6398ec`) and inspect `git status --short --branch` in the isolated worktree; the implementation worktree must begin clean.
- [ ] Use `git log --follow` for every historical file that will be restored, especially `org/bukkit/EntityType.java`, `org/bukkit/Particle.java`, `org/bukkit/potion/PotionType.java`, `org/bukkit/entity/memory/MemoryKey.java`, and `org/bukkit/Registry.java`.
- [ ] Confirm the pre-experiment blobs and their module paths with `git ls-tree` before copying: `EntityType`/`Particle` from the pre-14966/pre-344 commits, `PotionType`/`MemoryKey` from `a89ef5f4a^`, and the pre-generic server registry adapter from `d6650e1cd`.
- [ ] Build a current-reference inventory with repository search before editing. The inventory must include API, CraftBukkit, generator, server, and test consumers; use it as the deletion checklist rather than relying only on package directories.

**Expected result:** a clean isolated branch with a documented mapping from each current experimental symbol to either its historical native replacement, its intentional custom-block replacement, or deletion.

## 2. Restore native Particle, PotionType, and MemoryKey surfaces

**API sources:**

- `alkahest-api/src/main/java/org/bukkit/Particle.java`
- `alkahest-api/src/main/java/org/bukkit/VanillaParticle.java`
- `alkahest-api/src/main/java/org/bukkit/potion/PotionType.java`
- `alkahest-api/src/main/java/org/bukkit/potion/VanillaPotionType.java`
- `alkahest-api/src/main/java/org/bukkit/entity/memory/MemoryKey.java`
- `alkahest-api/src/main/java/org/bukkit/Registry.java`
- `com/destroystokyo/paper/ParticleBuilder.java`
- `org/bukkit/ParticleRegistry.java` and any other catalog-only public registry wrappers discovered by the reference inventory.

**API packages/tests to remove:**

- `alkahest-api/src/main/java/dev/mintychochip/particle/`
- `alkahest-api/src/main/java/dev/mintychochip/potion/`
- `alkahest-api/src/main/java/dev/mintychochip/memory/`
- `alkahest-api/src/test/java/dev/mintychochip/particle/`
- `alkahest-api/src/test/java/dev/mintychochip/potion/`
- `alkahest-api/src/test/java/dev/mintychochip/memory/`
- Any deleted/renamed native registry tests must be restored only when the historical source proves they were part of the native contract; do not replace deletion with tests for an API that no longer exists.

- [ ] Restore `Particle` as the generated Bukkit enum from the pre-344 history, retaining later upstream particle constants, data classes, javadocs, and builder behavior that are present in the current enum lineage. Remove `VanillaParticle` and all custom particle type/behavior references from the API.
- [ ] Restore `PotionType` as the historical enum from `a89ef5f4a^`, retaining only later unrelated upstream changes proven by `git log --follow`. Remove `VanillaPotionType`, `CustomPotionType`, and `PotionTypeCatalog`.
- [ ] Restore `MemoryKey` as the historical generated final class from `a89ef5f4a^`, retaining later unrelated upstream changes proven by history. Remove `CustomMemoryKey`, `MemoryKeyCatalog`, and `MemoryKeyNativeRegistry`.
- [ ] Restore `Registry.PARTICLE_TYPE`, `Registry.POTION`, and `Registry.MEMORY_MODULE_TYPE` to the native registry/static-view implementations used before the catalog migration. Remove imports and fields that reference `CatalogRegistry`, `ParticleCatalog`, `PotionTypeCatalog`, and `MemoryKeyCatalog`.
- [ ] Restore `ParticleBuilder` to accept the native `Particle` enum and use its existing native packet/conversion path. Remove the `CustomParticle` transport contract without weakening native particle validation.
- [ ] Remove API particle behavior/catalog tests and update any remaining native particle tests to assert enum/native registry behavior rather than catalog values.
- [ ] Search all API and server sources for `CustomParticle`, `VanillaParticle`, `CustomPotionType`, `VanillaPotionType`, `CustomMemoryKey`, `MemoryKeyCatalog`, and `CatalogRegistry`; resolve every remaining reference before compiling.

**Expected result:** external code sees the original enum/class contracts for particles, potions, and memory keys; no custom catalog package or static registry merge remains for those domains.

## 3. Remove the generic public catalog layer while retaining custom-block Material support

**API sources:**

- `alkahest-api/src/main/java/dev/mintychochip/registry/CustomCatalog.java`
- `alkahest-api/src/main/java/dev/mintychochip/registry/CatalogRegistry.java`
- `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlockCatalog.java`
- `alkahest-api/src/main/java/dev/mintychochip/customblock/CustomBlocks.java`
- `alkahest-api/src/main/java/org/bukkit/MaterialRegistry.java`
- `alkahest-api/src/main/java/org/bukkit/Registry.java`

**Server registry sources:**

- `paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java`
- `paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java`
- `paper-server/src/main/java/io/papermc/paper/registry/EntityTypeRegistry.java` (removed in Task 4 with the custom-entity adapter)

**Tests:**

- `alkahest-api/src/test/java/dev/mintychochip/registry/CatalogStaticRegistryTest.java`
- `alkahest-api/src/test/java/org/bukkit/MaterialRegistryTest.java`
- `paper-server/src/test/java/io/papermc/paper/registry/CatalogRegistryTest.java`
- `paper-server/src/test/java/io/papermc/paper/registry/CatalogRegistryTestSuite.java`
- `paper-server/src/test/java/io/papermc/paper/registry/CatalogNativeBoundaryTest.java`
- `paper-server/src/test/java/io/papermc/paper/registry/MergedRegistryTest.java`

- [ ] Remove `dev.mintychochip.registry.CustomCatalog` and `CatalogRegistry` after all consumers are migrated; do not remove the unrelated native `io.papermc.paper.registry` infrastructure.
- [ ] Remove `CustomBlockCatalog`'s `CustomCatalog` implementation and import. Keep its direct catalog operations (`all`, `asMap`, lookup, deterministic ordering, and registration semantics) unchanged; no replacement public generic catalog interface is introduced.
- [ ] Change `MaterialRegistry` to depend on `Supplier<? extends CustomBlockCatalog>` rather than a generic catalog. Its `get`, iteration, size, `isNative`, and `isCatalog` behavior must continue to use the custom-block catalog directly.
- [ ] Keep `Registry.MATERIAL` wired to the intentional custom-block Material registry. Do not restore the historical vanilla-only Material declaration from `14966f566^`; custom block Material support was introduced separately at `280a5a6ad` and remains in scope.
- [ ] Delete `paper-server/src/main/java/io/papermc/paper/registry/PaperCatalogRegistry.java` after Tasks 2 and 4 remove its particle, potion, and entity consumers. `PaperSimpleRegistry` must use native registry views directly and must not acquire a replacement generic merge abstraction.
- [ ] Delete `CatalogRegistryTest`, `CatalogRegistryTestSuite`, `CatalogNativeBoundaryTest`, and `MergedRegistryTest`; retain/adapt `MaterialRegistryTest` to cover the preserved custom-block Material contract and move any useful custom-material converter assertion into an existing custom-block server test.
- [ ] Search for `dev.mintychochip.registry` and confirm the only remaining registry classes are native Paper/CraftBukkit infrastructure under `io.papermc.paper.registry` and the deliberate custom-block `MaterialRegistry` adapter.
- [ ] Verify the dependency closure explicitly with a scoped search and the API Material registry test: `CustomBlockCatalog` may be referenced by `CustomBlocks`, `MaterialRegistry`, and custom-block tests only; no deleted generic-catalog type may remain in its imports, signatures, or bytecode dependencies.

**Expected result:** no public generic catalog abstraction remains, but custom-block definitions still resolve as the deliberate first-class Material surface and live blocks/items still use vanilla carriers.

## 4. Remove the experimental custom-entity API and restore EntityType enum behavior

**API sources to delete or restore:**

- Delete `alkahest-api/src/main/java/dev/mintychochip/customentity/`.
- Restore `alkahest-api/src/main/java/org/bukkit/entity/EntityType.java` to the pre-14966 enum shape, using the historical source at `14966f566^` and reapplying only later unrelated changes.
- Delete `alkahest-api/src/main/java/org/bukkit/entity/VanillaEntityType.java` and `alkahest-api/src/main/java/org/bukkit/EntityTypeRegistry.java`.
- Remove custom-entity methods from `alkahest-api/src/main/java/org/bukkit/entity/Entity.java` and the custom-entity spawn branch from `alkahest-api/src/main/java/org/bukkit/RegionAccessor.java`.
- Restore `alkahest-api/src/main/java/io/papermc/paper/tag/EntitySetTag.java` to use native `EntityType.values()`/name lookup.
- Preserve the genetics additions in `alkahest-api/src/main/java/org/bukkit/event/entity/EntityBreedEvent.java`; reapply them manually if the historical EntityType restoration touches nearby lines.

**API tests to delete:**

- `alkahest-api/src/test/java/dev/mintychochip/customentity/`
- `alkahest-api/src/test/java/org/bukkit/EntityTypeRegistryTest.java`

**Server sources/tests to delete or restore:**

- Delete `paper-server/src/main/java/io/papermc/paper/registry/EntityTypeRegistry.java` after restoring `Registry.ENTITY_TYPE`; `PaperSimpleRegistry.entityType()` must use the native `EntityType.class` registry constructor.
- Update `paper-server/src/main/java/io/papermc/paper/registry/PaperSimpleRegistry.java` so `entityType()` is `new PaperSimpleRegistry<>(EntityType.class, entity -> entity != EntityType.UNKNOWN, BuiltInRegistries.ENTITY_TYPE)`, and so `particleType()`/`potion()` use native `Particle.class`/`PotionType.class` constructors with no catalog adapter.
- Remove custom-entity bootstrap and spawn hooks from `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java` and `paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegionAccessor.java`.
- Restore native enum handling in `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntityType.java` and `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntityTypes.java`; remove carrier mappings and custom key dispatch.
- Remove custom-entity-only branches from `paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java`, `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/FieldRename.java`, and any other references found by the inventory.
- Delete `paper-server/src/test/java/dev/mintychochip/customentity/`, `paper-server/src/test/java/dev/mintychochip/EntityTypeBootstrapTest.java`, and custom-entity portions of `org/bukkit/EntityTypesTest.java` or related tests. Keep native EntityType and genetics coverage.

- [ ] Restore `Registry.ENTITY_TYPE` exactly as `Registry<EntityType> ENTITY_TYPE = registryFor(RegistryKey.ENTITY_TYPE);`; do not leave a merged catalog adapter.
- [ ] Remove all `CustomEntities`, `CustomEntityDefinition`, `CustomEntityLifecycle`, `CustomEntityType`, `VanillaEntityType`, and custom entity registry references from API, server, generator, tags, events, and tests.
- [ ] Verify `EntityBreedEvent` still compiles with the genetics API and that animal genetics behavior is unaffected by the EntityType rollback.

**Expected result:** `EntityType` is again a native Bukkit enum; no custom entity can appear in `EntityType.values()`, `Registry.ENTITY_TYPE`, spawn APIs, tags, or CraftBukkit conversion paths.

## 5. Remove server particle transport and restore generator/CraftBukkit conversion paths

**Server sources to delete or restore:**

- Delete `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleRouter.java`.
- Delete `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransport.java`.
- Delete `paper-server/src/main/java/dev/mintychochip/particle/CustomParticleTransportRegistry.java`.
- Restore native paths in `paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java`, `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java`, `paper-server/src/main/java/org/bukkit/craftbukkit/CraftParticle.java`, and `paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegistry.java`.
- Restore native potion/memory conversion paths in `paper-server/src/main/java/org/bukkit/craftbukkit/potion/CraftPotionType.java`, `paper-server/src/main/java/org/bukkit/craftbukkit/entity/memory/CraftMemoryKey.java`, and `paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegistry.java`.
- Restore enum-based EntityType conversion in `paper-server/src/main/java/org/bukkit/craftbukkit/CraftMagicNumbers.java` and remove catalog-native guards that only classify custom entity/particle/potion/memory values.
- Restore generated registrations in `paper-generator/src/main/java/io/papermc/generator/Rewriters.java`: native `Particle`, `PotionType`, and `EntityType` types return to their enum registrations; retain `VanillaMaterial` only where the custom-block Material split requires it.

**Tests:**

- Delete `paper-server/src/test/java/dev/mintychochip/particle/CustomParticleTransportTest.java` and `CustomParticleTransportTestSuite.java`.
- Remove catalog-specific cases from `paper-server/src/test/java/org/bukkit/ParticleTest.java`, `paper-server/src/test/java/org/bukkit/EntityTypesTest.java`, and CraftBukkit conversion tests; retain native conversion and registry assertions.

- [ ] Remove all bootstrap/router calls, including `CraftServer` initialization and `CraftWorld`/`CraftPlayer` custom emission branches.
- [ ] Restore native CraftBukkit conversion methods and generated mappings from the pre-catalog history, reapplying only unrelated current changes.
- [ ] Remove all references to `CustomParticleTransport`, `CustomParticleRouter`, `VanillaParticle`, `VanillaPotionType`, `VanillaEntityType`, and catalog-only native checks.
- [ ] Confirm `paper-generator` output assumptions match the restored API types before compiling the server.

**Expected result:** native particles, potion types, memory keys, and entity types use their original CraftBukkit and generated conversion paths; no server startup or player/world code initializes an experimental catalog transport.

## 6. Reconcile documentation and source references

**Files/areas:** repository-wide Java sources/tests and current feature documentation/specs; do not rewrite historical specs that document past experiments unless they are explicitly marked as current API contracts.

- [ ] Search the full repository (excluding build outputs and runtime worlds) for removed public names: `dev.mintychochip.particle`, `dev.mintychochip.potion`, `dev.mintychochip.memory`, `dev.mintychochip.registry`, `dev.mintychochip.customentity`, `VanillaParticle`, `VanillaPotionType`, `VanillaEntityType`, `CatalogRegistry`, `PaperCatalogRegistry`, `MemoryKeyCatalog`, `PotionTypeCatalog`, `ParticleCatalog`, and `CustomEntities`.
- [ ] Update only current documentation/API inventories that claim these experimental surfaces are available. Keep historical specs and commit history intact as historical records; do not claim that native registry infrastructure was removed.
- [ ] Confirm `AGENTS.md` remains accurate: custom blocks and native registry infrastructure stay present, while the removed experimental packages are not listed as current systems.
- [ ] Check generated/source lists and module exports for deleted files; remove stale entries rather than adding compatibility aliases or deprecated shims.

**Expected result:** current code and documentation expose only retained custom-block and native registry contracts; removed APIs have no discoverable source, test, generator, or documentation references.

## 7. Verify behavior and completion

- [ ] Run the API compile/test path for retained contracts:
  ```bash
  ./gradlew :alkahest-api:compileJava :alkahest-api:compileTestJava
  ./gradlew :alkahest-api:test --tests 'dev.mintychochip.season.*' --tests 'dev.mintychochip.ecology.*' --tests 'dev.mintychochip.genetics.*' --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.behavior.*'
  ```
  **Expected:** compilation succeeds; retained season, ecology, genetics, custom-block, and behavior tests pass. No test source references a deleted package.
- [ ] Run generator and server compilation:
  ```bash
  ./gradlew :paper-generator:compileJava :paper-server:compileJava
  ```
  **Expected:** generated enum/class registrations and CraftBukkit converters compile against the restored native API; no `CustomCatalog`/custom-entity/particle transport symbol remains.
- [ ] Run focused server suites for retained systems and native registry boundaries:
  ```bash
  ./gradlew :paper-server:test --tests 'dev.mintychochip.customblock.*' --tests 'dev.mintychochip.genetics.*' --tests 'dev.mintychochip.provenance.*' --tests 'io.papermc.paper.registry.RegistryBackendTestSuite' --tests 'io.papermc.paper.registry.RegistryEntryBuilderBackendTest' --tests 'org.bukkit.*Registry*'
  ```
  **Expected:** retained custom-block/genetics/provenance behavior and native registry backend tests pass; catalog-only test classes are absent rather than ignored.
- [ ] Exercise the server smoke path required by the repository with `./gradlew createPaperclipJar` and verify startup does not initialize removed catalog bootstraps or transports.
- [ ] Run repository searches after compilation and require zero references to removed experimental packages/symbols, allowing only historical documentation and commit metadata explicitly identified in Task 6.
- [ ] Inspect the final diff and status in the isolated worktree. Confirm no `paper-server/src/minecraft` files or custom-block patch files changed, no unrelated genetics/ecology/season/provenance files changed, and no parent-worktree user edits were staged.
- [ ] Commit the rollback as one focused change after all checks pass; stage only files belonging to this plan.

**Completion criteria:** the native Bukkit enum/class and registry contracts compile and pass their retained tests; custom blocks retain their intended Material/carrier behavior; custom entity/particle/potion/memory/generic catalog APIs and all corresponding wiring are absent; native registry backend infrastructure and unrelated Alkahest systems remain unchanged; the parent dirty worktree is untouched.
