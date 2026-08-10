package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

/**
 * Structural proof that the Animal breed path calls the genetics façade.
 */
@Normal
public class AnimalGeneticsHooksPresentTest {

    @Test
    public void animalSourceWiresGeneticsFacade() throws Exception {
        final String src = readProjectFile(
            "src/minecraft/java/net/minecraft/world/entity/animal/Animal.java",
            "paper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java"
        );
        assertTrue(src.contains("dev.mintychochip.genetics.AnimalGenetics.allowsMate"), "canMate must call AnimalGenetics.allowsMate");
        assertTrue(src.contains("dev.mintychochip.genetics.AnimalGenetics.prepareBreed"), "spawnChildFromBreeding must call AnimalGenetics.prepareBreed");
        assertTrue(src.contains("dev.mintychochip.genetics.AnimalGenetics.discardBreed"), "cancel path must discard child genome");
        assertTrue(src.contains("breedGenetics"), "EntityBreedEvent must receive genetics metadata");
        assertFalse(src.contains("dev.mintychochip.genetics.AnimalGenetics.save"), "save hook must move to AgeableMob");
        assertFalse(src.contains("dev.mintychochip.genetics.AnimalGenetics.load"), "load hook must move to AgeableMob");
        assertFalse(src.contains("dev.mintychochip.genetics.AnimalGenetics.remove"), "removal hook must move to AgeableMob");
    }

    @Test
    public void animalPatchDocumentsGeneticsHooks() throws Exception {
        final String animalPatch = readProjectFile(
            "patches/sources/net/minecraft/world/entity/animal/Animal.java.patch",
            "paper-server/patches/sources/net/minecraft/world/entity/animal/Animal.java.patch"
        );
        final String ageablePatch = readProjectFile(
            "patches/sources/net/minecraft/world/entity/AgeableMob.java.patch",
            "paper-server/patches/sources/net/minecraft/world/entity/AgeableMob.java.patch"
        );
        assertTrue(animalPatch.contains("AnimalGenetics.allowsMate"), "patch must include mate gate");
        assertTrue(animalPatch.contains("AnimalGenetics.prepareBreed") || animalPatch.contains("AnimalGenetics.onBreed"), "patch must include breed hook");
        assertTrue(ageablePatch.contains("AnimalGenetics.save"), "common patch must include save hook");
        assertTrue(ageablePatch.contains("AnimalGenetics.load"), "common patch must include load hook");
        assertTrue(ageablePatch.contains("AnimalGenetics.remove"), "common patch must include removal hook");
    }

    @Test
    public void serverFacadePersistsProfileIdBesideGenomeJson() throws Exception {
        final String source = readProjectFile(
            "src/main/java/dev/mintychochip/genetics/AnimalGenetics.java",
            "paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java"
        );
        assertTrue(source.contains("output.putString(PROFILE_NBT_KEY"), "save path must persist profile id");
        assertTrue(source.contains("input.getString(PROFILE_NBT_KEY"), "load path must read profile id");
    }

    @Test
    public void commonInsertionAndRemovalHooksArePresent() throws Exception {
        final String serverLevel = readProjectFile(
            "src/minecraft/java/net/minecraft/server/level/ServerLevel.java",
            "paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java"
        );
        final String ageable = readProjectFile(
            "src/minecraft/java/net/minecraft/world/entity/AgeableMob.java",
            "paper-server/src/minecraft/java/net/minecraft/world/entity/AgeableMob.java"
        );
        assertTrue(serverLevel.contains("AnimalGenetics.onAddedToWorld"), "accepted animals must attach genetics");
        assertTrue(ageable.contains("AnimalGenetics.remove"), "removed ageables must release genetics cache");
        assertTrue(ageable.contains("onRemoval"), "cache cleanup must be tied to the common removal lifecycle");
    }

    @Test
    public void specialBreedResolutionAndAdaptersArePresent() throws Exception {
        final String genetics = readProjectFile(
            "src/main/java/dev/mintychochip/genetics/AnimalGenetics.java",
            "paper-server/src/main/java/dev/mintychochip/genetics/AnimalGenetics.java"
        );
        final String applier = readProjectFile(
            "src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java",
            "paper-server/src/main/java/dev/mintychochip/genetics/PhenotypeApplier.java"
        );
        assertTrue(genetics.contains("familyProfile().breed("), "Animal breeding must delegate to the resolved family profile");
        assertTrue(genetics.contains("childProfile().id()"), "child cache must use the resolved child profile");
        assertTrue(applier.contains("\"equine.speed\""), "equine numeric adapter must be present");
        assertTrue(applier.contains("\"llama.strength\""), "llama strength adapter must be present");
        assertTrue(applier.contains("\"panda.hidden\""), "panda hidden-gene adapter must be present");
    }

    private static String readProjectFile(final String... relativeCandidates) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            for (final String rel : relativeCandidates) {
                final Path candidate = cwd.resolve(rel);
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate);
                }
            }
            final Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        throw new java.nio.file.NoSuchFileException(String.join(" | ", relativeCandidates));
    }
}
