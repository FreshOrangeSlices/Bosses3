package com.additionalbosses.reward;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The kinds of equipment the Boss Gear roll can produce.
 */
public enum GearKind {
    SWORD("SWORD", false, true, null),
    AXE("AXE", false, true, null),
    SPEAR("SPEAR", false, true, null),
    MACE(null, false, false, Material.MACE),
    BOW(null, false, false, Material.BOW),
    CROSSBOW(null, false, false, Material.CROSSBOW),
    TRIDENT(null, false, false, Material.TRIDENT),
    HELMET("HELMET", true, true, null),
    CHESTPLATE("CHESTPLATE", true, true, null),
    LEGGINGS("LEGGINGS", true, true, null),
    BOOTS("BOOTS", true, true, null);

    private final @Nullable String suffix;
    private final boolean armor;
    private final boolean tiered;
    private final @Nullable Material fixed;

    GearKind(@Nullable String suffix, boolean armor, boolean tiered, @Nullable Material fixed) {
        this.suffix = suffix;
        this.armor = armor;
        this.tiered = tiered;
        this.fixed = fixed;
    }

    public boolean armor() {
        return armor;
    }

    /** Resolves the actual material, falling back to iron if a tier does not exist for this kind. */
    public Material material(GearTier tier) {
        if (!tiered || suffix == null) {
            return fixed == null ? Material.IRON_SWORD : fixed;
        }
        Material m = Material.matchMaterial(tier.prefix(armor) + "_" + suffix);
        if (m == null) {
            m = Material.matchMaterial("IRON_" + suffix);
        }
        return m == null ? Material.IRON_SWORD : m;
    }

    public static @Nullable GearKind parse(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        for (GearKind kind : values()) {
            if (kind.name().equals(s)) {
                return kind;
            }
        }
        return null;
    }
}
