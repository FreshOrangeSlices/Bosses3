package com.questbook;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * How other plugins tell the Quest Book that a player did something: a counter on the player, under the
 * "questbook" namespace. Quests in quests.yml complete on {@code flag:<name>}.
 *
 * <p>A plugin doesn't need QuestBook installed (or on its build path) to do this; the same thing in one line:</p>
 * <pre>{@code
 * NamespacedKey key = NamespacedKey.fromString("questbook:relic_bound");
 * pdc.set(key, PersistentDataType.INTEGER, pdc.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1);
 * }</pre>
 * <p>Console and command blocks can use {@code /quests flag <player> <name>}.</p>
 */
public final class QuestFlags {

    private QuestFlags() {
    }

    private static NamespacedKey key(String flag) {
        NamespacedKey key = NamespacedKey.fromString("questbook:" + flag.trim().toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            throw new IllegalArgumentException("Not a valid flag name: " + flag);
        }
        return key;
    }

    /** Counts the flag up by one. */
    public static void mark(Player player, String flag) {
        NamespacedKey key = key(flag);
        int now = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, now + 1);
    }

    /** How many times the flag was marked (0: never). */
    public static int get(Player player, String flag) {
        NamespacedKey key = key(flag);
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
        }
        return pdc.has(key) ? 1 : 0; // set some other way (a byte...): counts as once
    }
}
