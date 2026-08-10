package dev.mintychochip.potion;

import dev.mintychochip.registry.CustomCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.VanillaPotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Atomically published catalog of concrete {@link CustomPotionType} values.
 */
@NullMarked
public final class PotionTypeCatalog implements CustomCatalog<CustomPotionType> {

    private static final Comparator<CustomPotionType> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());
    private static final PotionTypeCatalog GLOBAL = new PotionTypeCatalog();

    private volatile Snapshot snapshot = Snapshot.empty();

    private PotionTypeCatalog() {
    }

    /** Returns the process-wide catalog used by the static Bukkit registry views. */
    public static @NotNull PotionTypeCatalog global() {
        return GLOBAL;
    }

    /** Creates an isolated catalog for a test or server-owned registry view. */
    public static @NotNull PotionTypeCatalog create() {
        return new PotionTypeCatalog();
    }

    /** Starts a typed custom potion registration in this catalog. */
    public @NotNull Builder builder(@NotNull final NamespacedKey key) {
        return new Builder(key);
    }

    /** Registers a custom potion with immutable effect and feature metadata. */
    public @NotNull CustomPotionType register(
        @NotNull final NamespacedKey key,
        @NotNull final List<PotionEffect> effects,
        final boolean upgradeable,
        final boolean extendable,
        final int maxLevel,
        @NotNull final Set<FeatureFlag> requiredFeatures
    ) {
        return builder(key)
            .effects(effects)
            .upgradeable(upgradeable)
            .extendable(extendable)
            .maxLevel(maxLevel)
            .requiredFeatures(requiredFeatures)
            .build();
    }

    /** Gets the custom potion for {@code key}, or {@code null}. */
    public @Nullable CustomPotionType get(@Nullable final NamespacedKey key) {
        return key == null ? null : this.snapshot.byKey().get(key);
    }

    /** Returns an immutable, namespaced-key-sorted snapshot of custom potions. */
    @Override
    public @NotNull List<CustomPotionType> all() {
        return this.snapshot.values();
    }

    /** Returns the immutable custom-potion snapshot keyed by namespaced key. */
    @Override
    public @NotNull Map<NamespacedKey, CustomPotionType> asMap() {
        return this.snapshot.byKey();
    }

    /** Returns the number of custom potions. */
    public int size() {
        return this.snapshot.values().size();
    }

    /** Clears this custom potion catalog. */
    public synchronized void clear() {
        this.snapshot = Snapshot.empty();
    }

    /** Alias for {@link #clear()}. */
    public void reset() {
        clear();
    }

    /** Mutable registration builder that publishes only when {@link #build()} succeeds. */
    public final class Builder {
        private final NamespacedKey key;
        private List<PotionEffect> effects = List.of();
        private boolean upgradeable;
        private boolean extendable;
        private int maxLevel = 1;
        private Set<FeatureFlag> requiredFeatures = Set.of();

        private Builder(final NamespacedKey key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public @NotNull Builder effects(@NotNull final List<PotionEffect> effects) {
            this.effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
            return this;
        }

        public @NotNull Builder upgradeable(final boolean upgradeable) {
            this.upgradeable = upgradeable;
            return this;
        }

        public @NotNull Builder extendable(final boolean extendable) {
            this.extendable = extendable;
            return this;
        }

        public @NotNull Builder maxLevel(final int maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }

        public @NotNull Builder requiredFeatures(@NotNull final Set<FeatureFlag> requiredFeatures) {
            this.requiredFeatures = Set.copyOf(Objects.requireNonNull(requiredFeatures, "requiredFeatures"));
            return this;
        }

        public @NotNull CustomPotionType build() {
            if (NamespacedKey.MINECRAFT.equals(this.key.getNamespace())) {
                throw new IllegalArgumentException("custom potion key must not use the minecraft namespace: " + this.key);
            }
            if (VanillaPotionType.fromKey(this.key) != null) {
                throw new IllegalStateException("potion key collides with a vanilla key: " + this.key);
            }
            if (this.maxLevel < 1) {
                throw new IllegalArgumentException("maxLevel must be at least 1");
            }
            for (final PotionEffect effect : this.effects) {
                Objects.requireNonNull(effect, "effects cannot contain null");
            }
            final CustomPotionType value = new CustomPotionType(
                this.key,
                this.effects,
                this.upgradeable,
                this.extendable,
                this.maxLevel,
                this.requiredFeatures
            );
            synchronized (PotionTypeCatalog.this) {
                final Snapshot current = PotionTypeCatalog.this.snapshot;
                if (current.byKey().containsKey(this.key)) {
                    throw new IllegalStateException("potion type already registered: " + this.key);
                }
                final Map<NamespacedKey, CustomPotionType> next =
                    new LinkedHashMap<>(current.byKey());
                next.put(this.key, value);
                PotionTypeCatalog.this.snapshot = Snapshot.of(next);
            }
            return value;
        }
    }

    private record Snapshot(List<CustomPotionType> values, Map<NamespacedKey, CustomPotionType> byKey) {
        private static Snapshot empty() {
            return new Snapshot(List.of(), Map.of());
        }

        private static Snapshot of(final Map<NamespacedKey, CustomPotionType> values) {
            final List<CustomPotionType> orderedValues = new ArrayList<>(values.values());
            orderedValues.sort(KEY_ORDER);
            final LinkedHashMap<NamespacedKey, CustomPotionType> ordered = new LinkedHashMap<>();
            for (final CustomPotionType value : orderedValues) {
                ordered.put(value.getKey(), value);
            }
            return new Snapshot(
                List.copyOf(orderedValues),
                Collections.unmodifiableMap(ordered)
            );
        }
    }
}
