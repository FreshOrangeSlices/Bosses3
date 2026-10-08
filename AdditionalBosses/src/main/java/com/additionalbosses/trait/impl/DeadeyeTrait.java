package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import com.destroystokyo.paper.entity.RangedEntity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/** Offense (ranged mobs only): faster, harder-hitting projectiles. */
public final class DeadeyeTrait extends BaseTrait {

    private static final Set<EntityType> EXTRA_SHOOTERS = Set.of(EntityType.BLAZE, EntityType.GHAST, EntityType.BREEZE);
    private double speedBonus = 40;
    private double damageBonus = 25;

    public DeadeyeTrait() {
        super("deadeye", TraitCategory.OFFENSE, "Deadeye", "Deadeye");
    }

    @Override
    public void load(ConfigurationSection s) {
        speedBonus = s.getDouble("projectile-speed-bonus", 40);
        damageBonus = s.getDouble("damage-bonus", 25);
    }

    @Override
    public String description() {
        return "Ranged mobs only. Projectiles fly " + Text.num(speedBonus) + "% faster and deal +"
            + Text.num(damageBonus) + "% damage.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return entity instanceof RangedEntity || EXTRA_SHOOTERS.contains(entity.getType());
    }

    @Override
    public void onProjectileLaunch(Boss boss, Projectile projectile) {
        if (VolleyTrait.isSpawning()) {
            return; // volley arrows copy the already-boosted velocity of the main arrow
        }
        projectile.setVelocity(projectile.getVelocity().multiply(1.0 + speedBonus / 100.0));
    }

    @Override
    public void onAttack(Boss boss, LivingEntity victim, EntityDamageEvent event, DamageContext ctx) {
        if (ctx.projectile()) {
            ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
        }
    }
}
