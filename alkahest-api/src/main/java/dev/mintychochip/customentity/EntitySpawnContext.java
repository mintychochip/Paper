package dev.mintychochip.customentity;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.Position;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured before a custom entity carrier is spawned.
 */
public record EntitySpawnContext(
    @NotNull Position position,
    @NotNull Optional<ActorView> actor
) {

    public EntitySpawnContext {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(actor, "actor");
    }
}
