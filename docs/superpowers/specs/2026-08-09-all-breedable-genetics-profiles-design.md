# All breedable mob genetics profiles

**Date:** 2026-08-09
**Status:** design approved; written-spec review pending
**Package:** `dev.mintychochip.genetics`

## Goal

Give every current breedable Minecraft mob an explicit genetics profile while retaining one shared genome, meiosis, mutation, codec, and server lifecycle model.

Profiles must reach vanilla parity for inherited or visible species traits where those traits exist. Species without an inherited vanilla trait still receive an explicit profile and valid genome lifecycle; the implementation must not invent unrelated gameplay mechanics merely to fill a catalog.

The existing sheep profile remains the reference multi-locus implementation.

## Verified reproduction inventory

The following entities have a confirmed parent-mating path in the current Minecraft sources and receive standard breedable profiles:

- `ARMADILLO`
- `AXOLOTL`
- `BEE`
- `CAMEL`
- `CHICKEN`
- `CAT`
- `COW`
- `MOOSHROOM`
- `DONKEY`
- `HORSE`
- `LLAMA`
- `OCELOT`
- `FOX`
- `FROG`
- `GOAT`
- `HOGLIN`
- `NAUTILUS`
- `PANDA`
- `PIG`
- `RABBIT`
- `SHEEP`
- `SNIFFER`
- `STRIDER`
- `TURTLE`
- `WOLF`
- `VILLAGER`

Two additional explicit profiles are required by offspring-producing paths:

- `MULE`: horse × donkey offspring; not itself fertile.
- `HAPPY_GHAST`: a dried-ghast hydration path creates a ghastling without parent entities.

The following are not included in the breedable completeness contract merely because they implement `AgeableMob#getBreedOffspring`; they lack a current player-breeding path: polar bear, dolphin, squid, glow squid, parrot, skeleton horse, zombie horse, camel husk, zombie nautilus, and similar non-breedable ageables.

## Profile architecture

`GeneticsProfile` remains the NMS-free contract:

- stable profile ID;
- locus catalog;
- founder generation;
- mutation and recombination settings;
- phenotype decoding.

`GeneticsProfiles` becomes an explicit registry. Every listed standard breedable type, `MULE`, and `HAPPY_GHAST` has a registry entry. Reusable family builders may share catalogs and decoders, but registry coverage is per entity type and profile IDs remain stable.

The generic profile remains a compatibility fallback for existing unprofiled `Animal` types. A listed breedable entity must never resolve to that fallback.

The server façade remains `AnimalGenetics` for compatibility, but its lifecycle methods expand to `AgeableMob` where necessary. No NMS type enters the API profile package.

## Trait matrix

### Variant profiles

The following profiles expose one multiallelic autosomal variant locus, deterministic heterozygote resolution, and a server adapter to the existing Bukkit/NMS variant:

- `AXOLOTL`: axolotl variant;
- `CAT`: cat variant;
- `CHICKEN`: chicken variant;
- `COW`: cow variant;
- `MOOSHROOM`: mooshroom variant;
- `FOX`: fox variant;
- `FROG`: frog variant;
- `PIG`: pig variant;
- `RABBIT`: rabbit variant;
- `WOLF`: wolf variant.

Founder capture reads the entity's current vanilla variant. New children inherit the variant alleles through the shared meiosis engine. Profile-specific dominance/order rules keep a heterozygote's visible result deterministic.

### Sheep

The existing three-locus profile remains authoritative:

- `sheep.base`: `BLACK`, `BROWN`, `RED`, `YELLOW`, with black > brown > red > yellow;
- `sheep.dilution`: `FULL` / recessive `DILUTE`;
- `sheep.albinism`: `PIGMENTED` / recessive `ALBINO`.

The resolved color is applied through Bukkit `DyeColor` after vanilla spawn and load defaults.

### Panda

Use two multiallelic loci matching the current seven vanilla panda genes:

- `panda.main`;
- `panda.hidden`;
- allele labels: `NORMAL`, `LAZY`, `WORRIED`, `PLAYFUL`, `BROWN`, `WEAK`, and `AGGRESSIVE`.

The profile decoder applies the current rule: a non-recessive main gene is visible; a recessive main gene is visible only when it matches the hidden gene, otherwise the result is `NORMAL`. `BROWN` and `WEAK` are recessive. Child inheritance preserves the two-locus structure; no new panda mechanics are introduced.

### Equine family

`HORSE`, `DONKEY`, and offspring-only `MULE` share a quantitative equine family catalog:

- `equine.color` and `equine.markings` where the species supports visible horse coat state;
- `equine.speed`;
- `equine.jump`;
- `equine.health`.

Quantitative alleles are bounded numeric values. For each equine numeric trait, child inheritance follows the vanilla `createOffspringAttribute` formula: clamp both parent values to the trait range; set `margin = 0.15 × range`; set `spread = |parentA - parentB| + 2 × margin`; set `average = (parentA + parentB) / 2`; sample `quality = (r1 + r2 + r3) / 3 - 0.5`; calculate `average + spread × quality`; and reflect once at either bound if the result exceeds the range. The server adapter applies the resulting attributes after vanilla child finalization. Horse × donkey uses the equine family catalog and resolves the child profile from the actual `MULE` entity type.

### Llama

`LLAMA` uses:

- `llama.color`;
- `llama.strength`, as a bounded discrete quantitative trait.

The adapter applies color and strength through the existing llama state/attribute APIs after vanilla child setup.

### Villager

`VILLAGER` uses a `villager.type` multiallelic locus. Profession remains vanilla behavior: offspring are unemployed. The child type resolver retains the vanilla environmental/parent weighting rather than treating profession as inherited genetics.

### Explicit empty profiles

These profiles have stable IDs, founder generation, codec persistence, breeding-compatible genomes, and empty phenotype output because their current vanilla breeding path does not inherit a distinct visible trait:

- `ARMADILLO`
- `BEE`
- `CAMEL`
- `GOAT`
- `HOGLIN`
- `NAUTILUS`
- `OCELOT`
- `SNIFFER`
- `STRIDER`
- `TURTLE`

`HAPPY_GHAST` also uses an explicit profile. Its dried-ghast reproduction path has no parent genome, so the ghastling receives a founder profile genome on insertion.

## Founder capture and phenotype ownership

When a profiled entity first enters a world without a compatible cached genome:

1. a server-side `FounderCapture` adapter reads the current vanilla state for supported visible variants and numeric attributes;
2. the profile encodes that state as a founder genome;
3. the profile ID and genome are cached;
4. the profile phenotype is applied after vanilla initialization.

Each capture adapter is keyed by profile ID and may only read the relevant NMS/Bukkit state. If a state cannot be captured, the profile's deterministic random founder strategy supplies the value.

Loaded compatible genomes are authoritative. Legacy genomes without a profile ID are treated as generic data; when they lack the loci required by the entity's explicit profile, the current vanilla state is captured instead of applying an incompatible phenotype.

## Lifecycle and breeding integration

### Common persistence and removal

Patch `AgeableMob` so all profile-bearing ageables share:

- additive `MintyGenome` and `MintyGenomeProfile` save/load;
- cache eviction for `KILLED` and `DISCARDED` removal reasons;
- cache retention for chunk unload and dimension transfer.

The existing Animal-only save/load/removal hooks are moved to this common base to avoid duplicate persistence.

### Entity insertion

Patch `ServerLevel.EntityCallbacks.onTrackingStart`:

- initialize `Animal` instances through the profile registry;
- initialize `Villager` instances through the same façade;
- leave unsupported ageables untouched.

The insertion method is idempotent and runs after entity lookup acceptance, subclass NBT loading, and vanilla spawn finalization.

### Standard parent mating

`Animal.spawnChildFromBreeding` continues to prepare a child genome before the child enters the world. The breeding resolver selects:

- the same species profile for ordinary crosses;
- the shared equine family plan for horse/donkey crosses;
- the actual child profile from the constructed offspring entity.

Parent profiles must be compatible with the selected breeding plan. Event cancellation discards the pending child cache entry; appearance application is owned by insertion, not by the event path.

### Villager mating

Patch `VillagerMakeLove` immediately after child creation and before `addFreshEntityWithPassengers`. The resolver caches the child villager genome and derived type before insertion. The common insertion hook then applies the phenotype.

### Happy ghast reproduction

The dried-ghast block creates a ghastling with no parent entities. The insertion hook attaches the explicit Happy Ghast founder profile. No synthetic parent cross is generated.

## Compatibility

- `GenomeCodec` JSON remains unchanged (`sex` plus `genes`).
- `MintyGenomeProfile` is additive NBT.
- Existing public generic genome/cross helpers remain usable.
- Existing generic cat/wolf/cow mappings remain available for legacy data.
- Profile IDs are stable once written.
- Unknown future entities retain the generic fallback until added to the explicit registry.

## Verification requirements

### API tests

- Enumerate every standard breedable type plus `MULE` and `HAPPY_GHAST`.
- Assert every entry has a non-null profile, stable ID, catalog, founder, phenotype decoder, and valid settings.
- Assert no listed type resolves to the generic fallback.
- Parameterize variant inheritance and phenotype tests for all variant profiles.
- Test sheep recessives, panda genes, equine quantitative traits, llama strength, and villager type inheritance.
- Test empty profiles through generation, cross, codec round trip, and phenotype decode.

### Server tests

- Verify common AgeableMob persistence and removal hooks.
- Verify Animal and Villager insertion hooks.
- Verify VillagerMakeLove attaches a child genome before world insertion.
- Verify horse/donkey resolves a mule profile.
- Verify Happy Ghast insertion creates a profile without parent genomes.
- Verify all profile-specific Bukkit/NMS phenotype adapters.
- Verify legacy generic NBT migration and profile mismatch regeneration.

### Commands

```bash
./gradlew :alkahest-api:test --tests 'dev.mintychochip.genetics.*'
./gradlew :paper-server:compileJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.GeneticsTestSuite'
```

After any `src/minecraft` edit:

```bash
./gradlew fixupSourcePatches
./gradlew rebuildPatches
```

## Out of scope

- New non-vanilla gameplay mechanics unrelated to inherited/visible breed traits.
- Profiles for entities without a confirmed breeding or offspring path.
- DNA mutation rules that silently discard semantic variant labels.
- A genetics command or GUI.
