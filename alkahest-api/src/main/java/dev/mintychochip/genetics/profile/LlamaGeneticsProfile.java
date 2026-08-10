package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.dna.DnaSequence;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.random.RandomGenerator;

/** NMS-free llama color and bounded strength profile. */
public final class LlamaGeneticsProfile implements GeneticsProfile {

    public static final LocusDefinition COLOR = LocusDefinition.autosomal("llama.color", 1, 1, DominanceMode.COMPLETE);
    public static final LocusDefinition STRENGTH = LocusDefinition.autosomal("llama.strength", 1, 2, DominanceMode.COMPLETE);
    public static final List<String> COLORS = List.of("CREAMY", "WHITE", "BROWN", "GRAY");
    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 5;
    public static final LocusCatalog CATALOG = LocusCatalog.of(List.of(COLOR, STRENGTH));
    public static final LlamaGeneticsProfile INSTANCE = new LlamaGeneticsProfile();

    private LlamaGeneticsProfile() {
    }

    @Override
    public String id() {
        return "llama";
    }

    @Override
    public LocusCatalog catalog() {
        return CATALOG;
    }

    @Override
    public MutationSettings mutation() {
        return MutationSettings.NONE;
    }

    @Override
    public RecombinationSettings recombination() {
        return RecombinationSettings.DEFAULT;
    }

    @Override
    public Genome founder(final Sex sex, final RandomGenerator random) {
        return new GenomeGenerator(CATALOG, random, LlamaGeneticsProfile::founderAllele).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return new PhenotypeSnapshot(List.of(
            trait(genome, COLOR),
            trait(genome, STRENGTH)
        ));
    }

    private static Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        if (locus == COLOR) {
            return Allele.of(DnaSequence.functionalOrf(random, 3), COLORS.get(random.nextInt(COLORS.size())));
        }
        if (locus == STRENGTH) {
            return Allele.of(DnaSequence.functionalOrf(random, 3), Integer.toString(MIN_STRENGTH + random.nextInt(MAX_STRENGTH)));
        }
        throw new IllegalArgumentException("Unknown llama locus: " + locus.id());
    }

    private static PhenotypeTrait trait(final Genome genome, final LocusDefinition locus) {
        final GeneCopy copy = genome.get(locus.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing llama locus: " + locus.id()));
        final String label = copy.alleleA().label();
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Llama alleles require semantic labels: " + locus.id());
        }
        return new PhenotypeTrait(locus.phenotypeKey(), label);
    }
}
