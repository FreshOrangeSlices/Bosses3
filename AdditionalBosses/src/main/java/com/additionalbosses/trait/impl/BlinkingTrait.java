package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Movement: teleports behind targets that keep their distance (or shoot it). */
public final class BlinkingTrait extends BaseTrait {

    private double cooldown = 8;
    private double minDistance = 7;

    public BlinkingTrait() {
        super("blinking", TraitCategory.MOVEMENT, "Blinking", "Phasing");
    }

    @Override
    public void load(ConfigurationSection s) {
        cooldown = s.getDouble("cooldown-seconds", 8);
        minDistance = s.getDouble("min-distance", 7);
    }

    @Override
    public String description() {
        return "Teleports behind targets more than " + Text.num(minDistance) + " blocks away, or when shot (every "
            + Text.num(cooldown) + "s).";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return walks(entity);
    }

    @Override
    public void onTick(Boss boss, int now) {
        Player target = boss.target();
        if (target == null || !boss.ready("blink", now)) {
            return;
        }
        if (target.getLocation().distanceSquared(boss.entity().getLocation()) > minDistance * minDistance) {
            blink(boss, target, now);
        }
    }

    @Override
    public void afterDamaged(Boss boss, EntityDamageEvent event, double finalDamage, DamageContext ctx) {
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (ctx.projectile() && ctx.attacker() instanceof Player shooter && boss.fightable(shooter)
            && boss.ready("blink", now)
            && shooter.getLocation().distanceSquared(boss.entity().getLocation()) < 48 * 48) {
            blink(boss, shooter, now);
        }
    }

    private void blink(Boss boss, Player target, int now) {
        Location destination = findSpot(target);
        if (destination == null) {
            boss.cooldown("blink", now, 20);
            return;
        }
        LivingEntity e = boss.entity();
        Fx.particle(Fx.center(e), Particle.PORTAL, 30, 0.5, 0.4);
        Fx.play(e.getLocation(), "entity.enderman.teleport", 1.0f, 0.9f);
        e.teleport(destination);
        Fx.particle(Fx.center(e), Particle.PORTAL, 30, 0.5, 0.4);
        Fx.play(destination, "entity.enderman.teleport", 1.0f, 1.1f);
        boss.cooldown("blink", now, (int) Math.round(cooldown * 20));
    }

    /** A safe spot about two blocks behind the target, trying a few angles if the first is blocked. */
    private @Nullable Location findSpot(Player target) {
        Location origin = target.getLocation();
        World world = origin.getWorld();
        Vector back = origin.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        back.normalize().multiply(-2.2);
        double[] angles = {0, 45, -45, 90, -90, 135, -135, 180};
        for (double deg : angles) {
            Vector offset = back.clone().rotateAroundY(Math.toRadians(deg));
            Location candidate = origin.clone().add(offset);
            for (int dy = 1; dy >= -2; dy--) {
                Block feet = world.getBlockAt(candidate.getBlockX(), candidate.getBlockY() + dy, candidate.getBlockZ());
                Block head = feet.getRelative(BlockFace.UP);
                Block ground = feet.getRelative(BlockFace.DOWN);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                    && ground.getType().isSolid()) {
                    Location spot = feet.getLocation().add(0.5, 0, 0.5);
                    Vector look = origin.toVector().subtract(spot.toVector());
                    spot.setDirection(look.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : look);
                    return spot;
                }
            }
        }
        return null;
    }
}
