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
        return "Every " + Text.num(cooldown) + "s it hums and a violet ring appears, then it pulls players within "
            + Text.num(radius) + " blocks toward itself.";
    }

    @Override
    public void onTick(Boss boss, int now) {
        if (!boss.inCombat() || !boss.ready("gravity", now)) {
            return;
        }
        LivingEntity e = boss.entity();
        if (!telling(boss, "gravity", now)) {
            // Tell: a violet ring draws inward and the air hums before the pull.
            boolean someoneFar = false;
            for (Player p : boss.nearbyPlayers(radius)) {
                if (p.getLocation().distanceSquared(e.getLocation()) >= 9) {
                    someoneFar = true;
                    break;
                }
            }
            if (!someoneFar) {
                return;
            }
            startTell(boss, "gravity", now, 20);
            Fx.play(e.getLocation(), "block.beacon.deactivate", 1.0f, 0.5f);
            ring(e.getLocation(), radius, Color.fromRGB(0x8A2BE2), 1.3f);
            Fx.particle(Fx.center(e), Particle.REVERSE_PORTAL, 30, 0.6, 0.02);
            return;
        }
        if (!tellDone(boss, "gravity", now)) {
            ring(e.getLocation(), radius * 0.6, Color.fromRGB(0xB060FF), 1.3f);
            return;
        }
        boss.cooldown("gravity", now, (int) Math.round(cooldown * 20));
        List<Player> players = boss.nearbyPlayers(radius);
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
        }
        Fx.play(e.getLocation(), "block.respawn_anchor.deplete", 1.0f, 0.6f);
        Fx.particle(Fx.center(e), Particle.REVERSE_PORTAL, 40, 1.2, 0.1);
    }
}
