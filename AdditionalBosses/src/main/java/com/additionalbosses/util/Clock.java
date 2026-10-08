package com.additionalbosses.util;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * "Minecraft days" for timers (Nemesis returns, Escalation windows, trophy dates).
 *
 * <p>It counts the main world's game time, which always moves forward (even with the daylight cycle turned off),
 * plus any time skipped by sleeping or /time add, so a night slept through counts like a night waited out.
 * The skipped time is saved in nemesis.yml.</p>
 */
public final class Clock {

    public static final long DAY = 24000L;

    private static long offset;
    private static long lastGame = -1;
    private static long lastDay = -1;

    private Clock() {
    }

    public static void load(long savedOffset) {
        offset = Math.max(0, savedOffset);
        lastGame = -1;
        lastDay = -1;
    }

    public static long offset() {
        return offset;
    }

    public static long now() {
        World main = Bukkit.getWorlds().get(0);
        long game = main.getGameTime();
        long day = main.getFullTime();
        if (lastGame >= 0) {
            long gameDelta = game - lastGame;
            long dayDelta = day - lastDay;
            if (gameDelta >= 0 && dayDelta > gameDelta + 20) {
                offset += dayDelta - gameDelta; // the night was skipped
            }
        }
        lastGame = game;
        lastDay = day;
        return game + offset;
    }

    /** Day number for display (day 1 = the first day). */
    public static long day() {
        return now() / DAY + 1;
    }
}
