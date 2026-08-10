package dev.mintychochip.customblock;

import dev.mintychochip.behavior.BlockDataView;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Immutable block-data change relative to a custom plant's origin. */
public record PlantBlockChange(int x, int y, int z, @NotNull BlockDataView data) {

    public PlantBlockChange {
        Objects.requireNonNull(data, "data");
    }
}
