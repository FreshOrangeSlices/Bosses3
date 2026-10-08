package com.additionalbosses.listener;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BossTrait;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * The single damage pipeline for bosses and relics.
 * <ol>
 *   <li>HIGH: collect every change (boss rank damage, traits, relics) in one DamageContext, then apply it once.</li>
 *   <li>MONITOR: react to the final damage (lifesteal, reflection, on-hit effects, engagement, boss bar).</li>
 * </ol>
 */
public final class CombatListener implements Listener {

    private final AdditionalBosses plugin;

    public CombatListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private static boolean ignored(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID
            || cause == EntityDamageEvent.DamageCause.WORLD_BORDER;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || ignored(event)) {
            return;
        }
        DamageContext ctx = DamageContext.of(event);
        LivingEntity attacker = ctx.attacker();
        Boss attackerBoss = attacker == null || attacker == victim ? null : plugin.bosses().get(attacker);
        Boss victimBoss = plugin.bosses().get(victim);

        if (attackerBoss != null && !ctx.secondary()) {
            ctx.multiply(attackerBoss.damageMultiplier());
            for (BossTrait trait : attackerBoss.traits()) {
                trait.onAttack(attackerBoss, victim, event, ctx);
            }
        }
        if (victimBoss != null) {
            victimBoss.markHurt(Bukkit.getCurrentTick());
            for (BossTrait trait : victimBoss.traits()) {
                trait.onDamaged(victimBoss, event, ctx);
            }
        }
        if (attacker instanceof Player player && attacker != victim && !ctx.secondary()) {
            plugin.relics().onAttack(player, victim, event, ctx, victimBoss != null);
        }
        if (victim instanceof Player player) {
            plugin.relics().onDamaged(player, event, ctx);
        }

        if (ctx.cancelled()) {
            event.setCancelled(true);
        } else if (ctx.changed()) {
            event.setDamage(ctx.apply(event.getDamage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || ignored(event)) {
            return;
        }
        double finalDamage = event.getFinalDamage();
        DamageContext ctx = DamageContext.of(event);
        LivingEntity attacker = ctx.attacker();
        Boss attackerBoss = attacker == null || attacker == victim ? null : plugin.bosses().get(attacker);
        Boss victimBoss = plugin.bosses().get(victim);

        if (attackerBoss != null) {
            if (victim instanceof Player player) {
                plugin.bosses().engage(attackerBoss, player);
            }
            if (!ctx.secondary()) {
                for (BossTrait trait : attackerBoss.traits()) {
                    trait.afterAttack(attackerBoss, victim, finalDamage, ctx);
                }
            }
        }
        if (victimBoss != null) {
            if (attacker instanceof Player player) {
                plugin.bosses().engage(victimBoss, player);
            }
            for (BossTrait trait : victimBoss.traits()) {
                trait.afterDamaged(victimBoss, event, finalDamage, ctx);
            }
            plugin.bossBars().updateHealth(victimBoss, victim.getHealth() - finalDamage);
        }
        if (attacker instanceof Player player && attacker != victim && !ctx.secondary()) {
            plugin.relics().afterAttack(player, victim, finalDamage, ctx);
        }
        if (victim instanceof Player player) {
            plugin.relics().afterDamaged(player, event, finalDamage, ctx);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        Boss boss = plugin.bosses().get(event.getEntity());
        if (boss != null) {
            plugin.bossBars().updateHealth(boss, Math.min(boss.maxHealth(), boss.health() + event.getAmount()));
        }
    }
}
