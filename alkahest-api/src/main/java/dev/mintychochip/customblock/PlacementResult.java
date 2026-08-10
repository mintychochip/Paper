package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom-block placement receiver.
 */
public final class PlacementResult {

    private final Decision decision;
    private final ConsumePlan consumeItem;
    private final CleanupPlan cleanup;
    private final ValueOverride<BlockDataView> blockData;

    private PlacementResult(
        final Decision decision,
        final ConsumePlan consumeItem,
        final CleanupPlan cleanup,
        final ValueOverride<BlockDataView> blockData
    ) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.consumeItem = Objects.requireNonNull(consumeItem, "consumeItem");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    public static @NotNull PlacementResult defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull ConsumePlan consumeItem() {
        return this.consumeItem;
    }

    public @NotNull CleanupPlan cleanup() {
        return this.cleanup;
    }

    public @NotNull ValueOverride<BlockDataView> blockData() {
        return this.blockData;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private ConsumePlan consumeItem = ConsumePlan.defaultPlan();
        private CleanupPlan cleanup = CleanupPlan.defaultPlan();
        private ValueOverride<BlockDataView> blockData = ValueOverride.useDefault();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder consumeItem(final ConsumePlan consumeItem) {
            this.consumeItem = Objects.requireNonNull(consumeItem, "consumeItem");
            return this;
        }

        public Builder cleanup(final CleanupPlan cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
            return this;
        }

        public Builder blockData(final ValueOverride<BlockDataView> blockData) {
            this.blockData = Objects.requireNonNull(blockData, "blockData");
            return this;
        }

        public @NotNull PlacementResult build() {
            return new PlacementResult(this.decision, this.consumeItem, this.cleanup, this.blockData);
        }
    }
}
