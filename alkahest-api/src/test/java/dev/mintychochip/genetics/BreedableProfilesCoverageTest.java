package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.engine.BreedingEngine;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.io.GenomeCodec;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.BreedableEntityTypes;
import dev.mintychochip.genetics.profile.EmptyGeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import dev.mintychochip.genetics.profile.VariantGeneticsProfile;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BreedableProfilesCoverageTest {

    @Test
    void everyBreedableTypeHasAnExplicitProfile() {
        for (final EntityType type : BreedableEntityTypes.explicitTypes()) {
            final GeneticsProfile profile = GeneticsProfiles.forEntityType(type);
            assertTrue(GeneticsProfiles.isExplicit(type));
            assertNotEquals(GeneticsProfiles.generic(), profile);
            assertFalse(profile.id().isBlank());
            assertNotNull(profile.catalog());
            assertNotNull(profile.mutation());
            assertNotNull(profile.recombination());
            assertNotNull(profile.phenotype(profile.founder(Sex.FEMALE, new Random(1L))));
        }
    }

    @Test
    void unknownTypesRetainGenericFallback() {
        assertEquals(GeneticsProfiles.generic(), GeneticsProfiles.forEntityType(EntityType.POLAR_BEAR));
    }

    @Test
    void parentBreedingTypesIsExactlyTheParentSubset() {
        final List<EntityType> expected = List.of(
            EntityType.ARMADILLO,
            EntityType.AXOLOTL,
            EntityType.BEE,
            EntityType.CAMEL,
            EntityType.CHICKEN,
            EntityType.CAT,
            EntityType.COW,
            EntityType.MOOSHROOM,
            EntityType.DONKEY,
            EntityType.HORSE,
            EntityType.LLAMA,
            EntityType.OCELOT,
            EntityType.FOX,
            EntityType.FROG,
            EntityType.GOAT,
            EntityType.HOGLIN,
            EntityType.NAUTILUS,
            EntityType.PANDA,
            EntityType.PIG,
            EntityType.RABBIT,
            EntityType.SHEEP,
            EntityType.SNIFFER,
            EntityType.STRIDER,
            EntityType.TURTLE,
            EntityType.WOLF,
            EntityType.VILLAGER
        );
        assertEquals(Set.copyOf(expected), BreedableEntityTypes.parentBreedingTypes());
        assertEquals(28, BreedableEntityTypes.explicitTypes().size());
        assertTrue(BreedableEntityTypes.explicitTypes().containsAll(BreedableEntityTypes.parentBreedingTypes()));
        assertTrue(BreedableEntityTypes.explicitTypes().contains(EntityType.MULE));
        assertTrue(BreedableEntityTypes.explicitTypes().contains(EntityType.HAPPY_GHAST));
    }

    @ParameterizedTest
    @MethodSource("variantProfiles")
    void variantProfileDecodesTheSingleLabelAtItsLocusKey(
        final String id,
        final String locusKey,
        final String label
    ) {
        final VariantGeneticsProfile profile = VariantGeneticsProfile.of(id, locusKey, List.of(label));
        final Genome genome = profile.founder(Sex.FEMALE, new Random(1L));

        final List<PhenotypeTrait> traits = profile.phenotype(genome).traits();
        assertEquals(1, traits.size(), "variant profile exposes exactly one trait");
        assertEquals(locusKey, traits.get(0).key());
        assertEquals(label, traits.get(0).value());
    }

    static List<Object[]> variantProfiles() {
        return List.of(
            new Object[] {"axolotl", "axolotl.variant", "LUCY"},
            new Object[] {"cat", "cat.variant", "TABBY"},
            new Object[] {"chicken", "chicken.variant", "PLYMOUTH_ROCK"},
            new Object[] {"cow", "cow.variant", "NORMAL"},
            new Object[] {"mooshroom", "mooshroom.variant", "RED"},
            new Object[] {"fox", "fox.variant", "RED"},
            new Object[] {"frog", "frog.variant", "TEMPERATE"},
            new Object[] {"pig", "pig.variant", "MEATY"},
            new Object[] {"rabbit", "rabbit.variant", "BROWN"},
            new Object[] {"wolf", "wolf.variant", "PALE"}
        );
    }

    @Test
    void emptyProfileGeneratesCrossesAndCodecRoundTripsWithoutLoci() {
        final EmptyGeneticsProfile profile = EmptyGeneticsProfile.of("armadillo");

        final Genome mother = profile.founder(Sex.FEMALE, new Random(1L));
        final Genome father = profile.founder(Sex.MALE, new Random(2L));
        assertTrue(mother.genes().isEmpty(), "empty catalog produces an empty founder genome");
        assertEquals(List.of(), profile.phenotype(mother).traits());

        final Genome child = new BreedingEngine(
            profile.catalog(),
            RecombinationSettings.DEFAULT,
            MutationSettings.NONE,
            new Random(3L)
        ).cross(father, mother).orElseThrow().child();
        assertTrue(child.genes().isEmpty(), "a cross over zero loci produces an empty child genome");

        final String json = GenomeCodec.encode(mother);
        final Genome decoded = GenomeCodec.decode(json);
        assertTrue(GenomeCodec.deepEquals(mother, decoded), "empty genome survives the codec round trip");
        assertEquals(Sex.FEMALE, decoded.sex());
    }
}
