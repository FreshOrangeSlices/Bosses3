package com.questbook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/** Reads mcMMO skill levels through its public API, by reflection (mcMMO is optional). */
final class McMMO {

    private final QuestBook plugin;
    private @Nullable Method getLevel;
    private boolean retro = true;

    McMMO(QuestBook plugin) {
        this.plugin = plugin;
    }

    void hook() {
        getLevel = null;
        Plugin mcmmo = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (mcmmo == null || !mcmmo.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = mcmmo.getClass().getClassLoader();
            getLevel = Class.forName("com.gmail.nossr50.api.ExperienceAPI", true, loader)
                .getMethod("getLevel", Player.class, String.class);
            try {
                retro = (boolean) Class.forName("com.gmail.nossr50.mcMMO", true, loader)
                    .getMethod("isRetroModeEnabled").invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                retro = true;
            }
            plugin.getLogger().info("Reading mcMMO skill levels" + (retro ? "" : " (Standard mode: milestone levels / 10)")
                + ".");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getLevel = null;
            plugin.getLogger().warning("Couldn't read mcMMO's skill levels: " + ex);
        }
    }

    boolean present() {
        return getLevel != null;
    }

    /** Milestone levels are written for RetroMode (0-1000); on Standard (0-100) they're a tenth. */
    int scaled(int level) {
        return retro ? level : Math.max(1, (int) Math.round(level / 10.0));
    }

    /** The player's level in a skill (MINING, TAMING...), or -1 if it can't be read right now. */
    int level(Player p, String skill) {
        Method m = getLevel;
        if (m == null) {
            return -1;
        }
        try {
            return (int) m.invoke(null, p, skill.toUpperCase(Locale.ROOT));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1; // profile still loading, or no such skill
        }
    }
}
