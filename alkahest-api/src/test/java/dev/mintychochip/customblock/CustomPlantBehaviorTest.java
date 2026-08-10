package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.ecology.ClimateSample;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

public class CustomPlantBehaviorTest {

    @Test
    void defaultPlantBehaviorUsesExplicitDefaultFallback() {
        final PlantGrowthResult result = CustomPlantBehavior.defaults()
            .onGrowth()
            .receive(sampleContext(PlantGrowthCause.RANDOM_TICK));

        assertEquals(dev.mintychochip.behavior.Decision.DEFAULT, result.decision());
        assertEquals(PlantGrowthPlan.Kind.DEFAULT, result.plan().kind());
    }

    @Test
    void handledStateAndStructurePlansAreExplicit() {
        final BlockDataView wheat = new BlockDataView(Material.WHEAT, "minecraft:wheat[age=1]");
        final PlantGrowthPlan state = PlantGrowthPlan.state(wheat);
        final PlantGrowthPlan structure = PlantGrowthPlan.structure(
            List.of(new PlantBlockChange(
                0,
                1,
                0,
                new BlockDataView(Material.OAK_LOG, "minecraft:oak_log")
            ))
        );

        assertEquals(PlantGrowthPlan.Kind.STATE, state.kind());
        assertEquals(PlantGrowthPlan.Kind.STRUCTURE, structure.kind());
        assertInstanceOf(BlockDataView.class, state.state().orElseThrow());
    }

    @Test
    void structurePlanRejectsDuplicateOrUnboundedChanges() {
        final PlantBlockChange change = new PlantBlockChange(
            0,
            1,
            0,
            new BlockDataView(Material.OAK_LOG, "minecraft:oak_log")
        );

        assertThrows(IllegalArgumentException.class, () ->
            PlantGrowthPlan.structure(List.of(change, change)));
        assertThrows(IllegalArgumentException.class, () ->
            PlantGrowthPlan.structure(List.of(new PlantBlockChange(33, 0, 0, change.data()))));
    }

    @Test
    void handledResultRejectsDefaultPlan() {
        assertThrows(IllegalArgumentException.class, () ->
            PlantGrowthResult.handled(PlantGrowthPlan.defaultPlan()));
    }

    private static PlantGrowthContext sampleContext(final PlantGrowthCause cause) {
        final BlockDataView data = new BlockDataView(Material.WHEAT, "minecraft:wheat[age=0]");
        final BlockView block = new BlockView(
            new Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Material.WHEAT,
            data,
            Optional.of(new NamespacedKey("test", "plant"))
        );
        return new PlantGrowthContext(
            block,
            data,
            cause,
            OptionalInt.of(0),
            OptionalInt.of(7),
            Optional.empty(),
            Optional.of(new ClimateSample(0.5, "temperate"))
        );
    }
}
