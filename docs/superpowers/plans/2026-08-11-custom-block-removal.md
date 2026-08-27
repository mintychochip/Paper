# Custom Block Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the custom-block feature and catalog-backed custom-material behavior completely while preserving the current vanilla `Material`/`VanillaMaterial` architecture and unrelated gameplay systems.

**Architecture:** Delete the custom-block API/server slice and its only-consumer behavior snapshot API. Restore `Material` and `Registry.MATERIAL` to vanilla-only lookup through the existing interface/enum adapter, then remove Bukkit identity methods, bootstrap hooks, custom commands, custom item patches, persistence/display/pack code, and custom-block provenance glue. Historical design documents remain historical; only current inventory documentation is updated.

**Tech Stack:** Java 25, Gradle, Paper patch workflow, Bukkit/Paper API, JUnit 5.

## Global Constraints

- Use JDK 25 and the repository Gradle wrapper.
- Custom code belongs under `dev.mintychochip`; do not add replacement shims or aliases.
- Do not restore the pre-split native `Material` enum; retain the existing `Material`/`VanillaMaterial` split.
- Do not edit `paper-server/src/minecraft/java`; deleting the three custom item patches requires `applyPatches`, not `rebuildPatches`.
- Do not mutate ignored runtime work data under `run/`.
- Preserve seasons, ecology, genetics, provenance core, and native Bukkit/Paper registry behavior.
- Historical specs and rollback plans remain unchanged.
- Skip formatters, linters, and project-wide test suites during individual implementation tasks; run focused verification once the edits are complete.

---

### Task 1: Restore vanilla-only material surfaces

**Files:**
- Modify: `alkahest-api/src/main/java/org/bukkit/Material.java`
- Modify: `alkahest-api/src/main/java/org/bukkit/VanillaMaterial.java`
- Modify: `alkahest-api/src/main/java/org/bukkit/MaterialRegistry.java`
- Modify: `alkahest-api/src/main/java/org/bukkit/Registry.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/util/CraftMagicNumbers.java`
- Modify: `alkahest-api/src/test/java/org/bukkit/MaterialRegistryTest.java`
- Modify: `paper-server/src/test/java/dev/mintychochip/MaterialBootstrapTest.java`

**Interfaces:**
- Consumes: the existing `Material` interface, `VanillaMaterial` enum, and `Registry.SimpleRegistry<VanillaMaterial>`.
- Produces: a vanilla-only `Registry.MATERIAL`; `Material#getByKey(NamespacedKey)` resolves only native material keys; no `Material#isCustom` contract.

- [ ] **Step 1: Rewrite the API registry test around native behavior**

  Remove all `CustomBlocks`, `CustomBlockDefinition`, `CustomBlockCatalog`, `BlockFeel`, `PacketHostSpec`, setup, teardown, and custom assertions from `MaterialRegistryTest`.

  Keep native coverage with these assertions:

  ```java
  @Test
  public void getResolvesVanillaNonLegacy() {
      assertSame(Material.STONE, Registry.MATERIAL.get(NamespacedKey.minecraft("stone")));
      assertTrue(Registry.MATERIAL.stream().anyMatch(m -> m == Material.STONE));
      assertTrue(Registry.MATERIAL.size() > 0);
      assertInstanceOf(MaterialRegistry.class, Registry.MATERIAL);
      final MaterialRegistry registry = (MaterialRegistry) Registry.MATERIAL;
      assertTrue(registry.isNative(Material.STONE));
  }

  @Test
  public void doesNotIncludeLegacyMaterials() {
      assertNull(Registry.MATERIAL.get(NamespacedKey.minecraft("legacy_air")));
      for (final Material material : Registry.MATERIAL) {
          assertFalse(material.isLegacy(), () -> "legacy leaked into Registry.MATERIAL: " + material);
      }
  }

  @Test
  public void valuesAndRegistryAreVanillaOnly() {
      for (final Material material : Material.values()) {
          assertTrue(material.isVanilla());
      }
      for (final Material material : Registry.MATERIAL) {
          assertTrue(material.isVanilla());
      }
  }
  ```

  Delete the direct catalog-supplier test; no catalog type remains.

- [ ] **Step 2: Rewrite the server material bootstrap test**

  Keep the non-null/native assertions in `MaterialBootstrapTest`, remove custom-block imports and lifecycle cleanup, and replace the custom registry test with native lookup:

  ```java
  @Test
  public void registryMaterialIncludesVanilla() {
      assertSame(VanillaMaterial.STONE, Registry.MATERIAL.get(NamespacedKey.minecraft("stone")));
      assertSame(VanillaMaterial.STONE,
          Material.getByKey(NamespacedKey.minecraft("stone")).orElseThrow());
      assertTrue(Registry.MATERIAL.stream().allMatch(Material::isVanilla));
  }
  ```

  Keep `assertTrue(VanillaMaterial.GLASS.isVanilla())`; remove every `isCustom()` assertion.

- [ ] **Step 3: Remove the custom-material contract from `Material`**

  Change the top-level Javadoc to describe only vanilla constants and remove carrier/custom identity links. Delete the `isCustom()` declaration. Keep `isVanilla()` as the current `Material`/`VanillaMaterial` split marker.

  Retain namespaced native lookup, but make its implementation catalog-free:

  ```java
  static Optional<Material> getByKey(@Nullable final NamespacedKey key) {
      if (key == null) {
          return Optional.empty();
      }
      try {
          final Material registryValue = Registry.MATERIAL.get(key);
          if (registryValue != null) {
              return Optional.of(registryValue);
          }
          if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
              return Optional.empty();
          }
      } catch (final Throwable ignored) {
          if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
              return Optional.empty();
          }
      }
      return Optional.ofNullable(VanillaMaterial.byName(key.getKey().toUpperCase(Locale.ROOT)));
  }
  ```

  Keep `matchMaterial`'s namespaced-key attempt through `getByKey`; remove only comments and branches that mention or call `CustomBlocks`. `Material#getMaterial` remains the native-name lookup.

- [ ] **Step 4: Remove the custom implementation override**

  Delete the `isCustom()` override at the end of `VanillaMaterial.java`; keep `isVanilla()` returning `true`.

- [ ] **Step 5: Make `MaterialRegistry` a vanilla-only adapter**

  Remove the `CustomBlockCatalog` and `Supplier` imports/field, the public catalog constructor parameter, `customValues()`, catalog merging in `get`/`iterator`/`size`, and `isCatalog`.

  Use this shape:

  ```java
  @ApiStatus.Internal
  @NullMarked
  final class MaterialRegistry extends Registry.NotARegistry<Material> {
      private final Registry<VanillaMaterial> vanilla;

      MaterialRegistry(final Registry<VanillaMaterial> vanilla) {
          this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
      }

      @Override
      public @Nullable Material get(final NamespacedKey key) {
          return this.vanilla.get(Objects.requireNonNull(key, "key"));
      }

      @Override
      public @NotNull Iterator<Material> iterator() {
          final List<Material> values = new ArrayList<>(this.vanilla.size());
          for (final VanillaMaterial value : this.vanilla) {
              values.add(value);
          }
          return values.iterator();
      }

      @Override
      public int size() {
          return this.vanilla.size();
      }

      @Override
      public Stream<NamespacedKey> keyStream() {
          return StreamSupport.stream(this.spliterator(), false).map(Keyed::getKey);
      }

      public boolean isNative(final Material value) {
          return value != null && this.vanilla.get(value.getKey()) == value;
      }
  }
  ```

  Restore package-private class/constructor visibility; this adapter is an internal implementation detail.

- [ ] **Step 6: Restore the native `Registry.MATERIAL` construction**

  Remove the `CustomBlocks` import and custom-material Javadoc. Construct the existing adapter with one argument:

  ```java
  Registry<Material> MATERIAL = new MaterialRegistry(
      new SimpleRegistry<>(VanillaMaterial.class, mat -> !mat.isLegacy())
  );
  ```

- [ ] **Step 7: Remove conversion guards**

  In `CraftMagicNumbers`, delete the four `Preconditions.checkArgument(... !material.isCustom() ...)` blocks in `getBlock(Material, byte)`, `getItem(Material, short)`, `getItem(Material)`, and `getBlock(Material)`. Leave the native legacy conversion and existing unrelated preconditions intact.

- [ ] **Step 8: Run focused native material tests**

  Run: `./gradlew :alkahest-api:test --tests 'org.bukkit.MaterialRegistryTest' --tests 'org.bukkit.MaterialTest'`

  Expected: all selected native material tests pass; no custom-block class is compiled or referenced by these tests.

---

### Task 2: Delete custom API and Bukkit identity surfaces

**Files:**
- Delete: `alkahest-api/src/main/java/dev/mintychochip/customblock/`
- Delete: `alkahest-api/src/test/java/dev/mintychochip/customblock/`
- Delete: `alkahest-api/src/main/java/dev/mintychochip/behavior/`
- Delete: `alkahest-api/src/test/java/dev/mintychochip/behavior/`
- Modify: `alkahest-api/src/main/java/org/bukkit/block/Block.java`
- Modify: `alkahest-api/src/main/java/org/bukkit/inventory/ItemStack.java`

**Interfaces:**
- Consumes: the vanilla Bukkit `Block`, `ItemStack`, and `Material` contracts.
- Produces: no `dev.mintychochip.customblock` or `dev.mintychochip.behavior` API packages; `Block` and `ItemStack` expose only their existing vanilla/Paper methods.

- [ ] **Step 1: Remove the additive `Block` custom identity block**

  Delete the `// mintychochip start - custom block identity` section containing `getCustomKey`, `getCustomBlock`, and `isCustomBlock`. Leave neighboring `getType`, block data, and normal Bukkit methods unchanged.

- [ ] **Step 2: Remove the additive `ItemStack` custom identity block**

  Delete the `// mintychochip start - custom block item identity` section containing `getCustomKey`, `getCustomBlock`, and `isCustomBlockItem`. Leave `getType`, PDC, item meta, and stack mutation behavior unchanged.

- [ ] **Step 3: Delete the unreachable API packages and tests**

  Delete every file under the four directories listed above. Do not replace any type with an alias or deprecated facade.

- [ ] **Step 4: Audit API references**

  Run: `functions.grep`-equivalent repository search for `dev.mintychochip.customblock`, `dev.mintychochip.behavior`, `CustomMaterial`, `getCustomKey`, `getCustomBlock`, `isCustomBlock`, and `isCustomBlockItem` across `alkahest-api/src`.

  Expected: no matches in API sources or tests. Historical documentation matches are handled in Task 4.

---

### Task 3: Remove server runtime, provenance glue, resources, and patches

**Files:**
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java`
- Delete: `paper-server/src/test/java/dev/mintychochip/behavior/`
- Delete: `paper-server/src/main/java/dev/mintychochip/customblock/`
- Delete: `paper-server/src/test/java/dev/mintychochip/customblock/`
- Delete: `paper-server/src/test/java/dev/mintychochip/provenance/CustomBlockProvenanceTest.java`
- Delete: `paper-server/src/main/java/dev/mintychochip/MintyInternalPlugin.java` if the final reference audit confirms no consumer
- Delete: `paper-server/src/main/resources/mintychochip-pack/`
- Delete: `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemArgument.java.patch`
- Delete: `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemInput.java.patch`
- Delete: `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemParser.java.patch`
- Modify: `paper-server/patches/sources/net/minecraft/server/commands/GiveCommand.java.patch` (remove custom item parsing while retaining provenance/Paper hunks)
- Delete: `paper-server/patches/sources/net/minecraft/world/level/ExplosionDamageCalculator.java.patch`
- Modify: `paper-server/patches/sources/net/minecraft/world/level/block/CropBlock.java.patch` (remove custom plant lifecycle hunks; retain ecology and upstream hooks)
- Modify: `paper-server/patches/sources/net/minecraft/world/level/block/SaplingBlock.java.patch` (remove custom plant lifecycle hunks; retain ecology and upstream hooks)
- Modify: `paper-server/patches/sources/net/minecraft/world/level/block/state/BlockBehaviour.java.patch` (remove custom mining hunk; retain unrelated hooks)

**Interfaces:**
- Consumes: vanilla server startup, native item argument parsing, and the independent provenance bootstrap.
- Produces: no custom-block listeners, commands, lookup, SavedData, display service, pack server, or custom-block provenance adapter; provenance core remains bootstrapped.

- [ ] **Step 1: Remove the custom bootstrap hook only**

  In `CraftServer`, delete the custom-block `ensureInstalled(this)` block and its surrounding custom-block comments. Keep the immediately following provenance bootstrap hook exactly as-is:

  ```java
  // mintychochip start - item provenance tracking
  dev.mintychochip.provenance.ProvenanceBootstrap.ensureInstalled(this);
  // mintychochip end - item provenance tracking
  ```

- [ ] **Step 2: Remove behavior-only legacy adapters**

  In `MaterialRerouting`, delete the three overloads that accept `dev.mintychochip.behavior.BlockDataView`, `BlockView`, or `ItemStackView`. Retain the native `BlockData`, `Block`, and `ItemStack` overloads and the shared transformation helpers. Delete the matching server behavior integration and suite tests.

- [ ] **Step 3: Confirm shared plugin ownership before deletion**

  Search `paper-server/src/main/java` and `paper-server/src/test/java` for `MintyInternalPlugin`. If only the custom-block package references it, delete `MintyInternalPlugin.java`; otherwise retain it and remove only custom-block callers. The current audit shows custom-block-only references.

- [ ] **Step 4: Delete all custom-block server implementations and tests**

  Delete the complete `paper-server/src/main/java/dev/mintychochip/customblock/` tree, including `display/` and `pack/`, and the complete matching test tree. This removes `/customblock`, `/cb`, `/cblock`, default `electrum_ore`, placement SavedData, packet displays, plant routing, mining hooks, and custom resource-pack service.

- [ ] **Step 5: Delete custom-block provenance glue only**

  Delete `CustomBlockProvenance.java` with the custom-block implementation and delete `CustomBlockProvenanceTest.java`. Do not edit `ItemProvenance`, `StackStamp`, `LiveIndex`, `LineageStore`, `AuditLog`, or `ProvenanceBootstrap` unless compilation identifies a direct custom-block-only reference.

- [ ] **Step 6: Remove custom resource-pack assets**

  Delete `paper-server/src/main/resources/mintychochip-pack/`, including `pack.mcmeta`, `pack.png`, and all `electrum_ore` assets. Do not delete ignored `run/resourcepacks/mintychochip/` files.

- [ ] **Step 7: Remove all custom-block patch hunks**

  Delete the three item-argument patch files listed above. In `GiveCommand.java.patch`, remove the custom-material string parser and catalog suggestions while retaining the provenance birth and unrelated Paper hunks. Delete the custom-only `ExplosionDamageCalculator.java.patch`. In `CropBlock.java.patch`, `SaplingBlock.java.patch`, and `BlockBehaviour.java.patch`, remove only custom plant-lifecycle/mining hunks while retaining ecology, CraftBukkit, Paper, and other native hooks. Do not edit any generated or patch-managed `net.minecraft` source directly.

- [ ] **Step 8: Run patch application and focused server compilation**

  Run: `./gradlew applyPatches :paper-server:compileJava :paper-server:compileTestJava`

  Expected: patch application succeeds with native `ItemArgument`, `ItemInput`, and `ItemParser`; server main/test Java compilation succeeds without custom-block types.

---

### Task 4: Update current documentation and perform dependency closure audit

**Files:**
- Modify: `AGENTS.md`
- Preserve unchanged: `docs/superpowers/specs/2026-08-07-custom-block-definition-design.md`
- Preserve unchanged: `docs/superpowers/specs/2026-08-07-material-interface-design.md`
- Preserve unchanged: `docs/superpowers/plans/2026-08-10-api-catalog-rollback.md`
- Preserve unchanged: `docs/superpowers/specs/2026-08-07-item-provenance-design.md` except no historical rewrite is needed

**Interfaces:**
- Consumes: the approved removal spec and final source tree.
- Produces: an accurate current feature inventory without rewriting historical records.

- [ ] **Step 1: Remove the active custom-block feature map from `AGENTS.md`**

  Delete the current Custom blocks rows under the active feature table, API/server inventory, additive Bukkit surface, resource-pack description, custom-block tests, and custom-block-specific quick decision text. Keep the general patch rules and provenance sections intact.

- [ ] **Step 2: Add the clean-break runtime note**

  In the current runtime/config guidance, state that old custom-block SavedData/PDC/resource-pack files are ignored orphaned work data after the feature removal and are not migrated. Do not claim that ignored `run/` files were deleted.

- [ ] **Step 3: Verify historical-document boundaries**

  Do not remove historical references from the dated custom-block design, material-interface design, or rollback plan. These documents describe prior decisions and remain useful history; only current instructions must describe the new state.

- [ ] **Step 4: Run the full scoped reference audit**

  Search source, tests, patches, and current docs for:

  ```text
  dev.mintychochip.customblock
  dev.mintychochip.behavior
  CustomMaterial
  CustomBlock
  custom block
  custom-block
  custom_block
  isCustom()
  getCustomKey
  getCustomBlock
  isCustomBlock
  isCustomBlockItem
  ```

  Expected: no matches in `alkahest-api/src`, `paper-server/src`, or active `AGENTS.md`; remaining matches are limited to historical dated documents and the approved removal spec.

---

### Task 5: Verify native behavior and final repository state

**Files:**
- Test only: existing native API/server/provenance test sources
- Review: all files changed by Tasks 1–4

**Interfaces:**
- Consumes: the dependency-closed source tree and native-only material/item parser behavior.
- Produces: evidence that removal is complete and unrelated systems still compile and pass their focused tests.

- [ ] **Step 1: Run API regression tests**

  Run:

  ```bash
  ./gradlew :alkahest-api:test --tests 'org.bukkit.MaterialRegistryTest' --tests 'org.bukkit.MaterialTest'
  ```

  Expected: selected tests pass with no custom catalog/material classes.

- [ ] **Step 2: Run server native material and custom-feature absence tests**

  Run:

  ```bash
  ./gradlew :paper-server:test --tests 'dev.mintychochip.MaterialBootstrapTest'
  ```

  Expected: native material bootstrap/registry assertions pass and no custom-block bootstrap is loaded.

- [ ] **Step 3: Run the provenance regression suite**

  Run:

  ```bash
  ./gradlew :paper-server:test --tests 'org.bukkit.support.suite.ProvenanceTestSuite'
  ```

  Expected: provenance core tests pass without `CustomBlockProvenance`.

- [ ] **Step 4: Run focused custom-package absence and compilation checks**

  Run:

  ```bash
  ./gradlew :alkahest-api:compileJava :alkahest-api:compileTestJava :paper-server:compileJava :paper-server:compileTestJava
  ./gradlew applyPatches
  ```

  Expected: all compilation tasks and patch application succeed.

- [ ] **Step 5: Inspect the final diff and status**

  Confirm all of the following before committing:

  - No custom-block source, test, resource, patch, or current-doc artifact remains.
  - No `run/` file was changed.
  - `CraftServer` still calls `ProvenanceBootstrap.ensureInstalled(this)`.
  - `Registry.MATERIAL` has no catalog supplier and iterates only non-legacy `VanillaMaterial` values.
  - Native item argument classes contain no custom visitor/stack fields after `applyPatches`.
  - Existing user stash `stash@{0}` remains untouched.

- [ ] **Step 6: Commit the removal as one atomic change**

  After all verification passes, stage only the removal spec/plan and implementation files and commit with:

  ```bash
  git add AGENTS.md alkahest-api paper-server docs/superpowers/specs/2026-08-11-custom-block-removal-design.md docs/superpowers/plans/2026-08-11-custom-block-removal.md
  git commit -m "remove custom block feature"
  ```

  Do not stage ignored runtime data, unrelated provenance changes, or the pre-existing user stash.
