package com.bloompets;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything config.yml says. */
public final class Settings {

    public final int maxPets;
    public final Set<Species> enabled = EnumSet.noneOf(Species.class);
    private final Map<Species, Integer> feeds = new EnumMap<>(Species.class);
    public final int rideUnlockLevel;
    /** Pets that can be ridden from level 1. */
    public final Set<Species> rideFromStart = EnumSet.noneOf(Species.class);
    public final double hoverHeight;
    public final double minRiderScale;
    public final int restSeconds;
    public final double restHealth;
    public final boolean protectFromPlayers;
    public final boolean allayBottlesXp;
    public final int xpFeed;
    public final int xpOwnerKill;
    public final int xpPetKill;
    public final int rideBlocksPerXp;
    public final int xpPerMinute;
    public final int walkBlocksPerXp;
    public final int beeRadius;
    public final int beeCrops;
    public final double beeSeconds;
    public final int stationLevel;
    public final int maxStations;
    public final double guardRadius;
    public final double healPercent;

    public Settings(FileConfiguration c) {
        maxPets = Math.max(1, Math.min(45, c.getInt("max-pets", 27)));
        ConfigurationSection on = c.getConfigurationSection("pets");
        for (Species s : Species.values()) {
            if (on == null || on.getBoolean(s.id(), true)) {
                enabled.add(s);
            }
        }
        int def = Math.max(1, c.getInt("taming.feeds.default", 5));
        ConfigurationSection f = c.getConfigurationSection("taming.feeds");
        for (Species s : Species.values()) {
            int fallback = switch (s) {
                case IRON_GOLEM -> 8;
                case SNIFFER -> 6;
                case VEX, BAT -> 3;
                default -> def;
            };
            feeds.put(s, Math.max(1, f == null ? fallback : f.getInt(s.id(), fallback)));
        }
        rideUnlockLevel = Math.max(1, Math.min(Pet.MAX_LEVEL, c.getInt("riding.unlock-level", 3)));
        List<String> easy = c.isList("riding.ride-from-start") ? c.getStringList("riding.ride-from-start")
            : List.of("cat", "wolf", "rabbit", "horse");
        for (String id : easy) {
            Species s = Species.parse(id);
            if (s != null) {
                rideFromStart.add(s);
            }
        }
        hoverHeight = Math.max(1, Math.min(8, c.getDouble("riding.hover-height", 3)));
        minRiderScale = Math.max(0.1, Math.min(1, c.getDouble("riding.smallest-rider-scale", 0.3)));
        restSeconds = Math.max(0, c.getInt("fainting.rest-seconds", 60));
        restHealth = Math.max(0.1, Math.min(1, c.getDouble("fainting.comes-back-with-health", 0.5)));
        protectFromPlayers = c.getBoolean("protect-pets-from-other-players", true);
        allayBottlesXp = c.getBoolean("allay-bottles-xp", true);
        healPercent = Math.max(0, c.getDouble("feeding.heal-percent", 25));
        xpFeed = Math.max(0, c.getInt("bond-xp.feeding", 10));
        xpOwnerKill = Math.max(0, c.getInt("bond-xp.you-kill-a-mob", 3));
        xpPetKill = Math.max(0, c.getInt("bond-xp.pet-kills-a-mob", 6));
        rideBlocksPerXp = Math.max(1, c.getInt("bond-xp.blocks-ridden-per-xp", 10));
        walkBlocksPerXp = Math.max(1, c.getInt("bond-xp.blocks-walked-per-xp", 20));
        beeRadius = Math.max(1, Math.min(16, c.getInt("bee.radius", 5)));
        beeCrops = Math.max(1, Math.min(32, c.getInt("bee.crops-per-pulse", 3)));
        beeSeconds = Math.max(0.5, c.getDouble("bee.seconds-between", 3));
        stationLevel = Math.max(1, Math.min(Pet.MAX_LEVEL, c.getInt("stationing.unlock-level", 8)));
        maxStations = Math.max(0, c.getInt("stationing.max-per-player", 3));
        guardRadius = Math.max(4, Math.min(48, c.getDouble("stationing.guard-radius", 16)));
        xpPerMinute = Math.max(0, c.getInt("bond-xp.per-minute-out", 2));
    }

    /** The level this species can be ridden from. */
    public int rideLevel(Species species) {
        return rideFromStart.contains(species) ? 1 : rideUnlockLevel;
    }

    public int feedsToTame(Species species) {
        return feeds.getOrDefault(species, 5);
    }
}
