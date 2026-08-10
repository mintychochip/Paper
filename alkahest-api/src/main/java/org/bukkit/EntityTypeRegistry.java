package org.bukkit;

import dev.mintychochip.registry.CustomCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.VanillaEntityType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Bukkit {@link Registry} view for {@link EntityType}: non-UNKNOWN vanilla constants merged with
 * an injected catalog of custom entity types.
 *
 * <p>Tags are unsupported on this API-side registry. The server installs a tag-aware native view
 * separately through {@link io.papermc.paper.registry.RegistryAccess}.
 */
@ApiStatus.Internal
@NullMarked
public class EntityTypeRegistry extends Registry.NotARegistry<EntityType> {

    private static final Comparator<EntityType> KEY_ORDER =
        Comparator.comparing(value -> value.getKey().toString());

    private final Registry<VanillaEntityType> vanilla;
    private final Supplier<? extends CustomCatalog<? extends EntityType>> catalog;

    public EntityTypeRegistry(
        final Registry<VanillaEntityType> vanilla,
        final Supplier<? extends CustomCatalog<? extends EntityType>> catalog
    ) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** Vanilla-only view used by tag-aware server wrappers. */
    public final Registry<VanillaEntityType> vanilla() {
        return this.vanilla;
    }

    private List<EntityType> customValues() {
        final List<EntityType> values = new ArrayList<>(this.catalog.get().all());
        values.sort(KEY_ORDER);
        return List.copyOf(values);
    }

    @Override
    public @Nullable EntityType get(final NamespacedKey key) {
        final VanillaEntityType value = this.vanilla.get(Objects.requireNonNull(key, "key"));
        return value != null ? value : this.catalog.get().getOrNull(key);
    }

    @Override
    public @NotNull Iterator<EntityType> iterator() {
        final List<EntityType> all = new ArrayList<>(this.vanilla.size() + this.catalog.get().all().size());
        for (final VanillaEntityType value : this.vanilla) {
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

    /** Returns whether the value is the exact object in the native entity registry view. */
    public boolean isNative(final EntityType value) {
        return value != null && this.vanilla.get(value.getKey()) == value;
    }

    /** Returns whether the value is the exact object in the injected custom catalog. */
    public boolean isCatalog(final EntityType value) {
        return value != null && this.catalog.get().getOrNull(value.getKey()) == value;
    }
}
