package com.additionalbosses.util;

import com.additionalbosses.boss.BossRank;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-player records stored in the player's own PersistentDataContainer (saved with the player file).
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static int kills(Player player, BossRank rank) {
        return player.getPersistentDataContainer().getOrDefault(Keys.kills(rank), PersistentDataType.INTEGER, 0);
    }

    public static int totalKills(Player player) {
        int total = 0;
        for (BossRank rank : BossRank.values()) {
            total += kills(player, rank);
        }
        return total;
    }

    public static void addKill(Player player, BossRank rank) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(Keys.kills(rank), PersistentDataType.INTEGER, kills(player, rank) + 1);
    }

    public static int relicsBound(Player player) {
        return player.getPersistentDataContainer().getOrDefault(Keys.PLAYER_RELICS_BOUND, PersistentDataType.INTEGER, 0);
    }

    public static void addRelicBound(Player player) {
        player.getPersistentDataContainer().set(Keys.PLAYER_RELICS_BOUND, PersistentDataType.INTEGER, relicsBound(player) + 1);
    }

    public static boolean guideGiven(Player player) {
        return player.getPersistentDataContainer().has(Keys.PLAYER_GUIDE_GIVEN, PersistentDataType.BYTE);
    }

    public static void markGuideGiven(Player player) {
        player.getPersistentDataContainer().set(Keys.PLAYER_GUIDE_GIVEN, PersistentDataType.BYTE, (byte) 1);
    }

    /** Epoch millis when Second Dawn can trigger again. */
    public static long secondDawnReadyAt(Player player) {
        return player.getPersistentDataContainer().getOrDefault(Keys.PLAYER_SECOND_DAWN, PersistentDataType.LONG, 0L);
    }

    public static void setSecondDawnReadyAt(Player player, long epochMillis) {
        player.getPersistentDataContainer().set(Keys.PLAYER_SECOND_DAWN, PersistentDataType.LONG, epochMillis);
    }
}
