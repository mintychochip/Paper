package dev.mintychochip.behavior;

/**
 * Decision returned by a custom-value behavior receiver.
 */
public enum Decision {
    /** Use the carrier or metadata fallback. */
    DEFAULT,
    /** Explicitly allow the operation. */
    ALLOW,
    /** Explicitly deny the operation. */
    DENY
}
