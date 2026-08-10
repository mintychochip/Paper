package dev.mintychochip.behavior;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of a live carrier entity and logical custom identity.
 */
public record EntityView(
    @NotNull UUID uniqueId,
    @NotNull NamespacedKey carrierType,
    @NotNull Position position,
    @NotNull Optional<NamespacedKey> customKey
) {

    public EntityView {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(carrierType, "carrierType");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(customKey, "customKey");
    }
}
