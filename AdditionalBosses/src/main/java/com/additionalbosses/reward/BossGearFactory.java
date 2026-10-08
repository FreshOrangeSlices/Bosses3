package com.additionalbosses.reward;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.util.Rng;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Boss Gear: exactly one rank-branded equipment item, rolled independently of whatever the boss
 * was (cosmetically) wearing. Higher ranks get better materials, more enchantments, higher levels, and
 * (if configured) levels above the vanilla maximum.
 */
public final class BossGearFactory {

    private final AdditionalBosses plugin;

    public BossGearFactory(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(BossRank rank, @Nullable EntityType sourceType, String sourceName, @Nullable GearKind forcedKind) {
        PluginSettings s = plugin.settings();
        RankSettings.Gear gear = s.rank(rank).gear();

        GearKind kind = forcedKind != null ? forcedKind : rollKind(sourceType);
        GearTier tier = Rng.weighted(gear.materials());
        Material material = kind.material(tier == null ? GearTier.IRON : tier);
        ItemStack item = ItemStack.of(material);

        enchant(item, gear);
        plugin.items().markGear(item, rank, sourceName);
        return item;
    }

    private GearKind rollKind(@Nullable EntityType sourceType) {
        PluginSettings s = plugin.settings();
        Map<GearKind, Integer> weights = new EnumMap<>(s.gearWeights);
        if (sourceType != null) {
            Map<GearKind, Integer> extra = s.gearPreferences.get(sourceType);
            if (extra != null) {
                extra.forEach((k, v) -> weights.merge(k, v, Integer::sum));
            }
        }
        GearKind kind = Rng.weighted(weights);
        return kind == null ? GearKind.SWORD : kind;
    }

    private void enchant(ItemStack item, RankSettings.Gear gear) {
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
        Collections.shuffle(candidates);
        int wanted = Rng.between(gear.enchantMin(), gear.enchantMax());
        List<Enchantment> chosen = new ArrayList<>();
        for (Enchantment e : candidates) {
            if (chosen.size() >= wanted) {
                break;
            }
            boolean clash = false;
            for (Enchantment c : chosen) {
                if (c.conflictsWith(e) || e.conflictsWith(c)) {
                    clash = true;
                    break;
                }
            }
            if (clash) {
                continue;
            }
            chosen.add(e);
            int max = e.getMaxLevel();
            int min = Math.max(1, (int) Math.ceil(max * gear.minLevelPercent() / 100.0));
            int level = Rng.between(Math.min(min, max), max);
            if (max > 1 && gear.overMaxLevels() > 0 && Rng.chance(gear.overMaxChance())) {
                level = max + Rng.between(1, gear.overMaxLevels());
            }
            item.addUnsafeEnchantment(e, level);
        }
    }
}
