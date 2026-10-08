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

/** Movement: leaps at its target from a distance. */
public final class LeapingTrait extends BaseTrait {

    private double cooldown = 6;
    private double minDistance = 4;
    private double maxDistance = 14;
    private double strength = 1.0;

    public LeapingTrait() {
        super("leaping", TraitCategory.MOVEMENT, "Leaping", "Leaping");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = s.getDouble("cooldown-seconds", 6);
        minDistance = s.getDouble("min-distance", 4);
        maxDistance = s.getDouble("max-distance", 14);
        strength = s.getDouble("strength", 1.0);
    }

    @Override
    public String description() {
        return "Leaps at targets " + Text.num(minDistance) + "-" + Text.num(maxDistance) + " blocks away (every "
            + Text.num(cooldown) + "s).";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return walks(entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onTick(Boss boss, int now) {
        LivingEntity e = boss.entity();
        Player target = boss.target();
        if (target == null || !boss.ready("leap", now) || !e.isOnGround()) {
            return;
        }
        Vector diff = target.getLocation().toVector().subtract(e.getLocation().toVector());
        double distance = diff.length();
        if (distance < minDistance || distance > maxDistance) {
            return;
        }
        Vector horizontal = diff.clone().setY(0);
        if (horizontal.lengthSquared() < 0.01) {
            return;
        }
        horizontal.normalize().multiply((0.25 + distance * 0.075) * strength);
        horizontal.setY(0.42 + Math.max(0, diff.getY()) * 0.08 + distance * 0.015);
        e.setVelocity(horizontal);
        boss.cooldown("leap", now, (int) Math.round(cooldown * 20));
        Fx.play(e.getLocation(), "entity.goat.long_jump", 1.0f, 0.7f);
        Fx.particle(e.getLocation(), Particle.CLOUD, 10, 0.4, 0.02);
    }
}
