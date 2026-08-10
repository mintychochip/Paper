package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.ItemStackView;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured for custom-block placement.
 */
public record BlockPlaceContext(
    @NotNull BlockView block,
    @NotNull Optional<ActorView> actor,
    @NotNull Optional<ItemStackView> item,
    boolean canBuild
) {

    public BlockPlaceContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(item, "item");
    }
}
