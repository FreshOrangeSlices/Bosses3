package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

/** Defense: heals steadily whenever it hasn't been hurt for a few seconds. */
public final class RegeneratingTrait extends BaseTrait {

    private double percentPerSecond = 1.5;
    private double delaySeconds = 4;

    public RegeneratingTrait() {
        super("regenerating", TraitCategory.DEFENSE, "Regenerating", "Regenerating");
    }

    @Override
    public void load(ConfigurationSection s) {
        percentPerSecond = s.getDouble("heal-percent-per-second", 1.5);
        delaySeconds = s.getDouble("delay-seconds", 4);
    }

    @Override
    public String description() {
        return "If not hurt for " + Text.num(delaySeconds) + "s, it heals " + Text.num(percentPerSecond)
            + "% of its max health per second.";
    }

    @Override
    public void onTick(Boss boss, int now) {
        if (boss.healthRatio() >= 1.0 || now - boss.lastHurtTick() < delaySeconds * 20) {
            return;
        }
        // onTick runs every 10 ticks = half a second.
        boss.heal(boss.maxHealth() * percentPerSecond * boss.power() / 100.0 / 2.0);
        if (now % 40 < 10) {
            Fx.particle(Fx.center(boss.entity()), Particle.HAPPY_VILLAGER, 3, 0.5, 0);
        }
    }
}
