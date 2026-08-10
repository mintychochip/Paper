package dev.mintychochip.customblock;

import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Vanilla carrier metadata for a custom crop or sapling.
 *
 * <p>The server validates that the selected carrier belongs to the requested hook family. This
 * API type intentionally stores only the public carrier material and growth family.</p>
 */
public record PlantHostSpec(@NotNull Material carrier, @NotNull PlantKind kind) implements HostSpec {

    public PlantHostSpec {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(kind, "kind");
        if (carrier == Material.AIR || carrier == Material.CAVE_AIR || carrier == Material.VOID_AIR) {
            throw new IllegalArgumentException("plant carrier must not be air");
        }
    }

    public static @NotNull PlantHostSpec crop(@NotNull final Material carrier) {
        return new PlantHostSpec(carrier, PlantKind.CROP);
    }

    public static @NotNull PlantHostSpec sapling(@NotNull final Material carrier) {
        return new PlantHostSpec(carrier, PlantKind.SAPLING);
    }

    @Override
    public @NotNull BlockHostType type() {
        return BlockHostType.PLANT;
    }
}
