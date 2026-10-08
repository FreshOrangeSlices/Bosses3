package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.CombatGuard;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/** Defense: melee attackers take part of their own damage back. */
public final class ThornedTrait extends BaseTrait {

    private double reflectPercent = 20;

    public ThornedTrait() {
        super("thorned", TraitCategory.DEFENSE, "Thorned", "Thorned");
    }

    @Override
    public void load(ConfigurationSection s) {
        reflectPercent = s.getDouble("reflect-percent", 20);
    }

    @Override
    public String description() {
        return "Reflects " + Text.num(reflectPercent) + "% of melee damage back at the attacker.";
    }

    @Override
    public void afterDamaged(Boss boss, EntityDamageEvent event, double finalDamage, DamageContext ctx) {
        LivingEntity attacker = ctx.attacker();
        if (!ctx.melee() || ctx.secondary() || attacker == null || attacker == boss.entity()) {
            return;
        }
        double amount = finalDamage * reflectPercent * boss.power() / 100.0;
        if (amount < 0.5) {
            return;
        }
        CombatGuard.damage(attacker, amount, boss.entity(), DamageType.THORNS);
        Fx.particle(Fx.center(attacker), Particle.CRIT, 6, 0.3, 0.1);
    }
}
