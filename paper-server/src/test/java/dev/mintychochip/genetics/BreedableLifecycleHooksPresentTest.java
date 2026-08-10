package dev.mintychochip.genetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

/**
 * Structural proof that every supported breedable lifecycle path calls the
 * genetics façade, and that common persistence/removal hooks live on
 * {@code AgeableMob} exactly once instead of being duplicated per animal.
 */
@Normal
public class BreedableLifecycleHooksPresentTest {

    @Test
    public void commonAgeablePersistenceAndRemovalHooksArePresent() throws Exception {
        final String ageable = readProjectFile(
            "src/minecraft/java/net/minecraft/world/entity/AgeableMob.java",
            "paper-server/src/minecraft/java/net/minecraft/world/entity/AgeableMob.java"
        );
        assertTrue(ageable.contains("AnimalGenetics.save"), "common save path must persist the genome on AgeableMob");
        assertTrue(ageable.contains("AnimalGenetics.load"), "common load path must restore the genome on AgeableMob");
        assertTrue(ageable.contains("AnimalGenetics.remove"), "common removal path must release the genome cache on AgeableMob");
        assertTrue(ageable.contains("onRemoval"), "cache cleanup must be tied to the common removal lifecycle");
    }

    @Test
    public void animalNoLongerDuplicatesCommonLifecycleHooks() throws Exception {
        final String animal = readProjectFile(
            "src/minecraft/java/net/minecraft/world/entity/animal/Animal.java",
            "paper-server/src/minecraft/java/net/minecraft/world/entity/animal/Animal.java"
        );
        assertTrue(animal.contains("AnimalGenetics.prepareBreed"), "spawnChildFromBreeding must keep preparing Animal children");
        assertFalse(animal.contains("AnimalGenetics.save"), "Animal must not duplicate the common save hook");
        assertFalse(animal.contains("AnimalGenetics.load"), "Animal must not duplicate the common load hook");
        assertFalse(animal.contains("AnimalGenetics.remove"), "Animal must not duplicate the common removal hook");
    }

    @Test
    public void insertionHookAttachesSupportedAgeables() throws Exception {
        final String serverLevel = readProjectFile(
            "src/minecraft/java/net/minecraft/server/level/ServerLevel.java",
            "paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java"
        );
        assertTrue(serverLevel.contains("AnimalGenetics.onAddedToWorld"), "accepted entities must attach genetics after lookup acceptance");
        assertTrue(serverLevel.contains("npc.villager.Villager"), "insertion hook must cover Villager entities");
        assertTrue(serverLevel.contains("animal.Animal"), "insertion hook must cover Animal entities");
    }

    @Test
    public void villagerBreedPathPreparesChildGenome() throws Exception {
        final String villagerLove = readProjectFile(
            "src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java",
            "paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java"
        );
        assertTrue(villagerLove.contains("AnimalGenetics.prepareVillagerBreed"), "villager birth must prepare the child genome");
        assertTrue(villagerLove.contains("addFreshEntityWithPassengers"), "the hook must sit before world insertion");
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
