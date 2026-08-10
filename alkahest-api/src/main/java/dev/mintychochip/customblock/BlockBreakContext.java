package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.ItemStackView;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured for custom-block breaking.
 */
public record BlockBreakContext(
    @NotNull BlockView block,
    @NotNull Optional<ActorView> actor,
    @NotNull Optional<ItemStackView> tool,
    boolean creative,
    boolean dropItems
) {

    public BlockBreakContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(tool, "tool");
    }
}
