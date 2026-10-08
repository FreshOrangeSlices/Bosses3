package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Control: hits chill the target, slowing it (with the powder-snow frost overlay, but no freeze damage). */
public final class FrostboundTrait extends BaseTrait {

    private double seconds = 3;
    private int amplifier = 1;

    public FrostboundTrait() {
        super("frostbound", TraitCategory.CONTROL, "Frostbound", "Frostbound");
    }

    @Override
    public void load(ConfigurationSection s) {
        seconds = s.getDouble("effect-seconds", 3);
        amplifier = Math.max(0, s.getInt("amplifier", 1));
    }

    @Override
    public String description() {
        return "Hits inflict Slowness " + Text.roman(amplifier + 1) + " for " + Text.num(seconds) + "s.";
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (ctx.secondary() || finalDamage <= 0) {
            return;
        }
        int ticks = (int) Math.round(seconds * 20 * boss.power());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier));
        // Visual frost only: stay below the maximum so no freeze damage is dealt.
        victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), Math.max(0, victim.getMaxFreezeTicks() - 20)));
        Fx.particle(Fx.center(victim), Particle.SNOWFLAKE, 12, 0.4, 0.02);
    }
}
