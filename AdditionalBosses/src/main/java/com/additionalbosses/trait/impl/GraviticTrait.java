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

/** Control: periodically yanks nearby players toward itself. */
public final class GraviticTrait extends BaseTrait {

    private double cooldown = 8;
    private double radius = 10;
    private double strength = 0.9;

    public GraviticTrait() {
        super("gravitic", TraitCategory.CONTROL, "Gravitic", "Gravitic");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = s.getDouble("cooldown-seconds", 8);
        radius = s.getDouble("radius", 10);
        strength = s.getDouble("strength", 0.9);
    }

    @Override
    public String description() {
        return "Every " + Text.num(cooldown) + "s it pulls players within " + Text.num(radius) + " blocks toward itself.";
    }

    @Override
    public void onTick(Boss boss, int now) {
        if (!boss.inCombat() || !boss.ready("gravity", now)) {
            return;
        }
        LivingEntity e = boss.entity();
        List<Player> players = boss.nearbyPlayers(radius);
        boolean pulled = false;
        for (Player p : players) {
            Vector diff = e.getLocation().toVector().subtract(p.getLocation().toVector());
            double distance = diff.length();
            if (distance < 3) {
                continue;
            }
            Vector pull = diff.normalize().multiply(Math.min(1.6, strength * (0.4 + distance * 0.08)));
            pull.setY(Math.max(0.25, pull.getY()));
            p.setVelocity(pull);
            Fx.particle(Fx.center(p), Particle.REVERSE_PORTAL, 15, 0.3, 0.05);
            pulled = true;
        }
        if (pulled) {
            Fx.play(e.getLocation(), "block.respawn_anchor.deplete", 1.0f, 0.6f);
            Fx.particle(Fx.center(e), Particle.REVERSE_PORTAL, 40, 1.2, 0.1);
            boss.cooldown("gravity", now, (int) Math.round(cooldown * 20));
        }
    }
}
