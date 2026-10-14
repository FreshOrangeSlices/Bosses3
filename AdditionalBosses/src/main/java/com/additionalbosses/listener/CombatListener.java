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
        // An area attack (Quaking's slam, an Ascendant shockwave) deals its own fixed amount: no rank multiplier,
        // no minimum hit and no on-hit traits, then the cap, so it's dangerous but never instant.
        boolean area = com.additionalbosses.combat.AreaDamage.active() && victim instanceof Player && attackerBoss != null;

        if (attackerBoss != null && !ctx.secondary() && !area) {
            ctx.multiply(attackerBoss.damageMultiplier());
            if (ctx.melee()) {
                ctx.floor(plugin.settings().rank(attackerBoss.rank()).stats().minDamage());
            }
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
        double overflow = victim instanceof Player player && attackerBoss != null ? overflowArmor(player) : 1.0;
        if (victimBoss != null && victimBoss.isNemesis() && !ctx.secondary()) {
            plugin.nemesis().onHurt(victimBoss, event, ctx); // what it has adapted to hurts it less (or not at all)
        }
        if (attackerBoss != null && attackerBoss.isNemesis() && com.additionalbosses.boss.BossManager.isPet(victim)) {
            plugin.nemesis().onHitPet(attackerBoss, ctx);
        }

        if (ctx.cancelled()) {
            event.setCancelled(true);
        } else if (ctx.changed() || area || overflow < 1.0) {
            double damage = ctx.changed() ? ctx.apply(event.getDamage()) : event.getDamage();
            if (area) {
                damage = Math.min(damage, plugin.settings().areaDamageCap); // area attacks: dangerous, never instant
            }
            event.setDamage(damage * overflow); // overflow armor last, so it helps against capped hits too
        }
    }

    /**
     * Armor past the point where it stops helping (20) keeps paying off against bosses: 5% less damage from them per
     * point over. Applied after everything else, so it also counts against a boss's minimum hit.
     */
    private double overflowArmor(Player player) {
        var f = plugin.settings().features;
        if (!f.overflowArmor) {
            return 1.0;
        }
        org.bukkit.attribute.AttributeInstance armor = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
        double over = armor == null ? 0 : armor.getValue() - f.overflowArmorFrom;
        return over > 0 ? Math.max(0, 1 - over * f.overflowArmorPerPoint / 100.0) : 1.0;
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

        if (victim instanceof Player hurt && attacker != null
            && (attackerBoss != null || com.additionalbosses.boss.BossManager.isMinion(attacker))) {
            plugin.nemesis().markBossHit(hurt); // (a death a boss set up never counts as a surrender)
        }
        if (attackerBoss != null) {
            if (victim instanceof Player player) {
                plugin.bosses().engage(attackerBoss, player);
            }
            if (!ctx.secondary() && !com.additionalbosses.combat.AreaDamage.active()) {
                for (BossTrait trait : attackerBoss.traits()) {
                    trait.afterAttack(attackerBoss, victim, finalDamage, ctx);
                }
            }
        }
        if (victimBoss != null) {
            if (attacker instanceof Player player) {
                if (victimBoss.isNemesis()) {
                    plugin.nemesis().onProvoked(victimBoss, player);
                }
                plugin.bosses().engage(victimBoss, player);
                victimBoss.recordPlayerHit(player.getUniqueId(), finalDamage, ctx.projectile());
            }
            if (victimBoss.isNemesis()) {
                plugin.nemesis().recordDamage(victimBoss, event, attacker, finalDamage);
            }
            for (BossTrait trait : victimBoss.traits()) {
                trait.afterDamaged(victimBoss, event, finalDamage, ctx);
            }
            plugin.bosses().checkLastStand(victimBoss, victim.getHealth() - finalDamage);
            plugin.bosses().checkPhases(victimBoss, victim.getHealth() - finalDamage);
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
