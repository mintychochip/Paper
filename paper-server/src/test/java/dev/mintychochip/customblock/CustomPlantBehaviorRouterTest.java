package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.Position;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class CustomPlantBehaviorRouterTest {

    @Test
    void growthReceiverRunsOnceAndReturnsItsPlan() {
        final AtomicInteger calls = new AtomicInteger();
        final CustomBlockDefinition definition = cropDefinition(
            CustomPlantBehavior.builder()
                .onGrowth(context -> {
                    calls.incrementAndGet();
                    return PlantGrowthResult.handled(PlantGrowthPlan.none());
                })
                .build()
        );

        final PlantGrowthResult result = CustomPlantBehaviorRouter.growthPlan(
            definition,
            sampleContext(PlantGrowthCause.RANDOM_TICK)
        );

        assertEquals(1, calls.get());
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(PlantGrowthPlan.Kind.NONE, result.plan().kind());
    }

    @Test
    void nullGrowthResultsAreRejected() {
        final CustomBlockDefinition definition = cropDefinition(
            CustomPlantBehavior.builder().onGrowth(context -> null).build()
        );

        assertThrows(IllegalStateException.class, () ->
            CustomPlantBehaviorRouter.growthPlan(definition, sampleContext(PlantGrowthCause.BONEMEAL)));
    }

    private static CustomBlockDefinition cropDefinition(final CustomPlantBehavior behavior) {
        return CustomBlockDefinition.builder("test:plant_router")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .plantBehavior(behavior)
            .build();
    }

    private static PlantGrowthContext sampleContext(final PlantGrowthCause cause) {
        final BlockDataView data = new BlockDataView(Material.WHEAT, "minecraft:wheat[age=0]");
        return new PlantGrowthContext(
            new BlockView(
                new Position("minecraft:overworld", 0.0, 64.0, 0.0),
                Material.WHEAT,
                data,
                Optional.empty()
            ),
            data,
            cause,
            OptionalInt.of(0),
            OptionalInt.of(7),
            Optional.empty(),
            Optional.empty()
        );
    }
}
