package dev.mintychochip.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.behavior.Decision;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class CustomParticleBehaviorTest {

    @Test
    void particleOwnsAnEmissionReceiver() {
        final CustomParticleBehavior behavior = CustomParticleBehavior.builder()
            .onEmit(context -> ParticleEmissionPlan.handled(new NamespacedKey("mintychochip", "display")))
            .build();
        final CustomParticle particle = ParticleCatalog.create()
            .register(new NamespacedKey("mintychochip", "spark"), Void.class, behavior);

        assertSame(behavior, particle.behavior());
        assertEquals(
            Decision.ALLOW,
            particle.behavior().onEmit().receive(sampleContext()).decision()
        );
    }

    @Test
    void emissionPlanCopiesParametersAndRejectsMutation() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("color", "red");

        final ParticleEmissionPlan plan = ParticleEmissionPlan.builder()
            .parameters(parameters)
            .build();
        parameters.put("color", "blue");

        assertEquals("red", plan.parameters().get("color"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.parameters().put("size", "large")
        );
    }

    private static ParticleEmissionContext sampleContext() {
        return new ParticleEmissionContext(
            new dev.mintychochip.behavior.Position("minecraft:overworld", 0.0, 64.0, 0.0),
            java.util.List.of(),
            java.util.Optional.empty(),
            new NamespacedKey("mintychochip", "spark"),
            1,
            0.0,
            0.0,
            0.0,
            0.0,
            true,
            ParticleDataView.none()
        );
    }
}
