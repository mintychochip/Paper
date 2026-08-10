package dev.mintychochip.potion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PotionTypeCatalogTest {

    private final PotionTypeCatalog catalog = PotionTypeCatalog.create();

    @AfterEach
    void clearCatalog() {
        this.catalog.clear();
    }

    @Test
    void customPotionPublishesThroughNamespacedCatalog() {
        final PotionType type = this.catalog.builder(
                new NamespacedKey("mintychochip", "swift_custom"))
            .effects(List.of())
            .upgradeable(true)
            .extendable(true)
            .maxLevel(3)
            .requiredFeatures(Set.of(FeatureFlag.VANILLA))
            .build();

        assertTrue(type.isCustom());
        assertEquals(3, type.getMaxLevel());
        assertTrue(type instanceof CustomPotionType);
        assertEquals(Set.of(FeatureFlag.VANILLA), type.requiredFeatures());
        assertSame(type, this.catalog.get(type.getKey()));
        assertSame(type, this.catalog.asMap().get(type.getKey()));
    }
}
