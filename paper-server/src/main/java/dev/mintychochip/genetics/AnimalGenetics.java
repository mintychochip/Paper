package dev.mintychochip.genetics;

import dev.mintychochip.genetics.dna.MutationSettings;
import dev.mintychochip.genetics.dto.BreedGenetics;
import dev.mintychochip.genetics.dto.GenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeSnapshot;
import dev.mintychochip.genetics.dto.PhenotypeVariantResolver;
import dev.mintychochip.genetics.engine.BreedingEngine;
import dev.mintychochip.genetics.engine.BreedingResult;
import dev.mintychochip.genetics.engine.GeneticMatePolicy;
import dev.mintychochip.genetics.engine.RecombinationSettings;
import dev.mintychochip.genetics.io.GenomeCodec;
import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.LocusCatalog;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.BreedContext;
import dev.mintychochip.genetics.profile.BreedPlan;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import dev.mintychochip.genetics.profile.GeneticsProfiles;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Server façade: genome attach/persist on animals, opposite-sex mate gate,
 * and child genomes from the pure {@link BreedingEngine}.
 *
 * <p>Logic lives in paper-api; this class only bridges NMS entities.
 */
public final class AnimalGenetics {

    public static final String NBT_KEY = "MintyGenome";
    public static final String PROFILE_NBT_KEY = "MintyGenomeProfile";

    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final GeneticsProfile GENERIC_PROFILE = GeneticsProfiles.generic();
    private static final LocusCatalog CATALOG = GENERIC_PROFILE.catalog();
    private static volatile boolean enabled = true;

    private record CacheEntry(String profileId, Genome genome) {
    }

    private AnimalGenetics() {
    }

    /**
     * Outcome of a genetic cross ready for {@link org.bukkit.event.entity.EntityBreedEvent}.
     * Mother/father are chromosomal dam/sire entities.
     */
    public record BreedPrep(
        Animal mother,
        Animal father,
        Genome childGenome,
        BreedGenetics genetics
    ) {
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static LocusCatalog catalog() {
        return CATALOG;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static GeneticsProfile profileFor(final EntityType entityType) {
        return GeneticsProfiles.forEntityType(entityType);
    }

    public static GeneticsProfile profile(final Animal animal) {
        return profileFor(CraftEntityType.minecraftToBukkit(animal.getType()));
    }
    public static GeneticsProfile profile(final AgeableMob ageable) {
        return profileFor(CraftEntityType.minecraftToBukkit(ageable.getType()));
    }

    // ------------------------------------------------------------------
    // Persistence (entity NBT + in-memory cache)
    // ------------------------------------------------------------------

    public static void save(final Animal animal, final ValueOutput output) {
        final CacheEntry entry = CACHE.get(animal.getUUID());
        if (entry != null) {
            output.putString(NBT_KEY, GenomeCodec.encode(entry.genome()));
            output.putString(PROFILE_NBT_KEY, entry.profileId());
        }
    }

    public static void save(final AgeableMob ageable, final ValueOutput output) {
        final CacheEntry entry = CACHE.get(ageable.getUUID());
        if (entry != null) {
            output.putString(NBT_KEY, GenomeCodec.encode(entry.genome()));
            output.putString(PROFILE_NBT_KEY, entry.profileId());
        }
    }
    public static void load(final Animal animal, final ValueInput input) {
        input.getString(NBT_KEY).ifPresent(encoded -> {
            try {
                final Genome genome = GenomeCodec.decode(encoded);
                final String profileId = input.getString(PROFILE_NBT_KEY).orElse(GENERIC_PROFILE.id());
                CACHE.put(animal.getUUID(), new CacheEntry(profileId, genome));
            } catch (final RuntimeException ignored) {
                // Corrupt data: regenerate on next world insertion.
            }
        });
    }
    public static void load(final AgeableMob ageable, final ValueInput input) {
        input.getString(NBT_KEY).ifPresent(encoded -> {
            try {
                final Genome genome = GenomeCodec.decode(encoded);
                final String profileId = input.getString(PROFILE_NBT_KEY).orElse(GENERIC_PROFILE.id());
                CACHE.put(ageable.getUUID(), new CacheEntry(profileId, genome));
            } catch (final RuntimeException ignored) {
                // Corrupt data: regenerate on next world insertion.
            }
        });
    }

    /**
     * Attach or restore an animal after the entity lookup has accepted it.
     * This runs after subclass NBT has finished, so the genetic phenotype wins
     * over vanilla defaults and restored variants.
     */
    public static void onAddedToWorld(final Animal animal) {
        final GeneticsProfile profile = profile(animal);
        final CacheEntry entry = ensureEntry(animal, profile, asGenerator(animal.getRandom()), false);
        PhenotypeApplier.apply(animal, profile.phenotype(entry.genome()));
    }
    public static void onAddedToWorld(final AgeableMob ageable) {
        final GeneticsProfile resolved = profile(ageable);
        final CacheEntry entry = ensureEntry(ageable, resolved, asGenerator(ageable.getRandom()), false);
        PhenotypeApplier.apply(ageable, resolved.phenotype(entry.genome()));
    }

    public static void setGenome(final AgeableMob ageable, final Genome genome) {
        final GeneticsProfile resolved = profile(ageable);
        CACHE.put(ageable.getUUID(), new CacheEntry(resolved.id(), genome));
        PhenotypeApplier.apply(ageable, resolved.phenotype(genome));
    }

    public static @Nullable Genome getGenome(final AgeableMob ageable) {
        return getGenome(ageable.getUUID());
    }

    public static void remove(final Entity entity) {
        CACHE.remove(entity.getUUID());
    }

    /**
     * Direct attach for tests and tools (no entity required beyond UUID keying).
     */
    public static void setGenome(final UUID entityId, final Genome genome) {
        CACHE.put(entityId, new CacheEntry(GENERIC_PROFILE.id(), genome));
    }

    public static @Nullable Genome getGenome(final UUID entityId) {
        final CacheEntry entry = CACHE.get(entityId);
        return entry == null ? null : entry.genome();
    }

    public static void setGenome(final Animal animal, final Genome genome) {
        final GeneticsProfile profile = profile(animal);
        CACHE.put(animal.getUUID(), new CacheEntry(profile.id(), genome));
        PhenotypeApplier.apply(animal, profile.phenotype(genome));
    }

    public static @Nullable Genome getGenome(final Animal animal) {
        return getGenome(animal.getUUID());
    }

    /**
     * Round-trip helper for tests: encode → decode without touching entities.
     */
    public static Genome roundTrip(final Genome genome) {
        return GenomeCodec.decode(GenomeCodec.encode(genome));
    }

    public static PhenotypeSnapshot phenotypeOf(final Genome genome) {
        return GENERIC_PROFILE.phenotype(genome);
    }

    public static PhenotypeSnapshot phenotypeOf(final Animal animal, final Genome genome) {
        return profile(animal).phenotype(genome);
    }

    public static PhenotypeSnapshot phenotypeOf(final AgeableMob ageable, final Genome genome) {
        return profile(ageable).phenotype(genome);
    }

    public static PhenotypeSnapshot phenotypeOf(final AgeableMob ageable) {
        final GeneticsProfile resolved = profile(ageable);
        final CacheEntry entry = ensureEntry(ageable, resolved, asGenerator(ageable.getRandom()), false);
        return resolved.phenotype(entry.genome());
    }
    public static PhenotypeSnapshot phenotypeOf(final Animal animal) {
        final GeneticsProfile profile = profile(animal);
        final CacheEntry entry = ensureEntry(animal, profile, asGenerator(animal.getRandom()), false);
        return profile.phenotype(entry.genome());
    }

    public static Genome getOrCreate(final Animal animal, final RandomSource random) {
        final GeneticsProfile profile = profile(animal);
        return ensureEntry(animal, profile, asGenerator(random), true).genome();
    }

    public static Genome getOrCreate(final AgeableMob ageable, final RandomSource random) {
        final GeneticsProfile resolved = profile(ageable);
        return ensureEntry(ageable, resolved, asGenerator(random), true).genome();
    }
    private static CacheEntry ensureEntry(
        final AgeableMob ageable,
        final GeneticsProfile profile,
        final RandomGenerator random,
        final boolean apply
    ) {
        final UUID entityId = ageable.getUUID();
        final CacheEntry existing = CACHE.get(entityId);
        if (existing != null && existing.profileId().equals(profile.id())) {
            try {
                final PhenotypeSnapshot phenotype = profile.phenotype(existing.genome());
                if (apply) {
                    PhenotypeApplier.apply(ageable, phenotype);
                }
                return existing;
            } catch (final RuntimeException ignored) {
                CACHE.remove(entityId, existing);
            }
        }

        final Sex sex = random.nextBoolean() ? Sex.MALE : Sex.FEMALE;
        Genome genome;
        try {
            genome = FounderCaptures.capture(ageable, profile, sex, random);
            profile.phenotype(genome);
        } catch (final RuntimeException ignored) {
            genome = profile.founder(sex, random);
        }
        final CacheEntry created = new CacheEntry(profile.id(), genome);
        CACHE.put(entityId, created);
        if (apply) {
            PhenotypeApplier.apply(ageable, profile.phenotype(created.genome()));
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Mate / breed bridge (invoked from thin vanilla hooks)
    // ------------------------------------------------------------------

    /**
     * Additional mate gate after vanilla same-class / in-love checks.
     * Opposite sex only when both have (or receive) genomes.
     */
    public static boolean allowsMate(final Animal self, final Animal partner) {
        if (!enabled) {
            return true;
        }
        final Genome a = getOrCreate(self, self.getRandom());
        final Genome b = getOrCreate(partner, partner.getRandom());
        return GeneticMatePolicy.allowsMate(a, b);
    }

    /**
     * After vanilla creates the baby entity, assign a recombinant genome.
     * No-op if disabled or same-sex (should not reach here if canMate enforced).
     *
     * @deprecated prefer {@link #prepareBreed} so {@code EntityBreedEvent} gets metadata
     *     and cancel can discard the child genome
     */
    @Deprecated
    public static void onBreed(final Animal parentA, final Animal parentB, final @Nullable AgeableMob offspring) {
        prepareBreed(parentA, parentB, offspring);
    }

    /**
     * Run the genetic cross, attach the child genome, and build event metadata.
     *
     * <p>Call before {@code EntityBreedEvent}. If the event is cancelled, call
     * {@link #discardBreed(AgeableMob)} so the orphan genome is not left in cache.
     *
     * @return prep with genetic mother/father + {@link BreedGenetics}, or null if
     *     genetics disabled / no offspring / same-sex (should not happen after canMate)
     */
    public static @Nullable BreedPrep prepareBreed(
        final Animal parentA,
        final Animal parentB,
        final @Nullable AgeableMob offspring
    ) {
        if (!enabled || offspring == null) {
            return null;
        }
        final EntityType parentType = CraftEntityType.minecraftToBukkit(parentA.getType());
        final EntityType partnerType = CraftEntityType.minecraftToBukkit(parentB.getType());
        final EntityType childType = CraftEntityType.minecraftToBukkit(offspring.getType());
        final Optional<BreedPlan> plan = GeneticsProfiles.resolveBreed(parentType, partnerType, childType);
        if (plan.isEmpty()) {
            return null;
        }
        final BreedPlan breedPlan = plan.orElseThrow();
        final Genome ga = getOrCreate(parentA, parentA.getRandom());
        final Genome gb = getOrCreate(parentB, parentB.getRandom());
        final Optional<Genome> childResult = breedPlan.familyProfile().breed(
            ga,
            gb,
            asGenerator(parentA.getRandom()),
            BreedContext.of(parentType, partnerType, childType)
        );
        if (childResult.isEmpty()) {
            return null;
        }
        final Genome child = childResult.orElseThrow();
        CACHE.put(offspring.getUUID(), new CacheEntry(breedPlan.childProfile().id(), child));

        final Animal mother = ga.sex() == Sex.FEMALE ? parentA : parentB;
        final Animal father = ga.sex() == Sex.MALE ? parentA : parentB;
        final Genome motherGenome = mother == parentA ? ga : gb;
        final Genome fatherGenome = father == parentA ? ga : gb;

        return new BreedPrep(
            mother,
            father,
            child,
            snapshotsOf(motherGenome, fatherGenome, child, childType, breedPlan.childProfile())
        );
    }
    /**
     * Prepare a villager child genome before vanilla inserts the child.
     *
     * <p>Vanilla already chooses the child's type from the biome and parents.
     * Feed that same biome-derived type into the profile-aware cross, then
     * cache the result. Any profile or breed-resolution failure is ignored so
     * vanilla child creation continues unchanged.
     *
     * @return whether a child genome was cached
     */
    public static boolean prepareVillagerBreed(
        final Villager source,
        final Villager target,
        final Villager child
    ) {
        if (!enabled || source == null || target == null || child == null) {
            return false;
        }
        try {
            final EntityType parentType = CraftEntityType.minecraftToBukkit(source.getType());
            final EntityType partnerType = CraftEntityType.minecraftToBukkit(target.getType());
            final EntityType childType = CraftEntityType.minecraftToBukkit(child.getType());
            final Optional<BreedPlan> plan = GeneticsProfiles.resolveBreed(parentType, partnerType, childType);
            if (plan.isEmpty()) {
                return false;
            }
            final Genome parentGenome = getOrCreate(source, source.getRandom());
            final Genome partnerGenome = getOrCreate(target, target.getRandom());
            final String environmentalVariant = VillagerType.byBiome(
                source.level().getBiome(source.blockPosition())
            ).identifier().getPath();
            final Optional<Genome> childGenome = plan.get().breed(
                parentGenome,
                partnerGenome,
                asGenerator(source.getRandom()),
                BreedContext.of(parentType, partnerType, childType, environmentalVariant)
            );
            if (childGenome.isEmpty()) {
                return false;
            }
            CACHE.put(child.getUUID(), new CacheEntry(plan.get().childProfile().id(), childGenome.get()));
            return true;
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Drop a child genome after a cancelled breed (entity never enters the world).
     */
    public static void discardBreed(final @Nullable AgeableMob offspring) {
        if (offspring != null) {
            discardGenome(offspring.getUUID());
        }
    }

    /**
     * Drop a cached genome by entity id (cancel path / tests).
     */
    public static void discardGenome(final UUID entityId) {
        CACHE.remove(entityId);
    }

    /**
     * Build plugin-facing breed metadata from three genomes (tests + event payload).
     */
    public static BreedGenetics snapshotsOf(final Genome mother, final Genome father, final Genome child) {
        return snapshotsOf(mother, father, child, null);
    }

    /**
     * Build breed metadata and resolve the child's registry variant for {@code childType}.
     */
    public static BreedGenetics snapshotsOf(
        final Genome mother,
        final Genome father,
        final Genome child,
        final @Nullable EntityType childType
    ) {
        final GeneticsProfile profile = childType == null ? GENERIC_PROFILE : profileFor(childType);
        return snapshotsOf(mother, father, child, childType, profile);
    }

    private static BreedGenetics snapshotsOf(
        final Genome mother,
        final Genome father,
        final Genome child,
        final @Nullable EntityType childType,
        final GeneticsProfile profile
    ) {
        final PhenotypeSnapshot childPhenotype = profile.phenotype(child);
        final NamespacedKey childVariant = childType == null
            ? null
            : PhenotypeVariantResolver.resolve(childType, childPhenotype).orElse(null);
        return new BreedGenetics(
            GenotypeSnapshot.from(mother, profile.catalog()),
            GenotypeSnapshot.from(father, profile.catalog()),
            GenotypeSnapshot.from(child, profile.catalog()),
            profile.phenotype(mother),
            profile.phenotype(father),
            childPhenotype,
            childVariant
        );
    }

    /**
     * Pure cross entry used by tests and {@link #onBreed}.
     */
    public static Optional<BreedingResult> cross(
        final Genome parentA,
        final Genome parentB,
        final RandomSource random
    ) {
        return crossWithProfile(GENERIC_PROFILE, parentA, parentB, asGenerator(random));
    }

    public static Optional<BreedingResult> cross(
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random,
        final MutationSettings mutation,
        final RecombinationSettings recombination
    ) {
        if (!GeneticMatePolicy.allowsMate(parentA, parentB)) {
            return Optional.empty();
        }
        return new BreedingEngine(CATALOG, recombination, mutation, random).cross(parentA, parentB);
    }

    private static Optional<BreedingResult> crossWithProfile(
        final GeneticsProfile profile,
        final Genome parentA,
        final Genome parentB,
        final RandomGenerator random
    ) {
        if (!GeneticMatePolicy.allowsMate(parentA, parentB)) {
            return Optional.empty();
        }
        return new BreedingEngine(
            profile.catalog(),
            profile.recombination(),
            profile.mutation(),
            random
        ).cross(parentA, parentB);
    }

    private static RandomGenerator asGenerator(final RandomSource random) {
        // Adapt Minecraft RandomSource to RandomGenerator without sharing state oddly:
        // wrap each call. RandomSource is already the entity's RNG.
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return random.nextLong();
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public int nextInt() {
                return random.nextInt();
            }

            @Override
            public int nextInt(final int bound) {
                return random.nextInt(bound);
            }

            @Override
            public boolean nextBoolean() {
                return random.nextBoolean();
            }

            @Override
            public float nextFloat() {
                return random.nextFloat();
            }
        };
    }
}
