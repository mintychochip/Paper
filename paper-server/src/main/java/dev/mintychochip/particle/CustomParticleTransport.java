package dev.mintychochip.particle;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Server-owned transport for a handled custom particle emission.
 *
 * <p>Transport implementations receive immutable context and plan snapshots. They are the only
 * layer allowed to translate a logical custom particle into a client packet or another delivery
 * mechanism.</p>
 */
@FunctionalInterface
public interface CustomParticleTransport {

    void send(@NotNull Emission emission);

    /** Immutable transport input. */
    record Emission(
        @NotNull CustomParticle particle,
        @NotNull ParticleEmissionContext context,
        @NotNull ParticleEmissionPlan plan
    ) {
        public Emission {
            Objects.requireNonNull(particle, "particle");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(plan, "plan");
        }
    }
}
