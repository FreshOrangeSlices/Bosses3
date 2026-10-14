package com.additionalbosses.nemesis;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossArmor;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.config.Messages;
import com.additionalbosses.trait.BossTrait;
import com.additionalbosses.util.Clock;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.SafeSpots;
import com.additionalbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Nemesis system. A boss that kills a player (or that a player flees from after a real fight) becomes that
 * player's Nemesis: it withdraws, remembers everything, grows stronger, earns titles and counter-traits, and comes
 * back for its prey after a few Minecraft days. It ignores the normal boss cap and never despawns while active.
 */
public final class NemesisManager {

    private static final long DAY = 24000L;

    private final AdditionalBosses plugin;
    private final Map<String, NemesisRecord> records = new LinkedHashMap<>();
    private final File file;
    private @Nullable BukkitTask returnTask;

    public NemesisManager(AdditionalBosses plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nemesis.yml");
    }

    private FeatureSettings f() {
        return plugin.settings().features;
    }

    private Messages messages() {
        return plugin.settings().messages;
    }

    /** "Minecraft time" used for return timers (see {@link Clock}). */
    public long clock() {
        return Clock.now();
    }

    // =====================================================================
    // Storage
    // =====================================================================

    public void load() {
        records.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Clock.load(yaml.getLong("clock-offset", 0L));
        ConfigurationSection root = yaml.getConfigurationSection("nemeses");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                NemesisRecord r = s == null ? null : NemesisRecord.load(id, s);
                if (r != null) {
                    records.put(id, r);
                } else {
                    plugin.getLogger().warning("Skipping unreadable Nemesis entry " + id + " in nemesis.yml");
                }
            }
        }
        boolean named = false;
        for (NemesisRecord r : records.values()) {
            if (r.surrenderPicked) {
                named = true; // terms of surrender for a Nemesis from an older version: keep them
            }
            if (r.level > f().nemesisMaxLevel) {
                r.level = f().nemesisMaxLevel; // the level cap came down (50 -> 15)
                updateTitles(r);
                named = true;
            }
            if (r.name.isBlank()) {
                r.name = NemesisNames.pick(r.type, takenNames());
                named = true;
            }
            if (r.epithet.isBlank()) {
                BossTrait first = r.traits.isEmpty() ? null : plugin.traits().get(r.traits.get(0));
                r.epithet = first != null ? first.adjective() : plugin.settings().rank(r.baseRank).name();
                named = true;
            }
        }
        if (named) {
            save(); // Nemeses from an older version get their own names
        }
        if (returnTask == null) {
            returnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkReturns, 200L, 100L);
        }
    }

    private Set<String> takenNames() {
        Set<String> taken = new HashSet<>();
        for (NemesisRecord r : records.values()) {
            if (!r.name.isBlank()) {
                taken.add(r.name.toLowerCase(Locale.ROOT));
            }
        }
        return taken;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("clock-offset", Clock.offset());
        for (NemesisRecord r : records.values()) {
            r.save(yaml.createSection("nemeses." + r.id));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save nemesis.yml: " + ex.getMessage());
        }
    }

    /** On shutdown: active Nemeses aren't saved in the world, so they will return a little later instead. */
    public void shutdown() {
        for (NemesisRecord r : records.values()) {
            if (r.entity != null) {
                org.bukkit.entity.Entity living = Bukkit.getEntity(r.entity);
                if (living != null) {
                    living.remove();
                }
                r.entity = null;
                r.returnAt = Math.max(r.returnAt, clock() + DAY / 4);
            }
        }
        save();
        if (returnTask != null) {
            returnTask.cancel();
            returnTask = null;
        }
    }

    public int count() {
        return records.size();
    }

    public List<NemesisRecord> all() {
        return List.copyOf(records.values());
    }

    public List<NemesisRecord> forOwner(UUID owner) {
        List<NemesisRecord> out = new ArrayList<>();
        for (NemesisRecord r : records.values()) {
            if (r.owner.equals(owner)) {
                out.add(r);
            }
        }
        return out;
    }

    public @Nullable NemesisRecord get(@Nullable String id) {
        return id == null ? null : records.get(id);
    }

    public int clear(UUID owner) {
        int n = 0;
        for (NemesisRecord r : forOwner(owner)) {
            Boss active = activeBoss(r);
            if (active != null) {
                plugin.bosses().unregister(active);
                active.entity().remove();
            }
            records.remove(r.id);
            n++;
        }
        save();
        return n;
    }

    /** Makes the owner's Nemeses due to return right away (admin/testing). */
    public int summonNow(UUID owner) {
        int n = 0;
        for (NemesisRecord r : forOwner(owner)) {
            if (r.entity == null) {
                r.returnAt = 0;
                n++;
            }
        }
        checkReturns();
        return n;
    }

    // =====================================================================
    // Becoming a Nemesis / growing
    // =====================================================================

    /**
     * Called when a boss kills a player ({@code kills}: players it has killed this outing). Returns true if the
     * Nemesis system took care of the boss (it is a Nemesis, or just became one), so the two-kills rule is handled here.
     */
    public boolean onKilledPlayer(Boss boss, Player victim, int kills, List<ItemStack> drops) {
        FeatureSettings f = f();
        if (!f.nemesisEnabled) {
            return false;
        }
        NemesisRecord r = get(boss.nemesisId());
        if (r != null) {
            boolean owner = r.owner.equals(victim.getUniqueId());
            if (owner) {
                r.kills++;
                r.ownerDownTick = Bukkit.getCurrentTick();
                absorbFight(r, boss, victim.getUniqueId());
                drops.removeIf(item -> r.id.equals(challengeId(item))); // its note comes back fresh on respawn
                challengeOnRespawn(victim.getUniqueId(), r);
            }
            r.focus = null;
            if (grow(r, f.nemesisLevelsOnKill, boss.entity())) { // any player it kills makes it stronger
                adapt(boss, r);
            }
            refreshBody(boss, r);
            int limit = plugin.settings().despawnAfterKills;
            if (limit > 0 && kills >= limit) {
                // Two players down: it leaves, and comes back after the full wait.
                r.returnAt = clock() + Math.round(f.nemesisReturnDays * DAY);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (records.get(r.id) == r && activeBoss(r) == boss && boss.entity().isValid()
                        && !boss.entity().isDead()) {
                        vanish(boss, r);
                    }
                });
            }
            save();
            if (owner) {
                plugin.presentation().tell(victim, "nemesis-grows", Placeholder.component("boss", displayName(r)));
            }
            return true;
        }
        if (forOwner(victim.getUniqueId()).size() >= f.nemesisMaxPerPlayer || !Rng.chance(f.nemesisKillChance)) {
            return false;
        }
        NemesisRecord created = create(boss, victim);
        created.kills = 1;
        updateTitles(created);
        configure(boss, created, false);
        retreat(boss, created);
        challengeOnRespawn(victim.getUniqueId(), created);
        plugin.presentation().tell(victim, "nemesis-born", Placeholder.component("boss", displayName(created)));
        return true;
    }

    /**
     * Called when a player stops fighting a boss (their engagement expired). Returns true if the boss withdrew
     * (as a new or existing Nemesis), so the caller stops handling it.
     */
    public boolean onPlayerFled(Boss boss, UUID playerId) {
        FeatureSettings f = f();
        Player p = Bukkit.getPlayer(playerId);
        boolean reallyGone = p == null || !p.getWorld().equals(boss.entity().getWorld())
            || p.getLocation().distanceSquared(boss.entity().getLocation()) > 24 * 24;
        if (!f.nemesisEnabled || boss.isNemesis() || !reallyGone || (p != null && p.isDead())) {
            boss.forgetFight(playerId);
            return false;
        }
        boolean qualifies = boss.rank().atLeast(f.nemesisEscapeMinRank)
            && boss.damageBy(playerId) >= boss.maxHealth() * f.nemesisEscapeDamagePercent / 100.0
            && forOwner(playerId).size() < f.nemesisMaxPerPlayer
            && Rng.chance(f.nemesisEscapeChance);
        if (!qualifies) {
            boss.forgetFight(playerId);
            return false;
        }
        String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(playerId).getName();
        NemesisRecord created = create(boss, playerId, name == null ? "?" : name);
        created.escapes = 1;
        updateTitles(created);
        configure(boss, created, false);
        retreat(boss, created);
        if (p != null) {
            plugin.presentation().tell(p, "nemesis-born", Placeholder.component("boss", displayName(created)));
        }
        return true;
    }

    /**
     * Runs every boss tick for an active Nemesis: keeps it hunting its owner, and withdraws it (one level stronger)
     * if the owner logs off, changes world, or stays far away. Returns true if it withdrew.
     */
    public boolean tickActive(Boss boss) {
        NemesisRecord r = get(boss.nemesisId());
        if (r == null) {
            return false;
        }
        if (r.prowlUntil > 0) {
            return prowl(boss, r);
        }
        Player owner = Bukkit.getPlayer(r.owner);
        LivingEntity e = boss.entity();
        // right after killing its owner it only stays if they come back close; otherwise it would loiter
        double reach = Bukkit.getCurrentTick() - r.ownerDownTick < 2400 ? 40 : f().nemesisEscapeDistance;
        boolean inReach = owner != null && !owner.isDead() && owner.getWorld().equals(e.getWorld())
            && owner.getLocation().distanceSquared(e.getLocation()) <= reach * reach;
        Player focus = focus(boss, r, owner);
        if (inReach || focus != null) {
            r.lostTicks = 0;
            Player chase = focus != null ? focus : owner;
            if (e instanceof Mob mob && chase != null && boss.fightable(chase) && mob.getTarget() != chase) {
                mob.setTarget(chase);
            }
            return false;
        }
        r.lostTicks += 10;
        if (owner != null && r.lostTicks < 300) {
            return false; // 15 seconds of grace
        }
        if (Bukkit.getCurrentTick() - r.ownerDownTick < 2400) {
            // it killed its owner a moment ago and they woke up far away: that isn't fleeing
            return retreat(boss, r);
        }
        r.escapes++;
        absorbFight(r, boss, r.owner);
        if (grow(r, f().nemesisLevelsOnEscape, e)) {
            adapt(boss, r);
        }
        refreshBody(boss, r);
        boolean removed = retreat(boss, r);
        if (owner != null) {
            plugin.presentation().tell(owner, "nemesis-flee", Placeholder.component("boss", displayName(r)));
        }
        return removed;
    }

    /**
     * Team fights: it goes after its owner, unless another player hurts it a lot more than whoever it's after
     * (over the last 10 seconds, by aggro-switch-percent of its health): then it turns on them. Returns the player it
     * is after if that isn't simply its owner, or null.
     */
    private @Nullable Player focus(Boss boss, NemesisRecord r, @Nullable Player owner) {
        LivingEntity e = boss.entity();
        int now = Bukkit.getCurrentTick();
        Player current = r.focus == null ? owner : Bukkit.getPlayer(r.focus);
        if (r.focus != null && current == null) {
            current = owner; // the one it switched to logged off: back to its owner
            r.focus = null;
        }
        if (current != null && (!boss.fightable(current) || current.getLocation().distanceSquared(e.getLocation()) > 40 * 40)) {
            current = owner;
            r.focus = null;
        }
        UUID top = boss.topRecentDamager(now);
        if (top != null && (current == null || !top.equals(current.getUniqueId()))) {
            double lead = boss.recentDamage(top, now) - (current == null ? 0 : boss.recentDamage(current.getUniqueId(), now));
            Player challenger = Bukkit.getPlayer(top);
            if (challenger != null && boss.fightable(challenger)
                && lead >= boss.maxHealth() * f().nemesisAggroSwitchPercent / 100.0) {
                r.focus = top;
                current = challenger;
                Fx.play(e.getLocation(), "entity.ravager.roar", 0.8f, 1.2f);
            }
        }
        return current != null && owner != null && current.getUniqueId().equals(owner.getUniqueId()) ? null : current;
    }

    // =====================================================================
    // Prowling: after it lets its prey go, it roams for a while and feeds on other mobs
    // =====================================================================

    public boolean isProwling(Boss boss) {
        NemesisRecord r = get(boss.nemesisId());
        return r != null && r.prowlUntil > 0;
    }

    /** Mobs driven by "brain" AI (piglins, hoglins, breezes...) can't be steered, so they don't prowl. */
    private static final Set<EntityType> BRAIN_AI = Set.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.BREEZE, EntityType.WARDEN, EntityType.CREAKING);

    /** Leaves the fight: prowls nearby for a few minutes (if enabled), then vanishes until its return. */
    private boolean retreat(Boss boss, NemesisRecord r) {
        r.lostTicks = 0;
        r.returnAt = clock() + Math.round(f().nemesisReturnDays * DAY);
        LivingEntity e = boss.entity();
        int prowlTicks = (int) Math.round(f().nemesisProwlMinutes * 1200);
        if (prowlTicks <= 0 || !e.isValid() || e.isDead() || BRAIN_AI.contains(e.getType())) {
            vanish(boss, r);
            return true;
        }
        r.prowlUntil = Bukkit.getCurrentTick() + prowlTicks;
        r.provoker = null;
        boss.engaged().clear();
        if (e instanceof Mob mob) {
            mob.setTarget(null);
        }
        plugin.bossBars().refreshViewers(boss);
        Fx.particle(Fx.center(e), Particle.LARGE_SMOKE, 30, 0.6, 0.05);
        Fx.play(e.getLocation(), "entity.wither.ambient", 0.6f, 0.6f);
        save();
        return false;
    }

    /** Runs every boss tick while prowling: hunt nearby mobs, ignore players unless provoked. */
    private boolean prowl(Boss boss, NemesisRecord r) {
        LivingEntity e = boss.entity();
        if (Bukkit.getCurrentTick() >= r.prowlUntil) {
            vanish(boss, r);
            return true;
        }
        if (r.provoker != null) {
            Player p = Bukkit.getPlayer(r.provoker);
            if (p != null && boss.fightable(p) && p.getLocation().distanceSquared(e.getLocation()) < 24 * 24) {
                if (e instanceof Mob mob && mob.getTarget() != p) {
                    mob.setTarget(p);
                }
                return false; // busy fighting whoever attacked it
            }
            r.provoker = null;
            boss.engaged().clear();
        }
        if (e instanceof Mob mob && !(e instanceof org.bukkit.entity.Creeper)
            && r.outingKills < f().nemesisKillLevelsPerOuting) {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isValid() || target.isDead() || target instanceof Player) {
                LivingEntity prey = findPrey(e);
                if (prey != null) {
                    mob.setTarget(prey);
                }
            }
        }
        if (Rng.chance(20)) {
            Fx.particle(Fx.center(e), Particle.SMOKE, 6, 0.4, 0.01);
        }
        return false;
    }

    private static @Nullable LivingEntity findPrey(LivingEntity hunter) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity near : hunter.getNearbyEntities(16, 8, 16)) {
            if (!(near instanceof LivingEntity prey) || near instanceof Player || near instanceof org.bukkit.entity.ArmorStand
                || !(near instanceof org.bukkit.entity.Animals || near instanceof org.bukkit.entity.Enemy)
                || com.additionalbosses.boss.BossManager.isBoss(near) || com.additionalbosses.boss.BossManager.isMinion(near)
                || com.additionalbosses.boss.BossManager.isPet(near)
                || near.getPersistentDataContainer().has(Keys.STATUE, PersistentDataType.STRING)
                || (near instanceof org.bukkit.entity.Tameable t && t.isTamed()) || near.isInvulnerable()
                || near instanceof org.bukkit.entity.Boss || near instanceof org.bukkit.entity.Warden
                || near instanceof org.bukkit.entity.Ravager || near instanceof org.bukkit.entity.ElderGuardian) {
                continue;
            }
            double d = near.getLocation().distanceSquared(hunter.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = prey;
            }
        }
        return best;
    }

    /** A player attacked a prowling Nemesis: it turns on them. */
    public void onProvoked(Boss boss, Player attacker) {
        NemesisRecord r = get(boss.nemesisId());
        if (r == null || r.prowlUntil <= 0) {
            return;
        }
        if (attacker.getUniqueId().equals(r.owner)) {
            r.prowlUntil = 0; // its prey came back for more: the hunt is on again
            r.provoker = null;
        } else if (attacker.getUniqueId().equals(r.provoker)) {
            return;
        } else {
            r.provoker = attacker.getUniqueId(); // someone else: it fights them off, but keeps prowling
        }
        if (boss.entity() instanceof Mob mob) {
            mob.setTarget(attacker);
        }
        plugin.presentation().tell(attacker, "nemesis-provoked", Placeholder.component("boss", displayName(r)));
    }

    /** While prowling it only targets players who attacked it. */
    public boolean mayTarget(Boss boss, Player player) {
        NemesisRecord r = get(boss.nemesisId());
        return r == null || r.prowlUntil <= 0 || player.getUniqueId().equals(r.provoker);
    }

    /** Every mob a Nemesis kills makes it one level stronger (a few per outing). */
    public void onPreyKilled(Boss boss, LivingEntity prey) {
        NemesisRecord r = get(boss.nemesisId());
        if (r == null || r.outingKills >= f().nemesisKillLevelsPerOuting || r.level >= f().nemesisMaxLevel) {
            return;
        }
        r.outingKills++;
        grow(r, 1, boss.entity());
        refreshBody(boss, r);
        save();
        LivingEntity e = boss.entity();
        Fx.particle(Fx.center(e), Particle.SOUL, 12, 0.4, 0.03);
        Fx.play(e.getLocation(), "entity.player.levelup", 0.6f, 0.5f);
    }

    /** Re-applies level scaling, gear and name to the living Nemesis after it levels up (keeps its health %). */
    private void refreshBody(Boss boss, NemesisRecord r) {
        LivingEntity e = boss.entity();
        if (!e.isValid() || e.isDead()) {
            return;
        }
        double ratio = boss.healthRatio();
        applyBody(boss, r);
        e.setHealth(Math.max(1, Math.min(boss.maxHealth(), boss.maxHealth() * ratio)));
        applyRuntime(boss, r);
    }

    private NemesisRecord create(Boss boss, Player owner) {
        return create(boss, owner.getUniqueId(), owner.getName());
    }

    private NemesisRecord create(Boss boss, UUID owner, String ownerName) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        NemesisRecord r = new NemesisRecord(id, owner, ownerName, boss.entity().getType(), boss.rank());
        r.traits = new ArrayList<>(boss.traitIds());
        r.baseTraitCount = r.traits.size();
        r.createdAt = clock();
        r.wildRank = com.additionalbosses.boss.BossManager.wildRank(boss.entity(), boss.rank());
        r.name = NemesisNames.pick(r.type, takenNames());
        r.surrender = Surrender.pick(r.type);
        com.additionalbosses.trait.Synergies.Synergy synergy = com.additionalbosses.trait.Synergies.find(boss.traits());
        r.epithet = synergy != null ? synergy.title()
            : !boss.traits().isEmpty() ? boss.traits().get(0).adjective() : plugin.settings().rank(boss.rank()).name();
        LivingEntity e = boss.entity();
        r.baby = e instanceof org.bukkit.entity.Ageable a && !a.isAdult();
        r.size = e instanceof org.bukkit.entity.Slime slime ? slime.getSize() : -1;
        org.bukkit.inventory.EntityEquipment eq = e.getEquipment();
        r.hand = eq == null || eq.getItemInMainHand().isEmpty() ? "" : eq.getItemInMainHand().getType().name();
        r.gearFloor = BossArmor.wornStage(e);
        absorbFight(r, boss, owner);
        records.put(id, r);
        return r;
    }

    /**
     * The rank of Souls (and salvage) a Nemesis pays: its own, unless it was raised with Souls before it became a
     * Nemesis; then the rank it was found at, so Souls spent never come back multiplied.
     */
    public BossRank soulRank(Boss boss) {
        NemesisRecord r = get(boss.nemesisId());
        if (r != null && r.wildRank != null && r.baseRank != null && r.wildRank.ordinal() < r.baseRank.ordinal()) {
            return r.wildRank;
        }
        return boss.rank();
    }

    /** Remembers how the owner fought (melee or ranged) so the Nemesis can adapt. */
    private void absorbFight(NemesisRecord r, Boss boss, UUID owner) {
        int[] style = boss.hitStyle(owner);
        r.meleeHits += style[0];
        r.rangedHits += style[1];
        boss.forgetFight(owner);
    }

    /** Returns true if it actually gained a level (not already at the cap). */
    private boolean grow(NemesisRecord r, int levels, LivingEntity sample) {
        FeatureSettings f = f();
        int before = r.level;
        r.level = Math.min(f.nemesisMaxLevel, r.level + levels);
        // It climbs a rank every few levels (up to Legendary), but never drops below the rank it started at.
        int climbed = Math.min(BossRank.GOLD.ordinal(), r.baseRank.ordinal() + (r.level - 1) / f.nemesisLevelsPerRank);
        r.rank = BossRank.values()[Math.max(r.baseRank.ordinal(), climbed)];
        int wanted = Math.min(f.nemesisMaxTraits, r.baseTraitCount + (r.level - 1) / f.nemesisLevelsPerTrait);
        int guard = 0;
        while (r.traits.size() < wanted && guard++ < 10) {
            BossTrait next = counterTrait(r, sample);
            if (next == null) {
                next = plugin.traits().rollExtra(sample, resolve(r, sample));
            }
            if (next == null) {
                break;
            }
            r.traits.add(next.id());
        }
        updateTitles(r);
        return r.level > before;
    }

    /**
     * New traits it picks when it can: runners get chased down (by leaps and charges: bosses don't simply outrun
     * you), and a killer turns executioner. How you fight is handled by {@link #adapt}.
     */
    private @Nullable BossTrait counterTrait(NemesisRecord r, LivingEntity sample) {
        List<String> wishes = new ArrayList<>();
        if (r.escapes > r.kills) {
            wishes.addAll(List.of("leaping", "charging"));
        }
        if (r.kills >= 2) {
            wishes.add("executioner");
        }
        List<BossTrait> have = resolve(r, sample);
        for (String id : wishes) {
            BossTrait t = plugin.traits().get(id);
            if (t == null || r.traits.contains(id) || !plugin.traits().isEnabled(t) || !t.supports(sample)) {
                continue;
            }
            boolean ok = true;
            for (BossTrait h : have) {
                if (!plugin.traits().compatible(h, t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return t;
            }
        }
        return null;
    }

    private List<BossTrait> resolve(NemesisRecord r, LivingEntity sample) {
        List<BossTrait> out = new ArrayList<>();
        for (String id : r.traits) {
            BossTrait t = plugin.traits().get(id);
            if (t != null && t.supports(sample)) {
                out.add(t);
            }
        }
        return out;
    }

    /** History titles. The newest earned title is the one shown in the name. */
    private void updateTitles(NemesisRecord r) {
        addTitle(r, r.returns >= 1, "Returned");
        addTitle(r, r.escapes >= 2, "Twice-Fled");
        addTitle(r, r.kills >= 2, "Relentless");
        addTitle(r, r.level >= 5, "Unbroken");
        addTitle(r, r.kills >= 5, "Butcher");
        addTitle(r, r.level >= 10, "Dreaded");
        addTitle(r, r.level >= Math.min(15, f().nemesisMaxLevel), "Eternal");
    }

    private static void addTitle(NemesisRecord r, boolean earned, String title) {
        if (earned && !r.titles.contains(title)) {
            r.titles.add(title);
        }
    }

    /**
     * "☆ Returned Scrawl the Bulwark [Lv 7]": one white star (not rank stars, so it reads as its own category),
     * its history title, its own name, and what it was known for as a boss.
     */
    public Component displayName(NemesisRecord r) {
        return Component.text("☆ ", NamedTextColor.WHITE)
            .append(Component.text(r.title() + " ", NamedTextColor.GRAY))
            .append(Component.text(r.name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
            .append(Component.text(" the " + r.epithet, NamedTextColor.WHITE))
            .append(Component.text(" [Lv " + r.level + "]", NamedTextColor.DARK_GRAY));
    }

    public String plainName(NemesisRecord r) {
        return r.title() + " " + r.name + " the " + r.epithet;
    }

    /** Removes the Nemesis from the world; it comes back when its return time comes. */
    private void vanish(Boss boss, NemesisRecord r) {
        LivingEntity e = boss.entity();
        r.entity = null;
        r.lostTicks = 0;
        r.prowlUntil = 0;
        if (r.returnAt <= clock()) {
            r.returnAt = clock() + Math.round(f().nemesisReturnDays * DAY);
        }
        Fx.particle(Fx.center(e), Particle.LARGE_SMOKE, 40, 0.6, 0.05);
        Fx.play(e.getLocation(), "entity.wither.ambient", 0.6f, 0.6f);
        plugin.bosses().removeMinions(boss);
        plugin.bosses().unregister(boss);
        e.remove();
        save();
    }

    // =====================================================================
    // Returning
    // =====================================================================

    private void checkReturns() {
        long now = clock(); // also keeps the day counter in step with sleeping
        if (!f().nemesisEnabled || records.isEmpty()) {
            return;
        }
        long maxWait = Math.round(f().nemesisReturnDays * DAY);
        boolean dirty = false;
        for (NemesisRecord r : List.copyOf(records.values())) {
            if (r.entity != null && activeBoss(r) == null) {
                // Its chunk unloaded or it was removed some other way: it slinks off and returns a bit later.
                r.entity = null;
                r.prowlUntil = 0;
                r.returnAt = Math.max(r.returnAt, now + DAY / 8);
                dirty = true;
            }
            if (r.returnAt - now > maxWait) {
                r.returnAt = now + maxWait; // the clock was turned back (/time set)
                dirty = true;
            }
            if (r.entity != null || now < r.returnAt) {
                continue;
            }
            Player owner = Bukkit.getPlayer(r.owner);
            if (owner == null || owner.isDead()
                || (owner.getGameMode() != GameMode.SURVIVAL && owner.getGameMode() != GameMode.ADVENTURE)
                || owner.getWorld().getEnvironment() == World.Environment.THE_END
                || owner.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL
                || !plugin.settings().worldAllowed(owner.getWorld())) {
                continue;
            }
            spawnFor(r, owner);
        }
        if (dirty) {
            save();
        }
    }

    private void spawnFor(NemesisRecord r, Player owner) {
        spawnFor(r, owner, 14, 22, false);
    }

    /** Brings the Nemesis to its owner, between {@code min} and {@code max} blocks away. False if it couldn't. */
    private boolean spawnFor(NemesisRecord r, Player owner, int min, int max, boolean challenged) {
        Location spot = SafeSpots.around(owner.getLocation(), min, max, 16);
        if (spot == null) {
            return false; // try again on the next check
        }
        List<BossTrait> traits = new ArrayList<>();
        for (String id : r.traits) {
            BossTrait t = plugin.traits().get(id);
            if (t != null && plugin.traits().isEnabled(t)) {
                traits.add(t);
            }
        }
        // turned-off traits (Swift) leave its memory, so its next level-ups can learn something else
        r.traits.removeIf(id -> plugin.traits().get(id) == null || !plugin.traits().isEnabled(plugin.traits().get(id)));
        r.baseTraitCount = Math.min(r.baseTraitCount, r.traits.size());
        Class<?> cls = r.type.getEntityClass();
        if (cls == null || !Mob.class.isAssignableFrom(cls) || com.additionalbosses.config.PluginSettings.isMount(r.type)) {
            records.remove(r.id); // that mob can't exist any more
            save();
            return false;
        }
        Boss boss = plugin.bosses().summon(r.type, spot, r.rank, traits, false, "nemesis");
        if (boss == null) {
            return false; // the spawn was blocked here; try again on the next check
        }
        if (challenged) {
            com.additionalbosses.boss.BossManager.markCreated(boss, "challenge"); // its Souls, but no Totem
        }
        r.recentDamage.clear();
        r.focus = null;
        r.returns++;
        r.outingKills = 0;
        r.prowlUntil = 0;
        updateTitles(r);
        configure(boss, r, true);
        plugin.bossBars().updateHealth(boss, boss.health());
        save();

        Component name = displayName(r);
        owner.showTitle(Title.title(messages().get("nemesis-return-title"),
            messages().get("nemesis-return-subtitle", Placeholder.component("boss", name)),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        if (plugin.settings().bossChat) {
            owner.sendMessage(messages().prefixed("nemesis-return-subtitle", Placeholder.component("boss", name)));
        }
        Fx.playTo(owner, Fx.sound("entity.wither.spawn", 0.7f, 0.8f));
        plugin.bosses().engage(boss, owner);
        return true;
    }

    /** Applies everything a Nemesis is: its body (same as last time), level scaling, gear, name and persistence. */
    private void configure(Boss boss, NemesisRecord r, boolean fullHealth) {
        LivingEntity e = boss.entity();
        double ratio = boss.healthRatio();
        e.getPersistentDataContainer().set(Keys.NEMESIS, PersistentDataType.STRING, r.id);
        if (e instanceof org.bukkit.entity.Ageable a) {
            if (r.baby) {
                a.setBaby();
            } else {
                a.setAdult();
            }
        }
        if (e instanceof org.bukkit.entity.Slime slime && r.size > 0) {
            slime.setSize(r.size);
        }
        if (e instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true); // it would turn into an ordinary mob in the Overworld
        }
        if (e instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        applyBody(boss, r);
        // A returning Nemesis arrives at full strength; a boss that just became one keeps its wounds.
        e.setHealth(Math.max(1, fullHealth ? boss.maxHealth() : Math.min(boss.maxHealth(), boss.maxHealth() * ratio)));
        applyRuntime(boss, r);
    }

    /** Level-based health, size and gear. */
    private void applyBody(Boss boss, NemesisRecord r) {
        FeatureSettings f = f();
        LivingEntity e = boss.entity();
        int level = r.level;
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(Keys.MOD_NEMESIS_HEALTH);
            hp.addModifier(new AttributeModifier(Keys.MOD_NEMESIS_HEALTH, level * f.nemesisHealthPerLevel / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        AttributeInstance size = e.getAttribute(Attribute.SCALE);
        if (size != null) {
            size.removeModifier(Keys.MOD_NEMESIS_SIZE);
            size.addModifier(new AttributeModifier(Keys.MOD_NEMESIS_SIZE,
                Math.min(f.nemesisMaxExtraSize, level * f.nemesisSizePerLevel), AttributeModifier.Operation.ADD_NUMBER));
        }
        if (f.nemesisGearEvolution) {
            // Gear upgrades with level, starting from the tier that fits its rank (a Legendary starts in netherite).
            BossArmor.applyNemesis(f, e, level, r.id, r.rank, r.hand, r.gearFloor);
        } else if (!r.hand.isEmpty() && e.getEquipment() != null) {
            org.bukkit.Material held = org.bukkit.Material.matchMaterial(r.hand);
            if (held != null && held.isItem()) {
                e.getEquipment().setItemInMainHand(ItemStack.of(held));
                e.getEquipment().setItemInMainHandDropChance(0f);
            }
        }
    }

    /** The in-memory side of a Nemesis (damage, trait power, name, bar). Its body is saved on the mob itself. */
    private void applyRuntime(Boss boss, NemesisRecord r) {
        FeatureSettings f = f();
        LivingEntity e = boss.entity();
        r.entity = e.getUniqueId();
        r.lostTicks = 0;
        boss.setNemesisId(r.id);
        e.setPersistent(false);          // never saved into the world; the record brings it back
        e.setRemoveWhenFarAway(false);   // never despawns while out
        boss.setNemesisScaling(1.0 + r.level * f.nemesisDamagePerLevel / 100.0,
            1.0 + r.level * f.nemesisPowerPerLevel / 100.0, f.nemesisMaxDamageMultiplier);
        boss.rename(displayName(r), plainName(r));
        e.setCustomNameVisible(plugin.settings().alwaysShowName);
        BossBar bar = boss.bar();
        if (bar != null) {
            bar.color(BossBar.Color.PINK);
            bar.overlay(BossBar.Overlay.NOTCHED_20);
        }
        plugin.bossBars().retitle(boss);
    }

    /** True if a Nemesis mob that just appeared (e.g. through a portal) is the real one and should stay. */
    public boolean canReattach(String id, LivingEntity entity) {
        NemesisRecord r = records.get(id);
        if (r == null || !f().nemesisEnabled) {
            return false;
        }
        Boss other = activeBoss(r);
        return other == null || other.uuid().equals(entity.getUniqueId());
    }

    public void reattach(Boss boss, String id) {
        NemesisRecord r = records.get(id);
        if (r != null) {
            applyRuntime(boss, r);
        }
    }

    private @Nullable Boss activeBoss(NemesisRecord r) {
        return r.entity == null ? null : plugin.bosses().get(Bukkit.getEntity(r.entity));
    }

    // =====================================================================
    // Adaptation: it learns how you fight it
    // =====================================================================

    /** Damage it took, sorted by kind, so it can adapt at its next level-up. */
    public void recordDamage(Boss boss, org.bukkit.event.entity.EntityDamageEvent event, @Nullable LivingEntity attacker,
                             double damage) {
        NemesisRecord r = get(boss.nemesisId());
        DamageKind kind = DamageKind.of(event, attacker);
        if (r == null || kind == null || damage <= 0 || !DamageKind.learnsFrom(attacker)) {
            return;
        }
        r.recentDamage.merge(kind, damage, Double::sum);
    }

    /**
     * After a level-up from a fight: it adapts to the kind of damage that hurt it most since it last adapted. The
     * first time it resists that kind; if you keep at it, it becomes immune (at most max-immunities; fire is free).
     * Pets: it resists them, and hits them harder, but is never fully immune.
     */
    private void adapt(Boss boss, NemesisRecord r) {
        FeatureSettings f = f();
        if (!f.nemesisAdaptation || r.recentDamage.isEmpty()) {
            return;
        }
        List<Map.Entry<DamageKind, Double>> order = new ArrayList<>(r.recentDamage.entrySet());
        order.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double meaningful = Math.max(4, boss.maxHealth() * 0.02); // a stray scratch doesn't teach it anything
        for (Map.Entry<DamageKind, Double> entry : order) {
            if (entry.getValue() < meaningful) {
                break;
            }
            DamageKind kind = entry.getKey();
            int now = r.adapted.getOrDefault(kind, 0);
            int next = now + 1;
            if (next > 2 || (kind == DamageKind.PET && next > 1)
                || (next == 2 && kind != DamageKind.FIRE && r.immunities() >= f.nemesisMaxImmunities)) {
                continue; // can't learn more against this one: try the next
            }
            r.adapted.put(kind, next);
            r.recentDamage.clear();
            adaptedFx(boss.entity(), next == 2);
            return;
        }
    }

    /** A smoky pulse when it adapts, so sharp-eyed players notice that something changed. */
    private static void adaptedFx(LivingEntity e, boolean immune) {
        Location at = Fx.center(e);
        Fx.particle(at, Particle.LARGE_SMOKE, immune ? 60 : 35, 0.9, 0.04);
        Fx.particle(at, immune ? Particle.SCULK_SOUL : Particle.SMOKE, 30, 1.2, 0.03);
        Fx.play(e.getLocation(), immune ? "block.respawn_anchor.set_spawn" : "block.respawn_anchor.charge", 1.0f, 0.6f);
    }

    /** What it has adapted to hurts it less, or not at all. */
    public void onHurt(Boss boss, org.bukkit.event.entity.EntityDamageEvent event, com.additionalbosses.combat.DamageContext ctx) {
        NemesisRecord r = get(boss.nemesisId());
        if (r == null || r.adapted.isEmpty() || !f().nemesisAdaptation) {
            return;
        }
        DamageKind kind = DamageKind.of(event, ctx.attacker());
        int level = kind == null ? 0 : r.adapted.getOrDefault(kind, 0);
        if (level <= 0) {
            return;
        }
        LivingEntity e = boss.entity();
        if (kind == DamageKind.PET) {
            ctx.multiply(f().nemesisPetDamageTakenPercent / 100.0);
        } else if (level >= 2) {
            ctx.cancel(); // immune: the blow just slides off
            if (kind == DamageKind.FIRE) {
                e.setFireTicks(0);
            }
            if (Rng.chance(50)) {
                Fx.particle(Fx.center(e), Particle.SMOKE, 12, 0.5, 0.02);
                Fx.play(e.getLocation(), "item.shield.block", 0.6f, 0.6f);
            }
            return;
        } else {
            ctx.multiply(f().nemesisResistedPercent / 100.0);
        }
        if (Rng.chance(25)) {
            Fx.particle(Fx.center(e), Particle.SMOKE, 6, 0.4, 0.02);
        }
    }

    /** Having adapted to pets, it hits them harder too. */
    public void onHitPet(Boss boss, com.additionalbosses.combat.DamageContext ctx) {
        NemesisRecord r = get(boss.nemesisId());
        if (r != null && f().nemesisAdaptation && r.adapted.getOrDefault(DamageKind.PET, 0) > 0) {
            ctx.multiply(1 + f().nemesisPetDamageBonus / 100.0);
        }
    }

    // =====================================================================
    // Its Challenge: call it back now, or surrender
    // =====================================================================

    /** When a boss (or its minion) last hurt each player: a death it set up is never a surrender. */
    private final Map<UUID, Integer> lastBossHit = new java.util.HashMap<>();

    public void markBossHit(Player p) {
        lastBossHit.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    /** It killed its owner: they wake up with its Challenge (unless they still carry it). Saved, so a restart keeps it. */
    private void challengeOnRespawn(UUID owner, NemesisRecord r) {
        r.challengePending = true;
        save();
    }

    /** On respawn: hands over the Challenges it has earned. */
    public void giveChallenges(Player player) {
        boolean changed = false;
        for (NemesisRecord r : List.copyOf(records.values())) {
            if (!r.challengePending || !r.owner.equals(player.getUniqueId())) {
                continue;
            }
            r.challengePending = false;
            changed = true;
            if (carriesChallenge(player, r)) {
                continue; // they still have the current note
            }
            // a new copy: any older copy (left in a chest, dropped where it died) stops working
            r.challengeSerial++;
            ItemStack note = createChallenge(r);
            for (ItemStack left : player.getInventory().addItem(note).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            plugin.presentation().tell(player, "nemesis-challenge-received", Placeholder.component("boss", displayName(r)));
        }
        if (changed) {
            save();
        }
    }

    /** Whether they carry the current copy of this Nemesis's note (inventory or ender chest). */
    private static boolean carriesChallenge(Player p, NemesisRecord r) {
        for (ItemStack[] contents : List.of(p.getInventory().getContents(), p.getEnderChest().getContents())) {
            for (ItemStack item : contents) {
                if (r.id.equals(challengeId(item)) && challengeSerial(item) == r.challengeSerial) {
                    return true;
                }
            }
        }
        return false;
    }

    public static @Nullable String challengeId(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? null
            : item.getPersistentDataContainer().get(Keys.CHALLENGE, PersistentDataType.STRING);
    }

    private static int challengeSerial(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? -1
            : item.getPersistentDataContainer().getOrDefault(Keys.CHALLENGE_SERIAL, PersistentDataType.INTEGER, 0);
    }

    /** The note belongs to a Nemesis that still exists, and it's the current copy. */
    private @Nullable NemesisRecord liveChallenge(@Nullable ItemStack item) {
        String id = challengeId(item);
        NemesisRecord r = id == null ? null : records.get(id);
        return r != null && challengeSerial(item) == r.challengeSerial ? r : null;
    }

    /** The note: its name, how to call it, and its Terms of Surrender. */
    public ItemStack createChallenge(NemesisRecord r) {
        ItemStack item = ItemStack.of(org.bukkit.Material.PAPER);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_NAME,
            Component.text("☠ Challenge of " + r.name, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_STACK_SIZE, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line(r.name + " the " + r.epithet, NamedTextColor.GRAY));
        lore.add(line("Right-click (or use it on the ground):", NamedTextColor.GRAY));
        lore.add(line("call it to you now, instead of", NamedTextColor.GRAY));
        lore.add(line("waiting for it.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("Terms of Surrender:", NamedTextColor.DARK_RED));
        for (String part : Text.wrap("\"" + r.surrender.words() + "\"", 34)) {
            lore.add(line(part, NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        }
        lore.add(line("(" + r.surrender.plain() + " while carrying", NamedTextColor.DARK_GRAY));
        lore.add(line("this note, and the feud ends. No reward.)", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.CHALLENGE, PersistentDataType.STRING, r.id);
            pdc.set(Keys.CHALLENGE_SERIAL, PersistentDataType.INTEGER, r.challengeSerial);
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, com.additionalbosses.item.ItemService.Kind.CHALLENGE.name());
        });
        return item;
    }

    private static Component line(String text, net.kyori.adventure.text.format.TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Right-click with a Challenge: the Nemesis comes now (20-30 blocks away). The note is used up only if it came. */
    public void useChallenge(Player player, ItemStack item, org.bukkit.inventory.EquipmentSlot hand) {
        String id = challengeId(item);
        NemesisRecord r = liveChallenge(item);
        if (r == null) {
            player.getInventory().setItem(hand, null);
            if (id != null && records.containsKey(id)) {
                // the feud goes on, but a newer copy of the note replaced this one
                Fx.actionBar(player, Component.text("The ink has faded: a newer copy of this Challenge replaced it.",
                    NamedTextColor.GRAY));
            } else {
                plugin.presentation().tell(player, "nemesis-challenge-gone");
            }
            return;
        }
        if (!f().nemesisEnabled) {
            plugin.presentation().tell(player, "nemesis-challenge-blocked");
            return;
        }
        if (!r.owner.equals(player.getUniqueId())) {
            plugin.presentation().tell(player, "nemesis-challenge-not-yours");
            return;
        }
        if (activeBoss(r) != null) {
            plugin.presentation().tell(player, "nemesis-challenge-out", Placeholder.component("boss", displayName(r)));
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE
            || player.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL
            || player.getWorld().getEnvironment() == World.Environment.THE_END
            || !plugin.settings().worldAllowed(player.getWorld())
            || !spawnFor(r, player, 20, 30, true)) {
            plugin.presentation().tell(player, "nemesis-challenge-blocked");
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (id != null && id.equals(challengeId(held))) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        }
    }

    /**
     * Died by its Terms of Surrender while carrying its Challenge: the feud is over for good. No reward; the note
     * is gone. Works whether or not the Nemesis is out.
     */
    public void checkSurrender(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player p = event.getEntity();
        Integer hit = lastBossHit.remove(p.getUniqueId());
        if (hit != null && Bukkit.getCurrentTick() - hit < 200) {
            return; // a boss had a hand in this (knocked off a ledge, set on fire...)
        }
        for (Boss b : plugin.bosses().active()) {
            if (b.engaged().containsKey(p.getUniqueId())) {
                return; // mid-fight with a boss (blinded, pulled, knocked about): not a surrender
            }
        }
        Set<String> ended = new HashSet<>();
        for (ItemStack item : p.getInventory().getContents()) {
            NemesisRecord r = liveChallenge(item);
            String id = r == null ? null : r.id;
            if (r != null && !ended.contains(id) && r.owner.equals(p.getUniqueId()) && r.surrender.matches(event, r.type)) {
                ended.add(id);
                Boss active = activeBoss(r);
                if (active != null) {
                    LivingEntity e = active.entity();
                    Fx.particle(Fx.center(e), Particle.LARGE_SMOKE, 40, 0.6, 0.05);
                    plugin.bosses().removeMinions(active);
                    plugin.bosses().unregister(active);
                    e.remove();
                }
                records.remove(id);
                plugin.presentation().tell(p, "nemesis-surrendered", Placeholder.component("boss", displayName(r)));
            }
        }
        if (ended.isEmpty()) {
            return;
        }
        save();
        event.getDrops().removeIf(item -> ended.contains(challengeId(item)));
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (ended.contains(challengeId(inv.getItem(slot)))) {
                inv.setItem(slot, null); // (with keepInventory on)
            }
        }
    }

    // =====================================================================
    // Death
    // =====================================================================

    /** Nemesis loot: guaranteed boss gear above the normal ceiling, a guaranteed rune, boosted relic odds, a statue. */
    public void onSlain(Boss boss, @Nullable Player killer, EntityDeathEvent event, List<ItemStack> rewards) {
        NemesisRecord r = get(boss.nemesisId());
        if (r == null) {
            return;
        }
        if (killer == null) {
            // Killed by something other than a player (a golem, lava...): that isn't the end of a Nemesis.
            r.entity = null;
            r.prowlUntil = 0;
            r.provoker = null;
            r.returnAt = Math.max(r.returnAt, clock() + Math.round(f().nemesisReturnDays * DAY));
            save();
            return;
        }
        records.remove(r.id);
        save();
        if (killer != null) {
            var rs = plugin.settings().rank(r.rank);
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 + r.level * 0.2)));
            rewards.add(plugin.rewards().gear().createBonus(r.rank, r.type, plainName(r), 1 + r.level / 10));
            ItemStack rune = plugin.items().createRandomRune(r.rank);
            if (rune != null) {
                rewards.add(rune);
            }
            if (Rng.chance(Math.min(100, rs.rewards().relic() + 3.0 * r.level))) {
                ItemStack relic = plugin.items().createRandomRelic();
                if (relic != null) {
                    rewards.add(relic);
                }
            }
            if (plugin.settings().catalystEnabled && Rng.chance(Math.min(50, rs.rewards().relicCatalyst() + r.level))) {
                rewards.add(plugin.items().createCatalyst());
            }
            rewards.add(plugin.trophies().createStatue(boss, r, displayName(r)));
            if (killer.getUniqueId().equals(r.owner) && r.kills > 0) {
                event.setDroppedExp((int) Math.round(event.getDroppedExp() * f().nemesisRevengeXpMultiplier));
                rewards.addAll(plugin.rewards().roll(boss, killer));
                plugin.presentation().tell(killer, "revenge");
            }
            plugin.presentation().broadcast(messages().prefixed("nemesis-slain",
                Placeholder.component("player", killer.displayName()), Placeholder.component("boss", displayName(r))));
        }
    }

    // =====================================================================

    /** Ambient "battle scars": smoke that thickens with the Nemesis's history. */
    public int scarLevel(Boss boss) {
        NemesisRecord r = get(boss.nemesisId());
        return r == null ? 0 : Math.min(6, 1 + r.escapes + r.kills);
    }

    public String describeReturn(NemesisRecord r) {
        if (r.entity != null) {
            return "hunting you now";
        }
        long left = r.returnAt - clock();
        if (left <= 0) {
            return "returning any moment";
        }
        return "returns in " + Text.num(left / (double) DAY) + " days";
    }
}
