package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Invokes custom block receivers against immutable snapshots and returns validated plans.
 *
 * <p>This class never mutates Bukkit events or world state. {@link CustomBlockLifecycle} retains
 * live handles and applies the returned plans.</p>
 */
public final class CustomBlockBehaviorRouter {

    private CustomBlockBehaviorRouter() {
    }

    public static @NotNull PlacementResult placePlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final BlockPlaceContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return requireResult(definition.behavior().onPlace().receive(context), "placement", definition);
    }

    public static @NotNull BreakResult breakPlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final BlockBreakContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return requireResult(definition.behavior().onBreak().receive(context), "break", definition);
    }

    public static @NotNull InteractionResult interactPlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final BlockInteractContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return requireResult(definition.behavior().onInteract().receive(context), "interaction", definition);
    }

    public static @NotNull MovementResult movePlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final BlockMoveContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return requireResult(definition.behavior().onMove().receive(context), "movement", definition);
    }

    public static @NotNull ExplosionResult explodePlan(
        @NotNull final CustomBlockDefinition definition,
        @NotNull final BlockExplodeContext context
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return requireResult(definition.behavior().onExplode().receive(context), "explosion", definition);
    }

    private static <T> @NotNull T requireResult(
        final T result,
        final String operation,
        final CustomBlockDefinition definition
    ) {
        return Objects.requireNonNull(
            result,
            () -> "custom block " + operation + " receiver returned null for " + definition.namespacedKey()
        );
    }
}
