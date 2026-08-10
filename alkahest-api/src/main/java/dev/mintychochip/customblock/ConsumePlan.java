package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit item-consumption policy returned by a custom block receiver.
 */
public final class ConsumePlan {

    public enum Kind {
        DEFAULT,
        NONE,
        EXPLICIT
    }

    private final Kind kind;
    private final int amount;

    private ConsumePlan(final Kind kind, final int amount) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.amount = amount;
        if (kind == Kind.EXPLICIT && amount <= 0) {
            throw new IllegalArgumentException("explicit consumption must be positive");
        }
        if (kind != Kind.EXPLICIT && amount != 0) {
            throw new IllegalArgumentException("non-explicit consumption cannot contain an amount");
        }
    }

    public static @NotNull ConsumePlan defaultPlan() {
        return new ConsumePlan(Kind.DEFAULT, 0);
    }

    public static @NotNull ConsumePlan none() {
        return new ConsumePlan(Kind.NONE, 0);
    }

    public static @NotNull ConsumePlan explicit(final int amount) {
        return new ConsumePlan(Kind.EXPLICIT, amount);
    }

    public @NotNull Kind kind() {
        return this.kind;
    }

    /**
     * Explicit amount, or zero for {@link Kind#DEFAULT} and {@link Kind#NONE}.
     */
    public int amount() {
        return this.amount;
    }
}
