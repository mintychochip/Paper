package dev.mintychochip.particle;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.Decision;
import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Invokes custom particle receivers and dispatches handled plans before any native conversion.
 */
public final class CustomParticleRouter {

    /**
     * Detects and routes a Bukkit particle call. Vanilla particles return {@code false} so the
     * caller can continue through the native packet conversion path.
     */
    public static boolean emitIfCustom(
        @NotNull final Particle particle,
        @NotNull final World world,
        @Nullable final List<Player> receivers,
        @Nullable final Player source,
        final double x,
        final double y,
        final double z,
        final int count,
        final double offsetX,
        final double offsetY,
        final double offsetZ,
        final double extra,
        @Nullable final Object data,
        final boolean force
    ) {
        Objects.requireNonNull(particle, "particle");
        Objects.requireNonNull(world, "world");
        if (!(particle instanceof CustomParticle customParticle)) {
            return false;
        }
        final List<ActorView> receiverViews = new ArrayList<>();
        final List<Player> effectiveReceivers = receivers == null ? world.getPlayers() : receivers;
        if (effectiveReceivers != null) {
            for (final Player receiver : effectiveReceivers) {
                final ActorView view = snapshotActor(receiver);
                if (view != null) {
                    receiverViews.add(view);
                }
            }
        }
        final ActorView sourceView = snapshotActor(source);
        final ParticleEmissionContext context = new ParticleEmissionContext(
            new Position(worldKey(world), x, y, z),
            receiverViews,
            Optional.ofNullable(sourceView),
            customParticle.getKey(),
            count,
            offsetX,
            offsetY,
            offsetZ,
            extra,
            force,
            ParticleDataView.from(data)
        );
        return emit(customParticle, context);
    }

    private CustomParticleRouter() {
    }

    /**
     * Routes one immutable emission.
     *
     * @return {@code true} when the custom value consumed the native emission boundary, including
     * a receiver denial
     */
    public static boolean emit(
        @NotNull final CustomParticle particle,
        @NotNull final ParticleEmissionContext context
    ) {
        Objects.requireNonNull(particle, "particle");
        Objects.requireNonNull(context, "context");
        if (!particle.getKey().equals(context.particleKey())) {
            throw new IllegalArgumentException(
                "particle context key " + context.particleKey() + " does not match " + particle.getKey()
            );
        }
        final ParticleEmissionPlan plan = particle.behavior().onEmit().receive(context);
        if (plan == null) {
            throw new IllegalStateException("custom particle receiver returned null: " + particle.getKey());
        }
        if (plan.decision() == Decision.DENY) {
            return true;
        }
        if (!(plan.transportKey() instanceof ValueOverride.Value<NamespacedKey> value)) {
            throw new IllegalArgumentException(
                "custom particle " + particle.getKey() + " has no explicit transport plan"
            );
        }
        CustomParticleTransportRegistry.dispatch(
            value.value(),
            new CustomParticleTransport.Emission(particle, context, plan)
        );
        return true;
    }
    private static @Nullable ActorView snapshotActor(final Player player) {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }
        final Location location = player.getLocation();
        if (location == null) {
            return null;
        }
        return new ActorView(
            player.getUniqueId(),
            new Position(worldKey(location.getWorld()), location.getX(), location.getY(), location.getZ()),
            Optional.ofNullable(player.getName()),
            true
        );
    }

    private static String worldKey(final World world) {
        return world == null || world.getKey() == null ? "unknown" : world.getKey().toString();
    }
}
