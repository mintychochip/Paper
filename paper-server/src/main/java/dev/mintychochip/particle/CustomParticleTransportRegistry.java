package dev.mintychochip.particle;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Server-installed transport registry for handled custom particle plans.
 *
 * <p>The registry is deliberately separate from the API catalog: logical particle definitions are
 * portable API values, while packet delivery is server and client-version specific.</p>
 */
public final class CustomParticleTransportRegistry {

    private static final Map<NamespacedKey, CustomParticleTransport> TRANSPORTS = new ConcurrentHashMap<>();

    private CustomParticleTransportRegistry() {
    }

    public static void register(
        @NotNull final NamespacedKey key,
        @NotNull final CustomParticleTransport transport
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(transport, "transport");
        final CustomParticleTransport previous = TRANSPORTS.putIfAbsent(key, transport);
        if (previous != null) {
            throw new IllegalArgumentException("custom particle transport already registered: " + key);
        }
    }

    public static void unregister(@NotNull final NamespacedKey key) {
        TRANSPORTS.remove(Objects.requireNonNull(key, "key"));
    }

    public static void dispatch(
        @NotNull final NamespacedKey key,
        @NotNull final CustomParticleTransport.Emission emission
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(emission, "emission");
        final CustomParticleTransport transport = TRANSPORTS.get(key);
        if (transport == null) {
            throw new IllegalStateException("no custom particle transport registered: " + key);
        }
        transport.send(emission);
    }

    /** Tests and controlled server reloads only. */
    public static void clear() {
        TRANSPORTS.clear();
    }
}
