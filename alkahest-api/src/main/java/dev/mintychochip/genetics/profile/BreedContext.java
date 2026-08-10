package dev.mintychochip.genetics.profile;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.EntityType;

/** Immutable entity context for one profile-aware breed operation. */
public final class BreedContext {

    private final EntityType parentA;
    private final EntityType parentB;
    private final EntityType child;
    private final Optional<String> environmentalVariant;

    public BreedContext(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child
    ) {
        this(parentA, parentB, child, Optional.empty());
    }

    public BreedContext(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child,
        final Optional<String> environmentalVariant
    ) {
        this.parentA = Objects.requireNonNull(parentA, "parentA");
        this.parentB = Objects.requireNonNull(parentB, "parentB");
        this.child = Objects.requireNonNull(child, "child");
        this.environmentalVariant = Objects.requireNonNull(environmentalVariant, "environmentalVariant")
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    }

    public BreedContext(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child,
        final String environmentalVariant
    ) {
        this(parentA, parentB, child, Optional.ofNullable(environmentalVariant));
    }

    public static BreedContext of(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child
    ) {
        return new BreedContext(parentA, parentB, child);
    }

    public static BreedContext of(
        final EntityType parentA,
        final EntityType parentB,
        final EntityType child,
        final String environmentalVariant
    ) {
        return new BreedContext(parentA, parentB, child, environmentalVariant);
    }

    public EntityType parentA() {
        return this.parentA;
    }

    public EntityType parentB() {
        return this.parentB;
    }

    public EntityType child() {
        return this.child;
    }

    public EntityType parentAType() {
        return this.parentA;
    }

    public EntityType parentBType() {
        return this.parentB;
    }

    public EntityType childType() {
        return this.child;
    }

    public Optional<String> environmentalVariant() {
        return this.environmentalVariant;
    }
}
