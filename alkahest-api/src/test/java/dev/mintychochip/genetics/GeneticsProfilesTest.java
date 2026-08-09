package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.Random;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class GeneticsProfilesTest {

    @Test
    void unknownAnimalTypesUseTheGenericProfile() {
        assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.COW));
        assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.PIG));
    }

    @Test
    void customFounderFactoryControlsGeneratedAlleles() {
        final LocusDefinition marker = LocusDefinition.autosomal(
            "marker",
            1,
            1,
            DominanceMode.COMPLETE
        );
        final LocusCatalog catalog = LocusCatalog.of(List.of(marker));
        final Allele expected = Allele.of("ATGAAACCC", "MARKER");

        final GenomeGenerator generator = new GenomeGenerator(
            catalog,
            new Random(1L),
            (ignored, random) -> expected
        );

        final var genome = generator.generate(Sex.FEMALE);

        assertEquals("MARKER", genome.get(marker.id()).orElseThrow().alleleA().label());
        assertEquals("MARKER", genome.get(marker.id()).orElseThrow().alleleB().label());
    }
}
