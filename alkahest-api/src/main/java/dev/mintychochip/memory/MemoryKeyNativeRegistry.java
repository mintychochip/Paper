package dev.mintychochip.memory;

import java.util.Iterator;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.memory.MemoryKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/** Native-only memory-key view used as the first half of the merged API registry. */
@NullMarked
public final class MemoryKeyNativeRegistry extends Registry.NotARegistry<MemoryKey<?>> {

    private static final MemoryKeyNativeRegistry INSTANCE = new MemoryKeyNativeRegistry();

    private MemoryKeyNativeRegistry() {
    }

    public static @NotNull MemoryKeyNativeRegistry instance() {
        return INSTANCE;
    }

    @Override
    public @Nullable MemoryKey<?> get(final NamespacedKey key) {
        return MemoryKey.vanillaValue(key);
    }

    @Override
    public @NotNull Iterator<MemoryKey<?>> iterator() {
        return MemoryKey.values().stream().filter(MemoryKey::isVanilla).iterator();
    }

    @Override
    public int size() {
        return (int) MemoryKey.values().stream().filter(MemoryKey::isVanilla).count();
    }
}
