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

    /** Called when a boss kills a player. */
    public void onKilledPlayer(Boss boss, Player victim) {
        FeatureSettings f = f();
        if (!f.nemesisEnabled) {
            return;
        }
        NemesisRecord r = get(boss.nemesisId());
        if (r != null) {
            if (r.owner.equals(victim.getUniqueId())) {
                r.kills++;
                absorbFight(r, boss, victim.getUniqueId());
                grow(r, f.nemesisLevelsOnKill, boss.entity());
                refreshBody(boss, r);
                retreat(boss, r);
                victim.sendMessage(messages().prefixed("nemesis-grows", Placeholder.component("boss", displayName(r))));
            } else {
                grow(r, 1, boss.entity()); // it also grows by killing anyone else
                refreshBody(boss, r);
                save();
            }
            return;
        }
        if (forOwner(victim.getUniqueId()).size() >= f.nemesisMaxPerPlayer || !Rng.chance(f.nemesisKillChance)) {
            return;
        }
        NemesisRecord created = create(boss, victim);
        created.kills = 1;
        updateTitles(created);
        configure(boss, created);
        retreat(boss, created);
        victim.sendMessage(messages().prefixed("nemesis-born", Placeholder.component("boss", displayName(created))));
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
        configure(boss, created);
        retreat(boss, created);
        if (p != null) {
            p.sendMessage(messages().prefixed("nemesis-born", Placeholder.component("boss", displayName(created))));
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
        boolean inReach = owner != null && !owner.isDead() && owner.getWorld().equals(e.getWorld())
            && owner.getLocation().distanceSquared(e.getLocation()) <= f().nemesisEscapeDistance * f().nemesisEscapeDistance;
        if (inReach) {
            r.lostTicks = 0;
            if (e instanceof Mob mob && boss.fightable(owner) && mob.getTarget() != owner
                && owner.getLocation().distanceSquared(e.getLocation()) < 40 * 40) {
                mob.setTarget(owner);
            }
            return false;
        }
        r.lostTicks += 10;
        if (owner != null && r.lostTicks < 300) {
            return false; // 15 seconds of grace
        }
        r.escapes++;
        absorbFight(r, boss, r.owner);
        grow(r, f().nemesisLevelsOnEscape, e);
        refreshBody(boss, r);
        boolean removed = retreat(boss, r);
        if (owner != null) {
            owner.sendMessage(messages().prefixed("nemesis-flee", Placeholder.component("boss", displayName(r))));
        }
        return removed;
    }

    // =====================================================================
    // Prowling: after it lets its prey go, it roams for a while and feeds on other mobs
    // =====================================================================

    public boolean isProwling(Boss boss) {
        NemesisRecord r = get(boss.nemesisId());
        return r != null && r.prowlUntil > 0;
    }

    /** Leaves the fight: prowls nearby for a few minutes (if enabled), then vanishes until its return. */
    private boolean retreat(Boss boss, NemesisRecord r) {
        r.lostTicks = 0;
        r.returnAt = clock() + Math.round(f().nemesisReturnDays * DAY);
        LivingEntity e = boss.entity();
        int prowlTicks = (int) Math.round(f().nemesisProwlMinutes * 1200);
        if (prowlTicks <= 0 || !e.isValid() || e.isDead()) {
            vanish(boss, r);
            return true;
        }
        r.prowlUntil = Bukkit.getCurrentTick() + prowlTicks;
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
                || near.getPersistentDataContainer().has(Keys.STATUE, PersistentDataType.STRING)
                || (near instanceof org.bukkit.entity.Tameable t && t.isTamed()) || near.isInvulnerable()) {
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
        r.prowlUntil = 0;
        if (boss.entity() instanceof Mob mob) {
            mob.setTarget(attacker);
        }
        attacker.sendMessage(messages().prefixed("nemesis-provoked", Placeholder.component("boss", displayName(r))));
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
        r.name = NemesisNames.pick(r.type, takenNames());
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

    /** Remembers how the owner fought (melee or ranged) so the Nemesis can adapt. */
    private void absorbFight(NemesisRecord r, Boss boss, UUID owner) {
        int[] style = boss.hitStyle(owner);
        r.meleeHits += style[0];
        r.rangedHits += style[1];
        boss.forgetFight(owner);
    }

    private void grow(NemesisRecord r, int levels, LivingEntity sample) {
        FeatureSettings f = f();
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
    }

    /** Adaptive counters: archers get warded against, fighters get thorns, runners get chased down. */
    private @Nullable BossTrait counterTrait(NemesisRecord r, LivingEntity sample) {
        List<String> wishes = new ArrayList<>();
        if (r.rangedHits > r.meleeHits) {
            wishes.addAll(List.of("warded", "blinking", "deadeye"));
        } else if (r.meleeHits > 0) {
            wishes.addAll(List.of("thorned", "bulwark", "executioner"));
        }
        if (r.escapes > r.kills) {
            wishes.addAll(List.of("swift", "leaping", "charging"));
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
        addTitle(r, r.level >= 10, "Unbroken");
        addTitle(r, r.kills >= 5, "Butcher");
        addTitle(r, r.level >= 25, "Dreaded");
        addTitle(r, r.level >= 40, "Eternal");
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
        Location spot = SafeSpots.around(owner.getLocation(), 14, 22, 16);
        if (spot == null) {
            return; // try again on the next check
        }
        List<BossTrait> traits = new ArrayList<>();
        for (String id : r.traits) {
            BossTrait t = plugin.traits().get(id);
            if (t != null && plugin.traits().isEnabled(t)) {
                traits.add(t);
            }
        }
        Class<?> cls = r.type.getEntityClass();
        if (cls == null || !Mob.class.isAssignableFrom(cls)) {
            records.remove(r.id); // that mob can't exist any more
            save();
            return;
        }
        Boss boss = plugin.bosses().summon(r.type, spot, r.rank, traits, false, "nemesis");
        if (boss == null) {
            return; // the spawn was blocked here; try again on the next check
        }
        r.returns++;
        r.outingKills = 0;
        r.prowlUntil = 0;
        updateTitles(r);
        configure(boss, r);
        plugin.bossBars().updateHealth(boss, boss.health());
        save();

        Component name = displayName(r);
        owner.showTitle(Title.title(messages().get("nemesis-return-title"),
            messages().get("nemesis-return-subtitle", Placeholder.component("boss", name)),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        owner.sendMessage(messages().prefixed("nemesis-return-subtitle", Placeholder.component("boss", name)));
        Fx.playTo(owner, Fx.sound("entity.wither.spawn", 0.7f, 0.8f));
        plugin.bosses().engage(boss, owner);
    }

    /** Applies everything a Nemesis is: its body (same as last time), level scaling, gear, name and persistence. */
    private void configure(Boss boss, NemesisRecord r) {
        LivingEntity e = boss.entity();
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
        e.setHealth(boss.maxHealth());
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
        e.setCustomNameVisible(true);
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
    // Death
    // =====================================================================

    /** Nemesis loot: guaranteed boss gear above the normal ceiling, a guaranteed rune, boosted relic odds, a statue. */
    public void onSlain(Boss boss, @Nullable Player killer, EntityDeathEvent event, List<ItemStack> rewards) {
        NemesisRecord r = records.remove(boss.nemesisId());
        save();
        if (r == null) {
            return;
        }
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
                killer.sendMessage(messages().prefixed("revenge"));
            }
            Bukkit.getServer().sendMessage(messages().prefixed("nemesis-slain",
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
