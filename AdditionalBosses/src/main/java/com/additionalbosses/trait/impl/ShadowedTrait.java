package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Control: dark smoke gathers around the boss for a few seconds (the warning: step away), then everyone still close
 * is wrapped in Darkness. It used to blind on every hit, which together with other attacks could feel like an
 * instant, unreadable death.
 */
public final class ShadowedTrait extends BaseTrait {

    private double cooldown = 12;
    private double warning = 3;
    private double radius = 6;
    private double seconds = 3;

    public ShadowedTrait() {
        super("shadowed", TraitCategory.CONTROL, "Shadowed", "Shadowed");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = Math.max(1, s.getDouble("cooldown-seconds", 12));
        warning = Math.max(0.5, s.getDouble("warning-seconds", 3));
        radius = Math.max(1, s.getDouble("radius", 6));
        seconds = Math.max(0.5, s.getDouble("effect-seconds", 3));
    }

    @Override
    public String description() {
        return "Every " + Text.num(cooldown) + "s dark smoke gathers around it for " + Text.num(warning)
            + "s, then players within " + Text.num(radius) + " blocks are wrapped in Darkness for " + Text.num(seconds) + "s.";
    }

    @Override
    public void onTick(Boss boss, int now) {
        LivingEntity e = boss.entity();
        if (!boss.inCombat() || !boss.ready("shadow", now)) {
            return;
        }
        int warnTicks = (int) Math.round(warning * 20);
        if (!telling(boss, "shadow", now)) {
            if (boss.nearbyPlayers(radius).isEmpty()) {
                return;
            }
            startTell(boss, "shadow", now, warnTicks);
            Fx.play(e.getLocation(), "entity.warden.nearby_closer", 1.0f, 0.7f);
            Fx.particle(Fx.center(e), Particle.SQUID_INK, 20, 0.5, 0.02);
            ring(e.getLocation(), radius, Color.fromRGB(0x2A1B3D), 1.6f);
            return;
        }
        if (!tellDone(boss, "shadow", now)) {
            Fx.particle(Fx.center(e), Particle.LARGE_SMOKE, 10, 0.8, 0.01);
            ring(e.getLocation(), radius, Color.fromRGB(0x3B2357), 1.6f);
            return;
        }
        boss.cooldown("shadow", now, (int) Math.round(cooldown * 20));
        Fx.play(e.getLocation(), "entity.warden.sonic_charge", 0.8f, 0.6f);
        Fx.particle(Fx.center(e), Particle.SQUID_INK, 60, radius / 2.0, 0.05);
        int ticks = (int) Math.round(seconds * 20);
        for (Player p : boss.nearbyPlayers(radius)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0));
        }
    }
}
