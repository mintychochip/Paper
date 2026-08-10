package dev.mintychochip.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParticleCatalogTest {

    private final ParticleCatalog catalog = ParticleCatalog.create();

    @AfterEach
    void clearCatalog() {
        this.catalog.clear();
    }

    @Test
    void customParticlePublishesThroughNamespacedCatalog() {
        final Particle zebra = this.catalog.register(
            new NamespacedKey("mintychochip", "zebra"), String.class);
        final Particle apple = this.catalog.register(
            new NamespacedKey("mintychochip", "apple"), Integer.class);

        assertTrue(zebra.isCustom());
        assertTrue(zebra instanceof CustomParticle);
        assertSame(zebra, this.catalog.get(zebra.getKey()));
        assertEquals(
            List.of("mintychochip:apple", "mintychochip:zebra"),
            this.catalog.all().stream().map(value -> value.getKey().toString()).toList()
        );
        assertSame(apple, this.catalog.asMap().get(apple.getKey()));
        assertSame(zebra, zebra.builder().particle());
    }

    @Test
    void failedRegistrationLeavesPublishedSnapshot() {
        final Particle first = this.catalog.register(
            new NamespacedKey("mintychochip", "first"), Void.class);
        assertThrows(IllegalArgumentException.class,
            () -> this.catalog.register(NamespacedKey.minecraft("bad"), Void.class));
        assertThrows(IllegalArgumentException.class,
            () -> this.catalog.register(first.getKey(), Void.class));
        assertEquals(List.of(first), new ArrayList<>(this.catalog.all()));
    }
}
