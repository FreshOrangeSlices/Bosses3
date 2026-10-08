package com.additionalbosses.config;

import com.additionalbosses.boss.BossRank;
import com.additionalbosses.reward.GearTier;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Everything config.yml says about one rank.
 */
public record RankSettings(
    BossRank rank,
    String name,
    Stats stats,
    TraitRoll traits,
    double xpMultiplier,
    int xpBonus,
    Rewards rewards,
    Gear gear,
    boolean persistent,
    Presentation presentation
) {

    /** Multipliers (health, damage, speed) and flat bonuses (armor, toughness, knockback resistance, size). */
    public record Stats(double health, double damage, double armor, double toughness,
                        double knockbackResistance, double speed, double size) {
    }

    public record TraitRoll(int min, int max, double power) {
    }

    /** Independent percent chances, each rolled separately when the boss dies. */
    public record Rewards(double bossGear, double empowerment, double relic, double relicCatalyst) {
    }

    public record Gear(Map<GearTier, Integer> materials, int enchantMin, int enchantMax,
                       double minLevelPercent, double overMaxChance, int overMaxLevels) {
    }

    public enum Announce { NONE, ACTIONBAR, CHAT, TITLE }

    public enum Broadcast { NONE, NEARBY, SERVER }

    public record Presentation(Announce announce, @Nullable Sound sound, @Nullable Sound deathSound,
                               int particles, Broadcast deathBroadcast) {
    }

    /** "★★★★ Nightmare" */
    public String title() {
        return rank.starText() + " " + name;
    }
}
