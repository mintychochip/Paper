package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Position;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class CustomPlantLifecycleTest {

    @Test
    void defaultPlanClaimsCustomPlantWithoutMutatingCarrier() {
        final Block block = mock(Block.class);
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:lifecycle_plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();

        assertFalse(CustomPlantLifecycle.applyPlan(block, definition, PlantGrowthResult.defaults()));
        verify(block, never()).setBlockData(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void statePlanRejectsAReplacementWithTheWrongCarrier() {
        final Block block = mock(Block.class);
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:lifecycle_plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();
        final PlantGrowthResult result = PlantGrowthResult.handled(
            PlantGrowthPlan.state(new dev.mintychochip.behavior.BlockDataView(
                Material.OAK_LOG,
                "minecraft:oak_log"
            ))
        );

        assertThrows(IllegalArgumentException.class, () ->
            CustomPlantLifecycle.applyPlan(block, definition, result));
        verify(block, never()).setBlockData(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void structurePlanRejectsSerializedDataWithWrongMaterialBeforeMutation() {
        final Block block = mock(Block.class);
        when(block.getRelative(0, 0, 0)).thenReturn(block);
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:lifecycle_plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();
        final PlantGrowthResult result = PlantGrowthResult.handled(
            PlantGrowthPlan.structure(java.util.List.of(new PlantBlockChange(
                0,
                0,
                0,
                new dev.mintychochip.behavior.BlockDataView(Material.WHEAT, "minecraft:oak_log")
            )))
        );

        assertThrows(IllegalArgumentException.class, () ->
            CustomPlantLifecycle.applyPlan(block, definition, result));
        verify(block, never()).getState();
    }

    @Test
    void structurePlanRejectsTargetsOutsideWorldBounds() {
        final Block block = mock(Block.class);
        final Block target = mock(Block.class);
        final World world = mock(World.class);
        when(block.getRelative(0, -1, 0)).thenReturn(target);
        when(target.getY()).thenReturn(-1);
        when(target.getWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:lifecycle_plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();
        final PlantGrowthResult result = PlantGrowthResult.handled(
            PlantGrowthPlan.structure(java.util.List.of(new PlantBlockChange(
                0,
                -1,
                0,
                new dev.mintychochip.behavior.BlockDataView(Material.OAK_LOG, "minecraft:oak_log")
            )))
        );

        assertThrows(IllegalArgumentException.class, () ->
            CustomPlantLifecycle.applyPlan(block, definition, result));
        verify(target, never()).getState();
    }

    @Test
    void receiverFailureIsLoggedAndClaimsOperation() {
        final Block block = mock(Block.class);
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:lifecycle_plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .plantBehavior(CustomPlantBehavior.builder()
                .onGrowth(context -> {
                    throw new IllegalStateException("receiver failed");
                })
                .build())
            .build();

        assertTrue(CustomPlantLifecycle.applyGrowth(block, definition, sampleContext()));
        verify(block, never()).getState();
    }

    private static PlantGrowthContext sampleContext() {
        final BlockDataView data = new BlockDataView(Material.WHEAT, "minecraft:wheat[age=0]");
        return new PlantGrowthContext(
            new BlockView(
                new Position("minecraft:overworld", 0.0, 64.0, 0.0),
                Material.WHEAT,
                data,
                Optional.empty()
            ),
            data,
            PlantGrowthCause.RANDOM_TICK,
            OptionalInt.of(0),
            OptionalInt.of(7),
            Optional.empty(),
            Optional.empty()
        );
    }
}
