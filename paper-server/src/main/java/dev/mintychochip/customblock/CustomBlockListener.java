package dev.mintychochip.customblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit wiring for custom-block place / break. Additive — does not replace Material APIs.
 */
public final class CustomBlockListener implements Listener {

    /** Break event → immutable definition/behavior plan for MONITOR application (main thread). */
    private final Map<Location, CustomBlockLifecycle.BreakPreparation> pendingBreakDrops = new HashMap<>();
    /** Piston plans captured before vanilla movement. */
    private final Map<BlockPistonEvent, List<CustomBlockLifecycle.MovementPreparation>> pendingPistonMoves =
        new IdentityHashMap<>();
    /** Block-explosion plans captured before the event finalizes its block list. */
    private final Map<BlockExplodeEvent, List<CustomBlockLifecycle.ExplosionPreparation>> pendingBlockExplosions =
        new IdentityHashMap<>();
    /** Entity-explosion plans captured before the event finalizes its block list. */
    private final Map<EntityExplodeEvent, List<CustomBlockLifecycle.ExplosionPreparation>> pendingEntityExplosions =
        new IdentityHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        CustomBlockLifecycle.handlePlace(event);
    }

    /**
     * Route custom-block interaction and manual placement without replacing Bukkit events.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractPlace(final PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (CustomBlockLifecycle.handleInteract(event)) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        final ItemStack item = event.getItem();
        if (!CustomBlocks.isCustomBlockItem(item)) {
            return;
        }
        final Optional<CustomBlockDefinition> def = CustomBlocks.of(item);
        if (def.isEmpty() || def.get().itemMaterial().isBlock()) {
            // Placeable materials: vanilla place + onPlace handles them.
            return;
        }
        final boolean placed = CustomBlockLifecycle.handleManualPlace(
            event.getPlayer(),
            item,
            event.getClickedBlock(),
            event.getBlockFace(),
            event.getHand()
        );
        if (placed) {
            // Deny item-use so the client does not play the "use" animation for non-block bases.
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakPrepare(final BlockBreakEvent event) {
        final Optional<CustomBlockLifecycle.BreakPreparation> preparation =
            CustomBlockLifecycle.prepareBreakWithBehavior(event);
        preparation.ifPresent(value -> this.pendingBreakDrops.put(blockKey(event.getBlock()), value));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakFinish(final BlockBreakEvent event) {
        final CustomBlockLifecycle.BreakPreparation preparation =
            this.pendingBreakDrops.remove(blockKey(event.getBlock()));
        if (preparation == null) {
            CustomBlockLifecycle.finishBreak(event, null);
        } else {
            CustomBlockLifecycle.finishBreak(
                event,
                preparation.definition(),
                preparation.plan()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreakCancelledCleanup(final BlockBreakEvent event) {
        if (event.isCancelled()) {
            this.pendingBreakDrops.remove(blockKey(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        preparePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        preparePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPistonExtendFinish(final BlockPistonExtendEvent event) {
        finishPiston(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPistonRetractFinish(final BlockPistonRetractEvent event) {
        finishPiston(event);
    }

    private void finishPiston(final BlockPistonEvent event) {
        final List<CustomBlockLifecycle.MovementPreparation> preparations =
            this.pendingPistonMoves.remove(event);
        if (preparations == null || event.isCancelled()) {
            return;
        }
        for (final CustomBlockLifecycle.MovementPreparation preparation : preparations) {
            CustomBlockLifecycle.finishMove(preparation);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        final List<CustomBlockLifecycle.ExplosionPreparation> preparations = new ArrayList<>();
        routeExplosion(
            event.blockList(),
            CustomBlockLifecycle.position(event.getBlock()),
            false,
            preparations
        );
        if (!preparations.isEmpty()) {
            this.pendingBlockExplosions.put(event, preparations);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockExplodeFinish(final BlockExplodeEvent event) {
        finishExplosion(event, this.pendingBlockExplosions.remove(event));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        final List<CustomBlockLifecycle.ExplosionPreparation> preparations = new ArrayList<>();
        routeExplosion(
            event.blockList(),
            CustomBlockLifecycle.position(event.getLocation()),
            true,
            preparations
        );
        if (!preparations.isEmpty()) {
            this.pendingEntityExplosions.put(event, preparations);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityExplodeFinish(final EntityExplodeEvent event) {
        finishExplosion(event, this.pendingEntityExplosions.remove(event));
    }

    private void preparePiston(final BlockPistonEvent event, final List<Block> blocks) {
        final List<CustomBlockLifecycle.MovementPreparation> preparations = new ArrayList<>();
        for (final Block block : blocks) {
            final Optional<CustomBlockLifecycle.MovementPreparation> preparation =
                CustomBlockLifecycle.prepareMove(block, event.getDirection());
            if (preparation.isEmpty()) {
                continue;
            }
            final CustomBlockLifecycle.MovementPreparation value = preparation.get();
            if (value.plan().decision() != dev.mintychochip.behavior.Decision.ALLOW) {
                event.setCancelled(true);
                return;
            }
            preparations.add(value);
        }
        if (!preparations.isEmpty()) {
            this.pendingPistonMoves.put(event, preparations);
        }
    }

    private static void routeExplosion(
        final List<Block> blocks,
        final dev.mintychochip.behavior.Position source,
        final boolean entitySource,
        final List<CustomBlockLifecycle.ExplosionPreparation> preparations
    ) {
        for (final Block block : List.copyOf(blocks)) {
            final Optional<CustomBlockLifecycle.ExplosionPreparation> preparation =
                CustomBlockLifecycle.prepareExplosion(block, source, entitySource);
            if (preparation.isEmpty()) {
                continue;
            }
            final CustomBlockLifecycle.ExplosionPreparation value = preparation.get();
            if (value.plan().decision() == dev.mintychochip.behavior.Decision.DENY) {
                blocks.remove(block);
            } else {
                preparations.add(value);
            }
        }
    }

    private static void finishExplosion(
        final Object event,
        final List<CustomBlockLifecycle.ExplosionPreparation> preparations
    ) {
        if (preparations == null) {
            return;
        }
        final List<Block> blocks = event instanceof BlockExplodeEvent blockEvent
            ? blockEvent.blockList()
            : ((EntityExplodeEvent) event).blockList();
        final boolean cancelled = event instanceof BlockExplodeEvent blockEvent
            ? blockEvent.isCancelled()
            : ((EntityExplodeEvent) event).isCancelled();
        if (cancelled) {
            return;
        }
        for (final CustomBlockLifecycle.ExplosionPreparation preparation : preparations) {
            if (blocks.contains(preparation.block())) {
                CustomBlockLifecycle.finishExplosion(preparation);
            }
        }
    }

    private static @NotNull Location blockKey(final Block block) {
        final Location loc = block.getLocation();
        loc.setX(loc.getBlockX());
        loc.setY(loc.getBlockY());
        loc.setZ(loc.getBlockZ());
        return loc;
    }
}
