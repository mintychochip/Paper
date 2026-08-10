package dev.mintychochip.customblock;

import dev.mintychochip.behavior.Decision;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom-block explosion receiver.
 */
public final class ExplosionResult {

    private final Decision decision;
    private final DropPlan drops;
    private final CleanupPlan cleanup;

    private ExplosionResult(final Decision decision, final DropPlan drops, final CleanupPlan cleanup) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public static @NotNull ExplosionResult defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull DropPlan drops() {
        return this.drops;
    }

    public @NotNull CleanupPlan cleanup() {
        return this.cleanup;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private DropPlan drops = DropPlan.defaultPlan();
        private CleanupPlan cleanup = CleanupPlan.defaultPlan();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder drops(final DropPlan drops) {
            this.drops = Objects.requireNonNull(drops, "drops");
            return this;
        }

        public Builder cleanup(final CleanupPlan cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
            return this;
        }

        public @NotNull ExplosionResult build() {
            return new ExplosionResult(this.decision, this.drops, this.cleanup);
        }
    }
}
