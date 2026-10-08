package com.additionalbosses.trait.impl;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/** Offense: enrages below a health threshold, hitting harder and moving faster. */
public final class BerserkTrait extends BaseTrait {

    private static final String FLAG = "berserk";
    private double threshold = 50;
    private double damageBonus = 35;
    private double speedBonus = 20;

    public BerserkTrait() {
        super("berserk", TraitCategory.OFFENSE, "Berserk", "Raging");
    }

    @Override
    public void load(ConfigurationSection s) {
        threshold = s.getDouble("health-threshold", 50);
        damageBonus = s.getDouble("damage-bonus", 35);
        speedBonus = s.getDouble("speed-bonus", 20);
    }

    @Override
    public String description() {
        return "Below " + Text.num(threshold) + "% health it enrages: +" + Text.num(damageBonus) + "% damage and +"
            + Text.num(speedBonus) + "% speed.";
    }

    @Override
    public void onApply(Boss boss, boolean fresh) {
        AttributeInstance speed = boss.entity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(Keys.MOD_TRAIT_BERSERK) != null) {
            boss.setFlag(FLAG, true); // already enraged before a reload
        }
    }

    @Override
    public void onTick(Boss boss, int now) {
        if (!boss.flag(FLAG) && boss.healthRatio() * 100.0 <= threshold) {
            enrage(boss);
        }
    }

    @Override
    public void afterDamaged(Boss boss, EntityDamageEvent event, double finalDamage, DamageContext ctx) {
        if (!boss.flag(FLAG) && (boss.health() - finalDamage) / boss.maxHealth() * 100.0 <= threshold
            && boss.health() - finalDamage > 0) {
            enrage(boss);
        }
    }

    @Override
    public void onAttack(Boss boss, LivingEntity victim, EntityDamageEvent event, DamageContext ctx) {
        if (boss.flag(FLAG)) {
            ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
        }
    }

    private void enrage(Boss boss) {
        boss.setFlag(FLAG, true);
        LivingEntity e = boss.entity();
        AttributeInstance speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(Keys.MOD_TRAIT_BERSERK) == null) {
            speed.addModifier(new AttributeModifier(Keys.MOD_TRAIT_BERSERK, speedBonus * boss.power() / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        Fx.play(e.getLocation(), "entity.ravager.roar", 1.0f, 1.2f);
        Fx.particle(Fx.center(e), Particle.ANGRY_VILLAGER, 8, 0.6, 0);
        AdditionalBosses.get().presentation().messageNearby(boss, "enraged", 32);
    }
}
