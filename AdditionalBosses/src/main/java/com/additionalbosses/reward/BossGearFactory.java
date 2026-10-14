package com.additionalbosses.reward;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.util.Rng;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import com.additionalbosses.item.EquipmentType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Boss Gear: exactly one rank-branded equipment item, rolled independently of whatever the boss
 * was (cosmetically) wearing. Higher ranks get better materials, more enchantments, higher levels, and
 * (if configured) levels above the vanilla maximum. From Legendary up, enchantments that normally exclude each
 * other can come together (Sharpness with Smite, Protection with Blast Protection...).
 */
public final class BossGearFactory {

    private final AdditionalBosses plugin;

    public BossGearFactory(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(BossRank rank, @Nullable EntityType sourceType, String sourceName, @Nullable GearKind forcedKind) {
        PluginSettings s = plugin.settings();
        RankSettings.Gear gear = s.rank(rank).gear();

        GearKind kind = forcedKind != null ? forcedKind : rollKind(rank, sourceType);
        GearTier tier = Rng.weighted(gear.materials());
        Material material = kind.material(tier == null ? GearTier.IRON : tier);
        ItemStack item = ItemStack.of(material);

        GearQuality quality = Rng.weighted(gear.quality());
        if (quality == null) {
            quality = GearQuality.STANDARD;
        }
        enchant(item, gear, quality, 0, overlaps(rank));
        plugin.items().markGear(item, rank, sourceName, quality);
        return item;
    }

    private boolean overlaps(BossRank rank) {
        BossRank from = plugin.settings().overlappingEnchantsFrom;
        return from != null && rank.atLeast(from);
    }

    /**
     * First the category by rank (weaker bosses mostly armor and tools, stronger ones weapons, Ascendants an even
     * mix), then the item inside it by the item weights (plus what this mob likes: skeletons bows...).
     */
    private GearKind rollKind(BossRank rank, @Nullable EntityType sourceType) {
        PluginSettings s = plugin.settings();
        Map<GearKind, Integer> weights = new EnumMap<>(s.gearWeights);
        if (sourceType != null) {
            Map<GearKind, Integer> extra = s.gearPreferences.get(sourceType);
            if (extra != null) {
                extra.forEach((k, v) -> weights.merge(k, v, Integer::sum));
            }
        }
        Map<GearKind.Category, Integer> categories = s.gearCategories.get(rank);
        if (categories != null) {
            // only categories that have something in them can be picked
            Map<GearKind.Category, Integer> usable = new EnumMap<>(GearKind.Category.class);
            categories.forEach((cat, w) -> {
                if (w > 0 && weights.entrySet().stream().anyMatch(e -> e.getKey().category() == cat && e.getValue() > 0)) {
                    usable.put(cat, w);
                }
            });
            GearKind.Category cat = Rng.weighted(usable);
            if (cat != null) {
                weights.keySet().removeIf(k -> k.category() != cat);
            }
        }
        GearKind kind = Rng.weighted(weights);
        return kind == null ? GearKind.SWORD : kind;
    }

    /** Nemesis loot calls this with a bonus to break the normal level ceiling a little further. */
    public ItemStack createBonus(BossRank rank, @Nullable EntityType sourceType, String sourceName, int overMaxBonus) {
        RankSettings.Gear gear = plugin.settings().rank(rank).gear();
        GearKind kind = rollKind(rank, sourceType);
        GearTier tier = Rng.weighted(gear.materials());
        ItemStack item = ItemStack.of(kind.material(tier == null ? GearTier.DIAMOND : tier));
        enchant(item, gear, GearQuality.MASTERWORK, overMaxBonus, overlaps(rank));
        plugin.items().markGear(item, rank, sourceName, GearQuality.MASTERWORK);
        return item;
    }

    private void enchant(ItemStack item, RankSettings.Gear gear, GearQuality quality, int overMaxBonus, boolean overlap) {
        PluginSettings s = plugin.settings();
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (!e.canEnchantItem(item)) {
                continue;
            }
            if (e.isCursed() && !s.allowCurseEnchantments) {
                continue;
            }
            if (s.excludedEnchantments.contains(e.getKey().getKey())) {
                continue;
            }
            candidates.add(e);
        }
        // a random order, but on armor Protection comes up front more often (it's what armor is for)
        Map<Enchantment, Double> keys = new java.util.HashMap<>();
        for (Enchantment e : candidates) {
            double w = e.getKey().getKey().equals("protection") ? s.protectionWeight : 1.0;
            keys.put(e, Math.pow(java.util.concurrent.ThreadLocalRandom.current().nextDouble(), 1.0 / w));
        }
        candidates.sort((a, b) -> Double.compare(keys.get(b), keys.get(a)));
        int wanted = Math.max(0, Rng.between(gear.enchantMin(), gear.enchantMax()) + quality.extraEnchantments());
        List<Enchantment> chosen = new ArrayList<>();
        for (Enchantment e : candidates) {
            if (chosen.size() >= wanted) {
                break;
            }
            if (clashes(e, chosen, overlap)) {
                continue;
            }
            chosen.add(e);
            int max = e.getMaxLevel();
            int min = Math.max(1, (int) Math.ceil(max * gear.minLevelPercent() / 100.0));
            int level = Math.max(1, Math.min(max, Rng.between(Math.min(min, max), max) + quality.levelShift()));
            int overLevels = gear.overMaxLevels() + overMaxBonus;
            double overChance = overMaxBonus > 0 ? 100 : gear.overMaxChance() * quality.overMaxFactor();
            if (max > 1 && overLevels > 0 && Rng.chance(overChance)) {
                level = max + Rng.between(1, overLevels);
            }
            item.addUnsafeEnchantment(e, level);
        }
        // Boss tools can carry a weapon enchantment too (Sharpness on a pickaxe hits like it would on a sword).
        if (EquipmentType.of(item.getType()) == EquipmentType.TOOL && Rng.chance(s.features.toolOffensiveChance)) {
            addOffensive(item, gear, quality, chosen, overlap);
        }
    }

    /**
     * Normally two enchantments that exclude each other never come together. With overlap they can, except
     * pairs that would break the item: Riptide stops a trident being thrown, so it never joins Loyalty or Channeling.
     */
    private static boolean clashes(Enchantment e, List<Enchantment> chosen, boolean overlap) {
        for (Enchantment c : chosen) {
            if (c.equals(e)) {
                return true;
            }
            if (!(c.conflictsWith(e) || e.conflictsWith(c))) {
                continue;
            }
            if (!overlap || breaksTogether(c, e)) {
                return true;
            }
        }
        return false;
    }

    private static boolean breaksTogether(Enchantment a, Enchantment b) {
        String x = a.getKey().getKey();
        String y = b.getKey().getKey();
        return x.equals("riptide") && (y.equals("loyalty") || y.equals("channeling"))
            || y.equals("riptide") && (x.equals("loyalty") || x.equals("channeling"));
    }

    private static final String[] OFFENSIVE = {"sharpness", "sharpness", "sharpness", "smite", "bane_of_arthropods",
        "fire_aspect", "fire_aspect", "knockback"};

    private void addOffensive(ItemStack item, RankSettings.Gear gear, GearQuality quality, List<Enchantment> chosen,
                              boolean overlap) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (int attempt = 0; attempt < 6; attempt++) {
            String id = OFFENSIVE[Rng.between(0, OFFENSIVE.length - 1)];
            Enchantment e = registry.get(NamespacedKey.minecraft(id));
            if (e == null || plugin.settings().excludedEnchantments.contains(id)) {
                continue;
            }
            if (clashes(e, chosen, overlap)) {
                continue;
            }
            int max = e.getMaxLevel();
            int min = Math.max(1, (int) Math.ceil(max * gear.minLevelPercent() / 100.0));
            int level = Math.max(1, Math.min(max, Rng.between(Math.min(min, max), max) + quality.levelShift()));
            item.addUnsafeEnchantment(e, level);
            return;
        }
    }
}
