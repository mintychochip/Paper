package org.bukkit;

import dev.mintychochip.customblock.CustomBlockCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Bukkit {@link Registry} view for {@link Material}: non-legacy vanilla constants merged with an
 * injected catalog of custom materials.
 *
 * <p>Tags are unsupported, like the former API-side material registry.
 */
@ApiStatus.Internal
@NullMarked
public final class MaterialRegistry extends Registry.NotARegistry<Material> {

    private static final Comparator<Material> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());

    private final Registry<VanillaMaterial> vanilla;
    private final Supplier<? extends CustomBlockCatalog> catalog;

    public MaterialRegistry(
        final Registry<VanillaMaterial> vanilla,
        final Supplier<? extends CustomBlockCatalog> catalog
    ) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    private List<Material> customValues() {
        final List<Material> values = new ArrayList<>(this.catalog.get().all());
        values.sort(KEY_ORDER);
        return List.copyOf(values);
    }

    @Override
    public @Nullable Material get(final NamespacedKey key) {
        final VanillaMaterial value = this.vanilla.get(Objects.requireNonNull(key, "key"));
        return value != null ? value : this.catalog.get().getOrNull(key);
    }

    @Override
    public @NotNull Iterator<Material> iterator() {
        final List<Material> all = new ArrayList<>(this.vanilla.size() + this.catalog.get().all().size());
        for (final VanillaMaterial value : this.vanilla) {
            all.add(value);
        }
        all.addAll(customValues());
        return all.iterator();
    }

    @Override
    public int size() {
        return this.vanilla.size() + this.catalog.get().all().size();
    }

    @Override
    public Stream<NamespacedKey> keyStream() {
        return StreamSupport.stream(this.spliterator(), false).map(Keyed::getKey);
    }

    /** Returns whether the value is the exact object in the native material registry. */
    public boolean isNative(final Material value) {
        return value != null && this.vanilla.get(value.getKey()) == value;
    }

    /** Returns whether the value is the exact object in the injected custom catalog. */
    public boolean isCatalog(final Material value) {
        return value != null && this.catalog.get().getOrNull(value.getKey()) == value;
    }
}
