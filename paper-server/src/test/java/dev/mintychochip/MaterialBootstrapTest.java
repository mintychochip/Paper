package dev.mintychochip;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.VanillaMaterial;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
public class MaterialBootstrapTest {

    @Test
    public void vanillaMaterialEnumConstantsNonNull() {
        // Prefer VanillaMaterial for bootstrap assertions: Material interface constants can
        // still be null under partial class-init order in the Normal suite.
        assertNotNull(VanillaMaterial.GLASS);
        assertNotNull(VanillaMaterial.GLOWSTONE);
        assertNotNull(VanillaMaterial.IRON_ORE);
        assertTrue(VanillaMaterial.GLASS instanceof Material);
        assertTrue(VanillaMaterial.GLASS.isVanilla());
    }

    @Test
    public void registryMaterialIncludesVanilla() {
        assertSame(VanillaMaterial.STONE, Registry.MATERIAL.get(NamespacedKey.minecraft("stone")));
        assertSame(VanillaMaterial.STONE,
            Material.getByKey(NamespacedKey.minecraft("stone")).orElseThrow());
        assertTrue(Registry.MATERIAL.stream().allMatch(Material::isVanilla));
    }
}
