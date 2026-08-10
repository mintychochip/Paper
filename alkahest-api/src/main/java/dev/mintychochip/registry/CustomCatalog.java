package dev.mintychochip.registry;

import java.util.Collection;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Immutable snapshot view supplied to a catalog-backed registry.
 *
 * @param <T> custom registry value type
 */
@NullMarked
public interface CustomCatalog<T extends Keyed> {

    /** Returns the current deterministic custom-value snapshot. */
    @NotNull Collection<? extends T> all();

    /** Returns the current immutable key-to-value snapshot. */
    @NotNull Map<NamespacedKey, ? extends T> asMap();

    /** Resolves a custom value by key, or returns {@code null}. */
    default @Nullable T getOrNull(@NotNull final NamespacedKey key) {
        return asMap().get(key);
    }
}
