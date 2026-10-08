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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Control: hits wrap the target in Darkness. */
public final class ShadowedTrait extends BaseTrait {

    private double seconds = 4;

    public ShadowedTrait() {
        super("shadowed", TraitCategory.CONTROL, "Shadowed", "Shadowed");
    }

    @Override
    public void load(ConfigurationSection s) {
        seconds = s.getDouble("effect-seconds", 4);
    }

    @Override
    public String description() {
        return "Hits engulf players in Darkness for " + Text.num(seconds) + "s.";
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (ctx.secondary() || finalDamage <= 0 || !(victim instanceof Player)) {
            return;
        }
        int ticks = (int) Math.round(seconds * 20 * boss.power());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0));
        Fx.particle(Fx.center(victim), Particle.SQUID_INK, 8, 0.3, 0.02);
    }
}
