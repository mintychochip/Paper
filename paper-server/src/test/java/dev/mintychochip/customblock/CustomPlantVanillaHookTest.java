package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@Normal
class CustomPlantVanillaHookTest {

    @Test
    void cropRandomTickDelegatesToCustomPlantLifecycleBeforeNativeChecks() {
        final ServerLevel level = mock(ServerLevel.class);
        final BlockPos pos = new BlockPos(3, 64, 7);
        final RandomSource random = mock(RandomSource.class);
        final CropBlock crop = (CropBlock) Blocks.WHEAT;
        final BlockState state = crop.defaultBlockState();

        try (MockedStatic<CustomPlantLifecycle> lifecycle = mockStatic(CustomPlantLifecycle.class)) {
            lifecycle.when(() -> CustomPlantLifecycle.handleRandomTick(level, pos, state, random))
                .thenReturn(true);

            invokeCropRandomTick(crop, state, level, pos, random);

            lifecycle.verify(() -> CustomPlantLifecycle.handleRandomTick(level, pos, state, random));
        }
    }

    @Test
    void maxAgeCropRemainsRandomlyTickingForCustomReceiver() {
        final CropBlock crop = (CropBlock) Blocks.WHEAT;
        final BlockState state = crop.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);

        assertTrue(cropIsRandomlyTicking(crop, state));
    }

    @Test
    void cropBonemealDelegatesToCustomPlantLifecycleBeforeNativeGrowth() {
        final ServerLevel level = mock(ServerLevel.class);
        final BlockPos pos = new BlockPos(3, 64, 7);
        final RandomSource random = mock(RandomSource.class);
        when(level.getRandom()).thenReturn(random);
        final CropBlock crop = (CropBlock) Blocks.WHEAT;
        final BlockState state = crop.defaultBlockState();

        try (MockedStatic<CustomPlantLifecycle> lifecycle = mockStatic(CustomPlantLifecycle.class)) {
            lifecycle.when(() -> CustomPlantLifecycle.handleBonemeal(level, pos, state, random))
                .thenReturn(true);

            crop.growCrops(level, pos, state);

            lifecycle.verify(() -> CustomPlantLifecycle.handleBonemeal(level, pos, state, random));
        }
    }

    @Test
    void saplingBonemealDelegatesToCustomPlantLifecycleBeforeNativeTreeGrowth() {
        final ServerLevel level = mock(ServerLevel.class);
        final BlockPos pos = new BlockPos(3, 64, 7);
        final RandomSource random = mock(RandomSource.class);
        final SaplingBlock sapling = (SaplingBlock) Blocks.OAK_SAPLING;
        final BlockState state = sapling.defaultBlockState();

        try (MockedStatic<CustomPlantLifecycle> lifecycle = mockStatic(CustomPlantLifecycle.class)) {
            lifecycle.when(() -> CustomPlantLifecycle.handleBonemeal(level, pos, state, random))
                .thenReturn(true);

            sapling.performBonemeal(level, random, pos, state);

            lifecycle.verify(() -> CustomPlantLifecycle.handleBonemeal(level, pos, state, random));
        }
    }

    @Test
    void saplingRandomTickDelegatesToCustomPlantLifecycleBeforeNativeTreeGrowth() {
        final ServerLevel level = mock(ServerLevel.class);
        final BlockPos pos = new BlockPos(3, 64, 7);
        final RandomSource random = mock(RandomSource.class);
        final SaplingBlock sapling = (SaplingBlock) Blocks.OAK_SAPLING;
        final BlockState state = sapling.defaultBlockState();

        try (MockedStatic<CustomPlantLifecycle> lifecycle = mockStatic(CustomPlantLifecycle.class)) {
            lifecycle.when(() -> CustomPlantLifecycle.handleRandomTick(level, pos, state, random))
                .thenReturn(true);

            invokeSaplingRandomTick(sapling, state, level, pos, random);

            lifecycle.verify(() -> CustomPlantLifecycle.handleRandomTick(level, pos, state, random));
        }
    }

    private static void invokeCropRandomTick(
        final CropBlock crop,
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        try {
            final Method method = CropBlock.class.getDeclaredMethod(
                "randomTick",
                BlockState.class,
                ServerLevel.class,
                BlockPos.class,
                RandomSource.class
            );
            method.setAccessible(true);
            method.invoke(crop, state, level, pos, random);
        } catch (final ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean cropIsRandomlyTicking(final CropBlock crop, final BlockState state) {
        try {
            final Method method = CropBlock.class.getDeclaredMethod("isRandomlyTicking", BlockState.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(crop, state);
        } catch (final ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void invokeSaplingRandomTick(
        final SaplingBlock sapling,
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        try {
            final Method method = SaplingBlock.class.getDeclaredMethod(
                "randomTick",
                BlockState.class,
                ServerLevel.class,
                BlockPos.class,
                RandomSource.class
            );
            method.setAccessible(true);
            method.invoke(sapling, state, level, pos, random);
        } catch (final ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
