package dev.mintychochip.behavior;

import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of a block and its logical custom identity.
 */
public record BlockView(
    @NotNull Position position,
    @NotNull Material carrierType,
    @NotNull BlockDataView data,
    @NotNull Optional<NamespacedKey> customKey
) {

    public BlockView {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(carrierType, "carrierType");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(customKey, "customKey");
    }

    /**
     * Stable Adventure key for the live carrier material.
     */
    public @NotNull Key carrierKey() {
        return this.carrierType.getKey();
    }
}
