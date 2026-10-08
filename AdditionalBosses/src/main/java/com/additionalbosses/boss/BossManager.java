package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
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
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

    // =====================================================================
    // Natural spawns
    // =====================================================================

    public void handleSpawn(LivingEntity entity, SpawnReason reason) {
        PluginSettings s = settings();
        if (!s.enabled || !Bukkit.isPrimaryThread() || isBoss(entity) || isMinion(entity)) {
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
        if (active.size() >= s.maxActive || tooCloseToAnotherBoss(entity.getLocation())) {
            return;
        }
        // Roll #2: which rank? (weighted by the mob's category)
        BossRank rank = category.rollRank();
        createBoss(entity, rank, category.id(), null, true);
    }

    /** Paper gives mobs placed by structures/world generation the DEFAULT spawn reason. */
    public static boolean isWorldgen(@Nullable SpawnReason reason) {
        return reason == SpawnReason.DEFAULT;
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
        PluginSettings s = settings();
        RankSettings rs = s.rank(rank);
        MobProfile profile = s.profileFor(entity.getType());

        if (traits == null) {
            int count = Rng.between(rs.traits().min(), rs.traits().max());
            traits = plugin.traits().roll(entity, count);
        }

        applyStats(entity, rs.stats(), profile);

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
        if (s.displayGear) {
            applyDisplayGear(entity, rank);
        }
        for (BossTrait t : traits) {
            t.onApply(boss, true);
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
            if (t != null) {
                traits.add(t);
            }
        }
        Boss boss = build(entity, rank, category, traits);
        boss.setUndyingUsed(pdc.has(Keys.BOSS_UNDYING, PersistentDataType.BYTE));
        for (BossTrait t : traits) {
            t.onApply(boss, false);
        }
        register(boss);
        return boss;
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
        String adjective = traits.isEmpty() ? "" : traits.get(0).adjective() + " ";
        String plain = rank.starText() + " " + rs.name() + " " + adjective + Text.pretty(entity.getType().name());
        TextComponent name = Component.text(plain, rank.color());
        Component styled = rank == BossRank.GOLD ? name.decorate(TextDecoration.BOLD) : name;
        double damage = MobProfile.scaleMultiplier(rs.stats().damage(), profile.damage());
        return new Boss(entity, rank, categoryId, traits, styled, plain, damage, rs.traits().power());
    }

    private void applyStats(LivingEntity e, RankSettings.Stats st, MobProfile p) {
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
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            e.setHealth(max.getValue());
        }
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

    /**
     * Cosmetic armour dyed in the rank colour. It is unbreakable, gives no armour points, and never drops:
     * the real reward is generated separately when the boss dies.
     */
    private static void applyDisplayGear(LivingEntity e, BossRank rank) {
        if (!(e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof PiglinAbstract)) {
            return;
        }
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(cosmetic(Material.LEATHER_HELMET, rank));
        if (rank.atLeast(BossRank.RED)) {
            eq.setBoots(cosmetic(Material.LEATHER_BOOTS, rank));
        }
        if (rank.atLeast(BossRank.PURPLE)) {
            eq.setChestplate(cosmetic(Material.LEATHER_CHESTPLATE, rank));
        }
        if (rank == BossRank.GOLD) {
            eq.setLeggings(cosmetic(Material.LEATHER_LEGGINGS, rank));
        }
        clearDropChances(e);
    }

    private static ItemStack cosmetic(Material material, BossRank rank) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(rank.bukkitColor()));
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        item.setData(DataComponentTypes.UNBREAKABLE);
        if (rank.atLeast(BossRank.PURPLE)) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
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
            boss.expireEngagements(now, timeout);
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

    public void engage(Boss boss, Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean first = !boss.engaged().containsKey(player.getUniqueId());
        boss.engage(player, Bukkit.getCurrentTick());
        if (first) {
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
    // Death + rewards
    // =====================================================================

    public void handleDeath(Boss boss, EntityDeathEvent event) {
        LivingEntity e = boss.entity();
        Player killer = e.getKiller();
        if (killer == null) {
            killer = boss.lastEngagedPlayer();
        }
        RankSettings rs = settings().rank(boss.rank());
        boolean eligible = !settings().requirePlayerForRewards || killer != null;

        if (eligible) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * rs.xpMultiplier()) + rs.xpBonus());
            List<ItemStack> rewards = plugin.rewards().roll(boss, killer);
            dropRewards(boss, rewards);
            if (killer != null) {
                PlayerData.addKill(killer, boss.rank());
                if (!rewards.isEmpty()) {
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
        }

        for (BossTrait trait : boss.traits()) {
            trait.onDeath(boss);
        }
        plugin.presentation().death(boss, killer);
        removeMinions(boss);
        plugin.bossBars().remove(boss);
    }

    private void dropRewards(Boss boss, List<ItemStack> rewards) {
        Location at = boss.entity().getLocation().add(0, 0.5, 0);
        World world = at.getWorld();
        for (ItemStack reward : rewards) {
            world.dropItemNaturally(at, reward, (Item drop) -> {
                drop.setGlowing(true);
                drop.setInvulnerable(true);
                drop.setUnlimitedLifetime(true);
                drop.customName(reward.effectiveName());
                drop.setCustomNameVisible(true);
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
            plugin.bossBars().remove(boss);
        }
        active.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }
}
