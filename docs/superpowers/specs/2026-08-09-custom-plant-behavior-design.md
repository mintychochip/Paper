# Behavior-backed custom plants

**Date:** 2026-08-09
**Status:** architecture approved; implementation pending
**Packages:** `dev.mintychochip.customblock`, `dev.mintychochip.behavior`, and `dev.mintychochip.ecology`

## Goal

Make a custom block definition optionally behave like a real crop or sapling while keeping the logical definition, vanilla carrier, and public API boundary separate.

A custom plant must be able to receive growth and bonemeal operations through an API-owned receiver, return an immutable plan, and let the server apply that plan without exposing NMS or mutable Bukkit handles. Existing custom-block placement, breaking, drops, identity, provenance, and carrier behavior remain part of the same definition.

## Scope

This slice includes:

- an optional plant capability on `CustomBlockDefinition`;
- a `PLANT` host type with a vanilla crop or sapling carrier selected by `PlantHostSpec`;
- immutable growth contexts and explicit growth plans;
- random-tick and bonemeal routing for custom crop and sapling carriers;
- single-block stage changes and bounded multi-block structure plans;
- carrier-based placement and survival fallback;
- regression tests proving that custom plant growth is inert by default and does not leak carrier behavior;
- documentation of custom particles as the existing sibling implementation of the same definition → snapshot → plan → server-adapter pattern.

The following remain deferred:

- a registry for ordinary vanilla blocks such as `minecraft:wheat` or `minecraft:oak_sapling` that have no custom-block identity;
- arbitrary native tree-generator callbacks;
- direct world, NMS, packet, or mutable Bukkit handles in API receivers;
- new Minecraft registry IDs or a new vanilla `Material` enum entry;
- a new resource-pack host family beyond the selected vanilla crop/sapling carrier.

## Current problem

The behavior-backed custom value work already gives `CustomBlockDefinition` receivers for placement, interaction, breaking, movement, and explosions. Custom entities and particles follow the same immutable-context/plan pattern. Custom plants still have no public behavior capability, so a definition hosted on a crop- or sapling-like carrier cannot control random growth or bonemeal behavior without server-specific code.

The public ecology API currently provides `CropProfile` climate data and suitability math. The server-only `CropEcology` hooks gate native growth, but they do not represent a custom definition's growth algorithm. `CustomBlockBehavior` also cannot be reused directly: plant growth has different inputs and can replace a whole structure rather than only changing block metadata.

## Design principles

### Definition-owned optional capability

`CustomBlockDefinition` remains the logical identity and owns all behavior capabilities:

```text
CustomBlockDefinition
├── CustomBlockBehavior  // placement, interaction, break, movement, explosion
└── CustomPlantBehavior  // optional growth and bonemeal behavior
```

The plant capability is optional. A definition without a plant host is not a plant. A definition with a `PLANT` host but no explicit plant receiver uses the inert plant fallback defined below.

No second identity, item stamp, placement lookup, or catalog is introduced for plants.

### Vanilla carrier remains separate

A custom plant is rendered and physically represented by a vanilla carrier selected by `PlantHostSpec`:

- `PlantKind.CROP` selects an ageable crop carrier such as wheat, carrots, or a custom resource-pack carrier based on one of those states;
- `PlantKind.SAPLING` selects a sapling carrier such as oak sapling.

`Block#getType()`, `ItemStack#getType()`, and the live carrier's native block behavior continue to expose the vanilla carrier. The custom key is resolved through `CustomBlocks.of(Block)` and the placement lookup.

Carrier selection supplies default placement and survival rules. It does not implicitly supply random growth or bonemeal behavior to a custom plant.

### Immutable contexts and plans

Receivers receive API-safe snapshots and request data only. They do not receive `Block`, `World`, `Location`, `Player`, `ServerLevel`, `BlockState`, mutable `BlockData`, `RandomSource`, or NMS classes.

A receiver returns an immutable result. The server retains live handles, validates the result, preserves identity/provenance/display state, and applies the complete plan after the receiver returns.

### Explicit fallback and inert defaults

The result model distinguishes operation fallback from suppression:

- `Decision.DEFAULT` means the receiver did not override the operation;
- `Decision.ALLOW` means an explicit plant plan may be applied;
- `Decision.DENY` means the operation is suppressed;
- `PlantGrowthPlan.DEFAULT` resolves through the custom-plant fallback;
- `PlantGrowthPlan.NONE` handles the operation without changing the world;
- `PlantGrowthPlan.STATE` replaces one carrier block state;
- `PlantGrowthPlan.STRUCTURE` applies a bounded list of relative block changes.

For a custom plant, the default growth fallback is **inert**. This prevents a custom definition hosted on a crop or sapling from accidentally inheriting wheat growth, sapling tree generation, or another carrier's spreading behavior. Carrier survival and placement remain the default fallback.

### Structure safety

Structure plans contain relative offsets and immutable `BlockDataView` values. They are validated before any mutation:

- the list is immutable and non-null;
- every change has finite, bounded relative coordinates;
- block-data strings parse successfully;
- no duplicate relative positions exist;
- the origin remains the registered custom plant carrier until the plan is accepted;
- the server applies the complete validated plan or leaves the world unchanged.

The first implementation does not execute arbitrary tree-generator code supplied by an API receiver.

## API contracts

### Plant host metadata

Add `BlockHostType.PLANT` and a sealed `PlantHostSpec`:

```java
public enum PlantKind {
    CROP,
    SAPLING
}

public record PlantHostSpec(Material carrier, PlantKind kind) implements HostSpec {
    public static PlantHostSpec crop(Material carrier);
    public static PlantHostSpec sapling(Material carrier);
    public BlockHostType type();
}
```

The record rejects null or air carriers and requires a non-null `PlantKind`. `PlantHostSpec` is NMS-free; server bootstrap and placement validation confirm that the selected carrier belongs to the requested crop/sapling hook family. API catalog publication remains NMS-free and does not attempt to inspect native block classes.

`CustomBlockDefinition#carrierMaterial()` returns the selected vanilla carrier for `PLANT`. `CustomBlockPlacement` creates the carrier's initial state, setting an age/stage property to its minimum when the carrier exposes one.

### Growth cause and contexts

```java
public enum PlantGrowthCause {
    RANDOM_TICK,
    BONEMEAL
}
```

`PlantGrowthContext` contains:

- the immutable `BlockView` at the plant origin;
- the current `BlockDataView`;
- `PlantGrowthCause`;
- current stage and maximum stage when the carrier exposes an age/stage property;
- optional `ActorView` for bonemeal;
- optional `ClimateSample` supplied by the server ecology façade.

`PlantSurvival` is intentionally carrier-based in this slice. The API does not add a mutable physics callback; the selected vanilla carrier remains responsible for its normal support/soil rules. A future explicit survival receiver can be added only with a complete removal/drop plan.

### Growth plans

`PlantBlockChange` is an immutable relative change:

```java
public record PlantBlockChange(int x, int y, int z, BlockDataView data) {}
```

`PlantGrowthPlan` is a tagged immutable value with factories:

```java
PlantGrowthPlan.defaultPlan();
PlantGrowthPlan.none();
PlantGrowthPlan.state(BlockDataView data);
PlantGrowthPlan.structure(List<PlantBlockChange> changes);
```

`PlantGrowthPlan` rejects empty structures, duplicate positions, offsets whose absolute component exceeds `32`, or structures larger than `4096` changes. Custom structure plans do not expose `TreeType` because no vanilla species can describe an arbitrary custom structure without misrepresenting its identity.



`PlantGrowthResult` contains `Decision decision()` and `PlantGrowthPlan plan()` with factories/builders for default, deny, and handled plans. Invalid combinations are rejected during construction; an explicit handled plan cannot contain `DEFAULT`.

A state plan must retain the custom definition's selected carrier material. A structure plan may contain vanilla block data for the generated structure, but it cannot stamp unrelated custom identities or mutate the placement lookup directly.

### Custom plant behavior

`CustomPlantBehavior` is an immutable bundle with one growth receiver:

```java
@FunctionalInterface
public interface GrowthReceiver {
    PlantGrowthResult receive(PlantGrowthContext context);
}
```

The same receiver handles random ticks and bonemeal through `PlantGrowthCause`. `CustomPlantBehavior.defaults()` returns a receiver whose result is the inert default. A receiver returning `null` is an invalid server result and fails closed before world mutation.

`CustomBlockDefinition` gains:

```java
public CustomPlantBehavior plantBehavior();

public static final class Builder {
    public Builder plantBehavior(CustomPlantBehavior behavior);
}
```

The builder default is `CustomPlantBehavior.defaults()`. Definitions without `BlockHostType.PLANT` retain their existing behavior and cannot be routed through the plant lifecycle.

## Server data flow

### Placement

Existing custom-block placement remains authoritative:

1. resolve the stamped custom definition;
2. validate the selected plant carrier;
3. apply the carrier's initial state;
4. register custom identity and provenance;
5. let the existing `CustomBlockBehavior` placement plan handle explicit overrides.

No plant receiver runs during placement unless the existing placement receiver explicitly requests a block-data override.

### Random tick

Thin hooks in the vanilla crop and sapling classes call a server façade at the beginning of `randomTick`, before carrier brightness, maximum-age, or tree-stage gates:

```text
vanilla CropBlock/SaplingBlock random tick
        |
        v
CustomPlantLifecycle.randomTick(level, pos, carrier, random)
        |
        +--> non-plant or non-custom: return false; native checks continue
        +--> custom plant: invoke receiver once
                 |
                 +--> DEFAULT: inert custom-plant fallback; consume native path
                 +--> DENY: consume native path
                 +--> explicit plan: validate and apply; consume native path
```

For a custom plant, the native carrier algorithm never runs after the custom route claims the operation, even when the carrier is at maximum stage. Because vanilla `CropBlock.isRandomlyTicking` has no world position and cannot resolve custom identity, crop carriers remain scheduled at maximum age; ordinary max-age crops still exit at their existing age guard immediately after the hook. For ordinary vanilla blocks below maximum age, the hook returns false and preserves the existing ecology, brightness, age, and Bukkit growth path.


### Bonemeal

Crop and sapling bonemeal entry points call the same façade with `PlantGrowthCause.BONEMEAL`. A custom plant's receiver may deny, perform no operation, advance one state, or apply a structure plan. Vanilla crop/sapling behavior remains unchanged when no custom identity is present.

The server preserves Bukkit event semantics for single-state growth through the existing CraftBukkit growth-event path. Custom structure plans are materialized as detached block-state snapshots and applied only after validation; ordinary vanilla saplings retain their existing `StructureGrowEvent` path. A future custom structure event can be added without changing the receiver plan. The receiver never directly mutates an event.

### Native ecology ordering

The existing ecology suitability gate remains a server policy for native hooks. This slice does not add a native registry and does not let a custom plant receiver bypass the custom definition's own carrier validation. Climate data is supplied as context when available; custom plant behavior is otherwise independent of `Ecology` configuration.

### Failure handling

- Receiver exceptions are logged with the custom key and operation.
- Null or invalid results fail before mutation.
- Invalid structure plans leave the carrier and identity untouched.
- If applying a validated plan fails after partial server work, the lifecycle runs the existing custom-block cleanup path and clears stale identity/display state.
- A non-custom or non-plant block always returns to the vanilla caller without changing native behavior.

## Particles as the sibling track

Custom particles remain in the umbrella behavior architecture but are not coupled to plant growth. The existing implementation is the reference shape:

- `CustomParticleBehavior` owns an emission receiver;
- `ParticleEmissionContext` is immutable;
- `ParticleEmissionPlan` contains explicit decision, transport key, and parameters;
- `CraftWorld` and `CraftPlayer` route custom emissions before native conversion;
- `CustomParticleTransportRegistry` is server-owned because transports may use packets or resource-pack channels;
- native `Particle` conversion continues to reject catalog-only custom values.

The plant plan updates no particle transport code. It only documents the shared pattern and reruns the existing particle regression suite during final verification.

## Compatibility

- Public API remains NMS-free.
- `Material`, `EntityType`, and `Particle` remain compile-time API types.
- `Block#getType()` and `ItemStack#getType()` remain vanilla carriers.
- Existing non-plant custom block definitions remain source and binary compatible except for the additive builder/accessor field.
- Existing definitions without plant behavior continue to use custom-block defaults.
- Ordinary vanilla crops and saplings remain vanilla and are not intercepted by a custom registry in this slice.
- Custom particles retain their current transport-boundary behavior.

## Testing requirements

### API tests

Cover:

- `PlantHostSpec` factory and carrier/kind validation;
- immutable `PlantGrowthContext` snapshots;
- inert default behavior;
- explicit default, deny, none, state, and structure result branches;
- invalid state and structure plans rejected before publication;
- `CustomBlockDefinition` builder/accessor defaults and custom behavior retention;
- no API type imports NMS, CraftBukkit, packet, or mutable world handles.

### Server tests

Cover:

- custom crop random tick invokes the receiver once and suppresses carrier growth by default;
- custom sapling random tick invokes the receiver once and does not call the vanilla tree generator;
- bonemeal uses `PlantGrowthCause.BONEMEAL` and applies explicit state/structure plans;
- ordinary vanilla crop/sapling paths return false from the custom façade and retain existing behavior;
- invalid structure plans do not partially mutate the world;
- placement, break, drops, identity, provenance, and packet-display cleanup remain intact;
- particle transport and native-boundary regression suites continue to pass.

## Non-goals and deferred work

- No public registry for ordinary native crop/sapling materials in this slice. That is a separate compatibility feature using the same context/plan primitives.
- No arbitrary NMS tree generator or server callback exposed through the API.
- No custom `Material`, `EntityType`, or `Particle` enum entries.
- No direct receiver mutation of Bukkit events, worlds, entities, blocks, inventories, or NMS state.
- No automatic inheritance of the selected carrier's random growth or bonemeal algorithm.
