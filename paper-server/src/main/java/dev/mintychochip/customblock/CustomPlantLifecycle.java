package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.ecology.ClimateSample;
import dev.mintychochip.ecology.CropEcology;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.event.block.BlockGrowEvent;
import org.jetbrains.annotations.NotNull;

/** Server-side entry points and plan application for custom plant carriers. */
public final class CustomPlantLifecycle {

    private CustomPlantLifecycle() {
    }

    /**
     * Claims a custom plant random tick before the vanilla carrier algorithm runs.
     *
     * @return {@code true} when the operation belongs to a custom plant and the caller must stop
     */
    public static boolean handleRandomTick(
        @NotNull final ServerLevel level,
        @NotNull final BlockPos pos,
        @NotNull final BlockState state,
        @NotNull final RandomSource random
    ) {
        return handle(level, pos, state, PlantGrowthCause.RANDOM_TICK);
    }

    /**
     * Claims a custom plant bonemeal operation before the vanilla carrier algorithm runs.
     *
     * @return {@code true} when the operation belongs to a custom plant and the caller must stop
     */
    public static boolean handleBonemeal(
        @NotNull final ServerLevel level,
        @NotNull final BlockPos pos,
        @NotNull final BlockState state,
        @NotNull final RandomSource random
    ) {
        return handle(level, pos, state, PlantGrowthCause.BONEMEAL);
    }

    /** Apply a receiver result and report whether it changed a Bukkit block state. */
    static boolean applyPlan(
        @NotNull final Block block,
        @NotNull final CustomBlockDefinition definition,
        @NotNull final PlantGrowthResult result
    ) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(result, "result");
        if (result.decision() != dev.mintychochip.behavior.Decision.ALLOW) {
            return false;
        }

        final PlantGrowthPlan plan = result.plan();
        return switch (plan.kind()) {
            case DEFAULT -> throw new IllegalArgumentException("allowed plant result cannot use DEFAULT plan");
            case NONE -> false;
            case STATE -> applyState(block, definition, plan);
            case STRUCTURE -> applyStructure(block, definition, plan);
        };
    }

    /**
     * Applies a claimed growth operation without allowing receiver or runtime failures to
     * re-enter vanilla growth.
     */
    static boolean applyGrowth(
        @NotNull final Block block,
        @NotNull final CustomBlockDefinition definition,
        @NotNull final PlantGrowthContext context
    ) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        try {
            final PlantGrowthResult result = CustomPlantBehaviorRouter.growthPlan(definition, context);
            applyPlan(block, definition, result);
        } catch (final RuntimeException failure) {
            logFailure(block, definition, context.cause(), failure);
        }
        return true;
    }

    private static void logFailure(
        final Block block,
        final CustomBlockDefinition definition,
        final PlantGrowthCause cause,
        final RuntimeException failure
    ) {
        Bukkit.getLogger().log(
            java.util.logging.Level.WARNING,
            "[mintychochip] custom plant " + cause + " failed for "
                + definition.namespacedKey() + " at " + block.getLocation(),
            failure
        );
    }

    private static boolean handle(
        final ServerLevel level,
        final BlockPos pos,
        final BlockState state,
        final PlantGrowthCause cause
    ) {
        final Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        final Optional<CustomBlockDefinition> definition = CustomBlocks.of(block);
        if (definition.isEmpty()) {
            return false;
        }
        final CustomBlockDefinition custom = definition.get();
        if (!(custom.host() instanceof PlantHostSpec host)) {
            return false;
        }
        if (!matchesHost(block, state, host)) {
            Bukkit.getLogger().warning(
                "[mintychochip] refusing native plant behavior for mismatched carrier "
                    + custom.namespacedKey() + " at " + block.getLocation()
            );
            return true;
        }

        try {
            return applyGrowth(block, custom, context(level, pos, block, cause));
        } catch (final RuntimeException failure) {
            logFailure(block, custom, cause, failure);
            return true;
        }
    }

    private static boolean matchesHost(
        final Block block,
        final BlockState state,
        final PlantHostSpec host
    ) {
        if (block.getType() != host.carrier()) {
            return false;
        }
        return switch (host.kind()) {
            case CROP -> state.getBlock() instanceof CropBlock;
            case SAPLING -> state.getBlock() instanceof SaplingBlock;
        };
    }

    private static PlantGrowthContext context(
        final ServerLevel level,
        final BlockPos pos,
        final Block block,
        final PlantGrowthCause cause
    ) {
        final BlockView view = CustomBlockLifecycle.snapshot(block);
        final BlockDataView currentData = view.data();
        final BlockData data = block.getBlockData();
        final OptionalInt currentStage;
        final OptionalInt maximumStage;
        if (data instanceof Ageable ageable) {
            currentStage = OptionalInt.of(ageable.getAge());
            maximumStage = OptionalInt.of(ageable.getMaximumAge());
        } else if (data instanceof Sapling sapling) {
            currentStage = OptionalInt.of(sapling.getStage());
            maximumStage = OptionalInt.of(1);
        } else {
            currentStage = OptionalInt.empty();
            maximumStage = OptionalInt.empty();
        }

        Optional<ClimateSample> climate;
        try {
            climate = Optional.of(CropEcology.sampleClimate(level, pos));
        } catch (final RuntimeException ignored) {
            climate = Optional.empty();
        }
        return new PlantGrowthContext(
            view,
            currentData,
            cause,
            currentStage,
            maximumStage,
            Optional.empty(),
            climate
        );
    }

    private static boolean applyState(
        final Block block,
        final CustomBlockDefinition definition,
        final PlantGrowthPlan plan
    ) {
        final PlantHostSpec host = requirePlantHost(definition);
        final BlockDataView view = plan.state().orElseThrow(
            () -> new IllegalArgumentException("STATE plant plan has no block data")
        );
        if (view.material() != host.carrier()) {
            throw new IllegalArgumentException("plant state plan must retain its carrier material");
        }
        final BlockData data = Bukkit.createBlockData(view.serialized());
        if (data.getMaterial() != host.carrier()) {
            throw new IllegalArgumentException("plant state plan serialized data has the wrong carrier");
        }

        final org.bukkit.block.BlockState next = block.getState();
        next.setBlockData(data);
        final BlockGrowEvent event = new BlockGrowEvent(block, next);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        if (event.getNewState().getType() != host.carrier()) {
            throw new IllegalArgumentException("growth event changed a custom plant to another carrier");
        }
        try {
            event.getNewState().update(true, false);
        } catch (final RuntimeException failure) {
            CustomBlockLifecycle.cleanupAfterGrowthFailure(block);
            throw failure;
        }
        return true;
    }

    private static boolean applyStructure(
        final Block block,
        final CustomBlockDefinition definition,
        final PlantGrowthPlan plan
    ) {
        final PlantHostSpec host = requirePlantHost(definition);
        final List<org.bukkit.block.BlockState> before = new ArrayList<>();
        final List<org.bukkit.block.BlockState> after = new ArrayList<>();
        for (final PlantBlockChange change : plan.changes()) {
            final Block target = block.getRelative(change.x(), change.y(), change.z());
            final int targetY = target.getY();
            final org.bukkit.World targetWorld = target.getWorld();
            if (targetWorld == null
                || targetY < targetWorld.getMinHeight()
                || targetY >= targetWorld.getMaxHeight()) {
                throw new IllegalArgumentException("structure target is outside the world build height");
            }
            if (change.x() == 0 && change.y() == 0 && change.z() == 0
                && change.data().material() != host.carrier()) {
                throw new IllegalArgumentException("structure origin must retain its plant carrier");
            }
            if (!(target.equals(block)) && CustomBlocks.isCustomBlock(target)) {
                throw new IllegalArgumentException("structure cannot overwrite another custom block");
            }
            final BlockData data = Bukkit.createBlockData(change.data().serialized());
            if (data.getMaterial() != change.data().material()) {
                throw new IllegalArgumentException("structure block data material does not match its snapshot material");
            }
            before.add(target.getState());
            final org.bukkit.block.BlockState next = target.getState();
            next.setBlockData(data);
            after.add(next);
        }

        try {
            for (final org.bukkit.block.BlockState next : after) {
                next.update(true, false);
            }
        } catch (final RuntimeException failure) {
            try {
                for (final org.bukkit.block.BlockState old : before) {
                    old.update(false, false);
                }
            } finally {
                CustomBlockLifecycle.cleanupAfterGrowthFailure(block);
            }
            throw failure;
        }
        return !after.isEmpty();
    }

    private static PlantHostSpec requirePlantHost(final CustomBlockDefinition definition) {
        if (!(definition.host() instanceof PlantHostSpec host)) {
            throw new IllegalArgumentException("definition is not a plant host: " + definition.namespacedKey());
        }
        return host;
    }
}
