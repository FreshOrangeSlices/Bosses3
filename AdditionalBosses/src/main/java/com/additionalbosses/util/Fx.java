package com.additionalbosses.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Sound and particle helpers. Sounds are referenced by their Minecraft key (e.g. "entity.wither.spawn"),
 * which keeps the config readable and avoids depending on enum names.
 */
public final class Fx {

    private Fx() {
    }

    public static Sound sound(String key, float volume, float pitch) {
        return Sound.sound(Key.key(key), Sound.Source.HOSTILE, volume, pitch);
    }

    /**
     * Parses "entity.wither.spawn", "entity.wither.spawn 0.6" or "entity.wither.spawn 0.6 1.2".
     * Returns null for an empty or invalid value.
     */
    public static @Nullable Sound parseSound(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return Sound.sound(Key.key(parts[0].toLowerCase()), Sound.Source.HOSTILE, volume, pitch);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Plays a sound in the world at a location (everyone nearby hears it). */
    public static void play(Location at, String key, float volume, float pitch) {
        at.getWorld().playSound(sound(key, volume, pitch), at.getX(), at.getY(), at.getZ());
    }

    public static void play(Location at, @Nullable Sound sound) {
        if (sound != null) {
            at.getWorld().playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    /** Plays a sound only this player hears. */
    public static void playTo(Player player, @Nullable Sound sound) {
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    public static void dust(Location at, Color color, float size, int count, double spread) {
        if (count <= 0) {
            return;
        }
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0,
            new Particle.DustOptions(color, size));
    }

    public static void particle(Location at, Particle particle, int count, double spread, double speed) {
        if (count <= 0) {
            return;
        }
        at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, speed);
    }

    public static Location center(Entity entity) {
        return entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
    }
}
