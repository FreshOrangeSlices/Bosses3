package com.additionalbosses.util;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * "Minecraft days" for timers (Nemesis returns, Escalation windows, trophy dates): the main world's clock, so
 * sleeping through the night moves it forward like a real day. Falls back to game time if the world has no clock.
 */
public final class Clock {

    public static final long DAY = 24000L;

    private Clock() {
    }

    public static long now() {
        World main = Bukkit.getWorlds().get(0);
        long time = main.getFullTime();
        return time > 0 ? time : main.getGameTime();
    }

    public static long day() {
        return now() / DAY + 1;
    }
}
