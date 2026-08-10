package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import java.util.List;
import java.util.UUID;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import java.util.Random;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Mule;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Panda;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Normal
public class SpecialBreedLifecycleTest {
    @AfterEach
    public void cleanup() {
        AnimalGenetics.clearCache();
        AnimalGenetics.setEnabled(true);
    }

    @Test
    public void horseDonkeyPlanSelectsMuleChildProfile() {
        final var plan = GeneticsProfiles.resolveBreed(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE);
        assertTrue(plan.isPresent());
        assertEquals("equine", plan.orElseThrow().familyProfile().id());
        assertEquals("mule", plan.orElseThrow().childProfile().id());
        assertTrue(GeneticsProfiles.resolveBreed(
            EntityType.MULE, EntityType.MULE, EntityType.MULE).isEmpty());
    }

    @Test
    public void ordinaryExplicitProfilesResolveTheirChildProfile() {
        assertEquals("horse", GeneticsProfiles.resolveBreed(
            EntityType.HORSE, EntityType.HORSE, EntityType.HORSE).orElseThrow().childProfile().id());
        assertEquals("sheep", GeneticsProfiles.resolveBreed(
            EntityType.SHEEP, EntityType.SHEEP, EntityType.SHEEP).orElseThrow().childProfile().id());
    }

    @Test
    public void animalPrepareBreedCachesMuleGenomeWithMetadata() {
        final net.minecraft.world.entity.animal.Animal horse = mock(net.minecraft.world.entity.animal.Animal.class);
        final net.minecraft.world.entity.animal.Animal donkey = mock(net.minecraft.world.entity.animal.Animal.class);
        final AgeableMob mule = mock(AgeableMob.class);
        doReturn(EntityTypes.HORSE).when(horse).getType();
        doReturn(EntityTypes.DONKEY).when(donkey).getType();
        doReturn(EntityTypes.MULE).when(mule).getType();
        when(horse.getUUID()).thenReturn(UUID.randomUUID());
        when(donkey.getUUID()).thenReturn(UUID.randomUUID());
        when(mule.getUUID()).thenReturn(UUID.randomUUID());
        when(horse.getRandom()).thenReturn(RandomSource.create(1L));
        when(donkey.getRandom()).thenReturn(RandomSource.create(2L));
        AnimalGenetics.setGenome(horse, EquineGeneticsProfile.HORSE.founder(Sex.FEMALE, new Random(1L)));
        AnimalGenetics.setGenome(donkey, EquineGeneticsProfile.DONKEY.founder(Sex.MALE, new Random(2L)));

        final AnimalGenetics.BreedPrep prep = AnimalGenetics.prepareBreed(horse, donkey, mule);

        assertNotNull(prep);
        assertNotNull(prep.genetics());
        assertNotNull(AnimalGenetics.getGenome(mule));
        assertTrue(AnimalGenetics.getGenome(mule).get(EquineGeneticsProfile.SPEED.id()).isPresent());
    }

    @Test
    public void happyGhastFounderIsAttachedWithoutParentCross() {
        final AgeableMob ghast = Mockito.mock(AgeableMob.class);
        final UUID id = UUID.randomUUID();
        doReturn(EntityTypes.HAPPY_GHAST).when(ghast).getType();
        when(ghast.getUUID()).thenReturn(id);
        when(ghast.getRandom()).thenReturn(RandomSource.create(1L));

        final Genome genome = AnimalGenetics.getOrCreate(ghast, RandomSource.create(2L));

        assertNotNull(genome);
        assertTrue(genome.genes().isEmpty());
        assertTrue(GeneticsProfiles.resolveBreed(
            EntityType.HAPPY_GHAST, EntityType.HAPPY_GHAST, EntityType.HAPPY_GHAST).isEmpty());
    }

    @Test
    public void horseAttributesAndMarkingsApplyDeterministically() {
        final CraftEntity facade = Mockito.mock(
            CraftEntity.class, Mockito.withSettings().extraInterfaces(Horse.class, Attributable.class));
        final Attributable attributable = (Attributable) facade;
        final Horse horse = (Horse) facade;
        final AttributeInstance speed = mock(AttributeInstance.class);
        final AttributeInstance jump = mock(AttributeInstance.class);
        final AttributeInstance health = mock(AttributeInstance.class);
        when(attributable.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
        when(attributable.getAttribute(Attribute.JUMP_STRENGTH)).thenReturn(jump);
        when(attributable.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);

        assertTrue(PhenotypeApplier.apply(facade, EntityType.HORSE, snapshot(
            new PhenotypeTrait("equine.color", "BLACK"),
            new PhenotypeTrait("equine.markings", "WHITE_FIELD"),
            new PhenotypeTrait("equine.speed", "0.225"),
            new PhenotypeTrait("equine.jump", "0.7"),
            new PhenotypeTrait("equine.health", "26.0")
        )));
        verify(horse).setColor(Horse.Color.BLACK);
        verify(horse).setStyle(Horse.Style.WHITEFIELD);
        verify(speed).setBaseValue(0.225D);
        verify(jump).setBaseValue(0.7D);
        verify(health).setBaseValue(26.0D);
    }

    @Test
    public void llamaColorAndStrengthApply() {
        final Llama llama = Mockito.mock(
            Llama.class, Mockito.withSettings().extraInterfaces(Attributable.class));
        assertTrue(PhenotypeApplier.apply(llama, EntityType.LLAMA, snapshot(
            new PhenotypeTrait("llama.color", "GRAY"),
            new PhenotypeTrait("llama.strength", "4")
        )));
        verify(llama).setColor(Llama.Color.GRAY);
        verify(llama).setStrength(4);
    }

    @Test
    public void pandaMainAndHiddenGenesApply() {
        final Panda panda = Mockito.mock(Panda.class);
        assertTrue(PhenotypeApplier.apply(panda, EntityType.PANDA, snapshot(
            new PhenotypeTrait("panda.main", "LAZY"),
            new PhenotypeTrait("panda.hidden", "WEAK")
        )));
        verify(panda).setMainGene(Panda.Gene.LAZY);
        verify(panda).setHiddenGene(Panda.Gene.WEAK);
    }

    @Test
    public void invalidSpecialLabelsAreNoops() {
        final Llama llama = Mockito.mock(Llama.class);
        assertFalse(PhenotypeApplier.apply(llama, EntityType.LLAMA, snapshot(
            new PhenotypeTrait("llama.color", "INVALID"),
            new PhenotypeTrait("llama.strength", "99")
        )));
        final Panda panda = Mockito.mock(Panda.class);
        assertFalse(PhenotypeApplier.apply(panda, EntityType.PANDA, snapshot(
            new PhenotypeTrait("panda.main", "INVALID"),
            new PhenotypeTrait("panda.hidden", "INVALID")
        )));
    }

    @Test
    public void donkeyAndMuleFounderCapturePreservesNumericAttributes() {
        final Donkey donkey = mock(Donkey.class);
        doReturn(EntityTypes.DONKEY).when(donkey).getType();
        final Genome donkeyGenome = captureEquineNumeric(donkey, EquineGeneticsProfile.DONKEY);
        assertEquals("0.2", donkeyGenome.getOrNull(EquineGeneticsProfile.SPEED.id()).alleleA().label());
        assertEquals("0.8", donkeyGenome.getOrNull(EquineGeneticsProfile.JUMP.id()).alleleA().label());
        assertEquals("24.0", donkeyGenome.getOrNull(EquineGeneticsProfile.HEALTH.id()).alleleA().label());

        final Mule mule = mock(Mule.class);
        doReturn(EntityTypes.MULE).when(mule).getType();
        final Genome muleGenome = captureEquineNumeric(mule, EquineGeneticsProfile.MULE);
        assertEquals("0.2", muleGenome.getOrNull(EquineGeneticsProfile.SPEED.id()).alleleA().label());
        assertEquals("0.8", muleGenome.getOrNull(EquineGeneticsProfile.JUMP.id()).alleleA().label());
        assertEquals("24.0", muleGenome.getOrNull(EquineGeneticsProfile.HEALTH.id()).alleleA().label());
    }

    @Test
    public void equinePhenotypeRejectsNonFiniteAndOutOfRangeNumericLabels() {
        for (final String label : new String[] {"NaN", "Infinity", "-Infinity", "0.01", "0.5"}) {
            final Genome genome = equineGenome(EquineGeneticsProfile.SPEED, label);
            assertThrows(IllegalArgumentException.class,
                () -> EquineGeneticsProfile.HORSE.phenotype(genome), label);
        }
        assertThrows(IllegalArgumentException.class,
            () -> EquineGeneticsProfile.HORSE.phenotype(equineGenome(
                EquineGeneticsProfile.JUMP, "1.1")));
        assertThrows(IllegalArgumentException.class,
            () -> EquineGeneticsProfile.HORSE.phenotype(equineGenome(
                EquineGeneticsProfile.HEALTH, "31.0")));
    }

    @Test
    public void equineApplierRejectsInvalidNumericLabelsWithoutMutatingAttributes() {
        final CraftEntity facade = Mockito.mock(
            CraftEntity.class, Mockito.withSettings().extraInterfaces(Horse.class, Attributable.class));
        final Attributable attributable = (Attributable) facade;
        final AttributeInstance speed = mock(AttributeInstance.class);
        final AttributeInstance jump = mock(AttributeInstance.class);
        final AttributeInstance health = mock(AttributeInstance.class);
        when(attributable.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
        when(attributable.getAttribute(Attribute.JUMP_STRENGTH)).thenReturn(jump);
        when(attributable.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);

        assertFalse(PhenotypeApplier.apply(facade, EntityType.HORSE, snapshot(
            new PhenotypeTrait("equine.speed", "NaN"),
            new PhenotypeTrait("equine.jump", "1.1"),
            new PhenotypeTrait("equine.health", "31.0")
        )));
        Mockito.verifyNoInteractions(speed, jump, health);
    }

    private static Genome captureEquineNumeric(
        final AgeableMob entity,
        final dev.mintychochip.genetics.profile.EquineGeneticsProfile profile
    ) {
        final net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
            mock(net.minecraft.world.entity.ai.attributes.AttributeInstance.class);
        final net.minecraft.world.entity.ai.attributes.AttributeInstance jump =
            mock(net.minecraft.world.entity.ai.attributes.AttributeInstance.class);
        final net.minecraft.world.entity.ai.attributes.AttributeInstance health =
            mock(net.minecraft.world.entity.ai.attributes.AttributeInstance.class);
        when(speed.getValue()).thenReturn(0.2D);
        when(jump.getValue()).thenReturn(0.8D);
        when(health.getValue()).thenReturn(24.0D);
        when(entity.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(entity.getAttribute(Attributes.JUMP_STRENGTH)).thenReturn(jump);
        when(entity.getAttribute(Attributes.MAX_HEALTH)).thenReturn(health);
        return FounderCaptures.capture(entity, profile, Sex.FEMALE, new Random(1L));
    }

    private static Genome equineGenome(
        final dev.mintychochip.genetics.model.LocusDefinition replaced,
        final String label
    ) {
        final Genome.Builder genome = Genome.builder(Sex.FEMALE);
        for (final var locus : EquineGeneticsProfile.CATALOG.all()) {
            final String value = locus == replaced
                ? label
                : locus == EquineGeneticsProfile.COLOR ? "WHITE"
                : locus == EquineGeneticsProfile.MARKINGS ? "NONE"
                : locus == EquineGeneticsProfile.SPEED ? "0.2"
                : locus == EquineGeneticsProfile.JUMP ? "0.8" : "24.0";
            final var allele = dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", value);
            genome.put(locus, dev.mintychochip.genetics.model.GeneCopy.diploid(allele, allele));
        }
        return genome.build();
    }

    @Test
    public void villagerTypeAdapterRemainsAvailable() {
        final org.bukkit.entity.Villager villager = mock(org.bukkit.entity.Villager.class);
        assertTrue(PhenotypeApplier.apply(villager, EntityType.VILLAGER, snapshot(
            new PhenotypeTrait("villager.type", "PLAINS"))));
        verify(villager).setVillagerType(org.bukkit.entity.Villager.Type.PLAINS);
    }

    private static PhenotypeSnapshot snapshot(final PhenotypeTrait... traits) {
        return new PhenotypeSnapshot(List.of(traits));
    }
}
