package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit identity/display cleanup policy returned by a custom block receiver.
 */
public final class CleanupPlan {

    public enum Kind {
        DEFAULT,
        NONE,
        EXPLICIT
    }

    private final Kind kind;
    private final boolean clearIdentity;
    private final boolean despawnDisplay;

    private CleanupPlan(final Kind kind, final boolean clearIdentity, final boolean despawnDisplay) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.clearIdentity = clearIdentity;
        this.despawnDisplay = despawnDisplay;
        if (kind == Kind.EXPLICIT && !clearIdentity && !despawnDisplay) {
            throw new IllegalArgumentException("explicit cleanup must select an action");
        }
        if (kind != Kind.EXPLICIT && (clearIdentity || despawnDisplay)) {
            throw new IllegalArgumentException("non-explicit cleanup cannot contain actions");
        }
    }

    public static @NotNull CleanupPlan defaultPlan() {
        return new CleanupPlan(Kind.DEFAULT, false, false);
    }

    public static @NotNull CleanupPlan none() {
        return new CleanupPlan(Kind.NONE, false, false);
    }

    public static @NotNull CleanupPlan explicit(final boolean clearIdentity, final boolean despawnDisplay) {
        return new CleanupPlan(Kind.EXPLICIT, clearIdentity, despawnDisplay);
    }

    public @NotNull Kind kind() {
        return this.kind;
    }

    public boolean clearIdentity() {
        return this.clearIdentity;
    }

    public boolean despawnDisplay() {
        return this.despawnDisplay;
    }
}
