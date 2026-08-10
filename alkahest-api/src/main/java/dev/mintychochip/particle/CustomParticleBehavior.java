package dev.mintychochip.particle;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable emission receivers owned by a custom particle value.
 */
public final class CustomParticleBehavior {

    @FunctionalInterface
    public interface ParticleEmissionReceiver {
        ParticleEmissionPlan receive(ParticleEmissionContext context);
    }

    private final ParticleEmissionReceiver onEmit;

    private CustomParticleBehavior(final ParticleEmissionReceiver onEmit) {
        this.onEmit = Objects.requireNonNull(onEmit, "onEmit");
    }

    public static @NotNull CustomParticleBehavior defaults() {
        return builder().build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull ParticleEmissionReceiver onEmit() {
        return this.onEmit;
    }

    public static final class Builder {
        private ParticleEmissionReceiver onEmit = context -> ParticleEmissionPlan.defaults();

        private Builder() {
        }

        public Builder onEmit(final ParticleEmissionReceiver onEmit) {
            this.onEmit = Objects.requireNonNull(onEmit, "onEmit");
            return this;
        }

        public @NotNull CustomParticleBehavior build() {
            return new CustomParticleBehavior(this.onEmit);
        }
    }
}
