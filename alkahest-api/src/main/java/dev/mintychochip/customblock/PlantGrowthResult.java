package dev.mintychochip.customblock;

import dev.mintychochip.behavior.Decision;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Decision and immutable plan returned by a custom plant growth receiver. */
public final class PlantGrowthResult {

    private final Decision decision;
    private final PlantGrowthPlan plan;

    private PlantGrowthResult(final Decision decision, final PlantGrowthPlan plan) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.plan = Objects.requireNonNull(plan, "plan");
        if (decision == Decision.ALLOW && plan.kind() == PlantGrowthPlan.Kind.DEFAULT) {
            throw new IllegalArgumentException("an allowed plant result requires an explicit plan");
        }
        if (decision != Decision.ALLOW && plan.kind() != PlantGrowthPlan.Kind.DEFAULT) {
            throw new IllegalArgumentException("non-allowed plant result must use the default plan");
        }
    }

    public static @NotNull PlantGrowthResult defaults() {
        return new PlantGrowthResult(Decision.DEFAULT, PlantGrowthPlan.defaultPlan());
    }

    public static @NotNull PlantGrowthResult deny() {
        return new PlantGrowthResult(Decision.DENY, PlantGrowthPlan.defaultPlan());
    }

    public static @NotNull PlantGrowthResult handled(@NotNull final PlantGrowthPlan plan) {
        return new PlantGrowthResult(Decision.ALLOW, Objects.requireNonNull(plan, "plan"));
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull PlantGrowthPlan plan() {
        return this.plan;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private PlantGrowthPlan plan = PlantGrowthPlan.defaultPlan();

        private Builder() {
        }

        public @NotNull Builder decision(@NotNull final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public @NotNull Builder plan(@NotNull final PlantGrowthPlan plan) {
            this.plan = Objects.requireNonNull(plan, "plan");
            return this;
        }

        public @NotNull PlantGrowthResult build() {
            return new PlantGrowthResult(this.decision, this.plan);
        }
    }
}
