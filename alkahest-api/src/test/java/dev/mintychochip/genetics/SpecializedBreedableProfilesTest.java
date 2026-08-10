package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.genetics.profile.BreedContext;
import dev.mintychochip.genetics.profile.BreedPlan;
import dev.mintychochip.genetics.profile.EquineGeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import dev.mintychochip.genetics.profile.PandaGeneticsProfile;
import dev.mintychochip.genetics.profile.VillagerGeneticsProfile;
import java.util.random.RandomGenerator;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * Observable contracts for the specialized breedable profiles: panda gene
 * resolution, horse/donkey mule planning, bound-reflecting quantitative equine
 * traits, llama/villager resolution, and unrelated-combination rejection.
 */
class SpecializedBreedableProfilesTest {

    // ---- panda ----

    @Test
    void pandaRecessiveGenesResolveLikeVanilla() {
        assertEquals(
            "BROWN",
            PandaGeneticsProfile.INSTANCE.phenotype(pandaGenome("BROWN", "BROWN"))
                .getOrNull(PandaGeneticsProfile.MAIN.phenotypeKey()),
            "a recessive main gene equals the hidden gene and is visible"
        );
        assertEquals(
            "NORMAL",
            PandaGeneticsProfile.INSTANCE.phenotype(pandaGenome("BROWN", "NORMAL"))
                .getOrNull(PandaGeneticsProfile.MAIN.phenotypeKey()),
            "a recessive main gene that differs from the hidden gene resolves NORMAL"
        );
        assertEquals(
            "LAZY",
            PandaGeneticsProfile.INSTANCE.phenotype(pandaGenome("LAZY", "BROWN"))
                .getOrNull(PandaGeneticsProfile.MAIN.phenotypeKey()),
            "a non-recessive main gene is directly visible"
        );
    }

    // ---- quantitative equine trait formula (exact vanilla arithmetic) ----

    @Test
    void equineNumericTraitUsesReflectedVanillaFormula() {
        // speed range [0.1125, 0.3375]
        assertEquals(0.26625D, offspring(0.3D, 0.3D, 0.1125D, 0.3375D, 0.0D, 0.0D, 0.0D), 1.0E-9D);
        assertEquals(0.33375D, offspring(0.3D, 0.3D, 0.1125D, 0.3375D, 1.0D, 1.0D, 1.0D), 1.0E-9D);
        // jump range [0.4, 1.0]
        assertEquals(0.81D, offspring(0.9D, 0.9D, 0.4D, 1.0D, 0.0D, 0.0D, 0.0D), 1.0E-9D);
        assertEquals(0.99D, offspring(0.9D, 0.9D, 0.4D, 1.0D, 1.0D, 1.0D, 1.0D), 1.0E-9D);
        // health range [15.0, 30.0]
        assertEquals(17.75D, offspring(20.0D, 20.0D, 15.0D, 30.0D, 0.0D, 0.0D, 0.0D), 1.0E-9D);
        assertEquals(22.25D, offspring(20.0D, 20.0D, 15.0D, 30.0D, 1.0D, 1.0D, 1.0D), 1.0E-9D);
        // one-reflection bound handling: parents at the max, below-average rolls
        assertEquals(0.30375D, offspring(0.3375D, 0.3375D, 0.1125D, 0.3375D, 0.0D, 0.0D, 0.0D), 1.0E-9D);
        assertEquals(0.91D, offspring(1.0D, 1.0D, 0.4D, 1.0D, 0.0D, 0.0D, 0.0D), 1.0E-9D);
        assertEquals(17.25D, offspring(15.0D, 15.0D, 15.0D, 30.0D, 1.0D, 1.0D, 1.0D), 1.0E-9D);
        assertThrows(
            IllegalArgumentException.class,
            () -> offspring(0.5D, 0.5D, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D)
        );
    }

    // ---- breed-plan resolution ----

    @Test
    void horseAndDonkeyResolveToMulePlan() {
        final BreedPlan plan = GeneticsProfiles.resolveBreed(
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE
        ).orElseThrow();
        assertEquals("mule", plan.childProfile().id());
        assertEquals("equine", plan.familyProfile().id());
    }

    @Test
    void ordinarySameFamilyCrossResolvesSharedProfile() {
        final BreedPlan plan = GeneticsProfiles.resolveBreed(
            EntityType.SHEEP,
            EntityType.SHEEP,
            EntityType.SHEEP
        ).orElseThrow();
        assertEquals("sheep", plan.childProfile().id());
    }

    @Test
    void unrelatedParentAndChildCombinationsAreRejected() {
        assertTrue(GeneticsProfiles.resolveBreed(EntityType.SHEEP, EntityType.SHEEP, EntityType.WOLF).isEmpty(),
            "a sheep child must resolve only from sheep parents");
        assertTrue(GeneticsProfiles.resolveBreed(EntityType.HORSE, EntityType.SHEEP, EntityType.HORSE).isEmpty(),
            "mixed-species parents cannot produce a horse");
        assertTrue(GeneticsProfiles.resolveBreed(EntityType.SHEEP, EntityType.WOLF, EntityType.SHEEP).isEmpty(),
            "unrelated parent pair is rejected");
        assertTrue(GeneticsProfiles.resolveBreed(EntityType.MULE, EntityType.MULE, EntityType.MULE).isEmpty(),
            "mules are sterile and cannot produce offspring");
    }

    // ---- villager ----

    @Test
    void villagerTypeUsesEnvironmentOrParentAlleles() {
        final BreedContext context = BreedContext.of(
            EntityType.VILLAGER,
            EntityType.VILLAGER,
            EntityType.VILLAGER,
            "SWAMP"
        );
        final VillagerGeneticsProfile profile = VillagerGeneticsProfile.INSTANCE;
        assertEquals(
            "SWAMP",
            profile.breed(genome("PLAINS"), genome("DESERT"), new ScriptedDoubleRoll(0.25D), context)
                .orElseThrow()
                .genes()
                .get(profile.typeLocus().id())
                .alleleA()
                .label(),
            "rolls below 0.50 choose the environmental type"
        );
        assertEquals(
            "PLAINS",
            profile.breed(genome("PLAINS"), genome("DESERT"), new ScriptedDoubleRoll(0.60D), context)
                .orElseThrow()
                .genes()
                .get(profile.typeLocus().id())
                .alleleA()
                .label(),
            "rolls in [0.50, 0.75) choose parent A's type"
        );
        assertEquals(
            "DESERT",
            profile.breed(genome("PLAINS"), genome("DESERT"), new ScriptedDoubleRoll(0.90D), context)
                .orElseThrow()
                .genes()
                .get(profile.typeLocus().id())
                .alleleA()
                .label(),
            "rolls of 0.75 or higher choose parent B's type"
        );
    }

    // ---- helpers ----

    private static dev.mintychochip.genetics.model.Genome pandaGenome(final String main, final String hidden) {
        return dev.mintychochip.genetics.model.Genome.builder(dev.mintychochip.genetics.model.Sex.FEMALE)
            .put(PandaGeneticsProfile.MAIN, dev.mintychochip.genetics.model.GeneCopy.diploid(
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", main),
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", main)
            ))
            .put(PandaGeneticsProfile.HIDDEN, dev.mintychochip.genetics.model.GeneCopy.diploid(
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", hidden),
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", hidden)
            ))
            .build();
    }

    private static dev.mintychochip.genetics.model.Genome genome(final String type) {
        return dev.mintychochip.genetics.model.Genome.builder(dev.mintychochip.genetics.model.Sex.MALE)
            .put(VillagerGeneticsProfile.INSTANCE.typeLocus(), dev.mintychochip.genetics.model.GeneCopy.diploid(
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", type),
                dev.mintychochip.genetics.model.Allele.of("ATGAAACCC", type)
            ))
            .build();
    }

    private static double offspring(
        final double parentAValue,
        final double parentBValue,
        final double attributeRangeMin,
        final double attributeRangeMax,
        final double first,
        final double second,
        final double third
    ) {
        return EquineGeneticsProfile.createOffspringAttribute(
            parentAValue,
            parentBValue,
            attributeRangeMin,
            attributeRangeMax,
            new ScriptedDoubleSequence(first, second, third)
        );
    }
    private static final class ScriptedDoubleSequence implements RandomGenerator {

        private final double[] values;
        private int next;

        private ScriptedDoubleSequence(final double... values) {
            this.values = values.clone();
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public double nextDouble() {
            if (this.next >= this.values.length) {
                throw new IllegalStateException("Scripted random sequence exhausted");
            }
            return this.values[this.next++];
        }

        @Override
        public int nextInt() {
            return 0;
        }

        @Override
        public int nextInt(final int bound) {
            return 0;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }


    /**
     * Deterministic single-double source for villager type rolls.
     */
    private static final class ScriptedDoubleRoll implements RandomGenerator {

        private final double roll;

        private ScriptedDoubleRoll(final double roll) {
            this.roll = roll;
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public double nextDouble() {
            return this.roll;
        }

        @Override
        public int nextInt() {
            return 0;
        }

        @Override
        public int nextInt(final int bound) {
            return 0;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }
}
