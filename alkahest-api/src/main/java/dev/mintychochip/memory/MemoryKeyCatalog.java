package dev.mintychochip.memory;

import dev.mintychochip.registry.CustomCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.memory.MemoryKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Atomic catalog for concrete logical, non-native {@link CustomMemoryKey} values.
 */
@NullMarked
public final class MemoryKeyCatalog implements CustomCatalog<CustomMemoryKey<?>> {

    private static final Comparator<CustomMemoryKey<?>> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());
    private static final MemoryKeyCatalog GLOBAL = new MemoryKeyCatalog();

    private volatile Snapshot snapshot = Snapshot.empty();

    private MemoryKeyCatalog() {
    }

    /** Returns the process-wide catalog used by the static Bukkit registry view. */
    public static @NotNull MemoryKeyCatalog global() {
        return GLOBAL;
    }

    /** Creates an isolated catalog for a test or server-owned registry view. */
    public static @NotNull MemoryKeyCatalog createCatalog() {
        return new MemoryKeyCatalog();
    }

    /** Creates and atomically publishes a custom memory key in this catalog. */
    public synchronized <T> @NotNull CustomMemoryKey<T> create(
        @NotNull final NamespacedKey key,
        @NotNull final Class<T> memoryClass
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(memoryClass, "memoryClass");
        if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
            throw new IllegalArgumentException("custom memory key must not use the minecraft namespace: " + key);
        }

        final Snapshot current = this.snapshot;
        if (current.byKey().containsKey(key)) {
            throw new IllegalStateException("memory key already registered: " + key);
        }
        if (MemoryKey.vanillaValue(key) != null) {
            throw new IllegalStateException("memory key collides with a native key: " + key);
        }

        final CustomMemoryKey<T> value = new CustomMemoryKey<>(key, memoryClass);
        final List<CustomMemoryKey<?>> values = new ArrayList<>(current.values());
        values.add(value);
        values.sort(KEY_ORDER);
        this.snapshot = Snapshot.of(values);
        return value;
    }

    /** Gets the custom memory key for {@code key}, or {@code null}. */
    public @Nullable CustomMemoryKey<?> get(@NotNull final NamespacedKey key) {
        return this.snapshot.byKey().get(Objects.requireNonNull(key, "key"));
    }

    /** Returns an immutable, deterministically sorted snapshot of custom memory keys. */
    @Override
    public @NotNull Set<CustomMemoryKey<?>> all() {
        return this.snapshot.values();
    }

    /** Returns the immutable custom-memory snapshot keyed by namespaced key. */
    @Override
    public @NotNull Map<NamespacedKey, CustomMemoryKey<?>> asMap() {
        return this.snapshot.byKey();
    }

    /** Clears this custom memory-key catalog. */
    public synchronized void clear() {
        this.snapshot = Snapshot.empty();
    }

    private record Snapshot(Set<CustomMemoryKey<?>> values, Map<NamespacedKey, CustomMemoryKey<?>> byKey) {
        private static Snapshot empty() {
            return new Snapshot(Set.of(), Map.of());
        }

        private static Snapshot of(final List<CustomMemoryKey<?>> values) {
            final LinkedHashMap<NamespacedKey, CustomMemoryKey<?>> byKey = new LinkedHashMap<>();
            for (final CustomMemoryKey<?> value : values) {
                byKey.put(value.getKey(), value);
            }
            final LinkedHashSet<CustomMemoryKey<?>> ordered = new LinkedHashSet<>(values);
            return new Snapshot(
                Collections.unmodifiableSet(ordered),
                Collections.unmodifiableMap(byKey)
            );
        }
    }
}
