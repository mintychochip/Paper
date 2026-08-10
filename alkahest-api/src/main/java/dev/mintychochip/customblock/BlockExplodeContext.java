package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.Position;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured for block or entity explosion handling.
 */
public record BlockExplodeContext(
    @NotNull BlockView block,
    @NotNull Position source,
    boolean entitySource
) {

    public BlockExplodeContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(source, "source");
    }
}
