package dev.mintychochip.particle;

import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.ValueOverride;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable transport-neutral plan returned by a custom particle receiver.
 */
public final class ParticleEmissionPlan {

    private final Decision decision;
    private final ValueOverride<NamespacedKey> transportKey;
    private final Map<String, String> parameters;

    private ParticleEmissionPlan(
        final Decision decision,
        final ValueOverride<NamespacedKey> transportKey,
        final Map<String, String> parameters
    ) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.transportKey = Objects.requireNonNull(transportKey, "transportKey");
        final Map<String, String> copy = new LinkedHashMap<>(Objects.requireNonNull(parameters, "parameters"));
        copy.forEach((key, value) -> {
            Objects.requireNonNull(key, "parameter key");
            Objects.requireNonNull(value, "parameter value");
        });
        this.parameters = Map.copyOf(copy);
    }

    public static @NotNull ParticleEmissionPlan defaults() {
        return builder().build();
    }

    public static @NotNull ParticleEmissionPlan allow() {
        return builder().decision(Decision.ALLOW).build();
    }

    public static @NotNull ParticleEmissionPlan deny() {
        return builder().decision(Decision.DENY).build();
    }

    public static @NotNull ParticleEmissionPlan handled(@NotNull final NamespacedKey transportKey) {
        return builder()
            .decision(Decision.ALLOW)
            .transportKey(ValueOverride.value(transportKey))
            .build();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Decision decision() {
        return this.decision;
    }

    public @NotNull ValueOverride<NamespacedKey> transportKey() {
        return this.transportKey;
    }

    public @NotNull Map<String, String> parameters() {
        return this.parameters;
    }

    public static final class Builder {
        private Decision decision = Decision.DEFAULT;
        private ValueOverride<NamespacedKey> transportKey = ValueOverride.useDefault();
        private Map<String, String> parameters = Map.of();

        private Builder() {
        }

        public Builder decision(final Decision decision) {
            this.decision = Objects.requireNonNull(decision, "decision");
            return this;
        }

        public Builder transportKey(final ValueOverride<NamespacedKey> transportKey) {
            this.transportKey = Objects.requireNonNull(transportKey, "transportKey");
            return this;
        }

        public Builder parameters(final Map<String, String> parameters) {
            this.parameters = new LinkedHashMap<>(Objects.requireNonNull(parameters, "parameters"));
            return this;
        }

        public @NotNull ParticleEmissionPlan build() {
            return new ParticleEmissionPlan(this.decision, this.transportKey, this.parameters);
        }
    }
}
