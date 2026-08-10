package dev.mintychochip.behavior;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable world position used by behavior contexts.
 *
 * @param worldKey stable world identifier
 * @param x world-space x coordinate
 * @param y world-space y coordinate
 * @param z world-space z coordinate
 */
public record Position(@NotNull String worldKey, double x, double y, double z) {

    public Position {
        Objects.requireNonNull(worldKey, "worldKey");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("position coordinates must be finite");
        }
    }
}
