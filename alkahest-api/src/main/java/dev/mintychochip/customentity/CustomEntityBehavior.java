package dev.mintychochip.customentity;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable spawn and presentation receivers owned by a custom entity definition.
 */
public final class CustomEntityBehavior {

    @FunctionalInterface
    public interface EntitySpawnReceiver {
        EntitySpawnResult receive(EntitySpawnContext context);
    }

    @FunctionalInterface
    public interface EntityApplyReceiver {
        EntityApplyResult receive(EntityApplyContext context);
    }

    private final EntitySpawnReceiver onSpawn;
    private final EntityApplyReceiver onApply;

    private CustomEntityBehavior(final EntitySpawnReceiver onSpawn, final EntityApplyReceiver onApply) {
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.onApply = Objects.requireNonNull(onApply, "onApply");
    }

    public static @NotNull CustomEntityBehavior defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull EntitySpawnReceiver onSpawn() {
        return this.onSpawn;
    }

    public @NotNull EntityApplyReceiver onApply() {
        return this.onApply;
    }

    public static final class Builder {
        private EntitySpawnReceiver onSpawn = context -> EntitySpawnResult.defaults();
        private EntityApplyReceiver onApply = context -> EntityApplyResult.defaults();

        private Builder() {
        }

        public Builder onSpawn(final EntitySpawnReceiver onSpawn) {
            this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
            return this;
        }

        public Builder onApply(final EntityApplyReceiver onApply) {
            this.onApply = Objects.requireNonNull(onApply, "onApply");
            return this;
        }

        public @NotNull CustomEntityBehavior build() {
            return new CustomEntityBehavior(this.onSpawn, this.onApply);
        }
    }
}
