package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/** Offense: heals for part of the damage it deals. */
public final class VampiricTrait extends BaseTrait {

    private double healPercent = 25;

    public VampiricTrait() {
        super("vampiric", TraitCategory.OFFENSE, "Vampiric", "Ravenous");
    }

    @Override
    public void load(ConfigurationSection s) {
        healPercent = s.getDouble("heal-percent", 25);
    }

    @Override
    public String description() {
        return "Heals for " + Text.num(healPercent) + "% of the damage it deals.";
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (finalDamage <= 0) {
            return;
        }
        boss.heal(finalDamage * healPercent * boss.power() / 100.0);
        Fx.dust(Fx.center(boss.entity()), Color.fromRGB(0x8B0000), 1.2f, 6, 0.4);
    }
}
