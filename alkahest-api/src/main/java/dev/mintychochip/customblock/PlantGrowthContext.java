package dev.mintychochip.customblock;

import dev.mintychochip.behavior.ActorView;
import dev.mintychochip.behavior.BlockDataView;
import dev.mintychochip.behavior.BlockView;
import dev.mintychochip.ecology.ClimateSample;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.jetbrains.annotations.NotNull;

/** Immutable input snapshot supplied to a custom plant growth receiver. */
public record PlantGrowthContext(
    @NotNull BlockView block,
    @NotNull BlockDataView currentData,
    @NotNull PlantGrowthCause cause,
    @NotNull OptionalInt currentStage,
    @NotNull OptionalInt maximumStage,
    @NotNull Optional<ActorView> actor,
    @NotNull Optional<ClimateSample> climate
) {

    public PlantGrowthContext {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(currentData, "currentData");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(maximumStage, "maximumStage");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(climate, "climate");
        if (currentStage.isPresent() != maximumStage.isPresent()) {
            throw new IllegalArgumentException("current and maximum stage must be present together");
        }
        if (currentStage.isPresent() && currentStage.getAsInt() < 0) {
            throw new IllegalArgumentException("current stage must not be negative");
        }
        if (maximumStage.isPresent() && maximumStage.getAsInt() < 0) {
            throw new IllegalArgumentException("maximum stage must not be negative");
        }
        if (currentStage.isPresent() && currentStage.getAsInt() > maximumStage.getAsInt()) {
            throw new IllegalArgumentException("current stage cannot exceed maximum stage");
        }
    }
}
