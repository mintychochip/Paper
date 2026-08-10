package dev.mintychochip.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.behavior.Position;
import dev.mintychochip.behavior.ValueOverride;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Normal")
public class CustomParticleTransportTest {

    private static final NamespacedKey PARTICLE_KEY = new NamespacedKey("mintychochip", "spark");
    private static final NamespacedKey TRANSPORT_KEY = new NamespacedKey("mintychochip", "display");

    @AfterEach
    public void tearDown() {
        CustomParticleTransportRegistry.clear();
    }

    @Test
    public void handledCustomParticleUsesTransportInsteadOfNativeConversion() {
        final AtomicInteger sends = new AtomicInteger();
        CustomParticleTransportRegistry.register(TRANSPORT_KEY, emission -> {
            assertEquals(PARTICLE_KEY, emission.context().particleKey());
            assertEquals(
                TRANSPORT_KEY,
                ((ValueOverride.Value<NamespacedKey>) emission.plan().transportKey()).value()
            );
            sends.incrementAndGet();
        });
        final ParticleCatalog catalog = ParticleCatalog.create();
        final CustomParticle particle = catalog.register(
            PARTICLE_KEY,
            Void.class,
            CustomParticleBehavior.builder()
                .onEmit(context -> ParticleEmissionPlan.handled(TRANSPORT_KEY))
                .build()
        );
        final ParticleEmissionContext context = new ParticleEmissionContext(
            new Position("world", 1.0, 2.0, 3.0),
            List.of(),
            Optional.empty(),
            PARTICLE_KEY,
            2,
            0.1,
            0.2,
            0.3,
            0.4,
            false,
            ParticleDataView.none()
        );

        assertTrue(CustomParticleRouter.emit(particle, context));
        assertEquals(1, sends.get());
    }
    @Test
    public void boundarySnapshotRoutesBeforeNativeParticleConversion() {
        final AtomicReference<CustomParticleTransport.Emission> seen = new AtomicReference<>();
        CustomParticleTransportRegistry.register(TRANSPORT_KEY, seen::set);
        final CustomParticle particle = ParticleCatalog.create().register(
            new NamespacedKey("mintychochip", "boundary_spark"),
            Void.class,
            CustomParticleBehavior.builder()
                .onEmit(context -> ParticleEmissionPlan.handled(TRANSPORT_KEY))
                .build()
        );
        final World world = org.mockito.Mockito.mock(World.class);
        org.mockito.Mockito.when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "overworld"));

        assertTrue(CustomParticleRouter.emitIfCustom(
            particle,
            world,
            List.of(),
            null,
            4.0,
            5.0,
            6.0,
            3,
            0.1,
            0.2,
            0.3,
            0.4,
            "payload",
            true
        ));
        final ParticleEmissionContext context = seen.get().context();
        assertEquals("minecraft:overworld", context.position().worldKey());
        assertEquals(4.0, context.position().x());
        assertEquals(3, context.count());
        assertEquals(String.class.getName(), context.data().type());
        assertEquals("payload", context.data().value());
    }
}
