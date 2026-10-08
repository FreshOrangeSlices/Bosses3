package com.additionalbosses.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Material tier of a Boss Gear item. Weights per rank are set in config.yml (ranks.X.gear.materials).
 */
public enum GearTier {
    COPPER("COPPER", "COPPER"),
    IRON("IRON", "IRON"),
    GOLD("GOLDEN", "GOLDEN"),
    DIAMOND("DIAMOND", "DIAMOND"),
    NETHERITE("NETHERITE", "NETHERITE");

    private final String toolPrefix;
    private final String armorPrefix;

    GearTier(String toolPrefix, String armorPrefix) {
        this.toolPrefix = toolPrefix;
        this.armorPrefix = armorPrefix;
    }

    public String prefix(boolean armor) {
        return armor ? armorPrefix : toolPrefix;
    }

    public static @Nullable GearTier parse(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("GOLDEN")) {
            return GOLD;
        }
        for (GearTier tier : values()) {
            if (tier.name().equals(s)) {
                return tier;
            }
        }
        return null;
    }
}
