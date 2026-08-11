package org.bukkit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Bukkit {@link Registry} view for {@link Material}: non-legacy vanilla constants.
 *
 * <p>Tags are unsupported, like the former API-side material registry.
 */
@ApiStatus.Internal
@NullMarked
final class MaterialRegistry extends Registry.NotARegistry<Material> {

    private final Registry<VanillaMaterial> vanilla;

    MaterialRegistry(final Registry<VanillaMaterial> vanilla) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
    }

    @Override
    public @Nullable Material get(final NamespacedKey key) {
        return this.vanilla.get(Objects.requireNonNull(key, "key"));
    }

    @Override
    public @NotNull Iterator<Material> iterator() {
        final List<Material> values = new ArrayList<>(this.vanilla.size());
        for (final VanillaMaterial value : this.vanilla) {
            values.add(value);
        }
        return values.iterator();
    }

    @Override
    public int size() {
        return this.vanilla.size();
    }

    @Override
    public Stream<NamespacedKey> keyStream() {
        return StreamSupport.stream(this.spliterator(), false).map(Keyed::getKey);
    }

    /** Returns whether the value is the exact object in the native material registry. */
    public boolean isNative(final Material value) {
        return value != null && this.vanilla.get(value.getKey()) == value;
    }
}
