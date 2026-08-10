package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.EmptyGeneticsProfile;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import dev.mintychochip.genetics.profile.VariantGeneticsProfile;
import java.util.Iterator;
import java.util.Random;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Normal
class FounderCaptureTest {

    @Test
    void representativeVariantStateIsDuplicatedIntoBothCopies() {
        final Axolotl axolotl = mock(Axolotl.class);
        doReturn(EntityTypes.AXOLOTL).when(axolotl).getType();
        when(axolotl.getVariant()).thenReturn(Axolotl.Variant.GOLD);
        final GeneticsProfile profile = AnimalGenetics.profileFor(EntityType.AXOLOTL);
        final VariantGeneticsProfile variant = (VariantGeneticsProfile) profile;
        final var locus = firstLocus(profile);

        final Genome genome = FounderCaptures.capture(axolotl, profile, Sex.FEMALE, new Random(1L));

        final GeneCopy copy = genome.get(locus.id()).orElseThrow();
        assertEquals("GOLD", copy.alleleA().label());
        assertEquals("GOLD", copy.alleleB().label());
        assertFalse(copy.isHemizygous());
    }

    @Test
    void sheepDyeColorIsDuplicatedIntoBothCopies() {
        final Sheep sheep = mock(Sheep.class);
        doReturn(EntityTypes.SHEEP).when(sheep).getType();
        when(sheep.getColor()).thenReturn(DyeColor.PINK);

        final Genome genome = FounderCaptures.capture(
            sheep, SheepGeneticsProfile.INSTANCE, Sex.FEMALE, new Random(1L)
        );

        final String color = SheepGeneticsProfile.INSTANCE.phenotype(genome)
            .getOrNull(SheepGeneticsProfile.COLOR_KEY);
        assertEquals("PINK", color);
        final GeneCopy base = genome.get(SheepGeneticsProfile.BASE.id()).orElseThrow();
        assertEquals(base.alleleA().label(), base.alleleB().label());
    }

    @ParameterizedTest
    @EnumSource(DyeColor.class)
    void everySheepDyeColorCapturesValidGenotypeLabels(final DyeColor color) {
        final Sheep sheep = mock(Sheep.class);
        doReturn(EntityTypes.SHEEP).when(sheep).getType();
        when(sheep.getColor()).thenReturn(color);

        final Genome genome = FounderCaptures.capture(
            sheep, SheepGeneticsProfile.INSTANCE, Sex.FEMALE, new Random(1L)
        );

        final GeneCopy base = genome.get(SheepGeneticsProfile.BASE.id()).orElseThrow();
        final String baseLabel = switch (color) {
            case GRAY -> "BLACK";
            case LIGHT_GRAY -> "BROWN";
            case PINK -> "RED";
            case ORANGE -> "YELLOW";
            default -> color.name();
        };
        assertEquals(baseLabel, base.alleleA().label());
        assertEquals(baseLabel, base.alleleB().label());
        final String dilution = switch (color) {
            case GRAY, LIGHT_GRAY, PINK, ORANGE -> "DILUTE";
            default -> "FULL";
        };
        final GeneCopy dilutionCopy = genome.get(SheepGeneticsProfile.DILUTION.id()).orElseThrow();
        assertEquals(dilution, dilutionCopy.alleleA().label());
        assertEquals(dilution, dilutionCopy.alleleB().label());
        final GeneCopy albinism = genome.get(SheepGeneticsProfile.ALBINISM.id()).orElseThrow();
        assertEquals("PIGMENTED", albinism.alleleA().label());
        assertEquals("PIGMENTED", albinism.alleleB().label());
        assertEquals(color.name(), SheepGeneticsProfile.INSTANCE.phenotype(genome)
            .getOrNull(SheepGeneticsProfile.COLOR_KEY));
    }

    @Test
    void registryBackedCatVariantCapturePreservesCanonicalLabel() {
        final AgeableMob ageable = mock(AgeableMob.class);
        doReturn(EntityTypes.CAT).when(ageable).getType();
        final org.bukkit.craftbukkit.entity.CraftEntity facade = mock(
            org.bukkit.craftbukkit.entity.CraftEntity.class, withSettings().extraInterfaces(Cat.class)
        );
        final Cat cat = (Cat) facade;
        doReturn(EntityType.CAT).when(facade).getType();
        when(cat.getCatType()).thenReturn(Cat.Type.JELLIE);
        doReturn(facade).when(ageable).getBukkitEntity();
        final GeneticsProfile profile = AnimalGenetics.profileFor(EntityType.CAT);

        final Genome genome = FounderCaptures.capture(ageable, profile, Sex.FEMALE, zeroRandom());
        assertEquals("JELLIE", labelAt(genome, firstLocus(profile)));
        assertEquals("JELLIE", profile.phenotype(genome).getOrNull("cat.variant"));
    }

    @Test
    void registryBackedWolfVariantCapturePreservesCanonicalLabel() {
        final AgeableMob ageable = mock(AgeableMob.class);
        doReturn(EntityTypes.WOLF).when(ageable).getType();
        final org.bukkit.craftbukkit.entity.CraftEntity facade = mock(
            org.bukkit.craftbukkit.entity.CraftEntity.class, withSettings().extraInterfaces(Wolf.class)
        );
        final Wolf wolf = (Wolf) facade;
        doReturn(EntityType.WOLF).when(facade).getType();
        when(wolf.getVariant()).thenReturn(Wolf.Variant.SPOTTED);
        doReturn(facade).when(ageable).getBukkitEntity();
        final GeneticsProfile profile = AnimalGenetics.profileFor(EntityType.WOLF);
        final Genome genome = FounderCaptures.capture(ageable, profile, Sex.FEMALE, zeroRandom());

        assertEquals("SPOTTED", labelAt(genome, firstLocus(profile)));
        assertEquals("SPOTTED", profile.phenotype(genome).getOrNull("wolf.variant"));
    }

    @Test
    void registryBackedVillagerTypeCapturePreservesCanonicalLabel() {
        final AgeableMob ageable = mock(AgeableMob.class);
        doReturn(EntityTypes.VILLAGER).when(ageable).getType();
        final org.bukkit.craftbukkit.entity.CraftEntity facade = mock(
            org.bukkit.craftbukkit.entity.CraftEntity.class, withSettings().extraInterfaces(Villager.class)
        );
        final Villager villager = (Villager) facade;
        doReturn(EntityType.VILLAGER).when(facade).getType();
        when(villager.getVillagerType()).thenReturn(Villager.Type.PLAINS);
        doReturn(facade).when(ageable).getBukkitEntity();
        final GeneticsProfile profile = AnimalGenetics.profileFor(EntityType.VILLAGER);
        final Genome genome = FounderCaptures.capture(ageable, profile, Sex.FEMALE, zeroRandom());

        assertEquals("PLAINS", labelAt(genome, firstLocus(profile)));
        assertEquals("PLAINS", profile.phenotype(genome).getOrNull("villager.type"));
    }

    @Test
    void horseNumericStateIsCapturedAsHomozygousAlleles() {
        final Horse horse = mock(Horse.class);
        doReturn(EntityTypes.HORSE).when(horse).getType();
        when(horse.getVariant()).thenReturn(net.minecraft.world.entity.animal.equine.Variant.CHESTNUT);
        when(horse.getMarkings()).thenReturn(net.minecraft.world.entity.animal.equine.Markings.WHITE_DOTS);
        final AttributeInstance speed = attribute(0.225D);
        final AttributeInstance jump = attribute(0.7D);
        final AttributeInstance health = attribute(26.0D);
        when(horse.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(horse.getAttribute(Attributes.JUMP_STRENGTH)).thenReturn(jump);
        when(horse.getAttribute(Attributes.MAX_HEALTH)).thenReturn(health);

        final Genome genome = FounderCaptures.capture(
            horse, EquineGeneticsProfile.HORSE, Sex.MALE, new Random(1L)
        );

        assertEquals("CHESTNUT", labelAt(genome, EquineGeneticsProfile.COLOR));
        assertEquals("WHITE_DOTS", labelAt(genome, EquineGeneticsProfile.MARKINGS));
        assertEquals("0.7", labelAt(genome, EquineGeneticsProfile.JUMP));
        assertEquals("0.225", labelAt(genome, EquineGeneticsProfile.SPEED));
        assertEquals("26.0", labelAt(genome, EquineGeneticsProfile.HEALTH));
        for (final var locus : EquineGeneticsProfile.CATALOG.all()) {
            final GeneCopy copy = genome.get(locus.id()).orElseThrow();
            assertEquals(copy.alleleA().label(), copy.alleleB().label());
        }
    }

    @Test
    void emptyProfileDelegatesToFounderGeneration() {
        final AgeableMob mob = mock(AgeableMob.class);
        doReturn(EntityTypes.ARMADILLO).when(mob).getType();
        final EmptyGeneticsProfile profile = EmptyGeneticsProfile.of("armadillo");

        final Genome genome = FounderCaptures.capture(mob, profile, Sex.MALE, new Random(7L));

        assertNotNull(genome);
        assertEquals(0, genome.genes().size());
    }

    @Test
    void unknownEntityTypeDelegatesToGenericProfileFounder() {
        final GeneticsProfile profile = AnimalGenetics.profileFor(EntityType.POLAR_BEAR);
        final AgeableMob mob = mock(AgeableMob.class);
        doReturn(EntityTypes.POLAR_BEAR).when(mob).getType();

        final Genome genome = FounderCaptures.capture(mob, profile, Sex.FEMALE, new Random(3L));

        assertNotNull(genome);
        assertNotNull(genome.get(dev.mintychochip.genetics.catalog.DefaultGeneticsCatalog.COAT.id()));
    }

    private static dev.mintychochip.genetics.model.LocusDefinition firstLocus(final GeneticsProfile profile) {
        final Iterator<dev.mintychochip.genetics.model.LocusDefinition> loci = profile.catalog().all().iterator();
        return loci.next();
    }

    private static String labelAt(final Genome genome, final dev.mintychochip.genetics.model.LocusDefinition locus) {
        return genome.get(locus.id()).orElseThrow().alleleA().label();
    }

    private static AttributeInstance attribute(final double value) {
        final AttributeInstance instance = mock(AttributeInstance.class);
        when(instance.getValue()).thenReturn(value);
        return instance;
    }

    private static Random zeroRandom() {
        return new Random(1L) {
            @Override
            public int nextInt(final int bound) {
                return 0;
            }

            @Override
            public int nextInt() {
                return 0;
            }
        };
    }
}
