package dev.mintychochip.behavior;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable block-data snapshot represented by its carrier and serialized state.
 */
public record BlockDataView(@NotNull Material material, @NotNull String serialized) {

    public BlockDataView {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(serialized, "serialized");
    }

    public static @NotNull BlockDataView from(@NotNull final BlockData source) {
        Objects.requireNonNull(source, "source");
        return new BlockDataView(source.getMaterial(), source.getAsString());
    }
}
