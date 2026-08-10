package dev.mintychochip.customblock;

import dev.mintychochip.behavior.Decision;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom-block piston movement receiver.
 */
public final class MovementResult {

    private final Decision decision;
    private final CleanupPlan cleanup;

    private MovementResult(final Decision decision, final CleanupPlan cleanup) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public static @NotNull MovementResult defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull CleanupPlan cleanup() {
        return this.cleanup;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private CleanupPlan cleanup = CleanupPlan.defaultPlan();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder cleanup(final CleanupPlan cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
            return this;
        }

        public @NotNull MovementResult build() {
            return new MovementResult(this.decision, this.cleanup);
        }
    }
}
