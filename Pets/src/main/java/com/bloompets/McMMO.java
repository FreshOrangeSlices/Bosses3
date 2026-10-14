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
    // mcMMO's own combat XP (the same call a wolf's hit makes), when available
    private @Nullable Method userOf;
    private @Nullable Method processCombatXp;
    private @Nullable Object taming;
    // the checks mcMMO makes before a wolf's hit counts
    private @Nullable Method worldBlacklisted;
    private @Nullable Object skillTools;
    private @Nullable Method combatSkillsTrigger;
    private @Nullable Method npcPrevented;
    private @Nullable Method isNpc;
    private boolean retro = true;
    private boolean warned;

    /** Last known Taming level of each owner (a stationed pet's owner may be offline). */
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> checkedAt = new HashMap<>();
    private final Set<UUID> lookingUp = new HashSet<>();
    private final Map<UUID, Long> retryLookup = new HashMap<>();
    /** Whether each owner had mcMMO's Taming permission when last seen (for stationed pets of offline owners). */
    private final Map<UUID, Boolean> permitted = new HashMap<>();

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
            try {
                Class<?> skill = Class.forName("com.gmail.nossr50.datatypes.skills.PrimarySkillType", true, loader);
                Class<?> user = Class.forName("com.gmail.nossr50.datatypes.player.McMMOPlayer", true, loader);
                userOf = Class.forName("com.gmail.nossr50.util.player.UserManager", true, loader)
                    .getMethod("getPlayer", Player.class);
                processCombatXp = Class.forName("com.gmail.nossr50.util.skills.CombatUtils", true, loader)
                    .getMethod("processCombatXP", user, LivingEntity.class, skill, double.class);
                taming = skill.getMethod("valueOf", String.class).invoke(null, SKILL);
                Object main = Class.forName("com.gmail.nossr50.mcMMO", true, loader).getField("p").get(null);
                skillTools = main.getClass().getMethod("getSkillTools").invoke(main);
                combatSkillsTrigger = skillTools.getClass().getMethod("canCombatSkillsTrigger", skill,
                    org.bukkit.entity.Entity.class);
                isNpc = Class.forName("com.gmail.nossr50.util.Misc", true, loader)
                    .getMethod("isNPCEntityExcludingVillagers", org.bukkit.entity.Entity.class);
                if (expConfig != null) {
                    npcPrevented = expConfig.getClass().getMethod("isNPCInteractionPrevented");
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                processCombatXp = null; // use the public API with our own copy of the formula
                combatSkillsTrigger = null;
            }
            try {
                worldBlacklisted = Class.forName("com.gmail.nossr50.config.WorldBlacklist", true, loader)
                    .getMethod("isWorldBlacklisted", org.bukkit.World.class);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                worldBlacklisted = null;
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
        if (Boolean.FALSE.equals(permitted.get(owner))) {
            return 0;
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
        boolean allowed = p.hasPermission("mcmmo.skills.taming");
        permitted.put(p.getUniqueId(), allowed);
        if (!allowed) {
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

    /**
     * A stationed pet's owner is offline and we never saw them: ask mcMMO's database, off the main thread (again
     * after a few minutes if it failed).
     */
    private void lookUpOffline(UUID owner) {
        Method offline = getLevelOffline;
        long now = System.currentTimeMillis();
        if (offline == null || now < retryLookup.getOrDefault(owner, 0L) || !lookingUp.add(owner)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer found = null;
            try {
                found = (int) offline.invoke(null, owner, SKILL);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                // not in mcMMO's database (yet), or the database is busy
            }
            Integer level = found;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                lookingUp.remove(owner);
                if (level != null) {
                    levels.putIfAbsent(owner, level);
                } else {
                    retryLookup.put(owner, System.currentTimeMillis() + 300_000);
                }
            });
        });
    }

    /**
     * Before a combat pet's hit: has mcMMO itself award Taming XP for it, exactly like a tamed wolf's hit (same
     * formula, same rules for spawner mobs, bosses, PvP and parties). mcMMO notes the target's health now and pays
     * for whatever the hit took off. False if that isn't available (then {@link #hitXp} is used after the hit).
     */
    public boolean queueHitXp(Player owner, LivingEntity target) {
        Method process = processCombatXp;
        Method user = userOf;
        if (process == null || user == null || taming == null || getLevel == null
            || !owner.hasPermission("mcmmo.skills.taming")) {
            return process != null; // (no permission: no XP, and no fallback either)
        }
        if (!counts(target)) {
            return true; // mcMMO wouldn't pay for this hit either
        }
        try {
            Object mmoPlayer = user.invoke(null, owner);
            if (mmoPlayer != null) {
                process.invoke(null, mmoPlayer, target, taming, plugin.settings().tamingXpMultiplier);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "mcMMO's combat XP didn't work for a pet; using the API "
                    + "instead (shown once)", ex);
            }
            processCombatXp = null;
            return false;
        }
    }

    /** mcMMO's world blacklist: no Taming skills or XP in those worlds. */
    public boolean blacklisted(org.bukkit.World world) {
        Method m = worldBlacklisted;
        if (m == null) {
            return false;
        }
        try {
            return (boolean) m.invoke(null, world);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /**
     * The checks mcMMO makes before a tamed wolf's hit counts: not a blacklisted world, Taming allowed against this
     * target (mcMMO's PvP/PvE switches), and not an NPC if NPCs are excluded.
     */
    private boolean counts(LivingEntity target) {
        if (blacklisted(target.getWorld())) {
            return false;
        }
        try {
            if (combatSkillsTrigger != null && skillTools != null
                && !(boolean) combatSkillsTrigger.invoke(skillTools, taming, target)) {
                return false;
            }
            if (npcPrevented != null && isNpc != null && expConfig != null
                && (boolean) npcPrevented.invoke(expConfig) && (boolean) isNpc.invoke(null, target)) {
                return false;
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true;
        }
        return true;
    }

    /**
     * Taming XP for a hit by a combat pet, worked out like mcMMO does for a wolf: damage dealt x the mob's combat XP
     * x 10 (x 3 for Taming), or the PvP value for players.
     */
    public void hitXp(Player owner, LivingEntity target, double dealt) {
        Method add = addXp;
        if (add == null || dealt <= 0 || !owner.hasPermission("mcmmo.skills.taming") || blacklisted(target.getWorld())) {
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
            return d >= 0 ? d : fallback;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return fallback;
        }
    }

    public void forget(UUID player) {
        checkedAt.remove(player);
    }
}
