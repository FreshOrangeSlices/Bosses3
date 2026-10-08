package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Offense: its hits poison. */
public final class VenomousTrait extends BaseTrait {

    private double seconds = 4;
    private int amplifier = 1;

    public VenomousTrait() {
        super("venomous", TraitCategory.OFFENSE, "Venomous", "Venomous");
    }

    @Override
    public void load(ConfigurationSection s) {
        seconds = s.getDouble("effect-seconds", 4);
        amplifier = Math.max(0, s.getInt("amplifier", 1));
    }

    @Override
    public String description() {
        return "Hits inflict Poison " + Text.roman(amplifier + 1) + " for " + Text.num(seconds) + "s.";
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (ctx.secondary() || finalDamage <= 0) {
            return;
        }
        int ticks = (int) Math.round(seconds * 20 * boss.power());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, amplifier));
    }
}
