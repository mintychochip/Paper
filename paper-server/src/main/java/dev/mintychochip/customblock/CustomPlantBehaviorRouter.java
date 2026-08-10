package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Invokes custom plant receivers against immutable API snapshots and returns validated plans.
 *
 * <p>This router never mutates a Bukkit block or world. {@link CustomPlantLifecycle} retains live
 * server handles and applies the returned plan.</p>
 */
public final class CustomPlantBehaviorRouter {

    private CustomPlantBehaviorRouter() {
    }

    public static @NotNull PlantGrowthResult growthPlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final PlantGrowthContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        final PlantGrowthResult result = definition.plantBehavior().onGrowth().receive(context);
        if (result == null) {
            throw new IllegalStateException(
                "custom plant growth receiver returned null for " + definition.namespacedKey()
            );
        }
        return result;
    }
}
