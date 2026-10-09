package com.additionalbosses.config;

import com.additionalbosses.boss.BossRank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
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

    // ---- Difficulty + Threat Scaling ----
    public final double difficultyHealth;
    public final double difficultyDamage;
    public final boolean threatEnabled;
    public final double threatMaxHealth;
    public final double threatMaxDamage;
    public final double threatRewardBonus;
    public final double threatReferenceScore;
    public final double threatRadius;

    // ---- Reward reliability ----
    public final @Nullable BossRank rewardFloorFrom;
    public final int pityAfter;

    // ---- Soul promotion + Ascendant ----
    public final boolean promotionEnabled;
    public final Map<BossRank, int[]> promotionSteps;
    public final double grayPromotionChance;
    public final boolean ascendantPhases;
    public final List<Double> ascendantPhaseThresholds = new ArrayList<>();
    public final double ascendantShockwaveRadius;
    public final double ascendantShockwaveDamage;
    /** How big trophy figures placed in older versions are, in blocks (their larger side: height or width). */
    public final double trophySize;
    /** How big a placed Nemesis Statue is, in blocks (its larger side); 0 = the Nemesis's own size. */
    public final double statueSize;
    /** Only used when statueSize is 0: a fraction of the Nemesis's own size. */
    public final double statueScale;

    // ---- Waystones ----
    public final boolean waystonesEnabled;
    public final int waystoneAscendantDrops;
    public final Map<BossRank, Double> waystoneDropChance;
    public final double waystoneWarmupSeconds;
    public final double waystoneCooldownSeconds;
    public final boolean waystoneCrossDimension;
    public final boolean waystoneBlockInCombat;

    // ---- Boss armor sets + Nemesis gear ----
    public final boolean armorSets;
    public final Map<BossRank, List<String[]>> armorPalettes;
    public final List<String> trimPatterns;
    public final boolean nemesisGearEvolution;

    // ---- Tools + rune multipliers ----
    public final double toolOffensiveChance;
    public final double maxLootMultiplier;
    public final double maxFortuneMultiplier;

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

    // ---- Boss Souls (trophies in older configs) ----
    public final boolean soulsEnabled;
    public final Map<BossRank, Double> soulChance;

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
    public final double nemesisProwlMinutes;
    public final int nemesisKillLevelsPerOuting;

    public FeatureSettings(FileConfiguration c, Logger log) {
        lastStandRanks = ranks(c.getStringList("last-stand.ranks"), EnumSet.of(BossRank.PURPLE, BossRank.GOLD));
        lastStandRanks.add(BossRank.ASCENDANT);
        lastStandHealthPercent = c.getDouble("last-stand.health-percent", 25);
        lastStandPowerBonus = c.getDouble("last-stand.trait-power-bonus", 50);
        lastStandSpeedBonus = c.getDouble("last-stand.speed-bonus", 15);
        lastStandDormantTrait = c.getBoolean("last-stand.awaken-dormant-trait", true);

        pursuit = c.getBoolean("rank-personality.pursuit", true);

        difficultyHealth = Math.max(0.1, c.getDouble("difficulty.health-multiplier", 1.3));
        difficultyDamage = Math.max(0.1, c.getDouble("difficulty.damage-multiplier", 1.2));
        threatEnabled = c.getBoolean("difficulty.threat-scaling.enabled", true);
        threatMaxHealth = Math.max(0, c.getDouble("difficulty.threat-scaling.max-health-bonus", 60));
        threatMaxDamage = Math.max(0, c.getDouble("difficulty.threat-scaling.max-damage-bonus", 30));
        threatRewardBonus = Math.max(0, c.getDouble("difficulty.threat-scaling.max-reward-bonus", 25));
        threatReferenceScore = Math.max(1, c.getDouble("difficulty.threat-scaling.full-gear-score", 110));
        threatRadius = Math.max(4, c.getDouble("difficulty.threat-scaling.radius", 32));

        String floor = c.getString("reward-floor.guaranteed-from", "RED");
        rewardFloorFrom = floor == null || floor.equalsIgnoreCase("NONE") ? null : BossRank.parse(floor);
        pityAfter = Math.max(0, c.getInt("reward-floor.pity-after-empty-kills", 3));

        promotionEnabled = c.getBoolean("promotion.enabled", true);
        promotionSteps = new EnumMap<>(BossRank.class);
        int[][] steps = {{0, 1}, {1, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 5}};
        for (BossRank rank : BossRank.values()) {
            List<Integer> raw = c.getIntegerList("promotion.ranks-gained." + rank.name());
            int[] def = steps[Math.min(steps.length - 1, rank.ordinal())];
            promotionSteps.put(rank, raw.size() >= 2 ? new int[]{Math.min(raw.get(0), raw.get(1)), Math.max(raw.get(0), raw.get(1))}
                : raw.size() == 1 ? new int[]{raw.get(0), raw.get(0)} : def);
        }
        grayPromotionChance = c.getDouble("promotion.gray-soul-chance", c.getDouble("promotion.gray-trophy-chance", 25));
        ascendantPhases = c.getBoolean("ascendant.phases.enabled", true);
        List<Double> thresholds = c.getDoubleList("ascendant.phases.at-health-percent");
        ascendantPhaseThresholds.addAll(thresholds.isEmpty() ? List.of(66.0, 33.0) : thresholds);
        ascendantPhaseThresholds.sort(Comparator.reverseOrder());
        ascendantShockwaveRadius = c.getDouble("ascendant.phases.shockwave-radius", 7);
        ascendantShockwaveDamage = c.getDouble("ascendant.phases.shockwave-damage", 6);
        trophySize = Math.max(0.1, Math.min(4.0, c.getDouble("trophies.size", 0.5)));
        statueSize = Math.max(0, Math.min(4.0, c.getDouble("nemesis.statue-size", 0.5)));
        statueScale = Math.max(0.05, Math.min(1.0, c.getDouble("nemesis.statue-scale", 1.0)));

        waystonesEnabled = c.getBoolean("waystones.enabled", true);
        waystoneAscendantDrops = Math.max(0, c.getInt("waystones.ascendant-drops", 2));
        waystoneDropChance = rankMap(c.getConfigurationSection("waystones.drop-chance"), new double[]{0, 0, 0, 0, 3, 0});
        waystoneWarmupSeconds = Math.max(0, c.getDouble("waystones.warmup-seconds", 3));
        waystoneCooldownSeconds = Math.max(0, c.getDouble("waystones.cooldown-seconds", 5));
        waystoneCrossDimension = c.getBoolean("waystones.cross-dimension", true);
        waystoneBlockInCombat = c.getBoolean("waystones.blocked-during-boss-fights", true);

        armorSets = c.getBoolean("boss-armor.enabled", true);
        armorPalettes = new EnumMap<>(BossRank.class);
        String[][][] palettes = {
            {{"CHAINMAIL", "iron"}, {"CHAINMAIL", "quartz"}, {"CHAINMAIL", "copper"}},
            {{"COPPER", "emerald"}, {"IRON", "emerald"}, {"IRON", "copper"}},
            {{"IRON", "redstone"}, {"DIAMOND", "redstone"}, {"IRON", "netherite"}},
            {{"DIAMOND", "amethyst"}, {"NETHERITE", "amethyst"}, {"DIAMOND", "lapis"}},
            {{"GOLDEN", "redstone"}, {"NETHERITE", "gold"}, {"DIAMOND", "gold"}},
            {{"NETHERITE", "quartz"}, {"DIAMOND", "quartz"}, {"NETHERITE", "diamond"}}};
        for (BossRank rank : BossRank.values()) {
            List<String[]> list = new ArrayList<>();
            for (String entry : c.getStringList("boss-armor.palettes." + rank.name())) {
                String[] parts = entry.trim().split("\\s+");
                if (parts.length >= 2) {
                    list.add(new String[]{parts[0], parts[1]});
                }
            }
            if (list.isEmpty()) {
                list.addAll(List.of(palettes[Math.min(palettes.length - 1, rank.ordinal())]));
            }
            armorPalettes.put(rank, list);
        }
        List<String> patterns = c.getStringList("boss-armor.trim-patterns");
        trimPatterns = patterns.isEmpty() ? List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide",
            "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt") : patterns;
        nemesisGearEvolution = c.getBoolean("nemesis.gear-evolution", true);

        toolOffensiveChance = c.getDouble("boss-gear.tool-offensive-enchant-chance", 35);
        maxLootMultiplier = Math.max(1, c.getDouble("empowerment.max-loot-multiplier", 3));
        maxFortuneMultiplier = Math.max(1, c.getDouble("empowerment.max-fortune-multiplier", 3));

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
        totemDropChance = rankMap(c.getConfigurationSection("boss-totem.drop-chance"), new double[]{1, 1.5, 2.5, 4, 8, 15});
        totemRankWeights = rankMap(c.getConfigurationSection("boss-totem.rank-weights"), new double[]{0, 30, 35, 25, 10});

        escalationEnabled = c.getBoolean("escalation.enabled", true);
        escalationKills = Math.max(2, c.getInt("escalation.kills", 5));
        escalationWindowDays = Math.max(0.1, c.getDouble("escalation.within-days", 1));
        escalationBosses = Math.max(1, c.getInt("escalation.bosses", 2));
        escalationDelaySeconds = c.getDouble("escalation.delay-seconds", 10);
        escalationRanks = rankMap(c.getConfigurationSection("escalation.rank-weights"), new double[]{0, 0, 0, 70, 30});

        String souls = c.isConfigurationSection("souls") ? "souls" : "trophies"; // older configs call them trophies
        soulsEnabled = c.getBoolean(souls + ".enabled", true);
        soulChance = rankMap(c.getConfigurationSection(souls + ".chance"), new double[]{4, 7, 12, 25, 100, 100});

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
        nemesisProwlMinutes = Math.max(0, c.getDouble("nemesis.prowl-minutes", 1));
        nemesisKillLevelsPerOuting = Math.max(0, c.getInt("nemesis.levels-from-kills-per-outing", 5));
        if (log != null && compassTiers.size() > 5) {
            log.info("Hunter's Compass has " + compassTiers.size() + " tiers configured.");
        }
    }

    private static double num(@Nullable Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static Set<BossRank> ranks(List<String> raw, Set<BossRank> def) {
        Set<BossRank> out = EnumSet.noneOf(BossRank.class);
        if (raw.isEmpty()) {
            return EnumSet.copyOf(def);
        }
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
            double d = rank.ordinal() < def.length ? def[rank.ordinal()] : 0;
            out.put(rank, s == null ? d : s.getDouble(rank.name(), d));
        }
        return out;
    }

    public CompassTier compassTier(int tier) {
        return compassTiers.get(Math.max(0, Math.min(compassTiers.size() - 1, tier - 1)));
    }
}
