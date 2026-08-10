package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ItemStackView;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit drop policy returned by a custom block break or explosion receiver.
 */
public final class DropPlan {

    public enum Kind {
        DEFAULT,
        NONE,
        EXPLICIT
    }

    private final Kind kind;
    private final List<ItemStackView> items;

    private DropPlan(final Kind kind, final List<ItemStackView> items) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.items = List.copyOf(items);
        if (kind != Kind.EXPLICIT && !this.items.isEmpty()) {
            throw new IllegalArgumentException("non-explicit drop plans cannot contain items");
        }
    }

    public static @NotNull DropPlan defaultPlan() {
        return new DropPlan(Kind.DEFAULT, List.of());
    }

    public static @NotNull DropPlan none() {
        return new DropPlan(Kind.NONE, List.of());
    }

    public static @NotNull DropPlan explicit(@NotNull final List<? extends ItemStackView> items) {
        Objects.requireNonNull(items, "items");
        return new DropPlan(Kind.EXPLICIT, List.copyOf(items));
    }

    public static @NotNull DropPlan explicit(@NotNull final ItemStackView item, final ItemStackView... additional) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(additional, "additional");
        final java.util.ArrayList<ItemStackView> items = new java.util.ArrayList<>(additional.length + 1);
        items.add(item);
        for (final ItemStackView extra : additional) {
            items.add(Objects.requireNonNull(extra, "additional item"));
        }
        return explicit(items);
    }

    public @NotNull Kind kind() {
        return this.kind;
    }

    public @NotNull List<ItemStackView> items() {
        return this.items;
    }
}
