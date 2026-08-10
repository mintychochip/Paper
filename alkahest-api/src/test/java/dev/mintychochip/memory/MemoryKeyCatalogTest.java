package dev.mintychochip.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.memory.MemoryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MemoryKeyCatalogTest {

    private final MemoryKeyCatalog catalog = MemoryKeyCatalog.createCatalog();

    @AfterEach
    void clearCatalog() {
        this.catalog.clear();
    }

    @Test
    void customMemoryKeyPublishesThroughNamespacedCatalog() {
        final MemoryKey<Integer> zebra = this.catalog.create(
            new NamespacedKey("mintychochip", "zebra"), Integer.class);
        final MemoryKey<String> apple = this.catalog.create(
            new NamespacedKey("mintychochip", "apple"), String.class);

        assertTrue(zebra.isCustom());
        assertTrue(zebra instanceof CustomMemoryKey);
        assertSame(zebra, this.catalog.get(zebra.getKey()));
        assertEquals(
            List.of("mintychochip:apple", "mintychochip:zebra"),
            this.catalog.all().stream().map(key -> key.getKey().toString()).toList()
        );
        assertSame(apple, this.catalog.get(apple.getKey()));
    }
}
