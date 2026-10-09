package com.gmail.nossr50.util;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Server-specific addition: recognises bosses from the Additional Bosses plugin by the data it stores on the mob
 * (no hard dependency; if that plugin isn't installed, nothing here ever matches).
 *
 * <ul>
 *     <li>Bosses give more combat XP depending on their rank, with an extra bonus for a Nemesis.</li>
 *     <li>Mobs a boss summoned (minions) give reduced XP, so they can't be farmed.</li>
 *     <li>Bosses and their statues keep their own names (they already have a boss bar), so mcMMO's
 *     mob health bar leaves them alone.</li>
 *     <li>The ability charge bar waits while Additional Bosses has a message above the hotbar.</li>
 * </ul>
 */
public final class AdditionalBossesHook {

    private static final NamespacedKey BOSS = key("boss");
    private static final NamespacedKey RANK = key("boss_rank");
    private static final NamespacedKey NEMESIS = key("nemesis");
    private static final NamespacedKey MINION = key("minion_of");
    private static final NamespacedKey STATUE = key("statue");
    /** On a player: epoch millis until which Additional Bosses is using the action bar. */
    private static final NamespacedKey ACTION_BAR_BUSY = key("actionbar_busy_until");

    private AdditionalBossesHook() {
    }

    private static NamespacedKey key(String name) {
        return java.util.Objects.requireNonNull(NamespacedKey.fromString("additionalbosses:" + name));
    }

    /** True for an Additional Bosses boss (including a Nemesis). */
    public static boolean isBoss(@NotNull Entity entity) {
        return entity.getPersistentDataContainer().has(BOSS, PersistentDataType.BYTE);
    }

    /** True for anything Additional Bosses names itself: bosses and placed statues/trophies. */
    public static boolean hasOwnName(@NotNull Entity entity) {
        final PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(BOSS, PersistentDataType.BYTE) || pdc.has(STATUE, PersistentDataType.STRING);
    }

    /**
     * True while Additional Bosses has a message on this player's action bar (a boss warning, the boss
     * compass...), so mcMMO's ability charge bar doesn't paint over it.
     */
    public static boolean actionBarBusy(@NotNull Player player) {
        final Long busyUntil = player.getPersistentDataContainer()
                .get(ACTION_BAR_BUSY, PersistentDataType.LONG);
        return busyUntil != null && busyUntil > System.currentTimeMillis();
    }

    /** Combat XP multiplier for this mob (1.0 for anything that isn't a boss or a boss's minion). */
    public static double xpMultiplier(@NotNull Entity entity) {
        final PersistentDataContainer pdc = entity.getPersistentDataContainer();
        final ExperienceConfig config = ExperienceConfig.getInstance();
        if (pdc.has(MINION, PersistentDataType.STRING)) {
            return config.getAdditionalBossesMultiplier("Minion", 0.25);
        }
        if (!pdc.has(BOSS, PersistentDataType.BYTE)) {
            return 1.0;
        }
        final String rank = pdc.getOrDefault(RANK, PersistentDataType.STRING, "GRAY");
        double multiplier = config.getAdditionalBossesMultiplier(capitalize(rank), defaultFor(rank));
        if (pdc.has(NEMESIS, PersistentDataType.STRING)) {
            multiplier *= config.getAdditionalBossesMultiplier("Nemesis_Bonus", 1.5);
        }
        return Math.max(0, multiplier);
    }

    private static double defaultFor(String rank) {
        return switch (rank.toUpperCase(Locale.ROOT)) {
            case "GREEN" -> 2.0;
            case "RED" -> 3.0;
            case "PURPLE" -> 4.0;
            case "GOLD" -> 6.0;
            case "ASCENDANT" -> 10.0;
            default -> 1.5;
        };
    }

    private static String capitalize(String raw) {
        final String s = raw.toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
