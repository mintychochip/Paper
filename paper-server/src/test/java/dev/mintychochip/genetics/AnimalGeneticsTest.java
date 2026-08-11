package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mintychochip.genetics.catalog.DefaultGeneticsCatalog;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.BreedingResult;
import dev.mintychochip.genetics.engine.GeneticMatePolicy;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.io.GenomeCodec;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.entity.EntityType;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Normal
public class AnimalGeneticsTest {
    @AfterEach
    public void cleanup() {
        AnimalGenetics.clearCache();
        AnimalGenetics.setEnabled(true);
    }

    @Test
    public void setGenomeThenGetReturnsSameState() {
        final UUID id = UUID.randomUUID();
        final Genome genome = sampleFemale();
        AnimalGenetics.setGenome(id, genome);
        final Genome loaded = AnimalGenetics.getGenome(id);
        assertNotNull(loaded);
        assertTrue(GenomeCodec.deepEquals(genome, loaded));
    }

    @Test
    public void codecRoundTripViaServerHelper() {
        final Genome genome = sampleMale();
        final Genome back = AnimalGenetics.roundTrip(genome);
        assertTrue(GenomeCodec.deepEquals(genome, back));
        assertTrue(back.getOrNull(DefaultGeneticsCatalog.COAT.id()).isHemizygous());
    }

    @Test
    public void sameSexCrossRejected() {
        final Genome maleA = sampleMale();
        final Genome maleB = sampleMale();
        assertFalse(GeneticMatePolicy.allowsMate(maleA, maleB));
        final Optional<BreedingResult> result = AnimalGenetics.cross(maleA, maleB, new Random(1L), MutationSettings.NONE, RecombinationSettings.NONE);
        assertTrue(result.isEmpty());
    }

    @Test
    public void oppositeSexCrossProducesChildGenomeFromEngine() {
        final Genome father = sampleMale();
        final Genome mother = sampleFemale();
        assertTrue(GeneticMatePolicy.allowsMate(father, mother));
        final Optional<BreedingResult> result = AnimalGenetics.cross(father, mother, new Random(42L), MutationSettings.NONE, RecombinationSettings.NONE);
        assertTrue(result.isPresent());
        final Genome child = result.get().child();
        assertNotNull(child);
        assertTrue(child.get(DefaultGeneticsCatalog.COAT.id()).isPresent());
        assertTrue(child.get(DefaultGeneticsCatalog.VITALITY.id()).isPresent());
        assertTrue(child.get(DefaultGeneticsCatalog.MT_VIGOR.id()).isPresent());
        final Allele motherMt = mother.getOrNull(DefaultGeneticsCatalog.MT_VIGOR.id()).alleleA();
        final Allele childMt = child.getOrNull(DefaultGeneticsCatalog.MT_VIGOR.id()).alleleA();
        assertEquals(motherMt.sequence(), childMt.sequence());
    }

    @Test
    public void nbtKeyConstantStableForPersistence() {
        assertEquals("MintyGenome", AnimalGenetics.NBT_KEY);
    }

    @Test
    public void sheepProfileIsSelectedByEntityType() {
        assertEquals("sheep", AnimalGenetics.profileFor(EntityType.SHEEP).id());
        assertEquals("cow", AnimalGenetics.profileFor(EntityType.COW).id());
        assertEquals("generic", AnimalGenetics.profileFor(EntityType.POLAR_BEAR).id());
    }

    @Test
    public void snapshotsOfExposesMotherFatherChildSexAndPhenotypes() {
        final Genome mother = sampleFemale();
        final Genome father = sampleMale();
        final Optional<BreedingResult> result = AnimalGenetics.cross(father, mother, new Random(7L), MutationSettings.NONE, RecombinationSettings.NONE);
        assertTrue(result.isPresent());
        final Genome child = result.get().child();
        final var genetics = AnimalGenetics.snapshotsOf(mother, father, child);
        assertEquals(Sex.FEMALE, genetics.motherSex());
        assertEquals(Sex.MALE, genetics.fatherSex());
        assertEquals(child.sex(), genetics.childSex());
        assertNotNull(genetics.motherPhenotype());
        assertNotNull(genetics.fatherPhenotype());
        assertNotNull(genetics.childPhenotype());
        assertFalse(genetics.child().loci().isEmpty());
    }

    @Test
    public void snapshotsOfDecodesExplicitCatVariantBeforeAdapterResolution() {
        final var profile = AnimalGenetics.profileFor(EntityType.CAT);
        final Genome mother = profile.founder(Sex.FEMALE, new Random(1L));
        final Genome father = profile.founder(Sex.MALE, new Random(2L));
        final Genome child = profile.founder(Sex.FEMALE, new Random(3L));
        final var genetics = AnimalGenetics.snapshotsOf(mother, father, child, EntityType.CAT);
        assertNotNull(genetics.childPhenotype().getOrNull("cat.variant"));
        assertEquals(Optional.empty(), genetics.childVariant());
    }

    @Test
    public void discardGenomeRemovesCachedChildAfterCancelledBreed() {
        final UUID childId = UUID.randomUUID();
        AnimalGenetics.setGenome(childId, sampleFemale());
        assertNotNull(AnimalGenetics.getGenome(childId));
        AnimalGenetics.discardGenome(childId);
        assertEquals(null, AnimalGenetics.getGenome(childId));
    }

    @Test
    public void loadCachesWithoutApplyingAndSaveWritesBothKeys() {
        final AgeableMob ageable = mock(AgeableMob.class);
        final UUID id = UUID.randomUUID();
        when(ageable.getUUID()).thenReturn(id);
        final Genome genome = sampleFemale();
        final ValueInput input = mock(ValueInput.class);
        when(input.getString(AnimalGenetics.NBT_KEY)).thenReturn(Optional.of(GenomeCodec.encode(genome)));
        when(input.getString(AnimalGenetics.PROFILE_NBT_KEY)).thenReturn(Optional.of("legacy-profile"));
        AnimalGenetics.load(ageable, input);
        assertTrue(GenomeCodec.deepEquals(genome, AnimalGenetics.getGenome(id)));
        final ValueOutput output = mock(ValueOutput.class);
        AnimalGenetics.save(ageable, output);
        verify(output).putString(AnimalGenetics.NBT_KEY, GenomeCodec.encode(genome));
        verify(output).putString(AnimalGenetics.PROFILE_NBT_KEY, "legacy-profile");
    }

    @Test
    public void ageableProfileAndPhenotypeUseResolvedType() {
        final AgeableMob ageable = mock(AgeableMob.class);
        doReturn(net.minecraft.world.entity.EntityTypes.SHEEP).when(ageable).getType();
        final Genome genome = AnimalGenetics.profileFor(EntityType.SHEEP).founder(Sex.FEMALE, new Random(1L));
        assertEquals("sheep", AnimalGenetics.profile(ageable).id());
        assertNotNull(AnimalGenetics.phenotypeOf(ageable, genome));
    }

    @Test
    public void onAddedToWorldAgeableEntryIsIdempotent() {
        final AgeableMob ageable = mock(AgeableMob.class);
        final UUID id = UUID.randomUUID();
        when(ageable.getUUID()).thenReturn(id);
        doReturn(net.minecraft.world.entity.EntityTypes.ARMADILLO).when(ageable).getType();
        final net.minecraft.util.RandomSource random = mock(net.minecraft.util.RandomSource.class);
        when(ageable.getRandom()).thenReturn(random);
        final Genome expected = dev.mintychochip.genetics.profile.EmptyGeneticsProfile.of("armadillo")
            .founder(Sex.FEMALE, new Random(1L));
        AnimalGenetics.setGenome(id, expected);
        AnimalGenetics.onAddedToWorld(ageable);
        final Genome first = AnimalGenetics.getGenome(id);
        AnimalGenetics.onAddedToWorld(ageable);
        final Genome second = AnimalGenetics.getGenome(id);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(GenomeCodec.deepEquals(first, second));
    }

    private static Genome sampleMale() {
        return Genome.builder(Sex.MALE)
            .put(DefaultGeneticsCatalog.COAT, GeneCopy.hemizygous(Allele.of("ATGAAACCC", "O")))
            .put(DefaultGeneticsCatalog.VITALITY, GeneCopy.diploid(Allele.of("ATGAAAAAA"), Allele.of("ATGAAAAAA")))
            .put(DefaultGeneticsCatalog.MT_VIGOR, GeneCopy.diploid(Allele.of("ATGCCCCCC"), Allele.of("ATGCCCCCC")))
            .build();
    }

    private static Genome sampleFemale() {
        return Genome.builder(Sex.FEMALE)
            .put(DefaultGeneticsCatalog.COAT, GeneCopy.diploid(Allele.of("ATGCCCGGG", "o"), Allele.of("ATGCCCGGG", "o")))
            .put(DefaultGeneticsCatalog.VITALITY, GeneCopy.diploid(Allele.of("ATGGGGGGG"), Allele.of("ATGGGGGGG")))
            .put(DefaultGeneticsCatalog.MT_VIGOR, GeneCopy.diploid(Allele.of("ATGTTTTTT"), Allele.of("ATGTTTTTT")))
            .build();
    }
}
