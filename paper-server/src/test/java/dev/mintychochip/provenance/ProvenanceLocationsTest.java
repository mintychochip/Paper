package dev.mintychochip.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
public class ProvenanceLocationsTest {

    @Test
    public void unknownContainerDoesNotBecomeAConcreteRestartLocation() {
        assertFalse(ProvenanceLocations.forContainer(new SimpleContainer(1), 0).isConcrete());
    }

    @Test
    public void stableEntityLocationUsesEntityUuid() {
        final UUID entityId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        assertEquals(
            "container-entity:" + entityId + ":4",
            ProvenanceLocations.forEntityContainer(entityId, 4).display()
        );
    }
}
