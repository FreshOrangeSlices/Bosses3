package com.additionalbosses.boss;

import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.util.Rng;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Matching cosmetic armor sets for bosses: one material, one trim pattern and one trim colour across the whole
 * set, picked from the rank's palette. Nemeses get gear that visibly improves as they level up. All of it is
 * purely visual (no armor points, no weapon damage) and never drops; the rank's stats do the real work.
 */
public final class BossArmor {

    private static final String[] PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET};

    private BossArmor() {
    }

    /** Only these mobs actually render armor (illagers, spiders, slimes... don't). */
    public static boolean showsArmor(LivingEntity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof PiglinAbstract;
    }

    /** A random set from the rank's palette, with one random trim pattern for every piece. */
    public static void applyRankSet(FeatureSettings f, LivingEntity e, BossRank rank) {
        if (!showsArmor(e)) {
            return;
        }
        List<String[]> palette = f.armorPalettes.get(rank);
        if (palette == null || palette.isEmpty()) {
            return;
        }
        String[] pick = palette.get(Rng.between(0, palette.size() - 1));
        String pattern = f.trimPatterns.get(Rng.between(0, f.trimPatterns.size() - 1));
        equipSet(e, pick[0], pick[1], pattern, rank == BossRank.ASCENDANT);
    }

    /**
     * Nemesis gear by level: chainmail, then iron, diamond and netherite, gaining trims and finally a glint.
     * The trim pattern is fixed per Nemesis (from its id), so it keeps its look between returns.
     */
    public static void applyNemesis(FeatureSettings f, LivingEntity e, int level, String seed) {
        int stage = Math.min(4, Math.max(0, level / 10));
        String[] armor = {"CHAINMAIL", "IRON", "DIAMOND", "NETHERITE", "NETHERITE"};
        String[] trims = {null, "iron", "redstone", "gold", "quartz"};
        String pattern = f.trimPatterns.get(Math.floorMod(seed.hashCode(), f.trimPatterns.size()));
        if (showsArmor(e)) {
            equipSet(e, armor[stage], trims[stage], pattern, stage >= 4);
        }
        ItemStack weapon = nemesisWeapon(e.getType(), stage, e.getEquipment());
        EntityEquipment eq = e.getEquipment();
        if (weapon != null && eq != null) {
            eq.setItemInMainHand(weapon);
            eq.setItemInMainHandDropChance(0f);
        }
    }

    private static @Nullable ItemStack nemesisWeapon(EntityType type, int stage, @Nullable EntityEquipment eq) {
        String[] tiers = {"STONE", "IRON", "DIAMOND", "NETHERITE", "NETHERITE"};
        Material material = switch (type) {
            case ZOMBIE, HUSK, ZOMBIE_VILLAGER, WITHER_SKELETON, ZOMBIFIED_PIGLIN -> mat(tiers[stage] + "_SWORD");
            case VINDICATOR, PIGLIN_BRUTE -> mat(tiers[stage] + "_AXE");
            case PIGLIN -> eq != null && eq.getItemInMainHand().getType() == Material.CROSSBOW
                ? Material.CROSSBOW : mat(tiers[stage] + "_SWORD");
            case SKELETON, STRAY, BOGGED, PARCHED -> Material.BOW;
            case PILLAGER -> Material.CROSSBOW;
            case DROWNED -> Material.TRIDENT;
            default -> null;
        };
        if (material == null) {
            return null;
        }
        ItemStack item = ItemStack.of(material);
        cosmetic(item, stage >= (material == Material.BOW || material == Material.CROSSBOW
            || material == Material.TRIDENT ? 2 : 4));
        return item;
    }

    private static void equipSet(LivingEntity e, String armorPrefix, @Nullable String trim, String pattern, boolean glint) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        TrimMaterial trimMaterial = trim == null ? null : trimMaterial(trim);
        TrimPattern trimPattern = trimPattern(pattern);
        for (int i = 0; i < PIECES.length; i++) {
            Material m = mat(armorPrefix.toUpperCase(Locale.ROOT) + "_" + PIECES[i]);
            if (m == null) {
                m = mat("IRON_" + PIECES[i]);
            }
            if (m == null) {
                continue;
            }
            ItemStack piece = ItemStack.of(m);
            if (trimMaterial != null && trimPattern != null) {
                piece.editMeta(ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(trimMaterial, trimPattern)));
            }
            cosmetic(piece, glint);
            eq.setItem(SLOTS[i], piece);
            eq.setDropChance(SLOTS[i], 0f);
        }
    }

    /** No armor points / weapon damage, can't break. */
    private static void cosmetic(ItemStack item, boolean glint) {
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        item.setData(DataComponentTypes.UNBREAKABLE);
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    private static @Nullable Material mat(String name) {
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? null : m;
    }

    private static @Nullable TrimMaterial trimMaterial(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(key);
    }

    private static @Nullable TrimPattern trimPattern(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(key);
    }
}
