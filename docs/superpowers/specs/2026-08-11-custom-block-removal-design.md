# Custom Block Removal Design

**Date:** 2026-08-11  
**Status:** Design approved; specification review pending

## Context

The rollback work intentionally retained the `dev.mintychochip.customblock` feature and its catalog-backed `Material` integration. The current decision is to remove custom blocks completely. The removal must delete the feature as one dependency-closed slice rather than leave inert public APIs, commands, persistence readers, or resource-pack assets.

## Goals

- Remove all custom block definitions, placement hosts, behavior and plant contracts, runtime listeners, commands, displays, resource-pack hosting, and placement persistence.
- Remove custom-block identity from Bukkit `Block` and `ItemStack`.
- Remove catalog-backed custom materials and their registry/conversion plumbing.
- Restore vanilla-only item argument parsing and suggestions.
- Remove the behavior snapshot API and its legacy `MaterialRerouting` adapters/tests as an explicitly approved adjacent cleanup.
- Preserve unrelated systems: seasons, ecology, genetics, provenance core, native Bukkit/Paper registries, and the current `Material`/`VanillaMaterial` split used by CraftBukkit.
- Leave no compatibility aliases or deprecated shims for the removed feature or adjacent behavior API.

## Non-goals

- Do not restore the pre-split native `Material` enum. That would be a broader API/server rollback and is not required to remove custom blocks.
- Do not migrate or rewrite existing worlds. Old custom-block SavedData, custom-block PDC values, generated pack files, and ignored runtime configuration may remain on disk but will no longer be read or produced.
- Do not alter historical custom-block design specs or historical rollback plans. Current inventories and feature maps must be corrected.
- Do not modify provenance identity, census, lineage, audit, or persistence behavior except for deleting the custom-block provenance adapter and its custom-block-only tests.

## Removal boundary

### API sources and tests

Delete the complete `alkahest-api` custom-block package and its tests:

- `alkahest-api/src/main/java/dev/mintychochip/customblock/`
- `alkahest-api/src/test/java/dev/mintychochip/customblock/`

The package includes definitions, hosts, catalogs, PDC keys/tags, behavior/plant contracts, and result/context DTOs. These types have no supported consumers after custom blocks are removed.

Delete `alkahest-api/src/main/java/dev/mintychochip/behavior/` and its tests. The user explicitly approved removing this adjacent behavior snapshot API after discovery showed that it also feeds legacy `MaterialRerouting`; those adapters and their integration tests are removed with it.

Remove these additive Bukkit methods and their Javadocs:

- `Block#getCustomKey`, `Block#getCustomBlock`, `Block#isCustomBlock`
- `ItemStack#getCustomKey`, `ItemStack#getCustomBlock`, `ItemStack#isCustomBlockItem`

### Material and registry surface

Keep the current interface-based `Material` and `VanillaMaterial` architecture because unrelated CraftBukkit code currently switches on `VanillaMaterial` and depends on the split.

Remove only custom-material behavior:

- Delete `CustomMaterial` through deletion of the custom-block package.
- Remove `Material#isCustom` and its vanilla implementation.
- Remove custom-block catalog lookup and custom-specific documentation from `Material#matchMaterial`/`Material#getByKey`; retained key lookup resolves vanilla materials only.
- Keep `Material#isVanilla` as the current split marker. With custom implementations gone, all returned material values are vanilla.
- Change `MaterialRegistry` back to a vanilla-only adapter: no `CustomBlockCatalog` supplier, catalog merge, custom sorting, or `isCatalog` method.
- Keep `Registry.MATERIAL` backed by the vanilla-only adapter.
- Remove the four `CraftMagicNumbers` guards that reject `material.isCustom()`. Native conversion paths are again the only paths.

### Server sources and tests

Delete the complete `paper-server` custom-block implementation and tests:

- `paper-server/src/main/java/dev/mintychochip/customblock/`
- `paper-server/src/test/java/dev/mintychochip/customblock/`
- `paper-server/src/test/java/dev/mintychochip/provenance/CustomBlockProvenanceTest.java`
- `paper-server/src/test/java/dev/mintychochip/MaterialBootstrapTest.java` when its assertions are custom-material-only
- `paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java` (remove the three behavior-view adapter methods; retain native `MaterialRerouting` methods)
- `paper-server/src/test/java/dev/mintychochip/behavior/`

Remove the `CraftServer` custom-block bootstrap hook. Delete `MintyInternalPlugin` only if the final reference audit confirms it has no remaining consumers; current references are custom-block-only.

Remove the custom-block-only resource-pack files under `paper-server/src/main/resources/mintychochip-pack/`.

Delete the three custom Minecraft source patches:

- `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemArgument.java.patch`
- `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemInput.java.patch`
- `paper-server/patches/sources/net/minecraft/commands/arguments/item/ItemParser.java.patch`

These patches contain only custom-material/custom-block argument parsing, tab completion, and `/give` stack construction. Their removal restores the unmodified upstream item argument behavior; no replacement patch is required.

### Runtime effects

After removal:

- `/customblock`, `/cb`, and `/cblock` are absent.
- Vanilla `/give` accepts only native registered items.
- No custom block is registered at startup.
- No custom placement lookup or `custom_block_placements` SavedData is loaded or written.
- No custom PDC identity is created or interpreted.
- No custom display entities, dig skins, carrier rewrites, plant routing, or custom resource-pack HTTP server are started.
- Normal vanilla block placement, breaking, piston movement, explosions, and item conversion remain in charge.

Old custom-block data is intentionally treated as orphaned data. The server does not attempt to interpret, repair, or delete it.

## Documentation

Update the current feature inventory in `AGENTS.md` so custom blocks are no longer listed as an active system. Add a removal note only where needed to explain the clean break and ignored runtime data. Leave historical design/spec/plan documents intact.

## Verification contract

The implementation is complete only when all of the following are true:

1. Scoped source and test searches find no `dev.mintychochip.customblock`, custom-block identity method, `CustomMaterial`, `isCustom()`, custom-block command, or custom-block patch reference outside historical documentation.
2. The API tests compile without the deleted packages and preserve native material lookup/registry behavior.
3. The server tests compile without custom-block bootstrap, conversion guards, or provenance adapter references.
4. `applyPatches` succeeds with the three custom item patches absent.
5. Focused native material/API tests and the provenance suite pass.
6. API and server Java compilation pass.
7. The final diff contains no edits to ignored runtime work data under `run/` and no unrelated feature changes.
