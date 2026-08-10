package org.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class NativeApiContractTest {

    @Test
    void nativeTypesRetainEnumAndFinalClassContracts() {
        assertTrue(Particle.class.isEnum());
        assertTrue(PotionType.class.isEnum());
        assertTrue(Modifier.isFinal(MemoryKey.class.getModifiers()));
    }

    @Test
    void nativeLookupContractsRemainAvailable() {
        assertEquals(Particle.POOF, Particle.valueOf("POOF"));
        assertEquals(PotionType.WATER, PotionType.valueOf("WATER"));
        assertEquals(MemoryKey.HOME, MemoryKey.getByKey(NamespacedKey.minecraft("home")));
    }
}
