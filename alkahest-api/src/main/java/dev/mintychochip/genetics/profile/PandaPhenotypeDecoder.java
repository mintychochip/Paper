package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeTrait;
import dev.mintychochip.genetics.model.Allele;
import dev.mintychochip.genetics.model.GeneCopy;
import dev.mintychochip.genetics.model.Genome;
import java.util.List;

/** Decodes the vanilla panda main/hidden gene visibility rule. */
public final class PandaPhenotypeDecoder {

    public PhenotypeSnapshot decode(final Genome genome) {
        final GeneCopy mainCopy = genome.get(PandaGeneticsProfile.MAIN.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing panda.main locus"));
        final GeneCopy hiddenCopy = genome.get(PandaGeneticsProfile.HIDDEN.id()).orElseThrow(() ->
            new IllegalArgumentException("Missing panda.hidden locus"));
        final String main = resolveGene(mainCopy);
        final String hidden = resolveGene(hiddenCopy);
        final String visible = resolveVisible(main, hidden);
        return new PhenotypeSnapshot(List.of(
            new PhenotypeTrait(PandaGeneticsProfile.MAIN.phenotypeKey(), visible),
            new PhenotypeTrait(PandaGeneticsProfile.HIDDEN.phenotypeKey(), hidden),
            new PhenotypeTrait(PandaGeneticsProfile.VARIANT_KEY, visible)
        ));
    }

    private static String resolveGene(final GeneCopy copy) {
        final String a = label(copy.alleleA());
        if (copy.isHemizygous()) {
            return a;
        }
        final String b = label(copy.alleleB());
        for (final String candidate : PandaGeneticsProfile.LABELS) {
            if (candidate.equals(a) || candidate.equals(b)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown panda alleles: " + a + "/" + b);
    }

    public static String resolveVisible(final String main, final String hidden) {
        if (!PandaGeneticsProfile.LABELS.contains(main) || !PandaGeneticsProfile.LABELS.contains(hidden)) {
            throw new IllegalArgumentException("Unknown panda gene: " + main + "/" + hidden);
        }
        return PandaGeneticsProfile.RECESSIVE.contains(main) && !main.equals(hidden)
            ? "NORMAL"
            : main;
    }

    private static String label(final Allele allele) {
        final String label = allele.label();
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Panda alleles require semantic labels");
        }
        return label;
    }
}
