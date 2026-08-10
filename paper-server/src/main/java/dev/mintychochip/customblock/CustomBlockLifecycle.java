package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.ItemStackView;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.VanillaMaterial;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Place / break core for custom blocks. Called from {@link CustomBlockListener}.
 *
 * <p>Keeps Bukkit {@code BlockBreakEvent} / {@code BlockPlaceEvent} intact — only adds
 * identity registration, carrier correction, and custom drops.
 */
public final class CustomBlockLifecycle {

    private CustomBlockLifecycle() {
    }

    /**
     * After a successful vanilla place of a stamped custom-block item.
     * Corrects carrier to the host material and registers identity.
     *
     * @return {@code true} if this was a custom block placement
     */
    public static boolean handlePlace(@NotNull final BlockPlaceEvent event) {
        final ItemStack hand = event.getItemInHand();
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(hand);
        if (defOpt.isEmpty()) {
            return false;
        }
        final CustomBlockDefinition def = defOpt.get();
        final Block block = event.getBlockPlaced();
        final PlacementResult plan = CustomBlockBehaviorRouter.placePlan(
            def,
            new BlockPlaceContext(
                snapshot(block),
                actorSnapshot(event.getPlayer()),
                itemSnapshot(hand),
                event.canBuild()
            )
        );
        if (plan.decision() == dev.mintychochip.behavior.Decision.DENY) {
            event.setCancelled(true);
            return false;
        }
        try {
            applyPlacementData(block, def, plan);
            registerPlacement(block, def);
            // mintychochip - item provenance: place memory from hand (BlockItem may also fire)
            CustomBlockProvenance.recordPlace(block, hand, event.getPlayer());
            if (def.isPacket()) {
                dev.mintychochip.customblock.display.PacketDisplayService.get().spawn(block, def);
            }
            applyCleanup(block, plan.cleanup());
        } catch (final Throwable t) {
            org.bukkit.Bukkit.getLogger().log(
                java.util.logging.Level.SEVERE,
                "[mintychochip] custom block place failed for " + def.namespacedKey() + " at " + block.getLocation(),
                t
            );
            throw t instanceof RuntimeException re ? re : new RuntimeException(t);
        }
        return true;
    }

    /**
     * Manual place for items whose base material is not a block (e.g. PAPER).
     *
     * @return {@code true} if a custom block was placed
     */
    public static boolean handleManualPlace(
        @NotNull final Player player,
        @NotNull final ItemStack hand,
        @NotNull final Block against,
        @NotNull final BlockFace face,
        @Nullable final EquipmentSlot handSlot
    ) {
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(hand);
        if (defOpt.isEmpty()) {
            return false;
        }
        final CustomBlockDefinition def = defOpt.get();
        // Placeable base materials go through BlockPlaceEvent instead.
        if (def.itemMaterial().isBlock()) {
            return false;
        }

        final Block target = against.getRelative(face);
        if (!target.getType().isAir() && !target.isReplaceable()) {
            return false;
        }
        if (!canBuild(player, target)) {
            return false;
        }
        final PlacementResult plan = CustomBlockBehaviorRouter.placePlan(
            def,
            new BlockPlaceContext(
                snapshot(target),
                actorSnapshot(player),
                itemSnapshot(hand),
                true
            )
        );
        if (plan.decision() == dev.mintychochip.behavior.Decision.DENY) {
            return false;
        }

        applyPlacementData(target, def, plan);
        registerPlacement(target, def);
        // mintychochip - item provenance: placement before hand consume
        CustomBlockProvenance.recordPlace(target, hand, player);
        if (def.isPacket()) {
            dev.mintychochip.customblock.display.PacketDisplayService.get().spawn(target, def);
        }
        consume(player, hand, handSlot, plan.consumeItem());
        CustomBlockProvenance.afterHandConsume(hand);
        applyCleanup(target, plan.cleanup());
        // Non-block base items would otherwise show the "use" animation. Force a place feel:
        // arm swing + place sound. Prefer itemMaterial().isBlock() so vanilla place path runs.
        final EquipmentSlot swingSlot = handSlot != null ? handSlot : EquipmentSlot.HAND;
        player.swingHand(swingSlot);
        final Material carrier = CustomBlockPlacement.carrierMaterial(def);
        final Sound placeSound = placeSoundFor(carrier);
        target.getWorld().playSound(
            target.getLocation().add(0.5, 0.5, 0.5),
            placeSound,
            SoundCategory.BLOCKS,
            1.0f,
            0.8f + (float) (Math.random() * 0.4f)
        );
        return true;
    }

    static boolean handleInteract(@NotNull final PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(block);
        if (defOpt.isEmpty()) {
            return false;
        }
        final CustomBlockDefinition definition = defOpt.get();
        final InteractionResult plan = CustomBlockBehaviorRouter.interactPlan(
            definition,
            interactContext(event, block)
        );
        if (plan.decision() == dev.mintychochip.behavior.Decision.DENY) {
            event.setCancelled(true);
            return true;
        }
        if (plan.useItem() instanceof ValueOverride.Value<?> value) {
            event.setUseItemInHand((Boolean) value.value() ? org.bukkit.event.Event.Result.ALLOW : org.bukkit.event.Event.Result.DENY);
        }
        if (plan.useBlock() instanceof ValueOverride.Value<?> value) {
            event.setUseInteractedBlock((Boolean) value.value() ? org.bukkit.event.Event.Result.ALLOW : org.bukkit.event.Event.Result.DENY);
        }
        final ItemStack item = event.getItem();
        if (item != null && plan.consumeItem().kind() == ConsumePlan.Kind.EXPLICIT) {
            consume(event.getPlayer(), item, event.getHand(), plan.consumeItem());
        }
        return true;
    }

    private static void applyPlacementData(
        final Block block,
        final CustomBlockDefinition definition,
        final PlacementResult plan
    ) {
        if (plan.blockData() instanceof ValueOverride.Value<?> value) {
            final BlockDataView view = (BlockDataView) value.value();
            block.setBlockData(Bukkit.createBlockData(view.serialized()), false);
        } else {
            CustomBlockPlacement.applyCarrier(block, definition);
        }
    }

    private static void applyCleanup(final Block block, final CleanupPlan plan) {
        if (plan.kind() != CleanupPlan.Kind.EXPLICIT) {
            return;
        }
        if (plan.despawnDisplay()) {
            dev.mintychochip.customblock.display.PacketDisplayService.get().despawn(block);
        }
        if (plan.clearIdentity()) {
            CustomBlocks.lookup().clearAt(block);
            CustomBlockProvenance.clearPlacement(block);
        }
    }

    /** Clear custom identity and displays after a plant update fails after mutation begins. */
    static void cleanupAfterGrowthFailure(final Block block) {
        try {
            applyCleanup(block, CleanupPlan.explicit(true, true));
        } catch (final RuntimeException failure) {
            Bukkit.getLogger().log(
                java.util.logging.Level.SEVERE,
                "[mintychochip] custom plant failure cleanup failed at " + block.getLocation(),
                failure
            );
        }
    }

    private static void consume(
        final Player player,
        final ItemStack hand,
        final @Nullable EquipmentSlot handSlot,
        final ConsumePlan plan
    ) {
        switch (plan.kind()) {
            case DEFAULT -> consumeAmount(player, hand, handSlot, 1);
            case NONE -> {
            }
            case EXPLICIT -> consumeAmount(player, hand, handSlot, plan.amount());
        }
    }

    private static Sound placeSoundFor(final Material carrier) {
        // Best-effort; glass is the default packet-host collision.
        // Compare against VanillaMaterial — Material.* interface statics may be null.
        if (carrier == org.bukkit.VanillaMaterial.GLASS || carrier.name().contains("GLASS")) {
            return Sound.BLOCK_GLASS_PLACE;
        }
        if (carrier == org.bukkit.VanillaMaterial.TRIPWIRE) {
            return Sound.BLOCK_STONE_PLACE;
        }
        if (carrier.name().contains("MUSHROOM") || carrier == org.bukkit.VanillaMaterial.CHORUS_PLANT) {
            return Sound.BLOCK_WOOD_PLACE;
        }
        return Sound.BLOCK_STONE_PLACE;
    }

    private static void registerPlacement(final Block block, final CustomBlockDefinition def) {
        final CustomBlockLookup lookup = CustomBlocks.lookup();
        if (lookup instanceof MemoryCustomBlockLookup memory) {
            memory.put(block, def);
        } else {
            // Ensure bootstrap installed when events fire on a live server.
            CustomBlockBootstrap.placements().put(block, def);
        }
    }

    static record BreakPreparation(CustomBlockDefinition definition, BreakResult plan) {
    }
    static record MovementPreparation(
        Block block,
        Location destination,
        CustomBlockDefinition definition,
        MovementResult plan
    ) {
    }

    static record ExplosionPreparation(
        Block block,
        CustomBlockDefinition definition,
        ExplosionResult plan
    ) {
    }

    static Optional<MovementPreparation> prepareMove(
        @NotNull final Block block,
        @NotNull final BlockFace direction
    ) {
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(block);
        if (defOpt.isEmpty()) {
            return Optional.empty();
        }
        final CustomBlockDefinition definition = defOpt.get();
        final BlockView view = snapshot(block);
        final Position from = view.position();
        final Position to = new Position(
            from.worldKey(),
            from.x() + direction.getModX(),
            from.y() + direction.getModY(),
            from.z() + direction.getModZ()
        );
        final MovementResult plan = CustomBlockBehaviorRouter.movePlan(
            definition,
            new BlockMoveContext(view, from, to, direction.name())
        );
        final Location destination = block.getLocation().clone().add(
            direction.getModX(),
            direction.getModY(),
            direction.getModZ()
        );
        return Optional.of(new MovementPreparation(block, destination, definition, plan));
    }

    static Optional<ExplosionPreparation> prepareExplosion(
        @NotNull final Block block,
        @NotNull final Position source,
        final boolean entitySource
    ) {
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(block);
        if (defOpt.isEmpty()) {
            return Optional.empty();
        }
        final CustomBlockDefinition definition = defOpt.get();
        final ExplosionResult plan = CustomBlockBehaviorRouter.explodePlan(
            definition,
            new BlockExplodeContext(snapshot(block), source, entitySource)
        );
        return Optional.of(new ExplosionPreparation(block, definition, plan));
    }

    static void finishMove(@NotNull final MovementPreparation preparation) {
        final Block block = preparation.block();
        final Location from = block.getLocation().clone();
        final Location destination = preparation.destination().clone();
        final MovementResult plan = preparation.plan();
        final boolean explicitCleanup = plan.cleanup().kind() == CleanupPlan.Kind.EXPLICIT;
        final boolean clearIdentity = explicitCleanup && plan.cleanup().clearIdentity();
        final boolean despawnDisplay = explicitCleanup && plan.cleanup().despawnDisplay();

        if (clearIdentity) {
            // The piston destination is not registered when the receiver explicitly clears identity.
            // Clear the source store before any display move so no orphan remains behind.
            CustomBlocks.lookup().clearAt(block);
            CustomBlockProvenance.clearPlacement(block);
        } else {
            movePlacement(block, destination);
            CustomBlockProvenance.movePlacement(from, destination);
        }
        final Block target = block.getWorld().getBlockAt(destination);
        if (preparation.definition().isPacket() && !despawnDisplay) {
            final var displays = dev.mintychochip.customblock.display.PacketDisplayService.get();
            displays.despawn(from);
            displays.spawn(target, preparation.definition());
        } else if (despawnDisplay) {
            dev.mintychochip.customblock.display.PacketDisplayService.get().despawn(from);
        }
        if (explicitCleanup) {
            applyCleanup(target, plan.cleanup());
        }
    }

    static void finishExplosion(@NotNull final ExplosionPreparation preparation) {
        final ExplosionResult plan = preparation.plan();
        if (plan.decision() == dev.mintychochip.behavior.Decision.DENY) {
            return;
        }
        if (plan.cleanup().kind() == CleanupPlan.Kind.DEFAULT) {
            dev.mintychochip.customblock.display.PacketDisplayService.get().despawn(preparation.block());
            CustomBlocks.lookup().clearAt(preparation.block());
            // mintychochip - item provenance: explode no-drop must not leave placement orphans
            CustomBlockProvenance.clearPlacement(preparation.block());
        } else {
            applyCleanup(preparation.block(), plan.cleanup());
        }
        if (plan.drops().kind() != DropPlan.Kind.EXPLICIT) {
            return;
        }
        final Location dropAt = preparation.block().getLocation().add(0.5, 0.5, 0.5);
        for (final ItemStackView item : plan.drops().items()) {
            preparation.block().getWorld().dropItemNaturally(dropAt, item.copy());
        }
    }

    static @NotNull Position position(@NotNull final Block block) {
        return snapshot(block).position();
    }

    static @NotNull Position position(@NotNull final Location location) {
        final String worldKey = worldKey(location.getWorld());
        return new Position(worldKey, location.getX(), location.getY(), location.getZ());
    }

    private static void movePlacement(final Block block, final Location destination) {
        final CustomBlockLookup lookup = CustomBlocks.lookup();
        final MemoryCustomBlockLookup memory = lookup instanceof MemoryCustomBlockLookup value
            ? value
            : CustomBlockBootstrap.placements();
        final Optional<org.bukkit.NamespacedKey> key = memory.keyAt(block);
        if (key.isPresent()) {
            memory.remove(block.getLocation());
            memory.put(destination, key.get());
        }
    }


    static Optional<BreakPreparation> prepareBreakWithBehavior(@NotNull final BlockBreakEvent event) {
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(event.getBlock());
        if (defOpt.isEmpty()) {
            return Optional.empty();
        }
        final CustomBlockDefinition definition = defOpt.get();
        final BreakResult plan = CustomBlockBehaviorRouter.breakPlan(
            definition,
            breakContext(event.getBlock(), event.getPlayer(), event.isDropItems())
        );
        if (plan.decision() == dev.mintychochip.behavior.Decision.DENY
            || (plan.decision() == dev.mintychochip.behavior.Decision.DEFAULT
                && definition.feel().isUnbreakable()
                && event.getPlayer().getGameMode() != GameMode.CREATIVE)) {
            event.setCancelled(true);
            return Optional.empty();
        }
        applyBreakPlan(event, plan);
        return Optional.of(new BreakPreparation(definition, plan));
    }

    private static void applyBreakPlan(final BlockBreakEvent event, final BreakResult plan) {
        if (event.isDropItems()) {
            event.setDropItems(false);
        }
        if (plan.experience() instanceof ValueOverride.Value<?> value) {
            event.setExpToDrop((Integer) value.value());
        } else {
            event.setExpToDrop(0);
        }
    }

    /**
     * HIGH priority: suppress vanilla carrier drops for custom blocks; remember definition for drop.
     *
     * @return definition if this break is a custom block, else empty
     */
    public static Optional<CustomBlockDefinition> prepareBreak(@NotNull final BlockBreakEvent event) {
        final Optional<CustomBlockDefinition> defOpt = CustomBlocks.of(event.getBlock());
        if (defOpt.isEmpty()) {
            return Optional.empty();
        }
        final CustomBlockDefinition def = defOpt.get();
        // Unbreakable feel: cancel break entirely (bedrock-style).
        if (def.feel().isUnbreakable() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            return Optional.empty();
        }
        // Carrier (glass/chorus/…) must not drop vanilla loot.
        if (event.isDropItems()) {
            event.setDropItems(false);
        }
        event.setExpToDrop(0);
        return defOpt;
    }

    /**
     * MONITOR: clear placement identity and drop the custom block item when tool rules allow.
     */
    public static void finishBreak(
        @NotNull final BlockBreakEvent event,
        @Nullable final CustomBlockDefinition definition
    ) {
        finishBreak(event, definition, null);
    }

    static void finishBreak(
        @NotNull final BlockBreakEvent event,
        @Nullable final CustomBlockDefinition definition,
        @Nullable final BreakResult plan
    ) {
        final Block block = event.getBlock();
        if (plan != null && plan.decision() == dev.mintychochip.behavior.Decision.DENY) {
            return;
        }
        applyBreakCleanup(block, plan);
        if (definition == null) {
            CustomBlockProvenance.clearPlacement(block);
            return;
        }
        if (plan != null && plan.drops().kind() == DropPlan.Kind.NONE) {
            CustomBlockProvenance.clearPlacement(block);
            return;
        }
        if (plan != null && plan.drops().kind() == DropPlan.Kind.EXPLICIT) {
            final Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
            for (final dev.mintychochip.behavior.ItemStackView item : plan.drops().items()) {
                block.getWorld().dropItemNaturally(dropAt, item.copy());
            }
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            // mintychochip - item provenance: creative break clears placement, no drop
            CustomBlockProvenance.clearPlacement(block);
            return;
        }
        // Same as vanilla: wrong tool + requiresCorrectTool → no drop.
        if (!CustomBlockMining.canHarvest(event.getPlayer(), definition.feel())) {
            CustomBlockProvenance.clearPlacement(block);
            return;
        }
        final Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
        // mintychochip - item provenance: BLOCK_RECOVER drop linked to placed stack UUID
        final ItemStack drop = CustomBlockProvenance.createRecoverDrop(block, definition);
        block.getWorld().dropItemNaturally(dropAt, drop);
    }

    private static void applyBreakCleanup(final Block block, @Nullable final BreakResult plan) {
        if (plan == null || plan.cleanup().kind() == CleanupPlan.Kind.DEFAULT) {
            dev.mintychochip.customblock.display.PacketDisplayService.get().despawn(block);
            CustomBlocks.lookup().clearAt(block);
            return;
        }
        if (plan.cleanup().kind() == CleanupPlan.Kind.EXPLICIT) {
            if (plan.cleanup().despawnDisplay()) {
                dev.mintychochip.customblock.display.PacketDisplayService.get().despawn(block);
            }
            if (plan.cleanup().clearIdentity()) {
                CustomBlocks.lookup().clearAt(block);
                CustomBlockProvenance.clearPlacement(block);
            }
        }
    }

    /** Cancel piston moves that would shift a custom block. */
    public static boolean isCustom(@NotNull final Block block) {
        return CustomBlocks.isCustomBlock(block);
    }

    static @NotNull BlockView snapshot(@NotNull final Block block) {
        final Location location = Objects.requireNonNull(block.getLocation(), "block location");
        final Material carrier = block.getType() != null ? block.getType() : VanillaMaterial.AIR;
        final BlockData liveData = block.getBlockData();
        final BlockDataView data = liveData != null
            ? BlockDataView.from(liveData)
            : new BlockDataView(carrier, carrier.name().toLowerCase(java.util.Locale.ROOT));
        final String worldKey = worldKey(location.getWorld());
        return new BlockView(
            new Position(worldKey, location.getX(), location.getY(), location.getZ()),
            carrier,
            data,
            CustomBlocks.keyOf(block)
        );
    }

    static @NotNull Optional<ActorView> actorSnapshot(@Nullable final Player player) {
        if (player == null || player.getLocation() == null) {
            return Optional.empty();
        }
        final Location location = player.getLocation();
        final String worldKey = worldKey(location.getWorld());
        return Optional.of(new ActorView(
            player.getUniqueId(),
            new Position(worldKey, location.getX(), location.getY(), location.getZ()),
            Optional.ofNullable(player.getName()),
            true
        ));
    }

    static @NotNull Optional<ItemStackView> itemSnapshot(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ItemStackView.from(item));
    }

    static @NotNull BlockBreakContext breakContext(
        final Block block,
        final Player player,
        final boolean dropItems
    ) {
        final ItemStack tool = player.getInventory() == null ? null : player.getInventory().getItemInMainHand();
        return new BlockBreakContext(
            snapshot(block),
            actorSnapshot(player),
            itemSnapshot(tool),
            player.getGameMode() == GameMode.CREATIVE,
            dropItems
        );
    }

    static @NotNull BlockInteractContext interactContext(
        final PlayerInteractEvent event,
        final Block block
    ) {
        return new BlockInteractContext(
            snapshot(block),
            actorSnapshot(event.getPlayer()),
            itemSnapshot(event.getItem()),
            event.getAction().name(),
            event.getHand() == null ? "UNKNOWN" : event.getHand().name()
        );
    }

    private static String worldKey(@Nullable final org.bukkit.World world) {
        if (world == null) {
            return "unknown";
        }
        if (world.getKey() != null) {
            return world.getKey().toString();
        }
        return world.getName();
    }

    private static boolean canBuild(final Player player, final Block target) {
        // Mirror a light permission check; full region plugins still see interact/place events.
        return player.getGameMode() != GameMode.ADVENTURE || player.hasPermission("minecraft.admin.command_block");
    }

    private static void consumeOne(
        final Player player,
        final ItemStack hand,
        final @Nullable EquipmentSlot slot
    ) {
        consumeAmount(player, hand, slot, 1);
    }

    private static void consumeAmount(
        final Player player,
        final ItemStack hand,
        final @Nullable EquipmentSlot slot,
        final int amount
    ) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (amount <= 0 || hand.getAmount() < amount) {
            throw new IllegalArgumentException("cannot consume " + amount + " from stack of " + hand.getAmount());
        }
        final int remaining = hand.getAmount() - amount;
        if (remaining == 0) {
            // Zero the hand stack first so provenance afterHandConsume sees empty.
            hand.setAmount(0);
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(ItemStack.empty());
            } else {
                player.getInventory().setItemInMainHand(ItemStack.empty());
            }
        } else {
            hand.setAmount(remaining);
        }
    }
}
