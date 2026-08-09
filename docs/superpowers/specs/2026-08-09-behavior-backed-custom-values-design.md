# Behavior-backed custom API values

**Date:** 2026-08-09
**Status:** architecture approved; implementation pending
**Packages:** `dev.mintychochip.customblock`, `dev.mintychochip.customentity`, `dev.mintychochip.particle`, and the existing Bukkit API interfaces

## Goal

Make custom values first-class API definitions with both vanilla-facing metadata and explicit behavior contracts.

A custom value must not merely implement `Material`, `EntityType`, or `Particle` while leaving its actual behavior hard-coded in server classes. The definition owns the behavior contract. The server supplies routers that invoke those contracts from Bukkit events and apply the returned plans to the world.

The API remains NMS-free. A custom value can expose the same Bukkit identity and metadata surface as a vanilla value without pretending to be a native Minecraft registry holder.

## Scope

Behavior receivers are introduced for value types with concrete lifecycle or event surfaces:

- custom materials/blocks;
- custom entity types;
- custom particles.

Potion types and memory keys remain metadata-only in this slice. A potion behavior receiver belongs to an item/use contract, not to `PotionType` alone. A memory key is a typed identity and has no independent event lifecycle.

## Current problem

The current definitions already implement the public API interfaces and expose metadata, but behavior is concentrated in server-side classes:

- `paper-server/.../customblock/CustomBlockLifecycle` owns placement, break, drops, carrier cleanup, and display cleanup;
- `paper-server/.../customentity/CustomEntityLifecycle` owns carrier spawning and application;
- custom particles can be accepted by `ParticleBuilder`, but native particle emission still requires an NMS holder and therefore rejects custom values.

This makes behavior class-specific, difficult to compose, and disconnected from the registered definition.

## Design principles

### First-class API values

Existing value shapes remain:

- `CustomBlockDefinition` is a `Material` through `CustomMaterial`;
- `CustomEntityDefinition` is an `EntityType` through `CustomEntityType`;
- `CustomParticle` implements `Particle`.

Their existing vanilla-facing methods continue to expose metadata. `CustomBlockDefinition` still delegates block-data and world properties through host/carrier metadata, item properties through `itemMaterial`, and mining/explosion strength through `BlockFeel`.

### Value-owned immutable behavior

Each definition carries an immutable behavior bundle. A catalog stores the complete value; it does not maintain a separate behavior map that can become detached from the value.

A missing receiver means “use the definition’s metadata/carrier fallback,” not “do nothing.” Receivers override behavior selectively.

### Decision plans, not mutation callbacks

Receiver callbacks receive immutable API-safe context snapshots and return immutable results/plans.
They never receive a mutable Bukkit handle and never mutate Bukkit events, worlds, entities, blocks,
or NMS objects directly.

Every result uses explicit tagged states rather than nullable fields:

- `Decision` has `DEFAULT`, `ALLOW`, and `DENY`;
- `ValueOverride<T>` has `USE_DEFAULT` or `VALUE(T)`;
- `DropPlan`, `ConsumePlan`, and `CleanupPlan` are tagged plans with explicit default, suppress, and
  value/action branches.

Examples:

- `PlacementResult` can explicitly allow/deny/default placement, choose default or explicit item
  consumption, and request carrier/display actions;
- `BreakResult` can explicitly allow/deny/default breaking, choose default/no/explicit drops, set
  default or explicit XP, and request identity/display cleanup;
- `InteractionResult` can explicitly choose default/allow/deny event-use decisions and a resulting
  action;
- entity and particle results describe the requested operation for a server router to apply.

`USE_DEFAULT` is the only fallback state. A result cannot use `null` to mean both “not specified”
and “no value.” The server applies receiver overrides first, then resolves every
`USE_DEFAULT` branch from the existing vanilla/carrier/item metadata fallback.

### API-safe contexts

Contexts contain immutable snapshots and request data only. They do not expose mutable Bukkit
handles such as `Block`, `Entity`, `ItemStack`, `Location`, `World`, `Player`, mutable
`BlockData`, or mutable `ItemMeta`.

The API defines read-only views such as `BlockView`, `EntityView`, `ItemStackView`, `ActorView`,
`Position`, and `BlockDataView`. These expose identity and state needed for decisions without
world-mutating methods. `Material`, `EntityType`, `NamespacedKey`, `EquipmentSlot`, and other
immutable/value-like API types may be included directly.

The server router retains the live Bukkit handles outside the receiver call and applies the
returned plan after validation. A receiver cannot mutate a world, event, item, entity, or block
through its context. The API contracts must document every snapshot field and whether it is
captured before or after the underlying Bukkit event.

Contexts and results must not expose `net.minecraft.*`, CraftBukkit implementation types, packet
classes, or mutable internal server state.

### Native boundary remains explicit

Custom values are valid catalog/API identities, not native NMS holders:

- native particle, potion, and memory-holder conversion continues to reject catalog-only values;
- custom block and entity values use vanilla carriers where the world/client requires one;
- native tag/holder/wire systems only include native values unless a separate transport is installed.

The behavior API must not weaken these boundaries or silently translate a custom value into an unrelated vanilla identity.

## API contracts

Names below describe the intended public contracts; implementation should follow existing package conventions and keep each context/result small.

### Custom blocks and materials

`CustomBlockDefinition` gains a `CustomBlockBehavior` value with typed receivers for the existing server lifecycle:

- placement, including manual placement for non-block item bases;
- interaction;
- breaking and drops;
- piston movement policy;
- explosion handling and cleanup.

The contexts identify the logical custom definition and expose the relevant Bukkit operation. Results describe cancellation, drops, XP, item consumption, carrier changes, identity cleanup, and display cleanup. The server `CustomBlockListener` becomes a router: resolve the definition, invoke the receiver, then apply the plan.

Existing defaults remain authoritative when a receiver does not override them:

- `HostSpec` resolves the world carrier;
- `itemMaterial` resolves held/inventory behavior;
- `BlockFeel` resolves hardness, blast resistance, tool requirements, and mining fallback;
- packet-display and placement stores remain server implementation details.

### Custom entities

`CustomEntityDefinition` gains a `CustomEntityBehavior` bundle for the lifecycle currently supported by the server:

- spawn planning;
- applying definition metadata to an existing carrier;
- interaction/lifecycle cleanup where the server has a corresponding hook.

Entity results identify the carrier operation and presentation metadata to apply. `CustomEntityLifecycle` becomes the server-side plan applier rather than the owner of every custom entity’s behavior.

The live entity remains a vanilla carrier for Bukkit/NMS operations. The custom definition remains the logical `EntityType` identity.

### Custom particles

`CustomParticle` gains immutable particle metadata and an emission receiver. The receiver receives a `ParticleEmissionContext` containing:

- location;
- selected receivers or world-wide delivery;
- source player;
- count, offsets, extra, force, and custom data.

It returns a transport-neutral `ParticleEmissionPlan`. The server applies the plan through an installed custom particle transport. The native `World#spawnParticle` path remains available for vanilla particles and does not become a false native holder path for custom particles.

Builder construction and metadata remain supported for custom particles, but successful client emission requires the custom transport represented by the plan.

## Server data flow

```text
Bukkit event/API call
        |
        v
server router resolves logical custom definition
        |
        v
API receiver(context) -> immutable result/plan
        |
        v
server applies result
        |
        +--> receiver override
        +--> metadata/carrier fallback for unspecified fields
        +--> provenance, persistence, and display services
```

Routers must resolve the definition once per operation and use that same snapshot for the entire dispatch. A failed receiver must not leave a partially applied identity or carrier state; the router either applies the complete plan or performs the existing cleanup path.

## Error handling

- Receiver and context arguments are non-null unless explicitly annotated otherwise.
- Invalid result combinations fail before world mutation (for example, a drop plan with an invalid stack or a carrier action incompatible with the host).
- Receiver exceptions are logged with the custom key and operation, then use the existing server failure/cleanup policy; they must not be silently converted into a native vanilla value.
- Registration remains atomic. A definition with an invalid behavior bundle is rejected before catalog publication.

## Compatibility

- `Material`, `EntityType`, and `Particle` remain the public compile-time types used by existing Bukkit APIs.
- `values()` and `valueOf()` remain vanilla-only helpers.
- `Registry` custom views continue to return the exact registered custom object.
- `Block#getType()`, `ItemStack#getType()`, and live carrier `Entity#getType()` remain vanilla carrier values.
- Existing metadata-only definitions continue to work without receivers through the fallback path.

## Testing requirements

API tests must cover:

- immutable behavior bundles and default fallback behavior;
- context snapshots expose no mutating Bukkit handles and preserve documented capture timing;
- receiver results for placement, interaction, break/drop, entity spawn, and particle emission;
- every result branch is explicit (`DEFAULT`, `ALLOW`, `DENY`, `USE_DEFAULT`, suppression, or value);
- invalid result combinations rejected before publication/application;
- custom values remain usable through the corresponding Bukkit interfaces and static registry views;
- no receiver can expose or require NMS types in the API module.

Server tests must cover:

- Bukkit event routers dispatch to the definition’s receiver;
- receiver plans are applied exactly once;
- every `USE_DEFAULT` branch resolves through carrier/item metadata fallback;
- failed dispatch cleans placement identity and packet displays;
- custom particle plans use the custom transport while native conversion still rejects catalog-only particles;
- vanilla values retain their existing behavior.

## Non-goals

- Adding native Minecraft registry IDs for custom values;
- making custom values appear in vanilla enum `values()` or `valueOf()`;
- exposing NMS or CraftBukkit implementation types from the API;
- inventing memory-key callbacks without a corresponding lifecycle;
- making every Bukkit consumer automatically understand custom values when it explicitly requires a vanilla holder or enum.
