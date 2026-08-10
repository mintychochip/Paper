package dev.mintychochip.customblock;

/** Operation that caused a custom plant growth receiver to run. */
public enum PlantGrowthCause {
    /** The carrier received a scheduled random tick. */
    RANDOM_TICK,
    /** A bonemeal operation requested growth. */
    BONEMEAL
}
