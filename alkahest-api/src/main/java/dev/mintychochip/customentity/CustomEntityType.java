package dev.mintychochip.customentity;

import org.bukkit.entity.EntityType;

/**
 * Base identity for a catalog-backed custom entity type.
 *
 * <p>Concrete entity definitions extend this type and remain ordinary Bukkit {@link EntityType}
 * values while retaining their carrier-backed lifecycle implementation.
 */
public abstract class CustomEntityType implements EntityType {
}
