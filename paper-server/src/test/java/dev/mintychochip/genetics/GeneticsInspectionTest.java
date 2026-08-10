package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusId;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import org.bukkit.entity.EntityType;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Normal
class GeneticsInspectionTest {

    @AfterEach
    void clearCache() {
        AnimalGenetics.clearCache();
    }

    @Test
    void missingGenomeDoesNotCreateOne() {
        final UUID id = UUID.randomUUID();
        final AgeableMob mob = mock(AgeableMob.class);
        when(mob.getUUID()).thenReturn(id);
        doReturn(EntityTypes.SHEEP).when(mob).getType();

        assertTrue(GeneticsInspection.inspect(mob).isEmpty());
        assertNull(AnimalGenetics.getGenome(id));
    }

    @Test
    void existingGenomeReturnsProfileAndPhenotype() {
        final UUID id = UUID.randomUUID();
        final AgeableMob mob = mock(AgeableMob.class);
        when(mob.getUUID()).thenReturn(id);
        doReturn(EntityTypes.SHEEP).when(mob).getType();
        final Genome genome = SheepGeneticsProfile.INSTANCE.founder(Sex.FEMALE, new Random(4L));
        AnimalGenetics.setGenome(id, genome);

        final GeneticsInspection.Snapshot snapshot = GeneticsInspection.inspect(mob).orElseThrow();
        assertEquals(EntityType.SHEEP, snapshot.entityType());
        assertEquals("sheep", snapshot.profile().id());
        assertEquals(genome, snapshot.genome());
        assertNotNull(snapshot.phenotype().getOrNull(SheepGeneticsProfile.COLOR_KEY));
    }

    @Test
    void genericCatalogHasNoInventedAlleleList() {
        final GeneticsProfile generic = GeneticsProfiles.generic();
        final var coat = generic.catalog().require(LocusId.of("coat"));

        final GeneticsCatalogDescriptions.Description description =
            GeneticsCatalogDescriptions.describe(generic, coat);
        assertTrue(description.labels().isEmpty());
        assertNull(description.range());
    }

    @Test
    void knownProfileMetadataIsEnumeratedFromExistingConstants() {
        final GeneticsProfile equine = EquineGeneticsProfile.HORSE;
        final var speed = GeneticsCatalogDescriptions.describe(equine, EquineGeneticsProfile.SPEED);

        assertEquals("0.1125 - 0.3375", speed.range());
        assertEquals(EquineGeneticsProfile.COLORS,
            GeneticsCatalogDescriptions.describe(equine, EquineGeneticsProfile.COLOR).labels());
    }
}
