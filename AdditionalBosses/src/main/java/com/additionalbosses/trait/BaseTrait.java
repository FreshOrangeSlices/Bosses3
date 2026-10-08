package com.additionalbosses.trait;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

/**
 * Convenience base class holding a trait's identity.
 */
public abstract class BaseTrait implements BossTrait {

    /** Mobs that fly, swim-only or never move; movement traits skip these. */
    protected static final Set<EntityType> IMMOBILE_OR_FLYING = Set.of(
        EntityType.PHANTOM, EntityType.GHAST, EntityType.BLAZE, EntityType.VEX, EntityType.SHULKER,
        EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.WITHER, EntityType.BREEZE,
        EntityType.HAPPY_GHAST, EntityType.CREAKING);

    private final String id;
    private final TraitCategory category;
    private final String displayName;
    private final String adjective;

    protected BaseTrait(String id, TraitCategory category, String displayName, String adjective) {
        this.id = id;
        this.category = category;
        this.displayName = displayName;
        this.adjective = adjective;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public TraitCategory category() {
        return category;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String adjective() {
        return adjective;
    }

    protected static boolean walks(LivingEntity entity) {
        return !IMMOBILE_OR_FLYING.contains(entity.getType());
    }
}
