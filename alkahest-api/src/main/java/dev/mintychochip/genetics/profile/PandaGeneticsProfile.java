package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dna.DnaSequence;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** NMS-free genetics profile for the seven vanilla panda genes. */
public final class PandaGeneticsProfile implements GeneticsProfile {

    public static final LocusDefinition MAIN = LocusDefinition.autosomal("panda.main", 1, 1, DominanceMode.COMPLETE);
    public static final LocusDefinition HIDDEN = LocusDefinition.autosomal("panda.hidden", 1, 2, DominanceMode.COMPLETE);
    public static final List<String> LABELS = List.of(
        "NORMAL", "LAZY", "WORRIED", "PLAYFUL", "BROWN", "WEAK", "AGGRESSIVE"
    );
    public static final Set<String> RECESSIVE = Set.of("BROWN", "WEAK");
    public static final String VARIANT_KEY = "panda.variant";
    public static final PandaGeneticsProfile INSTANCE = new PandaGeneticsProfile();

    private static final LocusCatalog CATALOG = LocusCatalog.of(List.of(MAIN, HIDDEN));

    private final PandaPhenotypeDecoder phenotype = new PandaPhenotypeDecoder();

    private PandaGeneticsProfile() {
    }

    @Override
    public String id() {
        return "panda";
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
        return new GenomeGenerator(CATALOG, random, PandaGeneticsProfile::founderAllele).generate(sex);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        return this.phenotype.decode(genome);
    }

    private static Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        return Allele.of(DnaSequence.functionalOrf(random, 3), LABELS.get(random.nextInt(LABELS.size())));
    }
}
