package dev.mintychochip.customentity;

import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.ValueOverride;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable plan returned by a custom entity apply receiver.
 */
public final class EntityApplyResult {

    private final Decision decision;
    private final ValueOverride<BlockDataView> blockData;

    private EntityApplyResult(final Decision decision, final ValueOverride<BlockDataView> blockData) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    public static @NotNull EntityApplyResult defaults() {
        return builder().build();
    }

    public static @NotNull EntityApplyResult allow() {
        return builder().decision(Decision.ALLOW).build();
    }

    public static @NotNull EntityApplyResult deny() {
        return builder().decision(Decision.DENY).build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull ValueOverride<BlockDataView> blockData() {
        return this.blockData;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private ValueOverride<BlockDataView> blockData = ValueOverride.useDefault();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder blockData(final ValueOverride<BlockDataView> blockData) {
            this.blockData = Objects.requireNonNull(blockData, "blockData");
            return this;
        }

        public @NotNull EntityApplyResult build() {
            return new EntityApplyResult(this.decision, this.blockData);
        }
    }
}
