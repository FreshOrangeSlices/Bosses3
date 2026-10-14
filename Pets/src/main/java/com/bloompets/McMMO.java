package com.bloompets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The link to mcMMO, if it's installed: reads a player's Taming level and gives Taming XP, through mcMMO's public
 * API ({@code ExperienceAPI}). Done by reflection, so BloomPets runs fine without mcMMO (the skills just stay off).
 * mcMMO itself is never changed.
 */
public final class McMMO {

    private static final String SKILL = "TAMING";

    private final BloomPets plugin;
    private @Nullable Method getLevel;
    private @Nullable Method getLevelOffline;
    private @Nullable Method addXp;
    private @Nullable Object expConfig;
    private @Nullable Method combatXp;
    private @Nullable Method animalsXp;
    private @Nullable Method pvpXp;
    private boolean retro = true;
    private boolean warned;

    /** Last known Taming level of each owner (a stationed pet's owner may be offline). */
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> checkedAt = new HashMap<>();
    private final Set<UUID> lookingUp = new HashSet<>();

    public McMMO(BloomPets plugin) {
        this.plugin = plugin;
    }

    /** Looks for mcMMO (called on enable, after mcMMO has loaded, since it's a soft dependency). */
    public void hook() {
        getLevel = null;
        addXp = null;
        Plugin mcmmo = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (mcmmo == null || !mcmmo.isEnabled()) {
            plugin.getLogger().info("mcMMO not found: the Taming skills of combat pets are off.");
            return;
        }
        try {
            ClassLoader loader = mcmmo.getClass().getClassLoader();
            Class<?> api = Class.forName("com.gmail.nossr50.api.ExperienceAPI", true, loader);
            getLevel = api.getMethod("getLevel", Player.class, String.class);
            addXp = api.getMethod("addXP", Player.class, String.class, int.class, String.class);
            try {
                getLevelOffline = api.getMethod("getLevelOffline", UUID.class, String.class);
            } catch (NoSuchMethodException ignored) {
                getLevelOffline = null;
            }
            try {
                Method isRetro = Class.forName("com.gmail.nossr50.mcMMO", true, loader).getMethod("isRetroModeEnabled");
                retro = (boolean) isRetro.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                retro = true;
            }
            try {
                Class<?> config = Class.forName("com.gmail.nossr50.config.experience.ExperienceConfig", true, loader);
                expConfig = config.getMethod("getInstance").invoke(null);
                combatXp = config.getMethod("getCombatXP", EntityType.class);
                animalsXp = config.getMethod("getAnimalsXP", EntityType.class);
                pvpXp = config.getMethod("getPlayerVersusPlayerXP");
            } catch (ReflectiveOperationException | RuntimeException ex) {
                expConfig = null; // fall back to mcMMO's default numbers
            }
            plugin.getLogger().info("Hooked into mcMMO " + mcmmo.getPluginMeta().getVersion()
                + ": combat pets use their owner's Taming level" + (retro ? "" : " (Standard mode: levels / 10)") + ".");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getLevel = null;
            addXp = null;
            plugin.getLogger().log(Level.WARNING, "Couldn't hook into mcMMO; the Taming skills of combat pets are off", ex);
        }
    }

    public boolean present() {
        return getLevel != null;
    }

    /** mcMMO's level scale: 1 on RetroMode (0-1000), 0.1 on Standard (0-100). Unlock levels are multiplied by it. */
    public double scale() {
        return retro ? 1.0 : 0.1;
    }

    /** The owner's Taming level (their last known one while they're offline), 0 if unknown or mcMMO is missing. */
    public int tamingLevel(UUID owner) {
        if (getLevel == null) {
            return 0;
        }
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            return tamingLevel(p);
        }
        Integer known = levels.get(owner);
        if (known != null) {
            return known;
        }
        lookUpOffline(owner);
        return 0;
    }

    public int tamingLevel(Player p) {
        if (getLevel == null) {
            return 0;
        }
        if (!p.hasPermission("mcmmo.skills.taming")) {
            return 0;
        }
        long now = System.currentTimeMillis();
        Integer known = levels.get(p.getUniqueId());
        if (known != null && now - checkedAt.getOrDefault(p.getUniqueId(), 0L) < 1000) {
            return known;
        }
        try {
            int level = (int) getLevel.invoke(null, p, SKILL);
            levels.put(p.getUniqueId(), level);
            checkedAt.put(p.getUniqueId(), now);
            return level;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return known == null ? 0 : known; // their mcMMO profile isn't loaded yet
        }
    }

    /** A stationed pet's owner is offline and we never saw them: ask mcMMO's database once, off the main thread. */
    private void lookUpOffline(UUID owner) {
        Method offline = getLevelOffline;
        if (offline == null || !lookingUp.add(owner)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                levels.putIfAbsent(owner, (int) offline.invoke(null, owner, SKILL));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                levels.putIfAbsent(owner, 0); // never played with mcMMO
            }
        });
    }

    /**
     * Taming XP for a hit by a combat pet, worked out like mcMMO does for a wolf: damage dealt x the mob's combat XP
     * x 10 (x 3 for Taming), or the PvP value for players.
     */
    public void hitXp(Player owner, LivingEntity target, double dealt) {
        Method add = addXp;
        if (add == null || dealt <= 0 || !owner.hasPermission("mcmmo.skills.taming")) {
            return;
        }
        boolean pvp = target instanceof Player;
        double base = pvp ? 20 * number(pvpXp, null, 1.0) : 10 * mobXp(target);
        int xp = (int) Math.round(dealt * base * plugin.settings().tamingXpMultiplier);
        if (xp <= 0) {
            return;
        }
        try {
            add.invoke(null, owner, SKILL, xp, pvp ? "PVP" : "PVE");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable cause = ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause() : ex;
            if (cause.getClass().getSimpleName().contains("NotFound")) {
                return; // their mcMMO profile is still loading (just joined)
            }
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "mcMMO refused Taming XP from a pet (shown once)", ex);
            }
        }
    }

    private double mobXp(LivingEntity target) {
        EntityType type = target.getType();
        if (target instanceof Animals) {
            return number(animalsXp, type, 1.0);
        }
        if (target instanceof Monster) {
            return number(combatXp, type, 2.0);
        }
        return number(combatXp, type, 1.0);
    }

    private double number(@Nullable Method m, @Nullable EntityType type, double fallback) {
        if (m == null || expConfig == null) {
            return fallback;
        }
        try {
            Object v = type == null ? m.invoke(expConfig) : m.invoke(expConfig, type);
            double d = v instanceof Number n ? n.doubleValue() : fallback;
            return d > 0 ? d : fallback;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return fallback;
        }
    }

    public void forget(UUID player) {
        checkedAt.remove(player);
    }
}
