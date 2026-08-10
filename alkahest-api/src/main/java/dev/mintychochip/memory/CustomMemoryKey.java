package dev.mintychochip.memory;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.memory.MemoryKey;
import org.jetbrains.annotations.NotNull;

/** Concrete catalog-backed memory key created by {@link MemoryKeyCatalog}. */
public final class CustomMemoryKey<T> extends MemoryKey<T> {

    CustomMemoryKey(@NotNull final NamespacedKey key, @NotNull final Class<T> memoryClass) {
        super(key, memoryClass, false);
    }
}
