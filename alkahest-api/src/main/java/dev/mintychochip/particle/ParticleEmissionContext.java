package dev.mintychochip.particle;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.Position;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot captured before a custom particle is transported.
 */
public record ParticleEmissionContext(
    @NotNull Position position,
    @NotNull List<ActorView> receivers,
    @NotNull Optional<ActorView> source,
    @NotNull NamespacedKey particleKey,
    int count,
    double offsetX,
    double offsetY,
    double offsetZ,
    double extra,
    boolean force,
    @NotNull ParticleDataView data
) {

    public ParticleEmissionContext {
        Objects.requireNonNull(position, "position");
        receivers = List.copyOf(Objects.requireNonNull(receivers, "receivers"));
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(particleKey, "particleKey");
        Objects.requireNonNull(data, "data");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)
            || !Double.isFinite(extra)) {
            throw new IllegalArgumentException("particle emission values must be finite");
        }
    }
}
