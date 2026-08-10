# Genetics admin observability

**Date:** 2026-08-09
**Status:** design approved
**Package:** `dev.mintychochip.genetics`

## Goal

Provide operators with a read-only way to inspect a live ageable entity's stored genotype and decoded phenotype, and to inspect the registered profile catalog without changing the genetics model or entity state.

## Commands

### `/genetics inspect [selector]`

- With no selector, a player inspects the ageable entity under their crosshair at the existing Bukkit target-entity distance limit of 32 blocks.
- With a selector, the command resolves exactly one entity using Bukkit selector resolution. Zero matches and multiple matches are errors.
- The target must bridge to an NMS `AgeableMob`; players, items, projectiles, and other non-ageable entities are rejected with a clear message.
- Console and command blocks must provide a selector; they cannot use crosshair targeting.
- Inspection performs a non-creating cache lookup. A missing genome is reported as `no genome attached`; the command must not call `getOrCreate`, `ensureEntry`, or any phenotype path that creates a founder.
- A successful inspection displays entity type, UUID, profile ID, sex, every catalog locus, both allele displays where present, raw DNA sequence, inheritance mode, dominance mode, and the decoded phenotype.
- Empty profiles display their stable profile ID and an explicit `no loci` message.

### `/genetics profile <entity-type>`

- Resolves the Bukkit `EntityType` case-insensitively and displays the profile selected by `GeneticsProfiles.forEntityType`.
- Displays profile ID, mutation/recombination mode, every locus's key, chromosome, position, inheritance mode, dominance mode, and phenotype key.
- Displays canonical labels or numeric ranges only when that information is actually exposed by the profile catalog description. It must print `labels: not enumerated` when no description exists rather than inferring or inventing values from unrelated code.
- The generic profile exposes the legacy catalog metadata (`coat`, `vitality`, and `mt-vigor`) but remains a fallback profile.
- Empty profiles display `no loci`.

## Permissions and output

- Both commands use `mintychochip.genetics`.
- Operators bypass the permission check, matching the existing provenance command convention.
- Output uses Adventure components and the existing readable admin diagnostic style.
- The command is read-only: no genome creation, mutation, phenotype application, NBT write, cache insertion, or entity modification is allowed.
- Output is bounded to one selected entity and its registered loci; no world-wide scan is performed.

## Implementation boundary

- Add a server-only `GeneticsBukkitCommand` extending Bukkit's `Command`.
- Add a server-only bootstrap that registers the command once through the Bukkit command map, following `ProvenanceBootstrap`.
- Add a minimal server-side inspection formatter/description source. Do not add NMS types to `alkahest-api`.
- Use `AnimalGenetics.getGenome(UUID)` for inspection and `AnimalGenetics.profile(AgeableMob)` plus `profile.phenotype(genome)` only after a genome is confirmed present.
- Use `CraftEntity#getHandle()` to validate and bridge selected Bukkit entities to `AgeableMob`.
- Register the bootstrap alongside the existing mintychochip server bootstraps in `CraftServer`.

## Error behavior

- No permission: `No permission.`
- Missing selector from console/command block: show usage.
- Invalid selector: show the selector parser error without throwing to the command dispatcher.
- Zero or multiple selector matches: show an actionable target-count error.
- Non-ageable target: state that genetics inspection requires an ageable mob.
- Missing genome: show entity identity/profile and `no genome attached`; do not create one.
- Unknown entity type for `profile`: show valid usage and the supplied name.

## Verification

- Unit tests cover permission bypass, usage, selector cardinality, rejection of non-ageable targets, missing-genome non-creation, complete genotype/phenotype output, empty profiles, and truthful catalog metadata.
- Existing API genetics tests remain unchanged.
- Existing server genetics suite remains green.
- Compile the server and run the focused command tests before the full genetics suite.

## Out of scope

- Commands that edit, reroll, mutate, clone, or inject genomes.
- A GUI, client resource-pack display, or world-wide genetics census.
- Reclassifying `villager.type` or any other existing profile locus; observability exposes current semantics for later review.
