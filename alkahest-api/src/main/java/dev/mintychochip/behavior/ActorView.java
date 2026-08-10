package dev.mintychochip.behavior;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of an actor participating in a behavior operation.
 */
public record ActorView(
    @NotNull UUID uniqueId,
    @NotNull Position position,
    @NotNull Optional<String> name,
    boolean player
) {

    public ActorView {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(name, "name");
    }
}
