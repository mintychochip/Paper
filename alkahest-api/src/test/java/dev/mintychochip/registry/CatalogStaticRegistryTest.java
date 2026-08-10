package dev.mintychochip.registry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.customblock.CustomBlockDefinition;
import dev.mintychochip.customblock.CustomBlocks;
import dev.mintychochip.customblock.PacketHostSpec;
import dev.mintychochip.customentity.CustomEntities;
import dev.mintychochip.customentity.CustomEntityDefinition;
import dev.mintychochip.memory.CustomMemoryKey;
import dev.mintychochip.memory.MemoryKeyCatalog;
import dev.mintychochip.particle.ParticleCatalog;
import dev.mintychochip.potion.PotionTypeCatalog;
import java.util.List;
import java.util.Set;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionType;
import org.bukkit.entity.memory.MemoryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CatalogStaticRegistryTest {

    @AfterEach
    void clearCatalogs() {
        CustomBlocks.reset();
        CustomEntities.reset();
        ParticleCatalog.global().clear();
        PotionTypeCatalog.global().clear();
        MemoryKeyCatalog.global().clear();
    }

    @Test
    void staticRegistriesResolveInjectedCatalogValues() {
        final CustomBlockDefinition material = CustomBlockDefinition.builder("mintychochip:catalog_material")
            .host(PacketHostSpec.defaults())
            .build();
        CustomBlocks.register(material);

        final CustomEntityDefinition entity = CustomEntityDefinition.builder("mintychochip:catalog_entity")
            .blockModel(org.bukkit.Material.GLOWSTONE)
            .build();
        CustomEntities.register(entity);

        final var particle = ParticleCatalog.global().register(
            new NamespacedKey("mintychochip", "catalog_particle"), Void.class);
        final PotionType potion = PotionTypeCatalog.global().register(
            new NamespacedKey("mintychochip", "catalog_potion"), List.of(), false, false, 1,
            Set.of(FeatureFlag.VANILLA));
        final MemoryKey<String> memory = MemoryKeyCatalog.global().create(
            new NamespacedKey("mintychochip", "catalog_memory"), String.class);

        assertSame(material, Registry.MATERIAL.get(material.getKey()));
        assertSame(entity, Registry.ENTITY_TYPE.get(entity.getKey()));
        assertSame(particle, Registry.PARTICLE_TYPE.get(particle.getKey()));
        assertSame(potion, Registry.POTION.get(potion.getKey()));
        assertSame(memory, Registry.MEMORY_MODULE_TYPE.get(memory.getKey()));
        assertTrue(memory instanceof CustomMemoryKey);
    }
}
