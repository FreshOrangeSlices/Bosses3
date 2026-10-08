package com.additionalbosses.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Finds safe standing spots (solid ground, two free blocks above, no liquid).
 */
public final class SafeSpots {

    private SafeSpots() {
    }

    /** A safe spot about {@code distance} blocks behind the player, trying several angles. */
    public static @Nullable Location behind(Player target, double distance) {
        Location origin = target.getLocation();
        Vector back = origin.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        back.normalize().multiply(-distance);
        double[] angles = {0, 45, -45, 90, -90, 135, -135, 180};
        for (double deg : angles) {
            Location spot = standable(origin.clone().add(back.clone().rotateAroundY(Math.toRadians(deg))), 3);
            if (spot != null) {
                Vector look = origin.toVector().subtract(spot.toVector());
                spot.setDirection(look.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : look);
                return spot;
            }
        }
        return null;
    }

    /** A safe spot in a ring around {@code center} between min and max distance (random angles). */
    public static @Nullable Location around(Location center, double min, double max, int attempts) {
        for (int i = 0; i < attempts; i++) {
            double angle = Math.toRadians(Rng.between(0.0, 360.0));
            double dist = Rng.between(min, max);
            Location c = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            Location spot = standable(c, 6);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Searches up and down from the candidate for a block you can stand on. */
    public static @Nullable Location standable(Location candidate, int verticalSearch) {
        World world = candidate.getWorld();
        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int baseY = candidate.getBlockY();
        for (int i = 0; i <= verticalSearch * 2; i++) {
            int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
            int y = baseY + dy;
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
                continue;
            }
            Block feet = world.getBlockAt(x, y, z);
            Block head = feet.getRelative(BlockFace.UP);
            Block ground = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                && ground.getType().isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }
}
