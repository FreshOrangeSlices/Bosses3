package com.additionalbosses.nemesis;

import com.additionalbosses.boss.BossManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** The kinds of damage a Nemesis learns to resist. Every BloomPets pet counts as one kind. */
public enum DamageKind {
    MELEE("melee"),
    RANGED("arrows"),
    MAGIC("magic"),
    FIRE("fire"),
    EXPLOSION("explosions"),
    PET("pets");

    private final String plain;

    DamageKind(String plain) {
        this.plain = plain;
    }

    public String plain() {
        return plain;
    }

    /** What kind of damage this is, or null for kinds a Nemesis doesn't adapt to. */
    public static @Nullable DamageKind of(EntityDamageEvent event, @Nullable LivingEntity attacker) {
        if (attacker != null && BossManager.isPet(attacker)) {
            return PET;
        }
        return switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> FIRE;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> EXPLOSION;
            case MAGIC, POISON, WITHER, DRAGON_BREATH -> MAGIC;
            case PROJECTILE -> RANGED;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> MELEE;
            default -> null;
        };
    }

    /** Damage it learns from: a player's, a pet's, or with no attacker (burning, potion clouds...). */
    public static boolean learnsFrom(@Nullable LivingEntity attacker) {
        return attacker == null || attacker instanceof Player || BossManager.isPet(attacker);
    }

    public static @Nullable DamageKind parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
