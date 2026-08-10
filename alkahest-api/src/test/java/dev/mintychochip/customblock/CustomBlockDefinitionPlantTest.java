package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.mintychochip.behavior.Decision;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class CustomBlockDefinitionPlantTest {

    @Test
    void definitionDefaultsPlantBehaviorWithoutChangingBlockBehavior() {
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();

        assertNotNull(definition.behavior());
        assertNotNull(definition.plantBehavior());
        final PlantGrowthResult result = definition.plantBehavior().onGrowth().receive(sampleContext());
        assertEquals(Decision.DEFAULT, result.decision());
        assertEquals(PlantGrowthPlan.Kind.DEFAULT, result.plan().kind());
    }

    @Test
    void definitionRetainsConfiguredPlantBehavior() {
        final CustomPlantBehavior behavior = CustomPlantBehavior.builder()
            .onGrowth(context -> PlantGrowthResult.handled(PlantGrowthPlan.none()))
            .build();
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:plant")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .plantBehavior(behavior)
            .build();

        assertSame(behavior, definition.plantBehavior());
        assertEquals(Decision.ALLOW, definition.plantBehavior().onGrowth().receive(sampleContext()).decision());
    }

    private static PlantGrowthContext sampleContext() {
        final var data = new dev.mintychochip.behavior.BlockDataView(
            Material.WHEAT,
            "minecraft:wheat[age=0]"
        );
        final var block = new dev.mintychochip.behavior.BlockView(
            new dev.mintychochip.behavior.Position("minecraft:overworld", 0.0, 64.0, 0.0),
            Material.WHEAT,
            data,
            Optional.empty()
        );
        return new PlantGrowthContext(
            block,
            data,
            PlantGrowthCause.BONEMEAL,
            OptionalInt.of(0),
            OptionalInt.of(7),
            Optional.empty(),
            Optional.empty()
        );
    }
}
