package dev.mintychochip.behavior;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Explicitly distinguishes a receiver value from a metadata fallback.
 *
 * @param <T> overridden value type
 */
public sealed interface ValueOverride<T> permits ValueOverride.UseDefault, ValueOverride.Value {

    /** Request the value supplied by the carrier or metadata fallback. */
    record UseDefault<T>() implements ValueOverride<T> {
    }

    /** Supply a non-null receiver value. */
    record Value<T>(@NotNull T value) implements ValueOverride<T> {
        public Value {
            Objects.requireNonNull(value, "value");
        }
    }

    static <T> ValueOverride<T> useDefault() {
        return new UseDefault<>();
    }

    static <T> ValueOverride<T> value(final T value) {
        return new Value<>(value);
    }
}
