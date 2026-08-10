package dev.mintychochip.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Live registry view that merges a native registry with an injected custom catalog.
 *
 * @param <V> registry value type
 */
@NullMarked
public final class CatalogRegistry<V extends Keyed> extends Registry.NotARegistry<V> {

    private static final Comparator<Keyed> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());

    private final Supplier<? extends Registry<? extends V>> nativeRegistry;
    private final Supplier<? extends CustomCatalog<? extends V>> catalog;

    public CatalogRegistry(
        @NotNull final Supplier<? extends Registry<? extends V>> nativeRegistry,
        @NotNull final Supplier<? extends CustomCatalog<? extends V>> catalog
    ) {
        this.nativeRegistry = Objects.requireNonNull(nativeRegistry, "nativeRegistry");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    private Registry<? extends V> nativeRegistry() {
        return Objects.requireNonNull(this.nativeRegistry.get(), "native registry");
    }

    private Snapshot<V> customSnapshot() {
        final CustomCatalog<? extends V> source =
            Objects.requireNonNull(this.catalog.get(), "catalog");
        final Map<NamespacedKey, ? extends V> sourceMap = source.asMap();
        final List<V> values = new ArrayList<>(sourceMap.values());
        values.sort((left, right) -> KEY_ORDER.compare(left, right));
        final LinkedHashMap<NamespacedKey, V> byKey = new LinkedHashMap<>(sourceMap.size());
        for (final Map.Entry<NamespacedKey, ? extends V> entry : sourceMap.entrySet()) {
            byKey.put(entry.getKey(), entry.getValue());
        }
        return new Snapshot<>(List.copyOf(values), Map.copyOf(byKey));
    }

    @Override
    public @Nullable V get(final NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        final Registry<? extends V> nativeReg = nativeRegistry();
        final V nativeValue = nativeReg.get(key);
        return nativeValue != null ? nativeValue : customSnapshot().byKey().get(key);
    }

    @Override
    public @NotNull Iterator<V> iterator() {
        final Iterator<? extends V> nativeIterator = nativeRegistry().iterator();
        final Iterator<V> customIterator = customSnapshot().values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return nativeIterator.hasNext() || customIterator.hasNext();
            }

            @Override
            public V next() {
                return nativeIterator.hasNext() ? nativeIterator.next() : customIterator.next();
            }
        };
    }

    @Override
    public int size() {
        return nativeRegistry().size() + customSnapshot().values().size();
    }

    @Override
    public Stream<NamespacedKey> keyStream() {
        final Registry<? extends V> nativeReg = nativeRegistry();
        final Snapshot<V> custom = customSnapshot();
        return Stream.concat(nativeReg.keyStream(), custom.values().stream().map(Keyed::getKey));
    }

    /** Returns whether {@code value} is the exact native value resolved by this view. */
    public boolean isNative(final V value) {
        Objects.requireNonNull(value, "value");
        return nativeRegistry().get(value.getKey()) == value;
    }

    /** Returns whether {@code value} is the exact value in the current custom snapshot. */
    public boolean isCatalog(final V value) {
        Objects.requireNonNull(value, "value");
        return customSnapshot().byKey().get(value.getKey()) == value;
    }

    private record Snapshot<V>(List<V> values, Map<NamespacedKey, V> byKey) {
    }
}
