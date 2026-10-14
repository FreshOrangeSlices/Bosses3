package com.additionalbosses.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Tells a Quest Book plugin (QuestBook) what a player did, as a counter on the player under "questbook:<flag>".
 * Nothing depends on QuestBook being installed: without it these are just a few numbers nobody reads.
 */
public final class QuestFlags {

    private QuestFlags() {
    }

    private static NamespacedKey key(String flag) {
        return NamespacedKey.fromString("questbook:" + flag);
    }

    public static void mark(Player player, String flag) {
        NamespacedKey key = key(flag);
        if (key == null) {
            return;
        }
        int now = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, now + 1);
    }

    public static void mark(UUID player, String flag) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            mark(p, flag);
        }
    }

    /** Marks it only if it never was (for catching up on what a player did before). */
    public static void markOnce(Player player, String flag) {
        NamespacedKey key = key(flag);
        if (key != null && !player.getPersistentDataContainer().has(key)) {
            mark(player, flag);
        }
    }
}
