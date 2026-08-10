package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.behavior.ItemStackView;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured for custom-block interaction.
 */
public record BlockInteractContext(
    @NotNull BlockView block,
    @NotNull Optional<ActorView> actor,
    @NotNull Optional<ItemStackView> item,
    @NotNull String action,
    @NotNull String hand
) {

    public BlockInteractContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(item, "item");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (hand == null || hand.isBlank()) {
            throw new IllegalArgumentException("hand must not be blank");
        }
    }
}
