package com.additionalbosses.config;

import com.additionalbosses.boss.BossRank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Settings for the encounter features: Last Stand, anti-trap, Hunter's Compass, Boss Totem, Escalation,
 * trophies and the Nemesis system.
 */
public final class FeatureSettings {

    // ---- Last Stand ----
    public final Set<BossRank> lastStandRanks;
    public final double lastStandHealthPercent;
    public final double lastStandPowerBonus;
    public final double lastStandSpeedBonus;
    public final boolean lastStandDormantTrait;

    // ---- Rank personality ----
    public final boolean pursuit;

    // ---- Anti-trap ----
    public final boolean blockVehicles;
    public final boolean unstuckEnabled;
    public final int unstuckTicks;

    // ---- Hunter's Compass ----
    public final boolean compassEnabled;
    public final boolean compassRecipe;
    public final List<CompassTier> compassTiers = new ArrayList<>();

    public record CompassTier(double range, double revealDistance) {
    }

    // ---- Boss Totem ----
    public final boolean totemEnabled;
    public final Map<BossRank, Double> totemDropChance;
    public final Map<BossRank, Double> totemRankWeights;

    // ---- Escalation ----
    public final boolean escalationEnabled;
    public final int escalationKills;
    public final double escalationWindowDays;
    public final int escalationBosses;
    public final double escalationDelaySeconds;
    public final Map<BossRank, Double> escalationRanks;

    // ---- Trophies ----
    public final boolean trophiesEnabled;
    public final Map<BossRank, Double> trophyChance;

    // ---- Nemesis ----
    public final boolean nemesisEnabled;
    public final double nemesisKillChance;
    public final double nemesisEscapeChance;
    public final BossRank nemesisEscapeMinRank;
    public final double nemesisEscapeDamagePercent;
    public final double nemesisEscapeDistance;
    public final int nemesisMaxPerPlayer;
    public final int nemesisMaxLevel;
    public final int nemesisLevelsOnKill;
    public final int nemesisLevelsOnEscape;
    public final int nemesisLevelsPerRank;
    public final int nemesisLevelsPerTrait;
    public final int nemesisMaxTraits;
    public final double nemesisReturnDays;
    public final double nemesisHealthPerLevel;
    public final double nemesisDamagePerLevel;
    public final double nemesisMaxDamageMultiplier;
    public final double nemesisPowerPerLevel;
    public final double nemesisSizePerLevel;
    public final double nemesisMaxExtraSize;
    public final double nemesisRevengeXpMultiplier;

    public FeatureSettings(FileConfiguration c, Logger log) {
        lastStandRanks = ranks(c.getStringList("last-stand.ranks"), EnumSet.of(BossRank.PURPLE, BossRank.GOLD));
        lastStandHealthPercent = c.getDouble("last-stand.health-percent", 25);
        lastStandPowerBonus = c.getDouble("last-stand.trait-power-bonus", 50);
        lastStandSpeedBonus = c.getDouble("last-stand.speed-bonus", 15);
        lastStandDormantTrait = c.getBoolean("last-stand.awaken-dormant-trait", true);

        pursuit = c.getBoolean("rank-personality.pursuit", true);

        blockVehicles = c.getBoolean("anti-trap.block-vehicles-and-leads", true);
        unstuckEnabled = c.getBoolean("anti-trap.unstuck.enabled", true);
        unstuckTicks = (int) Math.round(Math.max(1, c.getDouble("anti-trap.unstuck.seconds", 6)) * 20);

        compassEnabled = c.getBoolean("hunters-compass.enabled", true);
        compassRecipe = c.getBoolean("hunters-compass.recipe", true);
        for (Map<?, ?> map : c.getMapList("hunters-compass.tiers")) {
            compassTiers.add(new CompassTier(num(map.get("range"), 150), num(map.get("reveal-rank-within"), 40)));
        }
        if (compassTiers.isEmpty()) {
            compassTiers.add(new CompassTier(150, 40));
            compassTiers.add(new CompassTier(300, 100));
            compassTiers.add(new CompassTier(600, 600));
        }

        totemEnabled = c.getBoolean("boss-totem.enabled", true);
        totemDropChance = rankMap(c.getConfigurationSection("boss-totem.drop-chance"), new double[]{1, 1.5, 2.5, 4, 8});
        totemRankWeights = rankMap(c.getConfigurationSection("boss-totem.rank-weights"), new double[]{0, 30, 35, 25, 10});

        escalationEnabled = c.getBoolean("escalation.enabled", true);
        escalationKills = Math.max(2, c.getInt("escalation.kills", 5));
        escalationWindowDays = Math.max(0.1, c.getDouble("escalation.within-days", 1));
        escalationBosses = Math.max(1, c.getInt("escalation.bosses", 2));
        escalationDelaySeconds = c.getDouble("escalation.delay-seconds", 10);
        escalationRanks = rankMap(c.getConfigurationSection("escalation.rank-weights"), new double[]{0, 0, 0, 70, 30});

        trophiesEnabled = c.getBoolean("trophies.enabled", true);
        trophyChance = rankMap(c.getConfigurationSection("trophies.chance"), new double[]{4, 7, 12, 25, 100});

        nemesisEnabled = c.getBoolean("nemesis.enabled", true);
        nemesisKillChance = c.getDouble("nemesis.become-on-kill-chance", 100);
        nemesisEscapeChance = c.getDouble("nemesis.become-on-escape-chance", 50);
        BossRank minRank = BossRank.parse(c.getString("nemesis.escape-min-rank", "RED"));
        nemesisEscapeMinRank = minRank == null ? BossRank.RED : minRank;
        nemesisEscapeDamagePercent = c.getDouble("nemesis.escape-min-damage-percent", 25);
        nemesisEscapeDistance = c.getDouble("nemesis.escape-distance", 80);
        nemesisMaxPerPlayer = Math.max(1, c.getInt("nemesis.max-per-player", 5));
        nemesisMaxLevel = Math.max(1, c.getInt("nemesis.max-level", 50));
        nemesisLevelsOnKill = Math.max(0, c.getInt("nemesis.levels-when-it-kills-you", 3));
        nemesisLevelsOnEscape = Math.max(0, c.getInt("nemesis.levels-when-you-flee", 1));
        nemesisLevelsPerRank = Math.max(1, c.getInt("nemesis.levels-per-rank-up", 4));
        nemesisLevelsPerTrait = Math.max(1, c.getInt("nemesis.levels-per-new-trait", 3));
        nemesisMaxTraits = Math.max(1, c.getInt("nemesis.max-traits", 7));
        nemesisReturnDays = Math.max(0.05, c.getDouble("nemesis.returns-after-days", 3));
        nemesisHealthPerLevel = c.getDouble("nemesis.health-per-level", 8);
        nemesisDamagePerLevel = c.getDouble("nemesis.damage-per-level", 4);
        nemesisMaxDamageMultiplier = Math.max(1, c.getDouble("nemesis.max-damage-multiplier", 5));
        nemesisPowerPerLevel = c.getDouble("nemesis.trait-power-per-level", 3);
        nemesisSizePerLevel = c.getDouble("nemesis.size-per-level", 0.02);
        nemesisMaxExtraSize = c.getDouble("nemesis.max-extra-size", 0.5);
        nemesisRevengeXpMultiplier = Math.max(1, c.getDouble("nemesis.revenge-xp-multiplier", 2));
        if (log != null && compassTiers.size() > 5) {
            log.info("Hunter's Compass has " + compassTiers.size() + " tiers configured.");
        }
    }

    private static double num(@Nullable Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static Set<BossRank> ranks(List<String> raw, Set<BossRank> def) {
        if (raw.isEmpty()) {
            return def;
        }
        Set<BossRank> out = EnumSet.noneOf(BossRank.class);
        for (String r : raw) {
            BossRank rank = BossRank.parse(r);
            if (rank != null) {
                out.add(rank);
            }
        }
        return out;
    }

    private static Map<BossRank, Double> rankMap(@Nullable ConfigurationSection s, double[] def) {
        Map<BossRank, Double> out = new EnumMap<>(BossRank.class);
        for (BossRank rank : BossRank.values()) {
            out.put(rank, s == null ? def[rank.ordinal()] : s.getDouble(rank.name(), def[rank.ordinal()]));
        }
        return out;
    }

    public CompassTier compassTier(int tier) {
        return compassTiers.get(Math.max(0, Math.min(compassTiers.size() - 1, tier - 1)));
    }
}
