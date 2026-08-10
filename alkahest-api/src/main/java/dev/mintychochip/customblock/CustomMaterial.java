package dev.mintychochip.customblock;

import org.bukkit.Material;

/**
 * Base identity for a catalog-backed custom material.
 *
 * <p>Concrete block/material definitions extend this type and remain ordinary {@link Material}
 * values. The catalog stores the concrete value; this class only makes the custom registry type
 * explicit without duplicating Bukkit's large material contract.
 */
public abstract class CustomMaterial implements Material {
}
