package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.genetics.engine.BreedingEngine;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.SheepGeneticsProfile;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class SheepGeneticsProfileTest {

    @Test
    void diluteCarriersCanProduceGrayFromBlackParents() {
        final Genome mother = sheepGenome(
            Sex.FEMALE,
            "BLACK/BLACK",
            "FULL/DILUTE",
            "PIGMENTED/PIGMENTED"
        );
        final Genome father = sheepGenome(
            Sex.MALE,
            "BLACK/BLACK",
            "FULL/DILUTE",
            "PIGMENTED/PIGMENTED"
        );

        final Genome child = breedWithScriptedPicks(
            mother,
            father,
            false,
            true,
            false,
            false,
            true,
            false
        );

        assertEquals("GRAY", SheepGeneticsProfile.INSTANCE.phenotype(child).getOrNull("sheep.color"));
    }

    @Test
    void albinoCarriersCanProduceWhiteRegardlessOfBasePigment() {
        final Genome mother = sheepGenome(
            Sex.FEMALE,
            "BLACK/BLACK",
            "FULL/FULL",
            "PIGMENTED/ALBINO"
        );
        final Genome father = sheepGenome(
            Sex.MALE,
            "BROWN/BROWN",
            "FULL/FULL",
            "PIGMENTED/ALBINO"
        );

        final Genome child = breedWithScriptedPicks(
            mother,
            father,
            false,
            false,
            true,
            false,
            false,
            true
        );

        assertEquals("WHITE", SheepGeneticsProfile.INSTANCE.phenotype(child).getOrNull("sheep.color"));
    }

    @Test
    void baseDominanceAndDilutionResolveKnownDyeColors() {
        assertColor("BLACK", "FULL", "BLACK");
        assertColor("BLACK", "DILUTE", "GRAY");
        assertColor("BROWN", "FULL", "BROWN");
        assertColor("BROWN", "DILUTE", "LIGHT_GRAY");
        assertColor("RED", "FULL", "RED");
        assertColor("RED", "DILUTE", "PINK");
        assertColor("YELLOW", "FULL", "YELLOW");
        assertColor("YELLOW", "DILUTE", "ORANGE");
    }

    private static void assertColor(final String base, final String dilution, final String expected) {
        final Genome genome = sheepGenome(
            Sex.FEMALE,
            base + "/" + base,
            dilution + "/" + dilution,
            "PIGMENTED/PIGMENTED"
        );
        assertEquals(expected, SheepGeneticsProfile.INSTANCE.phenotype(genome).getOrNull("sheep.color"));
    }

    private static Genome breedWithScriptedPicks(
        final Genome mother,
        final Genome father,
        final boolean... picks
    ) {
        return new BreedingEngine(
            SheepGeneticsProfile.INSTANCE.catalog(),
            RecombinationSettings.DEFAULT,
            MutationSettings.NONE,
            new ScriptedRandom(picks)
        ).cross(father, mother).orElseThrow().child();
    }

    private static Genome sheepGenome(
        final Sex sex,
        final String base,
        final String dilution,
        final String albinism
    ) {
        return Genome.builder(sex)
            .put(SheepGeneticsProfile.BASE, diploid(base))
            .put(SheepGeneticsProfile.DILUTION, diploid(dilution))
            .put(SheepGeneticsProfile.ALBINISM, diploid(albinism))
            .build();
    }

    private static GeneCopy diploid(final String pair) {
        final String[] alleles = pair.split("/", -1);
        if (alleles.length != 2) {
            throw new IllegalArgumentException("Expected diploid pair: " + pair);
        }
        return GeneCopy.diploid(allele(alleles[0]), allele(alleles[1]));
    }

    private static Allele allele(final String label) {
        return Allele.of("ATGAAACCC", label);
    }

    private static final class ScriptedRandom implements RandomGenerator {
        private final boolean[] picks;
        private int nextPick;

        private ScriptedRandom(final boolean[] picks) {
            this.picks = picks.clone();
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public double nextDouble() {
            return 0.0D;
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
            if (this.nextPick >= this.picks.length) {
                throw new IllegalStateException("Scripted random sequence exhausted");
            }
            return this.picks[this.nextPick++];
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }
}
