package dev.mintychochip.genetics;

import dev.mintychochip.genetics.model.Genome;
import dev.mintychochip.genetics.model.Sex;
import dev.mintychochip.genetics.profile.GeneticsProfile;
import java.util.random.RandomGenerator;
import net.minecraft.world.entity.AgeableMob;

@FunctionalInterface
public interface FounderCapture {
    Genome capture(AgeableMob entity, GeneticsProfile profile, Sex sex, RandomGenerator random);
}
