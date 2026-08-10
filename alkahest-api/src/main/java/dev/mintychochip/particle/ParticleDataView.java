package dev.mintychochip.particle;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable textual snapshot of optional particle payload data.
 *
 * <p>The receiver never receives the mutable payload object supplied to Bukkit. A server transport
 * may use the captured type/value pair as parameters or choose its own payload.</p>
 */
public record ParticleDataView(@NotNull String type, @NotNull String value) {

    public ParticleDataView {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    public static @NotNull ParticleDataView none() {
        return new ParticleDataView("none", "");
    }

    public static @NotNull ParticleDataView from(@Nullable final Object data) {
        if (data == null) {
            return none();
        }
        return new ParticleDataView(data.getClass().getName(), String.valueOf(data));
    }
}
