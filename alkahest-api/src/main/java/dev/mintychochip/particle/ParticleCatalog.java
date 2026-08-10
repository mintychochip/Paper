package dev.mintychochip.particle;

import com.google.common.base.Preconditions;
import dev.mintychochip.registry.CustomCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.VanillaParticle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Catalog of concrete logical, non-native {@link CustomParticle} values.
 *
 * <p>Instances are isolated and publish complete immutable snapshots. {@link #global()} is the
 * process-wide catalog injected into {@link org.bukkit.Registry#PARTICLE_TYPE}.
 */
@NullMarked
public final class ParticleCatalog implements CustomCatalog<CustomParticle> {

    private static final Comparator<CustomParticle> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());
    private static final ParticleCatalog GLOBAL = new ParticleCatalog();

    private volatile Snapshot snapshot = Snapshot.empty();

    private ParticleCatalog() {
    }

    /** Returns the process-wide catalog used by the static Bukkit registry view. */
    public static @NotNull ParticleCatalog global() {
        return GLOBAL;
    }

    /** Creates an isolated catalog. */
    public static @NotNull ParticleCatalog create() {
        return new ParticleCatalog();
    }

    /** Registers a custom particle with default behavior. */
    public synchronized @NotNull CustomParticle register(
        final @NotNull NamespacedKey key,
        final @NotNull Class<?> dataType
    ) {
        return register(key, dataType, CustomParticleBehavior.defaults());
    }

    /** Registers a custom particle with its immutable emission behavior. */
    public synchronized @NotNull CustomParticle register(
        final @NotNull NamespacedKey key,
        final @NotNull Class<?> dataType,
        final @NotNull CustomParticleBehavior behavior
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(behavior, "behavior");
        Preconditions.checkArgument(!key.getNamespace().equals(NamespacedKey.MINECRAFT),
            "Cannot register a custom particle in the 'minecraft' namespace: " + key);
        Preconditions.checkArgument(VanillaParticle.fromKey(key) == null,
            "Particle '" + key + "' is a vanilla particle");
        final Snapshot current = this.snapshot;
        Preconditions.checkArgument(!current.byKey().containsKey(key),
            "Particle '" + key + "' is already registered");
        final CustomParticle particle = new CustomParticle(key, dataType, behavior);
        final Map<NamespacedKey, CustomParticle> next = new LinkedHashMap<>(current.byKey());
        next.put(key, particle);
        this.snapshot = Snapshot.of(next);
        return particle;
    }

    /** Gets the custom particle for {@code key}, or {@code null}. */
    public @Nullable CustomParticle get(@Nullable final NamespacedKey key) {
        return key == null ? null : this.snapshot.byKey().get(key);
    }

    /** Gets the custom particle for {@code key}, if present. */
    public @NotNull Optional<CustomParticle> getOptional(@NotNull final NamespacedKey key) {
        return Optional.ofNullable(get(key));
    }

    /** Returns an immutable, namespaced-key-sorted snapshot of custom particles. */
    @Override
    public @NotNull Collection<CustomParticle> all() {
        return this.snapshot.values();
    }

    /** Returns the immutable custom-particle snapshot keyed by namespaced key. */
    @Override
    public @NotNull Map<NamespacedKey, CustomParticle> asMap() {
        return this.snapshot.byKey();
    }

    /** Returns the number of custom particles. */
    public int size() {
        return this.snapshot.values().size();
    }

    /** Clears this custom particle catalog. */
    public synchronized void clear() {
        this.snapshot = Snapshot.empty();
    }

    private record Snapshot(
        List<CustomParticle> values,
        Map<NamespacedKey, CustomParticle> byKey
    ) {
        private static Snapshot empty() {
            return new Snapshot(List.of(), Map.of());
        }

        private static Snapshot of(final Map<NamespacedKey, CustomParticle> source) {
            final List<CustomParticle> values = new ArrayList<>(source.values());
            values.sort(KEY_ORDER);
            return new Snapshot(
                List.copyOf(values),
                Collections.unmodifiableMap(new LinkedHashMap<>(source))
            );
        }
    }
}
