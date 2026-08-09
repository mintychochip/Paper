package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.dna.DnaSequence;
import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.DominanceMode;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.GenomeGenerator;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.LocusDefinition;
import dev.mintychochip.genetics.model.Sex;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Single-locus autosomal multiallelic profile. Founder labels are selected
 * uniformly; the phenotype resolves the visible label at {@code locusKey}
 * following the label order (first-listed present allele wins, mirroring the
 * sheep base-color dominance convention).
 */
public final class VariantGeneticsProfile implements GeneticsProfile {

    private final String id;
    private final LocusDefinition locus;
    private final LocusCatalog catalog;
    private final List<String> labels;

    private VariantGeneticsProfile(final String id, final String locusKey, final List<String> labels) {
        this.id = Objects.requireNonNull(id, "id");
        if (locusKey == null || locusKey.isBlank()) {
            throw new IllegalArgumentException("locusKey cannot be blank");
        }
        this.labels = List.copyOf(labels);
        if (this.labels.isEmpty()) {
            throw new IllegalArgumentException("labels cannot be empty");
        }
        this.locus = LocusDefinition.autosomal(locusKey, 1, 1, DominanceMode.COMPLETE);
        this.catalog = LocusCatalog.of(List.of(this.locus));
    }

    public static VariantGeneticsProfile of(
        final String id,
        final String locusKey,
        final List<String> labels
    ) {
        return new VariantGeneticsProfile(id, locusKey, labels);
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public LocusCatalog catalog() {
        return this.catalog;
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
        Objects.requireNonNull(random, "random");
        return new GenomeGenerator(this.catalog, random, this::founderAllele).generate(sex);
    }

    private Allele founderAllele(final LocusDefinition locus, final RandomGenerator random) {
        final String label = this.labels.get(random.nextInt(this.labels.size()));
        return Allele.of(DnaSequence.functionalOrf(random, 3), label);
    }

    @Override
    public PhenotypeSnapshot phenotype(final Genome genome) {
        final GeneCopy copy = genome.get(this.locus.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing variant locus: " + this.locus.id())
        );
        final String visible = resolve(copy);
        return new PhenotypeSnapshot(List.of(new PhenotypeTrait(this.locus.phenotypeKey(), visible)));
    }

    private String resolve(final GeneCopy copy) {
        final String a = copy.alleleA().label();
        final Allele b = copy.alleleB();
        for (final String candidate : this.labels) {
            if (candidate.equals(a) || (b != null && candidate.equals(b.label()))) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
            "Alleles [" + a + "/" + (b == null ? "-" : b.label())
                + "] not in the label set for " + this.id
        );
    }
}