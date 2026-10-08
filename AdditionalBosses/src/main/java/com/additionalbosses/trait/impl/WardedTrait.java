package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent;

/** Defense: often deflects projectiles, so it can't simply be shot from a pillar. */
public final class WardedTrait extends BaseTrait {

    private double blockChance = 50;

    public WardedTrait() {
        super("warded", TraitCategory.DEFENSE, "Warded", "Warded");
    }

    @Override
    public void load(ConfigurationSection s) {
        blockChance = s.getDouble("block-chance", 50);
    }

    @Override
    public String description() {
        return Text.num(blockChance) + "% chance to deflect arrows and other projectiles.";
    }

    @Override
    public void onDamaged(Boss boss, EntityDamageEvent event, DamageContext ctx) {
        if (ctx.projectile() && Rng.chance(Math.min(90.0, blockChance * boss.power()))) {
            ctx.cancel();
            Fx.play(boss.entity().getLocation(), "item.shield.block", 1.0f, 0.8f);
            Fx.particle(Fx.center(boss.entity()), Particle.ENCHANTED_HIT, 10, 0.4, 0.1);
        }
    }
}
