package dev.mintychochip.particle;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NullMarked;

/**
 * Concrete catalog-backed particle value created by {@link ParticleCatalog}.
 *
 * <p>{@link #behavior()} owns the immutable emission receiver. A server transport must handle
 * the returned {@link ParticleEmissionPlan}; custom values never fall through to native particle
 * holder conversion.
 */
@NullMarked
public final class CustomParticle implements Particle {

    private final NamespacedKey key;
    private final Class<?> dataType;
    private final CustomParticleBehavior behavior;

    CustomParticle(final NamespacedKey key, final Class<?> dataType) {
        this(key, dataType, CustomParticleBehavior.defaults());
    }

    CustomParticle(
        final NamespacedKey key,
        final Class<?> dataType,
        final CustomParticleBehavior behavior
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.dataType = Objects.requireNonNull(dataType, "dataType");
        this.behavior = Objects.requireNonNull(behavior, "behavior");
    }

    @Override
    public @NotNull Class<?> getDataType() {
        return this.dataType;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return this.key;
    }

    public @NotNull CustomParticleBehavior behavior() {
        return this.behavior;
    }

    @Override
    public @NotNull com.destroystokyo.paper.ParticleBuilder builder() {
        return new com.destroystokyo.paper.ParticleBuilder(this);
    }

    @Override
    public boolean isVanilla() {
        return false;
    }

    @Override
    public boolean isCustom() {
        return true;
    }
}
