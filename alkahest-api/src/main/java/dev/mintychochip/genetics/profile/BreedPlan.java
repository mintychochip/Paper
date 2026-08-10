package dev.mintychochip.genetics.profile;

import dev.mintychochip.genetics.model.Genome;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Immutable family/child profile pairing for one permitted breed cross. */
public final class BreedPlan {

    private final GeneticsProfile familyProfile;
    private final GeneticsProfile childProfile;

    public BreedPlan(final GeneticsProfile familyProfile, final GeneticsProfile childProfile) {
        this.familyProfile = Objects.requireNonNull(familyProfile, "familyProfile");
        this.childProfile = Objects.requireNonNull(childProfile, "childProfile");
    }

    public static BreedPlan of(final GeneticsProfile familyProfile, final GeneticsProfile childProfile) {
        return new BreedPlan(familyProfile, childProfile);
    }

    public GeneticsProfile familyProfile() {
        return this.familyProfile;
    }

    public GeneticsProfile childProfile() {
        return this.childProfile;
    }

    public Optional<Genome> breed(
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random,
        final BreedContext context
    ) {
        return this.familyProfile.breed(parentA, parentB, random, context);
    }
}
