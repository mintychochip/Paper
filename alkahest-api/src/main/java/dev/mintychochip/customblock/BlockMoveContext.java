package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Position;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured for piston movement policy.
 */
public record BlockMoveContext(
    @NotNull BlockView block,
    @NotNull Position from,
    @NotNull Position to,
    @NotNull String direction
) {

    public BlockMoveContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (direction == null || direction.isBlank()) {
            throw new IllegalArgumentException("direction must not be blank");
        }
    }
}
