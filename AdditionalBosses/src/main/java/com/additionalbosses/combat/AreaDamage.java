package com.additionalbosses.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Damage from a boss's area attacks (Quaking's slam, Ascendant shockwaves). It goes through the normal damage
 * handling, but never hits a player harder than the configured cap, however strong the boss: dangerous, not instant.
 */
public final class AreaDamage {

    private static int depth;

    private AreaDamage() {
    }

    public static void hit(Player player, double amount, LivingEntity source) {
        depth++;
        try {
            player.damage(amount, source);
        } finally {
            depth--;
        }
    }

    /** True while an area attack's damage is being dealt. */
    public static boolean active() {
        return depth > 0;
    }
}
