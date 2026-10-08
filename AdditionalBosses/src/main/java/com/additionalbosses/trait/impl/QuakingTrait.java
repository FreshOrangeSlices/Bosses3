package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Control: slams the ground, hurting and launching nearby players. */
public final class QuakingTrait extends BaseTrait {

    private double cooldown = 9;
    private double radius = 5;
    private double damage = 4;
    private double knockback = 1.1;

    public QuakingTrait() {
        super("quaking", TraitCategory.CONTROL, "Quaking", "Quaking");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = s.getDouble("cooldown-seconds", 9);
        radius = s.getDouble("radius", 5);
        damage = s.getDouble("damage", 4);
        knockback = s.getDouble("knockback", 1.1);
    }

    @Override
    public String description() {
        return "Every " + Text.num(cooldown) + "s it slams the ground, hitting players within " + Text.num(radius)
            + " blocks and knocking them away.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return walks(entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onTick(Boss boss, int now) {
        LivingEntity e = boss.entity();
        if (!boss.inCombat() || !boss.ready("quake", now) || !e.isOnGround()) {
            return;
        }
        List<Player> players = boss.nearbyPlayers(radius);
        if (players.isEmpty()) {
            return;
        }
        boss.cooldown("quake", now, (int) Math.round(cooldown * 20));
        Fx.play(e.getLocation(), "entity.generic.explode", 0.7f, 0.6f);
        Fx.particle(e.getLocation().add(0, 0.2, 0), Particle.EXPLOSION, 4, radius / 3.0, 0);
        Fx.particle(e.getLocation().add(0, 0.2, 0), Particle.CLOUD, 30, radius / 2.0, 0.05);
        for (Player p : players) {
            p.damage(damage * boss.power(), e);
            Vector away = p.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(0.1, 0, 0);
            }
            away.normalize().multiply(knockback).setY(0.55);
            p.setVelocity(away);
        }
    }
}
