package org.bukkit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the vanilla-only {@link Registry#MATERIAL} / {@link MaterialRegistry} view.
 */
public class MaterialRegistryTest {

    @Test
    public void getResolvesVanillaNonLegacy() {
        assertSame(Material.STONE, Registry.MATERIAL.get(NamespacedKey.minecraft("stone")));
        assertTrue(Registry.MATERIAL.stream().anyMatch(m -> m == Material.STONE));
        assertTrue(Registry.MATERIAL.size() > 0);
        assertInstanceOf(MaterialRegistry.class, Registry.MATERIAL);
        final MaterialRegistry registry = (MaterialRegistry) Registry.MATERIAL;
        assertTrue(registry.isNative(Material.STONE));
    }

    @Test
    public void adapterUsesOnlyNativeRegistry() {
        final MaterialRegistry registry = new MaterialRegistry(
            new Registry.SimpleRegistry<>(VanillaMaterial.class, material -> !material.isLegacy())
        );

        assertSame(Material.STONE, registry.get(NamespacedKey.minecraft("stone")));
        assertTrue(registry.stream().allMatch(Material::isVanilla));
    }

    @Test
    public void doesNotIncludeLegacyMaterials() {
        assertNull(Registry.MATERIAL.get(NamespacedKey.minecraft("legacy_air")));
        for (final Material material : Registry.MATERIAL) {
            assertFalse(material.isLegacy(), () -> "legacy leaked into Registry.MATERIAL: " + material);
        }
    }

    @Test
    public void valuesAndRegistryAreVanillaOnly() {
        for (final Material material : Material.values()) {
            assertTrue(material.isVanilla());
        }
        for (final Material material : Registry.MATERIAL) {
            assertTrue(material.isVanilla());
        }
    }
}
