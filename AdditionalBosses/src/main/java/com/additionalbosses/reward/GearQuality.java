package com.additionalbosses.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Item quality: rolled separately from the material. It shifts how many enchantments Boss Gear gets and how
 * high they go. Weights per rank are in config.yml (ranks.X.gear.quality).
 */
public enum GearQuality {
    CRUDE("Crude", -1, -1, 0.0),
    STANDARD("Standard", 0, 0, 1.0),
    FINE("Fine", 1, 0, 1.5),
    MASTERWORK("Masterwork", 1, 1, 2.0);

    private final String displayName;
    private final int extraEnchantments;
    private final int levelShift;
    private final double overMaxFactor;

    GearQuality(String displayName, int extraEnchantments, int levelShift, double overMaxFactor) {
        this.displayName = displayName;
        this.extraEnchantments = extraEnchantments;
        this.levelShift = levelShift;
        this.overMaxFactor = overMaxFactor;
    }

    public String displayName() {
        return displayName;
    }

    public int extraEnchantments() {
        return extraEnchantments;
    }

    /** Added to every rolled level (never below 1, never above the normal ceiling except via over-max). */
    public int levelShift() {
        return levelShift;
    }

    /** Multiplies the rank's chance to roll levels above the vanilla maximum. */
    public double overMaxFactor() {
        return overMaxFactor;
    }

    public static @Nullable GearQuality parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
