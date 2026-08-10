# Unified animal genetics: spawn-owned profiles and sheep traits

**Date:** 2026-08-09  
**Status:** approved direction; implementation pending  
**Package:** `dev.mintychochip.genetics`

## Goal

Make the genetics model applicable to every NMS `Animal`, with genome ownership established by the NMS entity lifecycle rather than by a breed-event listener. Add sheep as the first new species-specific phenotype using multiple Mendelian loci so recessive carriers can produce visible outcomes neither parent displays.

The shared recombination engine remains the source of inheritance behavior. Species profiles supply only the locus catalog, founder alleles, phenotype decoding, and server-side appearance application.

## User decisions

- Scope starts with all animals receiving a shared genome and sheep receiving a visible genetic color phenotype.
- The architecture is profile-driven over one shared engine.
- Sheep color uses multiple loci rather than one 16-way color allele.
- Genome initialization happens in the NMS entity insertion lifecycle, not in a Bukkit event.
- Breed-event genetics metadata may remain for plugin inspection, but it is not required to create or attach genomes.

## Current limitations being corrected

`AnimalGenetics` currently uses one `DefaultGeneticsCatalog` for every animal and generates founders lazily from `canMate`. `PhenotypeApplier` only applies registry variants for cats, wolves, and cows. The current cache has no NMS removal hook, so a genome can remain in the static map after an animal dies. Sheep's own `finalizeSpawn` and NBT load paths also write vanilla colors after generic hooks, so applying a phenotype only during a breed event cannot make the genome authoritative for all animals.

## Architecture

### Shared engine

Keep these existing NMS-free types as the common engine:

- `Genome`, `GeneCopy`, `Allele`, and `LocusDefinition`;
- `GenomeGenerator`, `MeiosisEngine`, and `BreedingEngine`;
- `MutationSettings` and `RecombinationSettings`;
- `GenotypeSnapshot`, `PhenotypeSnapshot`, and related DTOs;
- `GenomeCodec` for the genome JSON payload.

Add a founder-allele strategy to `GenomeGenerator` so a profile can choose semantic alleles without putting species switches into the generic generator. The existing constructor and generic founder behavior remain available for compatibility.

### Species profile registry

Add an NMS-free profile abstraction under `alkahest-api/src/main/java/dev/mintychochip/genetics/` (a `profile` package is preferred). A profile is keyed by Bukkit `EntityType` and provides:

- a stable profile id;
- the `LocusCatalog` used for inheritance;
- founder genome generation using a profile allele factory;
- profile-specific phenotype decoding;
- mutation and recombination settings.

The registry returns the specific profile for known species and a generic profile for every other `Animal` type. The generic profile wraps the existing catalog and decoder, so all animals receive a genome even when no custom visible trait exists yet. Cats, wolves, and cows keep their current `coat`-based registry mappings through the generic profile until they receive dedicated profiles.

The profile layer is pure API code. It does not import `net.minecraft.*`; applying a decoded phenotype to a live NMS animal remains in `paper-server`.

### Sheep profile

The first species profile is sheep. It uses three independent autosomal loci:

| Locus | Alleles | Rule |
|---|---|---|
| `sheep.base` | `BLACK`, `BROWN`, `RED`, `YELLOW` | Explicit dominance order: black > brown > red > yellow |
| `sheep.dilution` | `FULL`, `DILUTE` | `DILUTE` is recessive; only `DILUTE/DILUTE` changes color |
| `sheep.albinism` | `PIGMENTED`, `ALBINO` | `ALBINO` is recessive; only `ALBINO/ALBINO` produces white |

Founder allele frequencies are isolated in the sheep profile: each base allele has probability `0.25`, `FULL` has probability `0.75` and `DILUTE` has probability `0.25`, and `PIGMENTED` has probability `0.90` and `ALBINO` has probability `0.10` for each founder copy. These constants are profile tuning, not breeding-engine behavior.

The sheep phenotype decoder emits the resolved genetic traits plus a `sheep.color` trait using this deterministic table:

| Genotype result | Visible `DyeColor` |
|---|---|
| `ALBINO/ALBINO` at `sheep.albinism` | `WHITE` |
| black base, `FULL/FULL` or mixed dilution | `BLACK` |
| black base, `DILUTE/DILUTE` | `GRAY` |
| brown base, non-dilute | `BROWN` |
| brown base, dilute | `LIGHT_GRAY` |
| red base, non-dilute | `RED` |
| red base, dilute | `PINK` |
| yellow base, non-dilute | `YELLOW` |
| yellow base, dilute | `ORANGE` |

A base allele resolves by the stated dominance order. A later profile revision can add further modifier loci and colors without changing `Genome` or `BreedingEngine`.

Sheep uses `MutationSettings.NONE` for this first profile. The current DNA mutation implementation intentionally drops semantic labels when a sequence mutates; enabling it before profile-specific mutation-to-allele rules exist would turn a colored allele into an unresolvable functional/null label. Generic profiles retain their current mutation settings.

The server sheep adapter reads `sheep.color` and calls the Bukkit/NMS sheep color setter. No resource-pack or texture change is required for this first phenotype.

## NMS lifecycle and ownership

### Common insertion point

Patch `paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java` at `EntityCallbacks.onTrackingStart`. This is the common post-insertion callback reached by the server entity lookup after `ServerLevel.addEntity`, and it runs after the entity's NBT has been read and after subclass spawn defaults such as sheep's vanilla color have run. The hook is:

```java
if (entity instanceof net.minecraft.world.entity.animal.Animal animal) {
    dev.mintychochip.genetics.AnimalGenetics.onAddedToWorld(animal);
}
```

The method must be idempotent. It must:

1. resolve the profile from the animal's Bukkit `EntityType`;
2. use a loaded compatible genome if one exists;
3. otherwise generate one founder genome using the entity RNG;
4. cache the genome and profile id;
5. apply the profile phenotype after vanilla defaults.

This covers normal entity creation, direct `addFreshEntity` paths, breed-child insertion, spawn eggs, commands, and entity re-entry through the server entity lookup. `finalizeSpawn` is not the sole hook because loaded entities do not use it and sheep's override would otherwise precede or overwrite the generic assignment.

### Load ordering

Change `AnimalGenetics.load` to decode and cache only. It must not apply a phenotype while the subclass's `readAdditionalSaveData` method is still running. The common insertion hook applies the phenotype after all vanilla NBT fields have been restored.

Persist a separate `MintyGenomeProfile` string beside the existing `MintyGenome` JSON. Existing JSON shape remains unchanged (`sex` plus `genes`). A missing profile id is treated as a legacy generic genome. If a legacy generic genome is loaded onto a sheep and lacks the sheep profile loci, the first insertion hook regenerates a sheep founder genome instead of applying an incompatible old phenotype. New records always persist their profile id.

### Breeding

`Animal.canMate` may retain a defensive `ensureInitialized` fallback, but normal animals already have genomes from insertion. `AnimalGenetics.prepareBreed` resolves the parent profile, crosses the already-attached genomes with that profile's catalog/settings, and caches the child genome before the child is added to the world. The insertion hook then applies the child phenotype. A cancelled breed calls `discardBreed` and leaves no orphan cache entry.

`EntityBreedEvent` can continue receiving `BreedGenetics` snapshots. This is a read-only/plugin-facing metadata path; no event listener or event callback is needed for genome creation, persistence, or appearance application.

### Cache cleanup

Add a thin `Animal.onRemoval` hook. Remove the animal UUID from `AnimalGenetics` when the removal reason permanently destroys the entity (`KILLED` or `DISCARDED`). Retain the cache through chunk unload and dimension transfer because those paths preserve the entity and/or its saved NBT. The removal method remains a no-op for unknown UUIDs.

## API and compatibility

- Existing pure-engine entry points remain usable.
- Existing `BreedGenetics` snapshots gain sheep traits through their `PhenotypeSnapshot`; no NMS types are added to the API.
- Existing cat/wolf/cow phenotype behavior remains available through the generic profile.
- Genome JSON remains backward-readable; profile metadata is additive NBT.
- No `/genetics` command is part of this change.

## Testing strategy

### API tests

Add deterministic tests that:

1. resolve a sheep profile and expose all three loci;
2. generate founder genomes from the sheep allele factory;
3. cross two explicitly constructed black sheep carrying `DILUTE` and assert a deterministic `GRAY` child;
4. cross two pigmented sheep carrying `ALBINO` and assert a deterministic `WHITE` child;
5. assert the child genotype still contains the inherited alleles at all three loci;
6. verify generic profiles remain available for animal types without a species adapter.

Use `MutationSettings.NONE` and deterministic random sources for phenotype tests. Existing recombination, mutation, codec, and phenotype tests remain unchanged unless their public contract must be updated.

### Server tests

Add structural/integration coverage that:

1. confirms `ServerLevel.EntityCallbacks.onTrackingStart` calls the genetics insertion façade;
2. confirms `Animal.onRemoval` calls cache cleanup;
3. confirms load-before-apply ordering is represented in the source hook contract;
4. confirms the server façade resolves sheep's profile and applies its color adapter;
5. confirms a child genome is attached before world insertion and is not recreated by the insertion hook.

Run the focused API genetics tests and server genetics tests where the existing Paper test compilation permits. Always run server main compilation after patch regeneration.

## Out of scope

- Horse speed/jump/health inheritance, llama strength, panda genes, goat horns, and other non-color gameplay traits.
- Rabbit, horse, llama, parrot, axolotl, chicken, frog, tropical-fish, or other species-specific visible adapters beyond the generic genome in this first pass.
- DNA mutation rules that create or transform semantic visual alleles.
- A genetics inspection command or GUI.
- Broad changes to unrelated Paper, registry, memory, or runtime files.

## Acceptance criteria

1. Every NMS `Animal` entering a `ServerLevel` has an idempotently attached profile-backed genome before normal gameplay ticks.
2. Loading a persisted genome wins over vanilla spawn defaults; sheep color is reapplied after sheep NBT loading.
3. Permanent animal removal evicts its genome cache entry without evicting chunk-unloaded or dimension-transferred entities.
4. Breeding consumes attached parent genomes and attaches the recombinant child genome before child insertion; event cancellation cannot orphan it.
5. Sheep phenotype depends on multiple inherited loci and can produce recessive outcomes not visible in either parent.
6. Generic animals remain compatible with the shared model even without a custom appearance adapter.
7. API remains NMS-free and existing genetics tests continue to pass.
