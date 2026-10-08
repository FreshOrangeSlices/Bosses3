package com.additionalbosses.config;

import com.additionalbosses.boss.BossRank;
import com.additionalbosses.reward.GearKind;
import com.additionalbosses.reward.GearTier;
import com.additionalbosses.util.Fx;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A parsed, read-only snapshot of config.yml. A new snapshot is created on /bosses reload.
 * Every value here is used by the plugin; nothing is parsed "for later".
 */
public final class PluginSettings {

    // ---- bosses ----
    public final boolean enabled;
    public final double spawnChance;
    public final Set<SpawnReason> spawnReasons;
    public final boolean worldWhitelist;
    public final Set<String> worlds;
    public final Set<EntityType> excludedMobs;
    public final int maxActive;
    public final double minDistanceBetween;
    public final boolean alwaysShowName;
    public final boolean displayGear;
    public final int combatTimeoutTicks;
    public final boolean requirePlayerForRewards;

    // ---- bossbar / presentation ----
    public final double bossbarViewDistance;
    public final boolean showHealthNumbers;
    public final double announceRadius;

    // ---- ranks, categories, profiles ----
    private final Map<BossRank, RankSettings> ranks = new EnumMap<>(BossRank.class);
    public final List<MobCategory> categories = new ArrayList<>();
    private final Map<EntityType, MobCategory> categoryByType = new HashMap<>();
    public final List<MobProfile> profiles = new ArrayList<>();
    private final Map<EntityType, MobProfile> profileByType = new HashMap<>();

    // ---- boss gear ----
    public final Map<GearKind, Integer> gearWeights = new EnumMap<>(GearKind.class);
    public final Map<EntityType, Map<GearKind, Integer>> gearPreferences = new HashMap<>();
    public final boolean allowCurseEnchantments;
    public final Set<String> excludedEnchantments = new HashSet<>();

    // ---- empowerment ----
    public final int empowermentMaxPerItem;
    public final List<EmpowermentStat> empowermentStats = new ArrayList<>();

    // ---- relics ----
    public final int relicBaseSlots;
    public final double corruptionChance;
    public final boolean revealCorruption;
    public final boolean confirmBinding;
    public final boolean allowDuplicateRelics;
    public final boolean catalystEnabled;
    public final int catalystMaxSlots;

    // ---- items ----
    public final Material runeMaterial;
    public final Material relicMaterial;
    public final Material catalystMaterial;

    // ---- guide ----
    public final boolean guideOnFirstJoin;

    public final Messages messages;

    public PluginSettings(FileConfiguration c, Logger log) {
        // ---------------- bosses ----------------
        enabled = c.getBoolean("bosses.enabled", true);
        spawnChance = c.getDouble("bosses.spawn-chance", 0.5);
        spawnReasons = parseReasons(c.getStringList("bosses.spawn-reasons"), log);
        if (spawnReasons.isEmpty()) {
            spawnReasons.add(SpawnReason.NATURAL);
        }
        worldWhitelist = "whitelist".equalsIgnoreCase(c.getString("bosses.worlds.mode", "blacklist"));
        worlds = new HashSet<>();
        for (String w : c.getStringList("bosses.worlds.list")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        excludedMobs = parseMobs(c.getStringList("bosses.excluded-mobs"), log);
        maxActive = Math.max(0, c.getInt("bosses.max-active", 15));
        minDistanceBetween = Math.max(0, c.getDouble("bosses.min-distance-between", 32));
        alwaysShowName = c.getBoolean("bosses.always-show-name", true);
        displayGear = c.getBoolean("bosses.display-gear", true);
        combatTimeoutTicks = Math.max(20, (int) Math.round(c.getDouble("bosses.combat-timeout", 15) * 20));
        requirePlayerForRewards = c.getBoolean("bosses.require-player-for-rewards", true);

        bossbarViewDistance = c.getDouble("bossbar.view-distance", 48);
        showHealthNumbers = c.getBoolean("bossbar.show-health-numbers", true);
        announceRadius = c.getDouble("presentation.announce-radius", 48);

        // ---------------- ranks ----------------
        for (BossRank rank : BossRank.values()) {
            ranks.put(rank, parseRank(rank, c.getConfigurationSection("ranks." + rank.name()), log));
        }

        // ---------------- mob categories ----------------
        ConfigurationSection cats = c.getConfigurationSection("mob-categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection s = cats.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Map<BossRank, Double> weights = new EnumMap<>(BossRank.class);
                ConfigurationSection ws = s.getConfigurationSection("weights");
                if (ws != null) {
                    for (String key : ws.getKeys(false)) {
                        BossRank rank = BossRank.parse(key);
                        if (rank == null) {
                            log.warning("mob-categories." + id + ".weights: unknown rank '" + key + "'");
                            continue;
                        }
                        weights.put(rank, ws.getDouble(key));
                    }
                }
                if (weights.isEmpty()) {
                    weights.put(BossRank.GRAY, 1.0);
                }
                Set<SpawnReason> reasons = s.isList("spawn-reasons") ? parseReasons(s.getStringList("spawn-reasons"), log) : null;
                MobCategory category = new MobCategory(id,
                    s.getString("display-name", com.additionalbosses.util.Text.pretty(id)),
                    s.getString("description", ""),
                    s.getBoolean("enabled", true),
                    s.getDouble("spawn-chance-multiplier", 1.0), weights,
                    parseMobs(s.getStringList("mobs"), log), reasons);
                categories.add(category);
                for (EntityType type : category.mobs()) {
                    MobCategory previous = categoryByType.put(type, category);
                    if (previous != null) {
                        log.warning(type + " is listed in both mob-categories." + previous.id() + " and " + id + "; using " + id);
                    }
                }
            }
        }

        // ---------------- mob profiles ----------------
        ConfigurationSection profs = c.getConfigurationSection("mob-profiles");
        if (profs != null) {
            for (String id : profs.getKeys(false)) {
                ConfigurationSection s = profs.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                MobProfile profile = new MobProfile(id, parseMobs(s.getStringList("mobs"), log),
                    s.getDouble("health", 1), s.getDouble("damage", 1), s.getDouble("defense", 1),
                    s.getDouble("speed", 1), s.getDouble("size", 1));
                profiles.add(profile);
                for (EntityType type : profile.mobs()) {
                    profileByType.put(type, profile);
                }
            }
        }

        // ---------------- boss gear ----------------
        ConfigurationSection gw = c.getConfigurationSection("boss-gear.item-weights");
        if (gw != null) {
            for (String key : gw.getKeys(false)) {
                GearKind kind = GearKind.parse(key);
                if (kind == null) {
                    log.warning("boss-gear.item-weights: unknown item kind '" + key + "'");
                    continue;
                }
                gearWeights.put(kind, Math.max(0, gw.getInt(key)));
            }
        }
        if (gearWeights.isEmpty()) {
            for (GearKind kind : GearKind.values()) {
                gearWeights.put(kind, 10);
            }
        }
        ConfigurationSection prefs = c.getConfigurationSection("boss-gear.mob-preferences");
        if (prefs != null) {
            for (String mob : prefs.getKeys(false)) {
                EntityType type = parseMob(mob);
                ConfigurationSection ps = prefs.getConfigurationSection(mob);
                if (type == null || ps == null) {
                    log.warning("boss-gear.mob-preferences: unknown mob '" + mob + "'");
                    continue;
                }
                Map<GearKind, Integer> extra = new EnumMap<>(GearKind.class);
                for (String key : ps.getKeys(false)) {
                    GearKind kind = GearKind.parse(key);
                    if (kind != null) {
                        extra.put(kind, Math.max(0, ps.getInt(key)));
                    }
                }
                gearPreferences.put(type, extra);
            }
        }
        allowCurseEnchantments = c.getBoolean("boss-gear.allow-curse-enchantments", false);
        for (String e : c.getStringList("boss-gear.excluded-enchantments")) {
            excludedEnchantments.add(stripNamespace(e));
        }

        // ---------------- empowerment ----------------
        empowermentMaxPerItem = Math.max(1, c.getInt("empowerment.max-per-item", 2));
        ConfigurationSection stats = c.getConfigurationSection("empowerment.stats");
        if (stats != null) {
            for (String id : stats.getKeys(false)) {
                ConfigurationSection s = stats.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                Attribute attribute = parseAttribute(s.getString("attribute", ""));
                if (attribute == null) {
                    log.warning("empowerment.stats." + id + ": unknown attribute '" + s.getString("attribute") + "'");
                    continue;
                }
                AttributeModifier.Operation op;
                try {
                    op = AttributeModifier.Operation.valueOf(s.getString("operation", "ADD_NUMBER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    log.warning("empowerment.stats." + id + ": unknown operation, using ADD_NUMBER");
                    op = AttributeModifier.Operation.ADD_NUMBER;
                }
                Map<BossRank, double[]> ranges = new EnumMap<>(BossRank.class);
                ConfigurationSection rs = s.getConfigurationSection("ranges");
                for (BossRank rank : BossRank.values()) {
                    List<Double> list = rs == null ? List.of() : rs.getDoubleList(rank.name());
                    if (list.size() >= 2) {
                        ranges.put(rank, new double[]{Math.min(list.get(0), list.get(1)), Math.max(list.get(0), list.get(1))});
                    } else if (list.size() == 1) {
                        ranges.put(rank, new double[]{list.get(0), list.get(0)});
                    }
                }
                if (ranges.isEmpty()) {
                    log.warning("empowerment.stats." + id + ": no ranges set, skipping");
                    continue;
                }
                List<String> applies = s.getStringList("applies-to");
                if (applies.isEmpty()) {
                    applies = List.of("ANY");
                }
                empowermentStats.add(new EmpowermentStat(id, attribute, op, s.getString("display", id),
                    s.getBoolean("percent", false), s.getDouble("weight", 10), List.copyOf(applies), ranges));
            }
        }

        // ---------------- relics ----------------
        relicBaseSlots = Math.max(1, c.getInt("relics.base-slots", 1));
        corruptionChance = c.getDouble("relics.corruption-chance", 10);
        revealCorruption = c.getBoolean("relics.reveal-corruption", false);
        confirmBinding = c.getBoolean("relics.confirm-binding", true);
        allowDuplicateRelics = c.getBoolean("relics.allow-duplicates-on-item", false);
        catalystEnabled = c.getBoolean("relics.catalyst.enabled", true);
        catalystMaxSlots = Math.max(relicBaseSlots, c.getInt("relics.catalyst.max-slots", 2));

        // ---------------- items ----------------
        runeMaterial = parseMaterial(c.getString("items.rune-material"), Material.AMETHYST_SHARD, log);
        relicMaterial = parseMaterial(c.getString("items.relic-material"), Material.HEART_OF_THE_SEA, log);
        catalystMaterial = parseMaterial(c.getString("items.catalyst-material"), Material.NETHER_STAR, log);

        guideOnFirstJoin = c.getBoolean("guide.give-on-first-join", true);
        messages = new Messages(c.getConfigurationSection("messages"));
    }

    // =====================================================================

    public RankSettings rank(BossRank rank) {
        return ranks.get(rank);
    }

    public @Nullable MobCategory categoryFor(EntityType type) {
        return categoryByType.get(type);
    }

    public @Nullable MobCategory category(String id) {
        for (MobCategory category : categories) {
            if (category.id().equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }

    public MobProfile profileFor(EntityType type) {
        return profileByType.getOrDefault(type, MobProfile.DEFAULT);
    }

    public boolean worldAllowed(World world) {
        boolean listed = worlds.contains(world.getName().toLowerCase(Locale.ROOT));
        return worldWhitelist == listed;
    }

    public @Nullable EmpowermentStat stat(String id) {
        for (EmpowermentStat stat : empowermentStats) {
            if (stat.id().equalsIgnoreCase(id)) {
                return stat;
            }
        }
        return null;
    }

    public Set<EntityType> eligibleMobs() {
        Set<EntityType> out = new HashSet<>(categoryByType.keySet());
        out.removeAll(excludedMobs);
        return Collections.unmodifiableSet(out);
    }

    // =====================================================================

    private static RankSettings parseRank(BossRank rank, @Nullable ConfigurationSection s, Logger log) {
        int i = rank.ordinal();
        // Fallback values (used only if a line is missing from config.yml).
        double[] health = {1.6, 2.0, 2.75, 3.5, 5.0};
        double[] damage = {1.2, 1.35, 1.55, 1.8, 2.1};
        double[] armor = {2, 4, 6, 8, 10};
        double[] tough = {0, 1, 2, 4, 6};
        double[] kb = {0.1, 0.2, 0.3, 0.45, 0.6};
        double[] speed = {1.0, 1.05, 1.08, 1.1, 1.12};
        double[] size = {0.05, 0.1, 0.15, 0.2, 0.3};
        int[] tMin = {1, 1, 2, 2, 3};
        int[] tMax = {1, 2, 2, 3, 4};
        double[] power = {1.0, 1.15, 1.3, 1.5, 1.75};
        double[] xpMul = {3, 4, 6, 8, 12};
        int[] xpBonus = {10, 25, 50, 100, 300};
        double[] gear = {20, 30, 45, 65, 100};
        double[] emp = {10, 18, 28, 40, 60};
        double[] relic = {2, 4, 8, 15, 30};
        double[] cat = {0, 0, 0, 1.5, 5};

        if (s == null) {
            log.warning("ranks." + rank.name() + " is missing from config.yml, using built-in defaults");
        }
        ConfigurationSection st = s == null ? null : s.getConfigurationSection("stats");
        RankSettings.Stats stats = new RankSettings.Stats(
            d(st, "health", health[i]), d(st, "damage", damage[i]), d(st, "armor", armor[i]),
            d(st, "armor-toughness", tough[i]), d(st, "knockback-resistance", kb[i]),
            d(st, "speed", speed[i]), d(st, "size", size[i]));

        ConfigurationSection tr = s == null ? null : s.getConfigurationSection("traits");
        int min = (int) d(tr, "min", tMin[i]);
        int max = Math.max(min, (int) d(tr, "max", tMax[i]));
        RankSettings.TraitRoll traits = new RankSettings.TraitRoll(min, max, d(tr, "power", power[i]));

        ConfigurationSection xp = s == null ? null : s.getConfigurationSection("xp");
        ConfigurationSection rw = s == null ? null : s.getConfigurationSection("rewards");
        RankSettings.Rewards rewards = new RankSettings.Rewards(d(rw, "boss-gear", gear[i]),
            d(rw, "empowerment", emp[i]), d(rw, "relic", relic[i]), d(rw, "relic-catalyst", cat[i]));

        ConfigurationSection gs = s == null ? null : s.getConfigurationSection("gear");
        Map<GearTier, Integer> materials = new LinkedHashMap<>();
        ConfigurationSection ms = gs == null ? null : gs.getConfigurationSection("materials");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                GearTier tier = GearTier.parse(key);
                if (tier == null) {
                    log.warning("ranks." + rank.name() + ".gear.materials: unknown tier '" + key + "'");
                    continue;
                }
                materials.put(tier, Math.max(0, ms.getInt(key)));
            }
        }
        if (materials.isEmpty()) {
            materials.put(rank.atLeast(BossRank.PURPLE) ? GearTier.NETHERITE : GearTier.IRON, 1);
        }
        ConfigurationSection es = gs == null ? null : gs.getConfigurationSection("enchantments");
        int eMin = (int) d(es, "min", 1 + i / 2);
        int eMax = Math.max(eMin, (int) d(es, "max", 2 + i));
        RankSettings.Gear gearSettings = new RankSettings.Gear(materials, eMin, eMax,
            d(gs, "min-level-percent", 30 + i * 17), d(gs, "over-max-chance", i >= 3 ? 25 : 0),
            (int) d(gs, "over-max-levels", Math.max(0, i - 2)));

        ConfigurationSection ps = s == null ? null : s.getConfigurationSection("presentation");
        RankSettings.Presentation presentation = new RankSettings.Presentation(
            parseEnum(RankSettings.Announce.class, str(ps, "announce", "NONE"), RankSettings.Announce.NONE),
            Fx.parseSound(str(ps, "sound", "")),
            Fx.parseSound(str(ps, "death-sound", "")),
            (int) d(ps, "particles", i),
            parseEnum(RankSettings.Broadcast.class, str(ps, "death-broadcast", "NONE"), RankSettings.Broadcast.NONE));

        return new RankSettings(rank, str(s, "name", rank.defaultName()), stats, traits,
            d(xp, "multiplier", xpMul[i]), (int) d(xp, "bonus", xpBonus[i]), rewards, gearSettings,
            s != null ? s.getBoolean("persistent", i >= 1) : i >= 1, presentation);
    }

    private static double d(@Nullable ConfigurationSection s, String path, double def) {
        return s == null ? def : s.getDouble(path, def);
    }

    private static String str(@Nullable ConfigurationSection s, String path, String def) {
        return s == null ? def : s.getString(path, def);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E def) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static Set<SpawnReason> parseReasons(List<String> raw, Logger log) {
        Set<SpawnReason> out = new HashSet<>();
        for (String r : raw) {
            try {
                out.add(SpawnReason.valueOf(r.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("Unknown spawn reason '" + r + "'");
            }
        }
        return out;
    }

    public static @Nullable EntityType parseMob(String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENTITY_TYPE.get(key);
    }

    private static Set<EntityType> parseMobs(List<String> raw, Logger log) {
        Set<EntityType> out = new HashSet<>();
        for (String r : raw) {
            EntityType type = parseMob(r);
            if (type == null) {
                log.warning("Unknown mob '" + r + "' in config.yml (ignored)");
                continue;
            }
            out.add(type);
        }
        return out;
    }

    private static @Nullable Attribute parseAttribute(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("generic.") || s.startsWith("player.")) {
            s = s.substring(s.indexOf('.') + 1);
        }
        NamespacedKey key = NamespacedKey.fromString(s);
        return key == null ? null : Registry.ATTRIBUTE.get(key);
    }

    private static Material parseMaterial(@Nullable String raw, Material def, Logger log) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material m = Material.matchMaterial(raw.trim());
        if (m == null || !m.isItem() || m.isAir()) {
            log.warning("Unknown item material '" + raw + "', using " + def);
            return def;
        }
        return m;
    }

    public static String stripNamespace(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }
}
