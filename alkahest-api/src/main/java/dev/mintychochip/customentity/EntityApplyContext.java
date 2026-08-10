package dev.mintychochip.customentity;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.EntityView;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured before a custom entity presentation is applied.
 */
public record EntityApplyContext(
    @NotNull EntityView entity,
    @NotNull Optional<ActorView> actor,
    @NotNull Optional<BlockDataView> currentBlockData
) {

    public EntityApplyContext {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(currentBlockData, "currentBlockData");
    }
}
