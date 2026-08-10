package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Material;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class CustomPlantPlacementTest {

    @Test
    void cropHostRejectsANonCropCarrier() {
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:invalid_crop")
            .host(PlantHostSpec.crop(Material.OAK_LOG))
            .itemMaterial(Material.OAK_LOG)
            .build();

        assertThrows(IllegalArgumentException.class, () -> CustomBlockPlacement.carrierData(definition));
    }

    @Test
    void saplingHostRejectsACropCarrier() {
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:invalid_sapling")
            .host(PlantHostSpec.sapling(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();

        assertThrows(IllegalArgumentException.class, () -> CustomBlockPlacement.carrierData(definition));
    }
}
