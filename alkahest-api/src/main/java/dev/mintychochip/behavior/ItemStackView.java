package dev.mintychochip.behavior;

import dev.mintychochip.customblock.CustomBlockItemTags;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only snapshot of an item stack supplied to a behavior receiver.
 *
 * <p>The captured stack is never returned directly. {@link #copy()} returns a fresh mutable copy
 * for a server router to apply after validating a receiver plan.</p>
 */
public final class ItemStackView {

    private final ItemStack snapshot;
    private final Optional<NamespacedKey> customKey;

    private ItemStackView(final ItemStack source) {
        this.snapshot = source.clone();
        this.customKey = CustomBlockItemTags.read(this.snapshot.getPersistentDataContainer());
    }

    public static @NotNull ItemStackView from(@NotNull final ItemStack source) {
        return new ItemStackView(Objects.requireNonNull(source, "source"));
    }

    public @NotNull Material type() {
        return this.snapshot.getType();
    }

    public int amount() {
        return this.snapshot.getAmount();
    }

    /**
     * Logical custom-block key stamped on the captured stack, if present.
     */
    public @NotNull Optional<NamespacedKey> customKey() {
        return this.customKey;
    }

    /**
     * Return a mutable copy isolated from this snapshot.
     */
    public @NotNull ItemStack copy() {
        return this.snapshot.clone();
    }
}
