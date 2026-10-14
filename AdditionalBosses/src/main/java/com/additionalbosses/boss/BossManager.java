package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.Synergies;
import com.additionalbosses.util.SafeSpots;
import com.destroystokyo.paper.entity.RangedEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.config.MobProfile;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.trait.BossTrait;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.PlayerData;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects eligible spawns, turns mobs into bosses (two separate rolls: "is it a boss?" then "which rank?"),
 * applies stats, tracks active bosses by UUID, runs the single shared ticker, and cleans up on death,
 * despawn, unload and plugin disable.
 */
public final class BossManager {

    private final AdditionalBosses plugin;
    private final Map<UUID, Boss> active = new HashMap<>();
    private @Nullable BukkitTask ticker;

    public BossManager(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private PluginSettings settings() {
        return plugin.settings();
    }

    // =====================================================================
    // Lookup
    // =====================================================================

    public @Nullable Boss get(@Nullable Entity entity) {
        return entity == null ? null : active.get(entity.getUniqueId());
    }

    public Collection<Boss> active() {
        return Collections.unmodifiableCollection(active.values());
    }

    public static boolean isBoss(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.BYTE);
    }

    public static boolean isMinion(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.MINION, PersistentDataType.STRING);
    }

    private static final org.bukkit.NamespacedKey BLOOM_PET = org.bukkit.NamespacedKey.fromString("bloompets:pet");

    /** A pet from the BloomPets plugin: never turned into a boss, hunted, chased off or angered by curses. */
    public static boolean isPet(Entity entity) {
        return BLOOM_PET != null && entity.getPersistentDataContainer().has(BLOOM_PET);
    }

    // =====================================================================
    // Natural spawns
    // =====================================================================

    public void handleSpawn(LivingEntity entity, SpawnReason reason) {
        PluginSettings s = settings();
        if (!s.enabled || !Bukkit.isPrimaryThread() || isBoss(entity) || isMinion(entity) || isPet(entity)
            || entity.getPersistentDataContainer().has(Keys.STATUE, PersistentDataType.STRING)) {
            return;
        }
        MobCategory category = s.categoryFor(entity.getType());
        if (category == null || !category.enabled() || s.excludedMobs.contains(entity.getType())) {
            return;
        }
        if (!(category.spawnReasons() != null ? category.spawnReasons() : s.spawnReasons).contains(reason)) {
            return;
        }
        if (!s.worldAllowed(entity.getWorld())) {
            return;
        }
        // Bosses appear at about the height you're at, not deep in a cave far below (or high above) you.
        // A structure mob that loads with nobody near its height gets its roll the next time it loads.
        if (!nearPlayerHeight(entity, s.maxHeightDifference)) {
            return;
        }
        if (isWorldgen(reason)) {
            // Structure mobs (Elder Guardians, mansion illagers...) are rolled once, the first time they load.
            if (entity.getPersistentDataContainer().has(Keys.WORLDGEN_CHECKED, PersistentDataType.BYTE)) {
                return;
            }
            entity.getPersistentDataContainer().set(Keys.WORLDGEN_CHECKED, PersistentDataType.BYTE, (byte) 1);
        }
        // Roll #1: does this mob become a boss at all?
        if (!Rng.chance(s.spawnChance * category.spawnChanceMultiplier())) {
            return;
        }
        if (regularCount() >= s.maxActive || tooCloseToAnotherBoss(entity.getLocation())) {
            return;
        }
        // Roll #2: which rank? (weighted by the mob's category; the Nemesis-breeding Nether rolls higher)
        BossRank rank = category.rollRank(entity.getWorld().getEnvironment() == World.Environment.NETHER);
        createBoss(entity, rank, category.id(), null, true);
    }

    /** Active bosses that count toward the cap (Nemeses ignore it). */
    public int regularCount() {
        int n = 0;
        for (Boss b : active.values()) {
            if (!b.isNemesis()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Spawns a brand-new boss of the given type at a location (totems, escalation, nemesis returns, admin).
     * Ignores the boss cap. Returns null if the type isn't a mob.
     */
    public @Nullable Boss summon(EntityType type, Location at, BossRank rank, @Nullable List<BossTrait> traits,
                                 boolean announce, String category) {
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || !Mob.class.isAssignableFrom(cls)) {
            return null;
        }
        if (at.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL
            && org.bukkit.entity.Enemy.class.isAssignableFrom(cls)) {
            return null; // it would vanish on its first tick
        }
        Boss[] out = new Boss[1];
        Entity spawned = at.getWorld().spawn(at, cls, SpawnReason.CUSTOM, e -> {
            if (e instanceof LivingEntity living) {
                out[0] = createBoss(living, rank, category, traits, announce);
            }
        });
        if (out[0] != null && !spawned.isValid()) {
            unregister(out[0]); // another plugin cancelled the spawn
            return null;
        }
        return out[0];
    }

    /**
     * Marks a boss as player-made (a Totem, Escalation, a command, a Nemesis called by its Challenge): such
     * bosses never drop Souls or Totems, so they can't feed themselves. {@code how} is kept for the Nemesis rule.
     */
    public static void markCreated(Boss boss, String how) {
        boss.entity().getPersistentDataContainer().set(Keys.BOSS_CREATED, PersistentDataType.STRING, how);
    }

    public static @Nullable String created(LivingEntity e) {
        return e.getPersistentDataContainer().get(Keys.BOSS_CREATED, PersistentDataType.STRING);
    }

    /** The rank a promoted boss had before anyone offered it a Soul (its own rank if it was never promoted). */
    private static BossRank wildRank(LivingEntity e, BossRank now) {
        BossRank wild = BossRank.parse(e.getPersistentDataContainer().get(Keys.BOSS_WILD_RANK, PersistentDataType.STRING));
        return wild != null && !wild.atLeast(now) ? wild : now;
    }

    /** Paper gives mobs placed by structures/world generation the DEFAULT spawn reason. */
    public static boolean isWorldgen(@Nullable SpawnReason reason) {
        return reason == SpawnReason.DEFAULT;
    }

    /** Whether a player (not spectating) is around this mob, within {@code maxDy} blocks above or below it. */
    private static boolean nearPlayerHeight(LivingEntity entity, int maxDy) {
        if (maxDy <= 0) {
            return true;
        }
        Location at = entity.getLocation();
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            Location pl = p.getLocation();
            double dx = pl.getX() - at.getX();
            double dz = pl.getZ() - at.getZ();
            if (dx * dx + dz * dz <= 128 * 128 && Math.abs(pl.getY() - at.getY()) <= maxDy) {
                return true;
            }
        }
        return false;
    }

    private boolean tooCloseToAnotherBoss(Location location) {
        double min = settings().minDistanceBetween;
        if (min <= 0) {
            return false;
        }
        for (Boss boss : active.values()) {
            LivingEntity e = boss.entity();
            if (e.getWorld().equals(location.getWorld()) && e.getLocation().distanceSquared(location) < min * min) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // Creation / restore
    // =====================================================================

    /**
     * Turns a mob into a boss. {@code traits} may be null to roll them normally.
     * Works both during CreatureSpawnEvent (before the mob is in the world) and on live mobs.
     */
    public Boss createBoss(LivingEntity entity, BossRank rank, String categoryId, @Nullable List<BossTrait> traits,
                           boolean announce) {
        return createBoss(entity, rank, categoryId, traits, announce, Set.of());
    }

    /** {@code alreadyApplied}: trait ids the mob already carries (a promotion), so their one-time setup isn't repeated. */
    private Boss createBoss(LivingEntity entity, BossRank rank, String categoryId, @Nullable List<BossTrait> traits,
                            boolean announce, Set<String> alreadyApplied) {
        PluginSettings s = settings();
        RankSettings rs = s.rank(rank);
        MobProfile profile = s.profileFor(entity.getType());

        if (traits == null) {
            int count = Rng.between(rs.traits().min(), rs.traits().max());
            traits = plugin.traits().roll(entity, count);
        }

        applyStats(entity, rank, rs.stats(), profile);
        preventSunburn(entity);

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.BOSS_RANK, PersistentDataType.STRING, rank.name());
        pdc.set(Keys.BOSS_CATEGORY, PersistentDataType.STRING, categoryId);
        List<String> ids = new ArrayList<>();
        for (BossTrait t : traits) {
            ids.add(t.id());
        }
        pdc.set(Keys.BOSS_TRAITS, PersistentDataType.STRING, String.join(",", ids));

        Boss boss = build(entity, rank, categoryId, traits);
        entity.customName(boss.name());
        entity.setCustomNameVisible(s.alwaysShowName);
        entity.setCanPickupItems(false);
        clearDropChances(entity);
        if (s.features.armorSets && !entity.getPersistentDataContainer().has(Keys.NEMESIS, PersistentDataType.STRING)) {
            BossArmor.applyRankSet(s.features, entity, rank);
        }
        for (BossTrait t : traits) {
            t.onApply(boss, !alreadyApplied.contains(t.id()));
        }
        register(boss);
        if (announce) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active.containsKey(boss.uuid())) {
                    plugin.presentation().announceSpawn(boss);
                }
            });
        }
        return boss;
    }

    /** Rebuilds the in-memory boss from the entity's saved data (after chunk load or restart). */
    public @Nullable Boss restore(LivingEntity entity) {
        if (active.containsKey(entity.getUniqueId()) || !isBoss(entity)) {
            return active.get(entity.getUniqueId());
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (PluginSettings.isMount(entity.getType())) {
            demote(entity); // a horse/camel boss from an older version: mounts can't be bosses any more
            return null;
        }
        BossRank rank = BossRank.parse(pdc.get(Keys.BOSS_RANK, PersistentDataType.STRING));
        if (rank == null) {
            rank = BossRank.GRAY;
        }
        String category = pdc.getOrDefault(Keys.BOSS_CATEGORY, PersistentDataType.STRING, "custom");
        List<BossTrait> traits = new ArrayList<>();
        String raw = pdc.getOrDefault(Keys.BOSS_TRAITS, PersistentDataType.STRING, "");
        for (String id : raw.split(",")) {
            if (id.isBlank()) {
                continue;
            }
            BossTrait t = plugin.traits().get(id.trim());
            if (t != null && plugin.traits().isEnabled(t)) {
                traits.add(t);
            }
        }
        // Bosses no longer outrun players: speed saved on bosses from older versions goes (and turned-off traits).
        RankSettings.Stats stats = settings().rank(rank).stats();
        setModifier(entity, Attribute.MOVEMENT_SPEED, Keys.MOD_SPEED,
            MobProfile.scaleMultiplier(stats.speed(), settings().profileFor(entity.getType()).speed()) - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        setModifier(entity, Attribute.MOVEMENT_SPEED, Keys.MOD_LAST_STAND, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if (traits.stream().noneMatch(t -> t.id().equals("swift"))) {
            setModifier(entity, Attribute.MOVEMENT_SPEED, Keys.MOD_TRAIT_SWIFT, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
        pdc.set(Keys.BOSS_TRAITS, PersistentDataType.STRING, String.join(",", traits.stream().map(BossTrait::id).toList()));
        String nemesisId = pdc.get(Keys.NEMESIS, PersistentDataType.STRING);
        if (nemesisId != null && !plugin.nemesis().canReattach(nemesisId, entity)) {
            // A stray copy (its record is gone or it is already out): remove it once the add event is over.
            Bukkit.getScheduler().runTask(plugin, entity::remove);
            return null;
        }
        Boss boss = build(entity, rank, category, traits);
        entity.setCustomNameVisible(settings().alwaysShowName); // bosses saved before the setting changed follow it too
        entity.setGlowing(false); // (saved glowing when its chunk unloaded; the ticker turns it back on with its colour)
        preventSunburn(entity);
        boss.setUndyingUsed(pdc.has(Keys.BOSS_UNDYING, PersistentDataType.BYTE));
        if (pdc.has(Keys.BOSS_LAST_STAND, PersistentDataType.BYTE)) {
            boss.setLastStand(true);
            boss.scalePower(1.0 + settings().features.lastStandPowerBonus / 100.0);
        }
        for (BossTrait t : traits) {
            t.onApply(boss, false);
        }
        register(boss);
        if (nemesisId != null) {
            plugin.nemesis().reattach(boss, nemesisId); // e.g. it followed its prey through a portal
        }
        return boss;
    }

    /** Turns a boss back into an ordinary mob (used for mob types that can no longer be bosses). */
    private static void demote(LivingEntity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : new org.bukkit.NamespacedKey[]{Keys.BOSS, Keys.BOSS_RANK, Keys.BOSS_TRAITS,
            Keys.BOSS_CATEGORY, Keys.BOSS_UNDYING, Keys.BOSS_LAST_STAND, Keys.BOSS_THREAT, Keys.BOSS_PHASE, Keys.BOSS_CREATED,
            Keys.BOSS_PLAYER_KILLS, Keys.BOSS_WILD_RANK}) {
            pdc.remove(key);
        }
        for (Attribute attribute : new Attribute[]{Attribute.MAX_HEALTH, Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS,
            Attribute.KNOCKBACK_RESISTANCE, Attribute.MOVEMENT_SPEED, Attribute.SCALE, Attribute.FOLLOW_RANGE}) {
            AttributeInstance inst = e.getAttribute(attribute);
            if (inst == null) {
                continue;
            }
            for (org.bukkit.NamespacedKey key : new org.bukkit.NamespacedKey[]{Keys.MOD_HEALTH, Keys.MOD_ARMOR,
                Keys.MOD_TOUGHNESS, Keys.MOD_KNOCKBACK, Keys.MOD_SPEED, Keys.MOD_SIZE, Keys.MOD_FOLLOW,
                Keys.MOD_DIFFICULTY, Keys.MOD_HEALTH_FLOOR, Keys.MOD_THREAT, Keys.MOD_TRAIT_SWIFT, Keys.MOD_TRAIT_BERSERK, Keys.MOD_LAST_STAND}) {
                inst.removeModifier(key);
            }
        }
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && e.getHealth() > max.getValue()) {
            e.setHealth(max.getValue());
        }
        e.customName(null);
        e.setCustomNameVisible(false);
    }

    /** Bosses no longer wear helmets, so undead bosses would otherwise burn away in daylight. */
    private static void preventSunburn(LivingEntity e) {
        if (e instanceof Zombie z) {
            z.setShouldBurnInDay(false);
        } else if (e instanceof AbstractSkeleton sk) {
            sk.setShouldBurnInDay(false);
        } else if (e instanceof org.bukkit.entity.Phantom ph) {
            ph.setShouldBurnInDay(false);
        }
    }

    /** Carries boss status over when a boss converts (Zombie -> Drowned, Skeleton -> Stray, Piglin -> Zombified...). */
    public void transfer(Boss from, LivingEntity to) {
        List<BossTrait> keep = new ArrayList<>();
        for (BossTrait t : from.traits()) {
            if (t.supports(to)) {
                keep.add(t);
            }
        }
        double ratio = from.healthRatio();
        PersistentDataContainer old = from.entity().getPersistentDataContainer();
        String created = old.get(Keys.BOSS_CREATED, PersistentDataType.STRING);
        if (created != null) {
            to.getPersistentDataContainer().set(Keys.BOSS_CREATED, PersistentDataType.STRING, created);
        }
        Integer kills = old.get(Keys.BOSS_PLAYER_KILLS, PersistentDataType.INTEGER);
        if (kills != null) {
            to.getPersistentDataContainer().set(Keys.BOSS_PLAYER_KILLS, PersistentDataType.INTEGER, kills);
        }
        String wild = old.get(Keys.BOSS_WILD_RANK, PersistentDataType.STRING);
        if (wild != null) {
            to.getPersistentDataContainer().set(Keys.BOSS_WILD_RANK, PersistentDataType.STRING, wild);
        }
        Boss boss = createBoss(to, from.rank(), from.categoryId(), keep, false);
        AttributeInstance max = to.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            to.setHealth(Math.max(1.0, max.getValue() * ratio));
        }
        if (from.undyingUsed()) {
            boss.setUndyingUsed(true);
            to.getPersistentDataContainer().set(Keys.BOSS_UNDYING, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private Boss build(LivingEntity entity, BossRank rank, String categoryId, List<BossTrait> traits) {
        RankSettings rs = settings().rank(rank);
        MobProfile profile = settings().profileFor(entity.getType());
        Synergies.Synergy synergy = Synergies.find(traits);
        String adjective = synergy != null ? synergy.title() + " "
            : traits.isEmpty() ? "" : traits.get(0).adjective() + " ";
        String plain = rank.starText() + " " + rs.name() + " " + adjective + Text.pretty(entity.getType().name());
        Component styled = rank.styled(plain);
        double damage = MobProfile.scaleMultiplier(rs.stats().damage(), profile.damage())
            * settings().features.difficultyDamage
            * (1.0 + threat(entity) * settings().features.threatMaxDamage / 100.0);
        double power = rs.traits().power() * (synergy != null ? Synergies.POWER_BONUS : 1.0);
        return new Boss(entity, rank, categoryId, traits, styled, plain, damage, power);
    }

    private void applyStats(LivingEntity e, BossRank rank, RankSettings.Stats st, MobProfile p) {
        // Higher ranks notice players from further away (more aggressive encounters).
        setModifier(e, Attribute.FOLLOW_RANGE, Keys.MOD_FOLLOW, rank.ordinal() * 6.0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_HEALTH,
            MobProfile.scaleMultiplier(st.health(), p.health()) - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        setModifier(e, Attribute.ARMOR, Keys.MOD_ARMOR, st.armor() * p.defense(), AttributeModifier.Operation.ADD_NUMBER);
        setModifier(e, Attribute.ARMOR_TOUGHNESS, Keys.MOD_TOUGHNESS, st.toughness() * p.defense(),
            AttributeModifier.Operation.ADD_NUMBER);
        setModifier(e, Attribute.KNOCKBACK_RESISTANCE, Keys.MOD_KNOCKBACK, st.knockbackResistance() * p.defense(),
            AttributeModifier.Operation.ADD_NUMBER);
        setModifier(e, Attribute.MOVEMENT_SPEED, Keys.MOD_SPEED,
            MobProfile.scaleMultiplier(st.speed(), p.speed()) - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        setModifier(e, Attribute.SCALE, Keys.MOD_SIZE, st.size() * p.size(), AttributeModifier.Operation.ADD_NUMBER);
        setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_DIFFICULTY, settings().features.difficultyHealth - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyHealthFloor(e, st.minHealth());
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            e.setHealth(max.getValue());
        }
    }

    /**
     * Top ranks have a minimum health (Legendary at least a Warden's 500 by default), however small the mob. Threat
     * Scaling and Nemesis levels still add on top.
     */
    private static void applyHealthFloor(LivingEntity e, double minHealth) {
        setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_HEALTH_FLOOR, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null || minHealth <= 0 || inst.getValue() <= 0 || inst.getValue() >= minHealth) {
            return;
        }
        setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_HEALTH_FLOOR, minHealth / inst.getValue() - 1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private static void setModifier(LivingEntity e, Attribute attribute, org.bukkit.NamespacedKey key, double amount,
                                    AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(key);
        if (Math.abs(amount) > 1.0E-6) {
            inst.addModifier(new AttributeModifier(key, amount, op));
        }
    }

    private static void clearDropChances(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                eq.setDropChance(slot, 0f);
            } catch (IllegalArgumentException ignored) {
                // this mob can't use that slot
            }
        }
    }

    // =====================================================================
    // Tracking + ticker
    // =====================================================================

    private void register(Boss boss) {
        active.put(boss.uuid(), boss);
        plugin.bossBars().create(boss);
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        }
    }

    public void unregister(Boss boss) {
        if (active.remove(boss.uuid()) == null) {
            return;
        }
        glow(boss, false);
        plugin.bossBars().remove(boss);
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** One task for every boss. It stops itself when no bosses are loaded. */
    private void tick() {
        if (active.isEmpty()) {
            if (ticker != null) {
                ticker.cancel();
                ticker = null;
            }
            return;
        }
        int now = Bukkit.getCurrentTick();
        int timeout = settings().combatTimeoutTicks;
        for (Boss boss : List.copyOf(active.values())) {
            LivingEntity e = boss.entity();
            if (!e.isValid()) {
                unregister(boss); // safety net; normally the remove-from-world event handles this
                continue;
            }
            // A boss chasing a player keeps that player "in combat" even before the first hit lands.
            if (e instanceof Mob mob && mob.getTarget() instanceof Player chased && boss.fightable(chased)
                && chased.getLocation().distanceSquared(e.getLocation())
                <= settings().bossbarViewDistance * settings().bossbarViewDistance) {
                engage(boss, chased);
            }
            if (plugin.nemesis().tickActive(boss)) {
                continue; // the Nemesis withdrew (its prey fled or logged off)
            }
            boolean gone = false;
            for (UUID fled : boss.expireEngagements(now, timeout)) {
                if (plugin.nemesis().onPlayerFled(boss, fled)) {
                    gone = true;
                    break;
                }
            }
            if (gone) {
                continue;
            }
            checkStuck(boss, now);
            glow(boss, playerWithin(boss, settings().glowWithin));
            plugin.bossBars().refreshViewers(boss);
            for (BossTrait trait : boss.traits()) {
                try {
                    trait.onTick(boss, now);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Trait " + trait.id() + " failed on tick: " + ex);
                }
            }
            if (now % 20 < 10) {
                plugin.presentation().ambient(boss);
            }
        }
    }

    // =====================================================================
    // Glow: a boss lights up (in its rank's colour) while someone is close
    // =====================================================================

    private static boolean playerWithin(Boss boss, double radius) {
        if (radius <= 0) {
            return false;
        }
        LivingEntity e = boss.entity();
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(e.getLocation()) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void glow(Boss boss, boolean on) {
        LivingEntity e = boss.entity();
        if (e.isGlowing() == on || (!e.isValid() && on)) {
            return;
        }
        e.setGlowing(on);
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = e.getUniqueId().toString();
        if (on && settings().glowRankColors) {
            String name = "ab_glow_" + boss.rank().name().toLowerCase(java.util.Locale.ROOT);
            org.bukkit.scoreboard.Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
                team.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(boss.rank().color()));
                team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } else if (!on) {
            org.bukkit.scoreboard.Team team = board.getEntryTeam(entry);
            if (team != null && team.getName().startsWith("ab_glow_")) {
                team.removeEntry(entry);
            }
        }
    }

    // =====================================================================
    // Two strikes: a boss that has killed enough players leaves
    // =====================================================================

    /** A boss (or its minion) killed a player. Returns true if the boss left because of it. */
    public void onKilledPlayer(Boss boss, Player victim, List<ItemStack> drops) {
        LivingEntity e = boss.entity();
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        int kills = pdc.getOrDefault(Keys.BOSS_PLAYER_KILLS, PersistentDataType.INTEGER, 0) + 1;
        pdc.set(Keys.BOSS_PLAYER_KILLS, PersistentDataType.INTEGER, kills);
        boss.addVictim(victim.getUniqueId());
        if (plugin.nemesis().onKilledPlayer(boss, victim, kills, drops)) {
            return; // it is (or just became) a Nemesis: it handles its own leaving
        }
        int limit = settings().despawnAfterKills;
        if (limit > 0 && kills >= limit) {
            // after the death has been dealt with: it simply isn't there any more
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active.get(boss.uuid()) == boss && e.isValid() && !e.isDead()) {
                    Fx.particle(Fx.center(e), Particle.LARGE_SMOKE, 30, 0.6, 0.05);
                    removeMinions(boss);
                    unregister(boss);
                    e.remove();
                }
            });
        }
    }

    public void engage(Boss boss, Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean first = !boss.engaged().containsKey(player.getUniqueId());
        boss.engage(player, Bukkit.getCurrentTick());
        if (first) {
            applyThreat(boss);
            // Once a fight has started, ranks marked persistent stop despawning, so a boss never vanishes
            // just because the player stepped back to heal. Bosses nobody has fought still despawn normally.
            if (settings().rank(boss.rank()).persistent()) {
                boss.entity().setRemoveWhenFarAway(false);
            }
            plugin.presentation().reveal(boss, player);
            plugin.bossBars().refreshViewers(boss);
        }
    }

    // =====================================================================
    // Threat Scaling
    // =====================================================================

    /** The Threat Scaling value already applied to this boss (0 = none, 1 = full). */
    public static double threat(LivingEntity e) {
        return e.getPersistentDataContainer().getOrDefault(Keys.BOSS_THREAT, PersistentDataType.DOUBLE, 0.0);
    }

    /**
     * The first time a boss is engaged it counts the Boss Gear the players nearby are actually using (worn, or in
     * either hand): the more of it, the more health and damage it gains. Applied once per boss.
     */
    private void applyThreat(Boss boss) {
        FeatureSettings f = settings().features;
        LivingEntity e = boss.entity();
        if (!f.threatEnabled || e.getPersistentDataContainer().has(Keys.BOSS_THREAT, PersistentDataType.DOUBLE)) {
            return;
        }
        double best = 0;
        for (Player p : boss.nearbyPlayers(f.threatRadius)) {
            best = Math.max(best, bossGearInUse(p));
        }
        double threat = Math.max(0, Math.min(1, best / f.threatFullPieces));
        e.getPersistentDataContainer().set(Keys.BOSS_THREAT, PersistentDataType.DOUBLE, threat);
        if (threat <= 0.01) {
            return;
        }
        double ratio = boss.healthRatio();
        setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_THREAT, threat * f.threatMaxHealth / 100.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        e.setHealth(Math.max(1, boss.maxHealth() * ratio));
        boss.scaleDamage(1.0 + threat * f.threatMaxDamage / 100.0);
        plugin.bossBars().updateHealth(boss, e.getHealth());
    }

    /** How many pieces of Boss Gear (dropped by bosses) a player wears or holds. Their bag doesn't count. */
    public int bossGearInUse(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int n = 0;
        for (ItemStack item : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots(),
            inv.getItemInMainHand(), inv.getItemInOffHand()}) {
            if (plugin.items().kind(item) == com.additionalbosses.item.ItemService.Kind.GEAR) {
                n++;
            }
        }
        return n;
    }

    // =====================================================================
    // Promotion (trophies) + Ascendant phases
    // =====================================================================

    /**
     * Raises a living boss by some ranks (it heals to full at the new rank and gains traits to match).
     * Returns the new boss, or null if it can't be promoted (Nemesis, already Ascendant).
     */
    public @Nullable Boss promote(Boss boss, int steps, @Nullable Player by) {
        BossRank target = boss.rank().up(steps);
        if (boss.isNemesis() || target == boss.rank() || !boss.entity().isValid()) {
            return null;
        }
        LivingEntity e = boss.entity();
        List<BossTrait> traits = new ArrayList<>(boss.traits());
        RankSettings rs = settings().rank(target);
        int want = Rng.between(rs.traits().min(), rs.traits().max());
        while (traits.size() < want) {
            BossTrait extra = plugin.traits().rollExtra(e, traits);
            if (extra == null) {
                break;
            }
            traits.add(extra);
        }
        List<UUID> fighters = new ArrayList<>(boss.engaged().keySet());
        Set<UUID> minions = new HashSet<>(boss.minions());
        if (!e.getPersistentDataContainer().has(Keys.BOSS_WILD_RANK, PersistentDataType.STRING)) {
            e.getPersistentDataContainer().set(Keys.BOSS_WILD_RANK, PersistentDataType.STRING, boss.rank().name());
        }
        boolean undying = boss.undyingUsed();
        unregister(boss);
        e.getPersistentDataContainer().remove(Keys.BOSS_LAST_STAND);
        e.getPersistentDataContainer().remove(Keys.BOSS_PHASE);
        setModifier(e, Attribute.MOVEMENT_SPEED, Keys.MOD_LAST_STAND, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        double threat = threat(e);
        Boss promoted = createBoss(e, target, boss.categoryId(), traits, false, new HashSet<>(boss.traitIds()));
        if (threat > 0.01) {
            setModifier(e, Attribute.MAX_HEALTH, Keys.MOD_THREAT, threat * settings().features.threatMaxHealth / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            e.setHealth(promoted.maxHealth());
        }
        promoted.minions().addAll(minions);
        promoted.setUndyingUsed(undying);
        for (UUID id : fighters) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                engage(promoted, p);
            }
        }
        if (by != null) {
            engage(promoted, by);
        }
        e.getWorld().strikeLightningEffect(e.getLocation());
        Fx.dust(Fx.center(e), target.bukkitColor(), 2.2f, 60, 1.0);
        Fx.particle(Fx.center(e), Particle.END_ROD, 40, 0.8, 0.1);
        Fx.play(e.getLocation(), "block.beacon.power_select", 1.0f, 0.7f);
        plugin.presentation().announcePromotion(promoted, by);
        return promoted;
    }

    /** Ascendant bosses break into a new phase at set health marks: a shockwave and a new trait. */
    public void checkPhases(Boss boss, double healthAfter) {
        FeatureSettings f = settings().features;
        if (boss.rank() != BossRank.ASCENDANT || !f.ascendantPhases || healthAfter <= 0) {
            return;
        }
        LivingEntity e = boss.entity();
        int passed = e.getPersistentDataContainer().getOrDefault(Keys.BOSS_PHASE, PersistentDataType.INTEGER, 0);
        if (passed >= f.ascendantPhaseThresholds.size()
            || healthAfter / boss.maxHealth() * 100.0 > f.ascendantPhaseThresholds.get(passed)) {
            return;
        }
        e.getPersistentDataContainer().set(Keys.BOSS_PHASE, PersistentDataType.INTEGER, passed + 1);
        e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 3, false, false));
        double radius = f.ascendantShockwaveRadius;
        for (Player p : boss.nearbyPlayers(radius)) {
            com.additionalbosses.combat.AreaDamage.hit(p, f.ascendantShockwaveDamage * boss.power(), e);
            org.bukkit.util.Vector away = p.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new org.bukkit.util.Vector(0.1, 0, 0);
            }
            p.setVelocity(away.normalize().multiply(1.4).setY(0.6));
        }
        BossTrait extra = plugin.traits().rollExtra(e, boss.traits());
        if (extra != null) {
            boss.addTrait(extra);
            extra.onApply(boss, true);
            saveTraits(boss);
        }
        Fx.play(e.getLocation(), "entity.warden.sonic_boom", 1.0f, 0.8f);
        Fx.particle(Fx.center(e), Particle.SONIC_BOOM, 1, 0, 0);
        Fx.particle(e.getLocation().add(0, 0.2, 0), Particle.END_ROD, 80, radius / 2.0, 0.05);
        plugin.presentation().messageNearby(boss, "ascendant-phase", 48);
    }

    // =====================================================================
    // Death + rewards
    // =====================================================================

    public void handleDeath(Boss boss, EntityDeathEvent event) {
        LivingEntity e = boss.entity();
        Player killer = e.getKiller();
        if (killer == null) {
            killer = boss.lastEngagedPlayer();
        }
        RankSettings rs = settings().rank(boss.rank());
        FeatureSettings f = settings().features;
        boolean eligible = !settings().requirePlayerForRewards || killer != null;

        if (eligible) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * rs.xpMultiplier()) + rs.xpBonus());
            List<ItemStack> rewards = new ArrayList<>(plugin.rewards().rollWithFloor(boss, killer));

            // Revenge: this boss killed the player who finally brought it down.
            boolean revenge = killer != null && boss.killed(killer.getUniqueId());
            if (revenge) {
                event.setDroppedExp((int) Math.round(event.getDroppedExp() * f.nemesisRevengeXpMultiplier));
                rewards.addAll(plugin.rewards().roll(boss, killer));
                plugin.presentation().tell(killer, "revenge");
            }
            if (boss.isNemesis()) {
                plugin.nemesis().onSlain(boss, killer, event, rewards);
            }
            // Bosses players made (Totems, Escalation, commands) don't drop Souls or Totems, so they can't feed
            // themselves. A Nemesis always drops Souls; one called by its Challenge just no Totem.
            String created = created(e);
            boolean soulsOk = boss.isNemesis() || created == null;
            boolean totemOk = boss.isNemesis() ? !"challenge".equals(created) : created == null;
            // A wild boss raised with Souls pays Souls and Totems for the rank it was found at, so spending
            // Souls never makes more of them.
            BossRank wild = boss.isNemesis() ? boss.rank() : wildRank(e, boss.rank());
            if (f.soulsEnabled && soulsOk && (boss.isNemesis() || Rng.chance(f.soulChance.getOrDefault(wild, 0.0)))) {
                int[] count = f.soulCount.getOrDefault(wild, new int[]{1, 1});
                rewards.add(plugin.items().createSoul(wild, Rng.between(count[0], count[1])));
            }
            if (f.waystonesEnabled) {
                int stones = boss.rank() == BossRank.ASCENDANT ? f.waystoneAscendantDrops : 0;
                if (Rng.chance(f.waystoneDropChance.getOrDefault(boss.rank(), 0.0))) {
                    stones++;
                }
                for (int i = 0; i < stones; i++) {
                    rewards.add(plugin.waystones().createItem(null));
                }
            }
            if (f.totemEnabled && totemOk && Rng.chance(f.totemDropChance.getOrDefault(wild, 0.0))) {
                // a Totem of its own rank (sometimes one higher), up to Legendary: Ascendants are only ever raised
                BossRank called = wild.atLeast(BossRank.GOLD) ? BossRank.GOLD : wild;
                if (called != BossRank.GOLD && Rng.chance(f.totemHigherChance)) {
                    called = called.up(1);
                }
                rewards.add(plugin.items().createTotem(called));
            }
            dropRewards(boss, rewards);
            if (killer != null) {
                PlayerData.addKill(killer, boss.rank());
                plugin.escalation().recordKill(killer);
                if (!rewards.isEmpty() && settings().bossChat) {
                    Component list = Component.empty();
                    for (int i = 0; i < rewards.size(); i++) {
                        if (i > 0) {
                            list = list.append(Component.text(", "));
                        }
                        list = list.append(rewards.get(i).displayName());
                    }
                    killer.sendMessage(settings().messages.prefixed("rewards", Placeholder.component("items", list)));
                }
            }
        } else if (boss.isNemesis()) {
            plugin.nemesis().onSlain(boss, null, event, new ArrayList<>());
        }

        for (BossTrait trait : boss.traits()) {
            trait.onDeath(boss);
        }
        plugin.presentation().death(boss, killer);
        removeMinions(boss);
        plugin.bossBars().remove(boss);
    }

    // =====================================================================
    // Anti-trap + Last Stand
    // =====================================================================

    /** Pulls bosses out of vehicles and frees them if they can't reach their target for a while. */
    private void checkStuck(Boss boss, int now) {
        FeatureSettings f = settings().features;
        LivingEntity e = boss.entity();
        if (f.blockVehicles && e.isInsideVehicle()) {
            e.leaveVehicle();
        }
        if (!f.unstuckEnabled || !BaseTrait.walks(e)) {
            return;
        }
        Player target = boss.target();
        boolean wantsToMove = false;
        if (target != null) {
            double dist = target.getLocation().distance(e.getLocation());
            boolean ranged = e instanceof RangedEntity;
            // Archers standing still and shooting are fine; only count them stuck when they can't see you.
            wantsToMove = dist > 3.5 && dist < 48 && (!ranged || !e.hasLineOfSight(target));
        }
        if (boss.stuckTicks(now, wantsToMove) < f.unstuckTicks || target == null) {
            return;
        }
        boss.resetStuck();
        Location spot = SafeSpots.behind(target, 2.5);
        if (spot != null) {
            Fx.particle(Fx.center(e), Particle.PORTAL, 30, 0.5, 0.4);
            e.teleport(spot);
            Fx.particle(Fx.center(e), Particle.PORTAL, 30, 0.5, 0.4);
            Fx.play(spot, "entity.enderman.teleport", 1.0f, 0.7f);
        } else {
            // Nowhere to stand near the player (e.g. a 1x1 pillar): drag the player down instead.
            org.bukkit.util.Vector pull = e.getLocation().toVector().subtract(target.getLocation().toVector());
            if (pull.lengthSquared() > 0.01) {
                target.setVelocity(pull.normalize().multiply(1.1).setY(0.2));
            }
            Fx.play(target.getLocation(), "entity.warden.sonic_charge", 0.8f, 1.2f);
        }
        plugin.presentation().messageNearby(boss, "unstuck", 32);
    }

    /** Purple/Gold bosses (and every Nemesis) make a Last Stand once at low health. */
    public void checkLastStand(Boss boss, double healthAfter) {
        FeatureSettings f = settings().features;
        if (boss.lastStand() || healthAfter <= 0) {
            return;
        }
        if (!boss.isNemesis() && !f.lastStandRanks.contains(boss.rank())) {
            return;
        }
        if (healthAfter / boss.maxHealth() * 100.0 > f.lastStandHealthPercent) {
            return;
        }
        LivingEntity e = boss.entity();
        boss.setLastStand(true);
        e.getPersistentDataContainer().set(Keys.BOSS_LAST_STAND, PersistentDataType.BYTE, (byte) 1);
        boss.scalePower(1.0 + f.lastStandPowerBonus / 100.0);
        if (f.lastStandDormantTrait) {
            BossTrait extra = plugin.traits().rollExtra(e, boss.traits());
            if (extra != null) {
                boss.addTrait(extra);
                extra.onApply(boss, true);
                saveTraits(boss);
            }
        }
        e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false));
        Fx.play(e.getLocation(), "entity.ender_dragon.growl", 0.9f, 0.8f);
        Fx.dust(Fx.center(e), boss.rank().bukkitColor(), 2.0f, 40, 1.0);
        Fx.particle(Fx.center(e), Particle.SOUL_FIRE_FLAME, 40, 0.8, 0.05);
        plugin.presentation().messageNearby(boss, "last-stand", 40);
    }

    public void saveTraits(Boss boss) {
        boss.entity().getPersistentDataContainer().set(Keys.BOSS_TRAITS, PersistentDataType.STRING,
            String.join(",", boss.traitIds()));
    }

    private void dropRewards(Boss boss, List<ItemStack> rewards) {
        Location at = boss.entity().getLocation().add(0, 0.5, 0);
        World world = at.getWorld();
        for (ItemStack reward : rewards) {
            world.dropItemNaturally(at, reward, (Item drop) -> {
                drop.setGlowing(true);
                drop.setInvulnerable(true);
                drop.setUnlimitedLifetime(true);
            });
        }
    }

    public void removeMinions(Boss boss) {
        for (UUID id : boss.minions()) {
            Entity minion = Bukkit.getEntity(id);
            if (minion != null && minion.isValid()) {
                Fx.particle(Fx.center(minion), Particle.SMOKE, 10, 0.3, 0.02);
                minion.remove();
            }
        }
        boss.minions().clear();
    }

    // =====================================================================
    // Startup / shutdown / admin
    // =====================================================================

    public int restoreLoaded() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity e : world.getLivingEntities()) {
                if (isBoss(e) && restore(e) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    public int killAll() {
        int count = 0;
        for (Boss boss : List.copyOf(active.values())) {
            removeMinions(boss);
            unregister(boss);
            boss.entity().remove();
            count++;
        }
        return count;
    }

    public void shutdown() {
        for (Boss boss : List.copyOf(active.values())) {
            glow(boss, false); // not saved glowing, and no stale scoreboard entries
            plugin.bossBars().remove(boss);
        }
        active.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }
}
