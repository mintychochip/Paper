package dev.mintychochip.customblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class CustomPlantHostSpecTest {

    @Test
    void cropAndSaplingFactoriesExposePlantHostMetadata() {
        final PlantHostSpec crop = PlantHostSpec.crop(Material.WHEAT);
        final PlantHostSpec sapling = PlantHostSpec.sapling(Material.OAK_SAPLING);

        assertEquals(BlockHostType.PLANT, crop.type());
        assertEquals(PlantKind.CROP, crop.kind());
        assertEquals(Material.WHEAT, crop.carrier());
        assertEquals(PlantKind.SAPLING, sapling.kind());
        assertEquals(Material.OAK_SAPLING, sapling.carrier());
    }

    @Test
    void plantHostRejectsNullAndAirCarriers() {
        assertThrows(NullPointerException.class, () -> PlantHostSpec.crop(null));
        assertThrows(IllegalArgumentException.class, () -> PlantHostSpec.crop(Material.AIR));
    }

    @Test
    void definitionReportsSelectedPlantCarrier() {
        final CustomBlockDefinition definition = CustomBlockDefinition.builder("test:crop")
            .host(PlantHostSpec.crop(Material.WHEAT))
            .itemMaterial(Material.WHEAT)
            .build();

        assertEquals(BlockHostType.PLANT, definition.hostType());
        assertEquals(Material.WHEAT, definition.carrierMaterial());
    }
}
