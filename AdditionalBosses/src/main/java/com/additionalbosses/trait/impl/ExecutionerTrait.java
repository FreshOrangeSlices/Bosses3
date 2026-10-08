package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/** Offense: deals extra damage to targets that are already low on health. */
public final class ExecutionerTrait extends BaseTrait {

    private double threshold = 40;
    private double damageBonus = 50;

    public ExecutionerTrait() {
        super("executioner", TraitCategory.OFFENSE, "Executioner", "Merciless");
    }

    @Override
    public void load(ConfigurationSection s) {
        threshold = s.getDouble("health-threshold", 40);
        damageBonus = s.getDouble("damage-bonus", 50);
    }

    @Override
    public String description() {
        return "Deals +" + Text.num(damageBonus) + "% damage to targets below " + Text.num(threshold) + "% health.";
    }

    @Override
    public void onAttack(Boss boss, LivingEntity victim, EntityDamageEvent event, DamageContext ctx) {
        AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        if (victim.getHealth() / maxHealth * 100.0 <= threshold) {
            ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
        }
    }
}
