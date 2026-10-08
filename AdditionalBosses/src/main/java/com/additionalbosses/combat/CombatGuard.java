package com.additionalbosses.combat;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Deals "secondary" damage (reflections, echoes, lightning bonuses) while a guard flag is raised.
 * While the guard is up, on-hit traits and relics do not trigger again, which prevents endless
 * ping-pong loops such as a Thorned boss fighting a player wearing Bramble Heart.
 */
public final class CombatGuard {

    private static int depth;

    private CombatGuard() {
    }

    public static boolean active() {
        return depth > 0;
    }

    public static void damage(LivingEntity target, double amount, Entity source, DamageType type) {
        if (amount <= 0 || target.isDead() || !target.isValid()) {
            return;
        }
        DamageSource damageSource = DamageSource.builder(type)
            .withCausingEntity(source)
            .withDirectEntity(source)
            .build();
        depth++;
        try {
            target.damage(amount, damageSource);
        } finally {
            depth--;
        }
    }
}
