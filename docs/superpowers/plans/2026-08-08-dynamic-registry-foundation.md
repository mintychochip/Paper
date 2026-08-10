# Dynamic Registry Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every public Paper `RegistryKey` an explicit backend and lifecycle capability without changing which entries are writable yet.

**Architecture:** Add server-side backend metadata to the existing `RegistryEntryMeta` records and make `PaperRegistries` the single manifest. `PaperRegistryAccess` and tests consume that manifest; existing writable registries keep their current builder/event behavior. Later plans change individual manifest entries from plain Craft/API-only metadata to typed writable backends.

**Tech Stack:** Java 25, Gradle, Paper registry lifecycle events, JUnit 5, existing `MappedRegistry`/`RegistryEntryMeta` infrastructure.

## Global Constraints

- Every `RegistryKey` must have one explicit backend: `NATIVE_STATIC`, `NATIVE_DATA`, `NATIVE_RELOADABLE`, `CATALOG`, or `MERGED`.
- No native registry is mutated after `MappedRegistry.freeze()`.
- Plugin-created keys use a non-`minecraft` namespace and duplicate keys fail before publication.
- `ENTITY_TYPE` is a merged view: vanilla NMS entries plus fork catalog custom entities; custom entities do not receive native NMS IDs in this plan.
- `Registry.MATERIAL` remains the existing catalog-backed surface and is not a `RegistryKey`.
- Do not add `dev.mintychochip` sources to the Minecraft patch tree.
- Do not modify unrelated staged or unstaged user changes.

---

## File map

- **Create:** `paper-server/src/main/java/io/papermc/paper/registry/RegistryBackendKind.java` — closed server-side backend enum.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryMeta.java` — carry backend metadata through `ApiOnly`, `Craft`, and `Buildable` records.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryBuilder.java` — require an explicit backend on every generated entry.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java` — generated manifest output; do not hand-edit this file.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntry.java` — carry generator-side backend metadata.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java` — classify every generated entry.
- **Modify:** `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java` — emit backend calls into generated server code.
- **Verify:** `paper-generator/src/main/java/io/papermc/generator/RegistryBootstrapper.java` — keep the existing `PaperRegistriesRewriter` registration intact.
- **Modify:** `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java` — expose backend capability and improve writable-registry errors.
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryKeyTest.java` — assert every registered key has a backend.
- **Modify:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBuilderTest.java` — retain equality coverage and assert buildable metadata has a backend.
- **Create:** `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java` — test the complete backend partition and merged/API-only exceptions.

## Interfaces produced for later plans

```java
public enum RegistryBackendKind {
    NATIVE_STATIC,
    NATIVE_DATA,
    NATIVE_RELOADABLE,
    CATALOG,
    MERGED
}

public interface RegistryEntryMeta<M, A extends Keyed> {
    RegistryBackendKind backend();
    RegistryModificationApiSupport modificationApiSupport();
}

public static RegistryBackendKind backend(final RegistryKey<?> key) {
    return PaperRegistries.getEntry(key).meta().backend();
}
```

`RegistryBackendKind` is server-internal. Public plugins receive lifecycle behavior through existing registry event APIs, not this enum.

---

## Task 1: Add explicit backend metadata

**Files:**
- Create: `paper-server/src/main/java/io/papermc/paper/registry/RegistryBackendKind.java`
- Modify: `paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryMeta.java`
- Modify: `paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryBuilder.java`

- [ ] **Step 1: Add the failing metadata test**

Add a parameterized assertion to `RegistryBackendTest` that calls `PaperRegistries.getEntry(key).meta().backend()` for every `RegistryKeyImpl.REGISTRY_KEYS` entry and expects a non-null value. Also assert the `ApiOnly` metadata for `PARTICLE_TYPE`, `POTION`, and `MEMORY_MODULE_TYPE` is `CATALOG`, while `ENTITY_TYPE` is `MERGED`.

```java
@ParameterizedTest
@MethodSource("allKeys")
void everyKeyHasExplicitBackend(final RegistryKey<?> key) {
    assertNotNull(PaperRegistries.getEntry(key).meta().backend(), key::toString);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.RegistryBackendTest'
```

Expected: compilation/test failure because `RegistryEntryMeta` has no `backend()` contract and existing entries have no backend value.

- [ ] **Step 3: Implement the enum and record fields**

Add the five enum constants exactly as shown in the interface contract. Add `RegistryBackendKind backend()` to `RegistryEntryMeta`. Add a backend component to `ApiOnly`, `Craft`, and `Buildable`; preserve `modificationApiSupport()` as a separate property. Keep the record constructors package-visible where they are currently package-visible.

The resulting record shapes must be:

```java
record ApiOnly<M, A extends Keyed>(
    ResourceKey<? extends Registry<M>> mcKey,
    RegistryKey<A> apiKey,
    RegistryBackendKind backend,
    Supplier<org.bukkit.Registry<A>> registrySupplier
) implements RegistryEntryMeta<M, A> {}

record Craft<M, A extends Keyed>(
    ResourceKey<? extends Registry<M>> mcKey,
    RegistryKey<A> apiKey,
    Class<?> classToPreload,
    RegistryTypeMapper<M, A> registryTypeMapper,
    BiFunction<NamespacedKey, ApiVersion, NamespacedKey> serializationUpdater,
    RegistryBackendKind backend
) implements ServerSide<M, A> {}

record Buildable<M, A extends Keyed, B extends PaperRegistryBuilder<M, A>>(
    ResourceKey<? extends Registry<M>> mcKey,
    RegistryKey<A> apiKey,
    Class<?> classToPreload,
    RegistryTypeMapper<M, A> registryTypeMapper,
    BiFunction<NamespacedKey, ApiVersion, NamespacedKey> serializationUpdater,
    PaperRegistryBuilder.Filler<M, A, B> builderFiller,
    RegistryModificationApiSupport modificationApiSupport,
    RegistryBackendKind backend
) implements ServerSide<M, A> {}
```

- [ ] **Step 4: Require the builder stage to declare a backend**

Add `backend(RegistryBackendKind)` to `RegistryEntryBuilder.CraftStage`. Store it in the stage and make `build()`, `addable()`, `modifiable()`, `writable()`, and `create()` call a private `requireBackend()` that throws `IllegalStateException("Registry backend was not declared for " + this.apiKey)` when absent. Add an overload for API-only entries:

```java
public RegistryEntry<M, A> apiOnly(
    RegistryBackendKind backend,
    Supplier<org.bukkit.Registry<A>> apiRegistrySupplier
)
```

Keep the old `apiOnly(Supplier<...>)` method only if existing binary compatibility requires it; if retained, make it delegate to `apiOnly(CATALOG, supplier)` and mark it internal/deprecated rather than creating an implicit unknown backend.

- [ ] **Step 5: Re-run the focused test**

Run the same Gradle command. Expected: compilation succeeds and the test still fails only until every `PaperRegistries` entry is annotated in Task 2.

- [ ] **Step 6: Commit the metadata unit**

```bash
git add paper-server/src/main/java/io/papermc/paper/registry/RegistryBackendKind.java \
  paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryMeta.java \
  paper-server/src/main/java/io/papermc/paper/registry/entry/RegistryEntryBuilder.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java
git commit -m "feat: add registry backend metadata"
```

## Task 2: Declare the complete Paper registry manifest

**Files:**
- Modify: `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntry.java`
- Modify: `paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java`
- Modify: `paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java`
- **Verify:** `paper-generator/src/main/java/io/papermc/generator/registry/RegistryBootstrapper.java` — keep the existing `PaperRegistriesRewriter` registration intact.
- Generated by the task below: `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java`
- Modify: `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java`
- Modify: `paper-server/src/test/java/io/papermc/paper/registry/RegistryKeyTest.java`

- [ ] **Step 1: Update the generator metadata**

Add a generator-side backend property and fluent setter to `io.papermc.generator.registry.RegistryEntry`:

```java
private String backend;

public RegistryEntry<T> backend(final String backend) {
    Preconditions.checkArgument(
        Set.of("NATIVE_STATIC", "NATIVE_DATA", "NATIVE_RELOADABLE", "CATALOG", "MERGED").contains(backend),
        "Unknown registry backend: %s", backend
    );
    this.backend = backend;
    return this;
}

public String backend() {
    return Objects.requireNonNull(this.backend, () -> "No backend for " + this.registryKey);
}
```

Annotate every entry in `RegistryEntries.BUILT_IN`, `DATA_DRIVEN`, and `API_ONLY` with the corresponding setter. Keep the existing list partition as the source for built-in/data-driven/API-only classification; the new setter supplies backend semantics.

Update `PaperRegistriesRewriter.appendEntry` to emit:

```java
start(...).craft(...).backend(RegistryBackendKind.NATIVE_STATIC).build()
```

for native entries, and emit `apiOnly(RegistryBackendKind.CATALOG, ...)` or `apiOnly(RegistryBackendKind.MERGED, ...)` for API-only entries. `RegistryBootstrapper.bootstrapServer` must continue registering `PaperRegistriesRewriter` for the `RegistryDefinitions` marker.

- [ ] **Step 2: Regenerate and verify generated output**

Run:

```bash
./gradlew :paper-generator:rewrite
```

Expected: `PaperRegistries.java` changes only inside the generated `RegistryDefinitions` region, with every entry containing one backend call. Do not hand-edit generated output. If the generator rewrites unrelated regions, stop and narrow the generator change before continuing.

- [ ] **Step 3: Annotate every generated entry**

Add `.backend(NATIVE_STATIC)` to the 15 built-in entries, `.backend(NATIVE_DATA)` to the 24 data-driven entries, `apiOnly(CATALOG, ...)` to `PARTICLE_TYPE`, `POTION`, and `MEMORY_MODULE_TYPE`, and `apiOnly(MERGED, PaperSimpleRegistry::entityType)` to `ENTITY_TYPE`.

The required partition is:

```text
NATIVE_STATIC:
GAME_EVENT, STRUCTURE_TYPE, MOB_EFFECT, BLOCK, ITEM,
VILLAGER_PROFESSION, POINT_OF_INTEREST_TYPE, VILLAGER_TYPE,
MAP_DECORATION_TYPE, MENU, ATTRIBUTE, FLUID, SOUND_EVENT,
DATA_COMPONENT_TYPE, GAME_RULE

NATIVE_DATA:
BIOME, STRUCTURE, TRIM_MATERIAL, TRIM_PATTERN, DAMAGE_TYPE,
WOLF_VARIANT, WOLF_SOUND_VARIANT, ENCHANTMENT, JUKEBOX_SONG,
BANNER_PATTERN, PAINTING_VARIANT, INSTRUMENT, CAT_VARIANT,
CAT_SOUND_VARIANT, FROG_VARIANT, CHICKEN_VARIANT,
CHICKEN_SOUND_VARIANT, COW_VARIANT, COW_SOUND_VARIANT,
PIG_VARIANT, PIG_SOUND_VARIANT, ZOMBIE_NAUTILUS_VARIANT,
SULFUR_CUBE_ARCHETYPE, DIALOG

CATALOG: PARTICLE_TYPE, POTION, MEMORY_MODULE_TYPE
MERGED: ENTITY_TYPE
```

Do not change mutation support in this task. The existing `.writable()` calls remain as-is; closed entries remain closed until Plans 2–4 implement their builders.

- [ ] **Step 4: Add manifest lookup helpers**

Add these package-level methods to `PaperRegistries`:

```java
public static RegistryBackendKind backend(final RegistryKey<?> registryKey) {
    return Objects.requireNonNull(getEntry(registryKey), () -> "No registry entry for " + registryKey).meta().backend();
}

public static RegistryBackendKind backend(final ResourceKey<?> registryKey) {
    return Objects.requireNonNull(getEntry((ResourceKey) registryKey), () -> "No registry entry for " + registryKey).meta().backend();
}
```

Use the existing identity maps; do not add a second mutable map.

- [ ] **Step 5: Add complete-partition assertions**

Assert that the union of the five backend sets equals `RegistryKeyImpl.REGISTRY_KEYS`, that the sets are pairwise disjoint, and that `ENTITY_TYPE` is the only `MERGED` key. Keep the assertion data in the test, not a second production registry list.

- [ ] **Step 6: Run the registry coverage tests**

```bash
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.RegistryBackendTest' \
  --tests 'io.papermc.paper.registry.RegistryKeyTest' \
  --tests 'io.papermc.paper.registry.RegistryBuilderTest'
```

Expected: PASS for backend coverage and existing builder equality.

- [ ] **Step 7: Commit the manifest unit**

```bash
git add paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntry.java \
  paper-generator/src/main/java/io/papermc/generator/registry/RegistryEntries.java \
  paper-generator/src/main/java/io/papermc/generator/rewriter/types/registry/PaperRegistriesRewriter.java \
  paper-server/src/main/java/io/papermc/paper/registry/PaperRegistries.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryKeyTest.java
git commit -m "feat: declare registry backend manifest"
```

## Task 3: Make registry access capability-aware

**Files:**
- Modify: `paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java`
- Modify: `paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java`

- [ ] **Step 1: Add access assertions**

Cover these observable cases:

```java
assertEquals(RegistryBackendKind.NATIVE_DATA, PaperRegistryAccess.instance().backend(RegistryKey.BIOME));
assertEquals(RegistryBackendKind.MERGED, PaperRegistryAccess.instance().backend(RegistryKey.ENTITY_TYPE));
assertThrows(IllegalArgumentException.class,
    () -> PaperRegistryAccess.instance().getWritableRegistry(RegistryKey.BIOME));
```

Also assert the current `RegistryKey.GAME_EVENT` and `RegistryKey.TRIM_PATTERN` writable paths still return `WritableCraftRegistry`.

- [ ] **Step 2: Add the backend accessor**

Implement:

```java
public RegistryBackendKind backend(final RegistryKey<?> key) {
    return PaperRegistries.backend(key);
}
```

Keep `getRegistry(RegistryKey)` behavior unchanged for delayed registries. Update `getWritableRegistry` to include the backend and mutation support in its failure message, for example:

```java
throw new IllegalArgumentException(
    key + " is " + PaperRegistries.backend(key) + " and does not expose a writable native registry"
);
```

Do not return a fake writable wrapper for `CATALOG` or `MERGED` in this foundation plan; Plan 4 supplies those adapters.

- [ ] **Step 3: Run focused server registry tests**

```bash
./gradlew :paper-server:test \
  --tests 'io.papermc.paper.registry.RegistryBackendTest' \
  --tests 'io.papermc.paper.registry.RegistryBuilderTest'
```

Expected: PASS, including existing writable registry behavior.

- [ ] **Step 4: Commit the access unit**

```bash
git add paper-server/src/main/java/io/papermc/paper/registry/PaperRegistryAccess.java \
  paper-server/src/test/java/io/papermc/paper/registry/RegistryBackendTest.java
git commit -m "feat: expose registry backend capabilities"
```

## Task 4: Foundation verification and handoff

- [ ] **Step 1: Run the full registry package smoke set**

```bash
./gradlew :paper-server:test --tests 'io.papermc.paper.registry.*'
```

Expected: all existing registry tests pass; no `.patch` rebuild is required because these changes are under `paper-server/src/main/java` and tests.

- [ ] **Step 2: Verify the staged tree before handoff**

Run:

```bash
git status --short
git log -4 --oneline
```

Expected: only intentional registry foundation commits are new; unrelated user changes remain unstaged or in their original commits.

- [ ] **Step 3: Handoff contract**

The next plan may assume `PaperRegistries.backend(RegistryKey<?>)`, `PaperRegistryAccess.backend(RegistryKey<?>)`, and `RegistryEntryMeta.backend()` exist. It must not add a second backend map or infer lifecycle from `.delayed()`.
