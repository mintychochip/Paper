package dev.mintychochip.customblock;

import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom-block interaction receiver.
 */
public final class InteractionResult {

    private final Decision decision;
    private final ValueOverride<Boolean> useItem;
    private final ValueOverride<Boolean> useBlock;
    private final ConsumePlan consumeItem;

    private InteractionResult(
        final Decision decision,
        final ValueOverride<Boolean> useItem,
        final ValueOverride<Boolean> useBlock,
        final ConsumePlan consumeItem
    ) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.useItem = Objects.requireNonNull(useItem, "useItem");
        this.useBlock = Objects.requireNonNull(useBlock, "useBlock");
        this.consumeItem = Objects.requireNonNull(consumeItem, "consumeItem");
    }

    public static @NotNull InteractionResult defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull ValueOverride<Boolean> useItem() {
        return this.useItem;
    }

    public @NotNull ValueOverride<Boolean> useBlock() {
        return this.useBlock;
    }

    public @NotNull ConsumePlan consumeItem() {
        return this.consumeItem;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private ValueOverride<Boolean> useItem = ValueOverride.useDefault();
        private ValueOverride<Boolean> useBlock = ValueOverride.useDefault();
        private ConsumePlan consumeItem = ConsumePlan.defaultPlan();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder useItem(final ValueOverride<Boolean> useItem) {
            this.useItem = Objects.requireNonNull(useItem, "useItem");
            return this;
        }

        public Builder useBlock(final ValueOverride<Boolean> useBlock) {
            this.useBlock = Objects.requireNonNull(useBlock, "useBlock");
            return this;
        }

        public Builder consumeItem(final ConsumePlan consumeItem) {
            this.consumeItem = Objects.requireNonNull(consumeItem, "consumeItem");
            return this;
        }

        public @NotNull InteractionResult build() {
            return new InteractionResult(this.decision, this.useItem, this.useBlock, this.consumeItem);
        }
    }
}
