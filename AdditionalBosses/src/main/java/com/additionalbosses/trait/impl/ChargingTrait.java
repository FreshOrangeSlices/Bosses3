package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Movement: periodically charges at its target; a charging hit hurts more and sends you flying. */
public final class ChargingTrait extends BaseTrait {

    private static final String CHARGE_END = "charge_end";
    private double cooldown = 10;
    private double duration = 2.5;
    private double damageBonus = 30;
    private double knockback = 1.0;

    public ChargingTrait() {
        super("charging", TraitCategory.MOVEMENT, "Charging", "Charging");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = s.getDouble("cooldown-seconds", 10);
        duration = s.getDouble("duration-seconds", 2.5);
        damageBonus = s.getDouble("damage-bonus", 30);
        knockback = s.getDouble("knockback", 1.0);
    }

    @Override
    public String description() {
        return "Every " + Text.num(cooldown) + "s it charges; a charging hit deals +" + Text.num(damageBonus)
            + "% damage and heavy knockback.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return walks(entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onTick(Boss boss, int now) {
        Player target = boss.target();
        LivingEntity e = boss.entity();
        if (target == null || !boss.ready("charge", now) || !e.isOnGround()) {
            return;
        }
        double distance = target.getLocation().distance(e.getLocation());
        if (distance < 5 || distance > 18) {
            return;
        }
        int ticks = (int) Math.round(duration * 20);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 2, false, false));
        boss.cooldown(CHARGE_END, now, ticks);
        boss.cooldown("charge", now, (int) Math.round(cooldown * 20));
        Fx.play(e.getLocation(), "entity.ravager.roar", 0.8f, 1.5f);
        Fx.particle(e.getLocation(), Particle.CLOUD, 12, 0.4, 0.05);
    }

    private boolean charging(Boss boss) {
        return Bukkit.getCurrentTick() < boss.cooldownUntil(CHARGE_END);
    }

    @Override
    public void onAttack(Boss boss, LivingEntity victim, EntityDamageEvent event, DamageContext ctx) {
        if (ctx.melee() && charging(boss)) {
            ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
        }
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (!ctx.melee() || !charging(boss)) {
            return;
        }
        LivingEntity e = boss.entity();
        Vector push = victim.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 0.01) {
            push.normalize().multiply(knockback * 1.2).setY(0.45 * knockback);
            victim.setVelocity(push);
        }
        e.removePotionEffect(PotionEffectType.SPEED);
        boss.cooldown(CHARGE_END, Bukkit.getCurrentTick(), 0);
        Fx.play(victim.getLocation(), "entity.player.attack.knockback", 1.0f, 0.7f);
    }
}
