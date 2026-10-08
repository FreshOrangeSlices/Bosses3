package com.additionalbosses.boss;

import com.additionalbosses.trait.BossTrait;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state of one active (loaded) boss. The permanent identity (rank, traits, category) lives in the
 * entity's PersistentDataContainer; this object is the cached, in-memory view while the boss is loaded.
 */
public final class Boss {

    private final UUID uuid;
    private final LivingEntity entity;
    private final BossRank rank;
    private final String categoryId;
    private final List<BossTrait> traits;
    private Component name;
    private String plainName;
    private double damageMultiplier;
    private double traitPower;
    private @Nullable String nemesisId;
    private boolean lastStand;
    private @Nullable org.bukkit.Location lastPosition;
    private int stuckSince = -1;

    /** Player UUID -> server tick of their last interaction with this boss. */
    private final Map<UUID, Integer> engaged = new HashMap<>();
    private final Set<UUID> barViewers = new HashSet<>();
    private final Set<UUID> revealedTo = new HashSet<>();
    private final Set<UUID> minions = new HashSet<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final Set<String> flags = new HashSet<>();
    private @Nullable BossBar bar;
    private boolean undyingUsed;
    private boolean revealed;
    private int lastHurtTick;

    public Boss(LivingEntity entity, BossRank rank, String categoryId, List<BossTrait> traits, Component name,
                String plainName, double damageMultiplier, double traitPower) {
        this.uuid = entity.getUniqueId();
        this.entity = entity;
        this.rank = rank;
        this.categoryId = categoryId;
        this.traits = new ArrayList<>(traits);
        this.name = name;
        this.plainName = plainName;
        this.damageMultiplier = damageMultiplier;
        this.traitPower = traitPower;
    }

    public UUID uuid() {
        return uuid;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BossRank rank() {
        return rank;
    }

    public String categoryId() {
        return categoryId;
    }

    public List<BossTrait> traits() {
        return Collections.unmodifiableList(traits);
    }

    /** Adds a trait mid-fight (Last Stand, Nemesis adaptation). */
    public void addTrait(BossTrait trait) {
        if (!hasTrait(trait.id())) {
            traits.add(trait);
        }
    }

    public boolean hasTrait(String id) {
        for (BossTrait trait : traits) {
            if (trait.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public Component name() {
        return name;
    }

    public void rename(Component name, String plainName) {
        this.name = name;
        this.plainName = plainName;
        entity.customName(name);
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public void setPower(double power) {
        this.traitPower = power;
    }

    public @Nullable String nemesisId() {
        return nemesisId;
    }

    public void setNemesisId(@Nullable String nemesisId) {
        this.nemesisId = nemesisId;
    }

    public boolean isNemesis() {
        return nemesisId != null;
    }

    public boolean lastStand() {
        return lastStand;
    }

    public void setLastStand(boolean lastStand) {
        this.lastStand = lastStand;
    }

    /**
     * Stuck detection, called every ticker run: returns how many ticks the boss has barely moved while it had a
     * reason to move, or -1 when it is moving fine.
     */
    public int stuckTicks(int now, boolean wantsToMove) {
        org.bukkit.Location here = entity.getLocation();
        if (!wantsToMove) {
            stuckSince = -1;
            lastPosition = here;
            return -1;
        }
        if (lastPosition == null || !lastPosition.getWorld().equals(here.getWorld())
            || lastPosition.distanceSquared(here) > 1.0) {
            lastPosition = here;
            stuckSince = now;
            return 0;
        }
        if (stuckSince < 0) {
            stuckSince = now;
        }
        return now - stuckSince;
    }

    public void resetStuck() {
        stuckSince = -1;
        lastPosition = null;
    }

    public String plainName() {
        return plainName;
    }

    /** Rank (and mob-profile) damage multiplier applied to everything this boss hits. */
    public double damageMultiplier() {
        return damageMultiplier;
    }

    /** How strong traits are for this boss's rank (1.0 = base). */
    public double power() {
        return traitPower;
    }

    public double health() {
        return entity.getHealth();
    }

    public double maxHealth() {
        AttributeInstance inst = entity.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? Math.max(1, entity.getHealth()) : inst.getValue();
    }

    public double healthRatio() {
        return Math.max(0, Math.min(1, health() / maxHealth()));
    }

    public void heal(double amount) {
        if (amount <= 0 || entity.isDead()) {
            return;
        }
        entity.setHealth(Math.min(maxHealth(), entity.getHealth() + amount));
    }

    // ---------------- engagement (who is fighting this boss) ----------------

    public void engage(Player player, int now) {
        engaged.put(player.getUniqueId(), now);
    }

    public Map<UUID, Integer> engaged() {
        return engaged;
    }

    /** Removes players who left combat and returns them (used to detect players fleeing a boss). */
    public List<UUID> expireEngagements(int now, int timeoutTicks) {
        List<UUID> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Integer>> it = engaged.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (now - e.getValue() > timeoutTicks) {
                expired.add(e.getKey());
                it.remove();
            }
        }
        return expired;
    }

    // ---------------- per-player fight history (Nemesis escape + adaptation) ----------------

    private final Map<UUID, double[]> fightLog = new HashMap<>(); // [total damage, melee hits, ranged hits]
    private final Set<UUID> victims = new HashSet<>();

    public void recordPlayerHit(UUID player, double damage, boolean projectile) {
        double[] log = fightLog.computeIfAbsent(player, k -> new double[3]);
        log[0] += damage;
        log[projectile ? 2 : 1]++;
    }

    public double damageBy(UUID player) {
        double[] log = fightLog.get(player);
        return log == null ? 0 : log[0];
    }

    /** {melee hits, ranged hits} this player landed on the boss. */
    public int[] hitStyle(UUID player) {
        double[] log = fightLog.get(player);
        return log == null ? new int[2] : new int[]{(int) log[1], (int) log[2]};
    }

    public void forgetFight(UUID player) {
        fightLog.remove(player);
    }

    public void addVictim(UUID player) {
        victims.add(player);
    }

    public boolean killed(UUID player) {
        return victims.contains(player);
    }

    public boolean inCombat() {
        return !engaged.isEmpty();
    }

    /** Most recently engaged online player, if any. */
    public @Nullable Player lastEngagedPlayer() {
        UUID best = null;
        int bestTick = Integer.MIN_VALUE;
        for (Map.Entry<UUID, Integer> e : engaged.entrySet()) {
            if (e.getValue() > bestTick) {
                bestTick = e.getValue();
                best = e.getKey();
            }
        }
        return best == null ? null : Bukkit.getPlayer(best);
    }

    /**
     * The player this boss is fighting: its AI target if that is a player, otherwise the most recently
     * engaged player within 24 blocks. Creative/spectator players are ignored.
     */
    public @Nullable Player target() {
        if (entity instanceof Mob mob && mob.getTarget() instanceof Player p && fightable(p)) {
            return p;
        }
        Player last = lastEngagedPlayer();
        if (last != null && fightable(last) && last.getLocation().distanceSquared(entity.getLocation()) <= 24 * 24) {
            return last;
        }
        return null;
    }

    public List<Player> nearbyPlayers(double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : entity.getWorld().getPlayers()) {
            if (fightable(p) && p.getLocation().distanceSquared(entity.getLocation()) <= radius * radius) {
                out.add(p);
            }
        }
        return out;
    }

    public boolean fightable(Player p) {
        return p.isValid() && !p.isDead() && p.getWorld().equals(entity.getWorld())
            && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    // ---------------- per-trait state ----------------

    public boolean ready(String key, int now) {
        Integer until = cooldowns.get(key);
        return until == null || now >= until;
    }

    public void cooldown(String key, int now, int ticks) {
        cooldowns.put(key, now + ticks);
    }

    public int cooldownUntil(String key) {
        return cooldowns.getOrDefault(key, 0);
    }

    public boolean flag(String key) {
        return flags.contains(key);
    }

    public void setFlag(String key, boolean value) {
        if (value) {
            flags.add(key);
        } else {
            flags.remove(key);
        }
    }

    public void markHurt(int now) {
        lastHurtTick = now;
    }

    public int lastHurtTick() {
        return lastHurtTick;
    }

    public boolean undyingUsed() {
        return undyingUsed;
    }

    public void setUndyingUsed(boolean undyingUsed) {
        this.undyingUsed = undyingUsed;
    }

    public Set<UUID> minions() {
        return minions;
    }

    // ---------------- presentation ----------------

    public @Nullable BossBar bar() {
        return bar;
    }

    public void bar(@Nullable BossBar bar) {
        this.bar = bar;
    }

    public Set<UUID> barViewers() {
        return barViewers;
    }

    /** True the first time a given player engages; used for the one-time encounter cue. */
    public boolean revealTo(Player player) {
        return revealedTo.add(player.getUniqueId());
    }

    /** True the first time anyone engages; used for the one-time burst of particles. */
    public boolean revealOnce() {
        if (revealed) {
            return false;
        }
        revealed = true;
        return true;
    }

    public List<String> traitIds() {
        List<String> ids = new ArrayList<>();
        for (BossTrait t : traits) {
            ids.add(t.id());
        }
        return Collections.unmodifiableList(ids);
    }
}
