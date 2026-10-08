package com.additionalbosses.trait;

import com.additionalbosses.boss.Boss;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
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

    public static boolean walks(LivingEntity entity) {
        return !IMMOBILE_OR_FLYING.contains(entity.getType());
    }

    // ---------------- Boss Tells: readable wind-ups before big attacks ----------------

    /** True while this attack is winding up (the tell has been shown and the attack hasn't fired yet). */
    protected static boolean telling(Boss boss, String key, int now) {
        String flag = key + ":tell";
        if (!boss.flag(flag)) {
            return false;
        }
        if (now > boss.cooldownUntil(flag) + 40) {
            boss.setFlag(flag, false); // stale wind-up (the fight moved on); start over next time
            return false;
        }
        return true;
    }

    /** Starts a wind-up that lasts {@code ticks}. */
    protected static void startTell(Boss boss, String key, int now, int ticks) {
        boss.setFlag(key + ":tell", true);
        boss.cooldown(key + ":tell", now, ticks);
    }

    /** True once the wind-up is over; clears it so the attack fires exactly once. */
    protected static boolean tellDone(Boss boss, String key, int now) {
        if (!boss.ready(key + ":tell", now)) {
            return false;
        }
        boss.setFlag(key + ":tell", false);
        return true;
    }

    /** A flat ring of coloured dust, used to show the danger zone of an attack. */
    protected static void ring(Location center, double radius, Color color, float size) {
        int points = Math.max(12, (int) Math.round(radius * 7));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Location at = center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            center.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
        }
    }
}
