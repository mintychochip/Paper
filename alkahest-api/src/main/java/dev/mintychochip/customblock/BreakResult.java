package dev.mintychochip.customblock;

import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom-block break receiver.
 */
public final class BreakResult {

    private final Decision decision;
    private final DropPlan drops;
    private final ValueOverride<Integer> experience;
    private final CleanupPlan cleanup;

    private BreakResult(
        final Decision decision,
        final DropPlan drops,
        final ValueOverride<Integer> experience,
        final CleanupPlan cleanup
    ) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        if (experience instanceof ValueOverride.Value<?> value && ((Integer) value.value()) < 0) {
            throw new IllegalArgumentException("experience must not be negative");
        }
    }

    public static @NotNull BreakResult defaults() {
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

    public @NotNull ValueOverride<Integer> experience() {
        return this.experience;
    }

    public @NotNull CleanupPlan cleanup() {
        return this.cleanup;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private DropPlan drops = DropPlan.defaultPlan();
        private ValueOverride<Integer> experience = ValueOverride.useDefault();
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

        public Builder experience(final ValueOverride<Integer> experience) {
            this.experience = Objects.requireNonNull(experience, "experience");
            return this;
        }

        public Builder cleanup(final CleanupPlan cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
            return this;
        }

        public @NotNull BreakResult build() {
            return new BreakResult(this.decision, this.drops, this.experience, this.cleanup);
        }
    }
}
