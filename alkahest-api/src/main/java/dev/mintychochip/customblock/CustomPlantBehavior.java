package dev.mintychochip.customblock;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Immutable growth behavior owned by a custom plant definition. */
public final class CustomPlantBehavior {

    @FunctionalInterface
    public interface GrowthReceiver {
        @NotNull PlantGrowthResult receive(@NotNull PlantGrowthContext context);
    }

    private final GrowthReceiver onGrowth;

    private CustomPlantBehavior(final GrowthReceiver onGrowth) {
        this.onGrowth = Objects.requireNonNull(onGrowth, "onGrowth");
    }

    public static @NotNull CustomPlantBehavior defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull GrowthReceiver onGrowth() {
        return this.onGrowth;
    }

    public static final class Builder {
        private GrowthReceiver onGrowth = context -> PlantGrowthResult.defaults();

        private Builder() {
        }

        public @NotNull Builder onGrowth(@NotNull final GrowthReceiver onGrowth) {
            this.onGrowth = Objects.requireNonNull(onGrowth, "onGrowth");
            return this;
        }

        public @NotNull CustomPlantBehavior build() {
            return new CustomPlantBehavior(this.onGrowth);
        }
    }
}
