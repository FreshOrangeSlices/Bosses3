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
    public final int roamRadius;
    // bond XP from fighting
    public final int xpCombatHit;
    public final int xpCombatCrit;
    public final long combatXpCooldown;
    public final double guardOwnerRange;
    // pets in PvP
    public final boolean petPvp;
    // mcMMO Taming skills
    public final boolean skills;
    public final boolean tamingXp;
    public final double tamingXpMultiplier;
    private final Map<String, Integer> unlocks = new java.util.HashMap<>();
    public final double goreMaxChance;
    public final double goreMaxLevel;
    public final double goreMultiplier;
    public final double fastFoodChance;
    public final double thickFurDivisor;
    public final double shockProofDivisor;
    public final double clawsBonus;
    public final int guardianSeconds;
    public final int guardianLevel;
    public final double doubleStrikePer50;
    public final double doubleStrikeMax;
    public final double phaseArmor;
    public final double phaseArmorHigh;
    public final int phaseHighLevel;
    public final int crushSeconds;
    public final int crushHighLevel;
    public final int momentumEvery;
    public final double momentumBonus;
    public final double ramLaunch;
    public final int blessingSeconds;
    public final double soothChanceLow;
    public final double soothChanceHigh;
    public final double soothHealBase;
    public final double soothHealPer100;
    public final double soothHealMax;
    public final double soothRadius;
    // happiness
    public final boolean happiness;
    public final double happyStart;
    public final double sadBelow;
    public final double happyFeed;
    public final long happyFeedCooldown;
    public final double happyFetch;
    public final long happyFetchCooldown;
    public final double happyPetting;
    public final long happyPettingCooldown;
    public final double minutesPerPointOut;
    public final double minutesPerPointPost;
    public final boolean postDropsOffline;
    public final long tiredAfter;
    public final double tiredMultiplier;
    public final double restPerMinute;
    public final double minutesToRest;
    public final long lovedMillis;
    public final double lovedMultiplier;

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
        roamRadius = Math.max(0, Math.min(16, c.getInt("stationing.roam-radius", 6)));
        xpCombatHit = Math.max(0, c.getInt("bond-xp.combat-hit", 1));
        xpCombatCrit = Math.max(0, c.getInt("bond-xp.combat-critical-hit", 2));
        combatXpCooldown = Math.max(0, (long) (c.getDouble("bond-xp.combat-hit-cooldown-seconds", 1.5) * 1000));
        guardOwnerRange = Math.max(0, c.getDouble("bond-xp.guard-owner-range", 30));
        petPvp = c.getBoolean("pet-pvp.allow-toggle", true);

        skills = c.getBoolean("mcmmo.enabled", true);
        tamingXp = c.getBoolean("mcmmo.taming-xp", true);
        tamingXpMultiplier = Math.max(0, c.getDouble("mcmmo.taming-xp-multiplier", 3));
        for (Skills.Slot slot : Skills.Slot.values()) {
            unlocks.put(slot.key, Math.max(0, c.getInt("mcmmo.unlock." + slot.key, slot.defaultLevel)));
        }
        goreMaxChance = clamp(c.getDouble("mcmmo.gore.max-chance", 100), 0, 100);
        goreMaxLevel = Math.max(1, c.getDouble("mcmmo.gore.max-chance-level", 1000));
        goreMultiplier = Math.max(1, c.getDouble("mcmmo.gore.damage-multiplier", 2.0));
        fastFoodChance = clamp(c.getDouble("mcmmo.fast-food-chance", 50), 0, 100);
        thickFurDivisor = Math.max(1, c.getDouble("mcmmo.thick-fur-divisor", 2.0));
        shockProofDivisor = Math.max(1, c.getDouble("mcmmo.shock-proof-divisor", 6.0));
        clawsBonus = Math.max(0, c.getDouble("mcmmo.sharpened-claws-bonus", 2.0));
        guardianSeconds = Math.max(1, c.getInt("mcmmo.iron-golem.guardian-resistance-seconds", 20));
        guardianLevel = Math.max(1, Math.min(5, c.getInt("mcmmo.iron-golem.guardian-resistance-level", 2)));
        doubleStrikePer50 = clamp(c.getDouble("mcmmo.vex.double-strike-chance-per-50-levels", 2), 0, 100);
        doubleStrikeMax = clamp(c.getDouble("mcmmo.vex.double-strike-max-chance", 10), 0, 100);
        phaseArmor = clamp(c.getDouble("mcmmo.vex.phase-strike-armor-ignored", 25), 0, 100);
        phaseArmorHigh = clamp(c.getDouble("mcmmo.vex.phase-strike-armor-ignored-high", 50), 0, 100);
        phaseHighLevel = Math.max(0, c.getInt("mcmmo.vex.phase-strike-high-level", 500));
        crushSeconds = Math.max(1, c.getInt("mcmmo.polar-bear.crushing-presence-seconds", 5));
        crushHighLevel = Math.max(0, c.getInt("mcmmo.polar-bear.crushing-presence-high-level", 500));
        momentumEvery = Math.max(1, c.getInt("mcmmo.goat.momentum-every-hits", 3));
        momentumBonus = Math.max(0, c.getDouble("mcmmo.goat.momentum-bonus", 30));
        ramLaunch = clamp(c.getDouble("mcmmo.goat.ram-launch", 1.1), 0, 3);
        blessingSeconds = Math.max(1, c.getInt("mcmmo.panda.bamboo-blessing-seconds", 8));
        soothChanceLow = clamp(c.getDouble("mcmmo.panda.soothing-chance-at-unlock", 15), 0, 100);
        soothChanceHigh = clamp(c.getDouble("mcmmo.panda.soothing-chance-at-1000", 60), 0, 100);
        soothHealBase = Math.max(0, c.getDouble("mcmmo.panda.soothing-heal-at-unlock", 3));
        soothHealPer100 = Math.max(0, c.getDouble("mcmmo.panda.soothing-heal-per-100-levels", 1));
        soothHealMax = Math.max(0, c.getDouble("mcmmo.panda.soothing-heal-max", 9));
        soothRadius = clamp(c.getDouble("mcmmo.panda.soothing-radius", 6), 1, 16);

        happiness = c.getBoolean("happiness.enabled", true);
        happyStart = clamp(c.getDouble("happiness.start", 80), 0, 100);
        sadBelow = clamp(c.getDouble("happiness.sad-below", 30), 0, 100);
        happyFeed = Math.max(0, c.getDouble("happiness.feeding", 15));
        happyFeedCooldown = seconds(c, "happiness.feeding-cooldown-seconds", 60);
        happyFetch = Math.max(0, c.getDouble("happiness.fetch", 8));
        happyFetchCooldown = seconds(c, "happiness.fetch-cooldown-seconds", 15);
        happyPetting = Math.max(0, c.getDouble("happiness.petting", 5));
        happyPettingCooldown = seconds(c, "happiness.petting-cooldown-seconds", 30);
        minutesPerPointOut = Math.max(0.1, c.getDouble("happiness.minutes-per-point-out", 3));
        minutesPerPointPost = Math.max(0.1, c.getDouble("happiness.minutes-per-point-at-post", 2));
        postDropsOffline = c.getBoolean("happiness.post-drops-while-owner-offline", false);
        tiredAfter = Math.max(1, (long) (c.getDouble("happiness.tired-after-minutes", 90) * 60_000));
        tiredMultiplier = Math.max(1, c.getDouble("happiness.tired-drop-multiplier", 3));
        restPerMinute = Math.max(0, c.getDouble("happiness.rest-per-minute", 10));
        minutesToRest = Math.max(0.1, c.getDouble("happiness.minutes-to-fully-rest", 5));
        lovedMillis = Math.max(0, (long) (c.getDouble("happiness.loved-minutes", 30) * 60_000));
        lovedMultiplier = clamp(c.getDouble("happiness.loved-drop-multiplier", 0.25), 0, 1);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static long seconds(FileConfiguration c, String path, double def) {
        return Math.max(0, (long) (c.getDouble(path, def) * 1000));
    }

    /** The mcMMO Taming level this skill slot unlocks at (on RetroMode's 0-1000 scale; Standard is a tenth). */
    public int unlock(Skills.Slot slot, double scale) {
        return (int) Math.round(unlocks.getOrDefault(slot.key, slot.defaultLevel) * scale);
    }

    /** The level this species can be ridden from. */
    public int rideLevel(Species species) {
        return rideFromStart.contains(species) ? 1 : rideUnlockLevel;
    }

    public int feedsToTame(Species species) {
        return feeds.getOrDefault(species, 5);
    }
}
