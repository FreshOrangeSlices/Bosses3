package com.additionalbosses.relic;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.relic.effects.Auras;
import com.additionalbosses.relic.effects.Burdened;
import com.additionalbosses.relic.effects.Curses;
import com.additionalbosses.relic.effects.HauntCurses;
import com.additionalbosses.relic.effects.MoreCurses;
import com.additionalbosses.relic.effects.NatureCurses;
import com.additionalbosses.relic.effects.Relics;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Registry of Relic effects and Curses, plus the runtime that activates them.
 *
 * <p>Each player's active relics are read from their equipment once and cached; the cache is rebuilt only when
 * their equipment changes. Passive relics (like Owl's Sight) are handled by one shared task that only runs while
 * at least one online player has a passive relic equipped.</p>
 */
public final class RelicManager {

    public record Active(RelicEffect effect, RelicContext.Slot slot) {
    }

    private final AdditionalBosses plugin;
    private final Map<String, RelicEffect> registry = new LinkedHashMap<>();
    private final Set<String> enabled = new HashSet<>();
    private final Map<String, Double> weights = new HashMap<>();

    private final Map<UUID, List<Active>> cache = new HashMap<>();
    private final Set<UUID> passivePlayers = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> cooldowns = new HashMap<>();
    /** Curses put on a player for testing (/bosses curse): curse id -> server tick it wears off. */
    private final Map<UUID, Map<String, Integer>> tests = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> testTasks = new HashMap<>();
    private @Nullable BukkitTask passiveTask;

    public RelicManager(AdditionalBosses plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /** Built-in relics and curses. Add new ones here. */
    private void registerDefaults() {
        register(new Relics.BloodPact());
        register(new Relics.SoulHarvest());
        register(new Relics.SecondDawn());
        register(new Relics.BrambleHeart());
        register(new Relics.Emberheart());
        register(new Relics.Frostbite());
        register(new Relics.Stormcaller());
        register(new Relics.Windstep());
        register(new Relics.HuntersMark());
        register(new Relics.EchoStrike());
        register(new Relics.OwlsSight());
        register(new Relics.Lodestone());
        register(new Relics.Aegis());
        register(new Relics.Featherweight());
        register(new Relics.Prospector());
        register(Auras.ironWill());
        register(Auras.bloodMending());
        register(Auras.skybound());
        register(Auras.emberWard());
        register(Auras.tidebound());
        register(Auras.oceanGrace());
        register(Auras.villagerFavor());
        register(Auras.minersFavor());
        register(Auras.nightstalker());
        register(Auras.sunblessed());
        register(new Burdened.Greed());
        register(new Burdened.HeavyCrown());

        register(new Curses.Dread());
        register(new Curses.Butterfingers());
        register(new Curses.FowlOmen());
        register(new Curses.Insomnia());
        register(new Curses.Limelight());
        register(new Curses.Gluttony());
        register(new Curses.Recoil());
        register(new Curses.GlassBones());
        register(new Curses.BloodTithe());
        register(new MoreCurses.Terror());
        register(new MoreCurses.Echoes());
        register(new MoreCurses.Reduction());
        register(new MoreCurses.Matador());
        register(new MoreCurses.MotherHen());
        register(new NatureCurses.Pariah());
        register(new NatureCurses.Herbivore());
        register(new HauntCurses.WitheringWaters());
        register(new HauntCurses.UninvitedGuest());
        register(new HauntCurses.DontBlink());
        register(new HauntCurses.Poltergeist());
        register(new HauntCurses.RestlessDead());
        register(new HauntCurses.Hiccups());
    }

    public void register(RelicEffect effect) {
        registry.put(effect.id(), effect);
    }

    public void load(@Nullable ConfigurationSection relicsSection, Logger log) {
        enabled.clear();
        weights.clear();
        ConfigurationSection root = relicsSection == null ? new MemoryConfiguration() : relicsSection;
        for (RelicEffect effect : registry.values()) {
            String path = (effect.curse() ? "curses." : "effects.") + effect.id();
            ConfigurationSection s = root.getConfigurationSection(path);
            if (s == null) {
                s = new MemoryConfiguration();
            }
            if (s.getBoolean("enabled", true)) {
                enabled.add(effect.id());
            }
            weights.put(effect.id(), Math.max(0, s.getDouble("weight", effect.defaultWeight())));
            effect.load(s);
        }
        for (String group : List.of("effects", "curses")) {
            ConfigurationSection g = root.getConfigurationSection(group);
            if (g == null) {
                continue;
            }
            for (String key : g.getKeys(false)) {
                if (!registry.containsKey(key)) {
                    log.warning("relics." + group + "." + key + " does not match any relic (ignored)");
                }
            }
        }
        // Let relics that just got disabled clean up (Don't Blink's Creaking, Mother Hen's chicks...).
        for (Player p : Bukkit.getOnlinePlayers()) {
            deactivateMissing(p, cache.get(p.getUniqueId()), List.of());
        }
        cache.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    // =====================================================================
    // Registry
    // =====================================================================

    public @Nullable RelicEffect get(@Nullable String id) {
        return id == null ? null : registry.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<RelicEffect> all() {
        return registry.values();
    }

    public boolean isEnabled(RelicEffect effect) {
        return enabled.contains(effect.id());
    }

    public List<RelicEffect> enabledEffects(boolean curses) {
        List<RelicEffect> out = new ArrayList<>();
        for (RelicEffect e : registry.values()) {
            if (e.curse() == curses && enabled.contains(e.id())) {
                out.add(e);
            }
        }
        return out;
    }

    public @Nullable RelicEffect rollEffect() {
        return Rng.weighted(enabledEffects(false), e -> weights.getOrDefault(e.id(), 0.0));
    }

    public @Nullable RelicEffect rollCurse() {
        return Rng.weighted(enabledEffects(true), e -> weights.getOrDefault(e.id(), 0.0));
    }

    // =====================================================================
    // Active relic cache
    // =====================================================================

    public List<Active> active(Player player) {
        List<Active> list = cache.get(player.getUniqueId());
        return list != null ? list : refresh(player);
    }

    /** Re-reads the player's equipment. Called only when equipment changes. */
    public List<Active> refresh(Player player) {
        Map<String, Active> found = new LinkedHashMap<>();
        PlayerInventory inv = player.getInventory();
        bindWornCursedArmor(inv);
        collect(inv.getItemInMainHand(), RelicContext.Slot.HAND, found);
        for (ItemStack armor : inv.getArmorContents()) {
            collect(armor, RelicContext.Slot.ARMOR, found);
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == Material.SHIELD) {
            collect(off, RelicContext.Slot.ARMOR, found);
        }
        Map<String, Integer> testing = tests.get(player.getUniqueId());
        if (testing != null) {
            int now = Bukkit.getCurrentTick();
            for (Map.Entry<String, Integer> test : testing.entrySet()) {
                RelicEffect effect = get(test.getKey());
                if (effect != null && test.getValue() > now) {
                    found.putIfAbsent(effect.id() + "@test", new Active(effect, RelicContext.Slot.ARMOR));
                }
            }
        }
        List<Active> list = List.copyOf(found.values());
        List<Active> previous = cache.put(player.getUniqueId(), list);
        deactivateMissing(player, previous, list);
        applyAttributes(player, list);

        boolean hasPassive = false;
        for (Active a : list) {
            if (a.effect().passive()) {
                hasPassive = true;
                break;
            }
        }
        if (hasPassive) {
            passivePlayers.add(player.getUniqueId());
            if (passiveTask == null) {
                passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassives, 20L, 20L);
            }
        } else {
            passivePlayers.remove(player.getUniqueId());
        }
        return list;
    }

    private static final org.bukkit.inventory.EquipmentSlot[] ARMOR_SLOTS = {org.bukkit.inventory.EquipmentSlot.HEAD,
        org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET};

    /** Cursed armor that was bound before Curse of Binding was added gets it as soon as it is worn. */
    private void bindWornCursedArmor(PlayerInventory inv) {
        for (org.bukkit.inventory.EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = inv.getItem(slot);
            if (!worn.isEmpty() && worn.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.BINDING_CURSE) <= 0
                && plugin.items().bindCursedArmor(worn)) {
                inv.setItem(slot, worn);
            }
        }
    }

    private void collect(@Nullable ItemStack item, RelicContext.Slot slot, Map<String, Active> out) {
        if (item == null || item.isEmpty()) {
            return;
        }
        PersistentDataContainerView view = item.getPersistentDataContainer();
        for (NamespacedKey key : List.of(Keys.RELICS, Keys.CURSES)) {
            List<String> ids = view.get(key, PersistentDataType.LIST.strings());
            if (ids == null) {
                continue;
            }
            for (String id : ids) {
                RelicEffect effect = get(id);
                if (effect != null && enabled.contains(effect.id())) {
                    // The same relic on two worn pieces only counts once.
                    out.putIfAbsent(effect.id() + "@" + slot, new Active(effect, slot));
                }
            }
        }
    }

    /** Calls onDeactivate for relics that were active before but aren't any more. */
    private void deactivateMissing(Player player, @Nullable List<Active> before, List<Active> after) {
        if (before == null) {
            return;
        }
        Set<String> still = new HashSet<>();
        for (Active a : after) {
            still.add(a.effect().id());
        }
        Set<String> done = new HashSet<>();
        for (Active a : before) {
            if (!still.contains(a.effect().id()) && done.add(a.effect().id())) {
                a.effect().onDeactivate(player);
            }
        }
    }

    /**
     * Relic stat changes are transient attribute modifiers: they are never saved, so they can't get stuck after a
     * crash, logout or uninstall. They are rebuilt here every time the player's equipment changes.
     */
    private void applyAttributes(Player player, List<Active> active) {
        for (RelicEffect effect : registry.values()) {
            List<RelicEffect.AttributeBonus> bonuses = effect.attributeBonuses();
            for (int i = 0; i < bonuses.size(); i++) {
                AttributeInstance inst = player.getAttribute(bonuses.get(i).attribute());
                if (inst != null) {
                    inst.removeModifier(attributeKey(effect, i));
                }
            }
        }
        Set<String> applied = new HashSet<>();
        for (Active a : active) {
            RelicEffect effect = a.effect();
            if (!applied.add(effect.id())) {
                continue;
            }
            List<RelicEffect.AttributeBonus> bonuses = effect.attributeBonuses();
            for (int i = 0; i < bonuses.size(); i++) {
                RelicEffect.AttributeBonus b = bonuses.get(i);
                AttributeInstance inst = player.getAttribute(b.attribute());
                if (inst != null) {
                    inst.addTransientModifier(new AttributeModifier(attributeKey(effect, i), b.amount(), b.operation()));
                }
            }
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }

    private NamespacedKey attributeKey(RelicEffect effect, int index) {
        return new NamespacedKey(plugin, "relic_" + effect.id().replace('-', '_') + "_" + index);
    }

    // =====================================================================
    // Testing (/bosses curse)
    // =====================================================================

    /** The player has this curse (or relic) for a while, exactly as if they were wearing it. */
    public void addTest(Player player, RelicEffect effect, int seconds) {
        UUID id = player.getUniqueId();
        int until = Bukkit.getCurrentTick() + seconds * 20;
        tests.computeIfAbsent(id, k -> new HashMap<>()).put(effect.id(), until);
        refresh(player);
        trackTest(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<String, Integer> map = tests.get(id);
            if (map == null || !Integer.valueOf(until).equals(map.get(effect.id()))) {
                return; // replaced by a newer test, or cleared
            }
            map.remove(effect.id());
            if (map.isEmpty()) {
                tests.remove(id);
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                refresh(p);
            }
        }, seconds * 20L));
    }

    /** Fires the effect's event every {@code everyTicks} for {@code seconds}, whatever its normal odds. */
    public void repeatTest(Player player, RelicEffect effect, int seconds, int everyTicks) {
        UUID id = player.getUniqueId();
        int total = seconds * 20;
        trackTest(id, new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || elapsed >= total) {
                    cancel();
                    return;
                }
                if (!p.isDead()) {
                    effect.trigger(p, RelicManager.this);
                }
                elapsed += everyTicks;
            }
        }.runTaskTimer(plugin, 0L, everyTicks));
    }

    /** True if the effect has a single event that /bosses curse can fire (a hiccup, a visit...). */
    public static boolean hasTrigger(RelicEffect effect) {
        try {
            return effect.getClass().getMethod("trigger", Player.class, RelicManager.class).getDeclaringClass()
                != RelicEffect.class;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private void trackTest(UUID id, BukkitTask task) {
        List<BukkitTask> list = testTasks.computeIfAbsent(id, k -> new ArrayList<>());
        list.removeIf(BukkitTask::isCancelled);
        list.add(task);
    }

    /** Ends every test on the player. Returns how many curses were being tested. */
    public int clearTests(Player player) {
        List<BukkitTask> tasks = testTasks.remove(player.getUniqueId());
        if (tasks != null) {
            tasks.forEach(BukkitTask::cancel);
        }
        Map<String, Integer> map = tests.remove(player.getUniqueId());
        refresh(player);
        return map == null ? 0 : map.size();
    }

    public void forget(Player player) {
        List<BukkitTask> tasks = testTasks.remove(player.getUniqueId());
        if (tasks != null) {
            tasks.forEach(BukkitTask::cancel);
        }
        tests.remove(player.getUniqueId());
        List<Active> before = cache.get(player.getUniqueId());
        deactivateMissing(player, before, List.of());
        applyAttributes(player, List.of());
        cache.remove(player.getUniqueId());
        passivePlayers.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }

    private void tickPassives() {
        if (passivePlayers.isEmpty()) {
            if (passiveTask != null) {
                passiveTask.cancel();
                passiveTask = null;
            }
            return;
        }
        for (UUID id : List.copyOf(passivePlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                passivePlayers.remove(id);
                continue;
            }
            if (p.isDead()) {
                continue;
            }
            Set<String> ran = new HashSet<>();
            for (Active a : active(p)) {
                // The same relic on a held item and on armor ticks once, not twice.
                if (a.effect().passive() && ran.add(a.effect().id())) {
                    a.effect().onPassive(context(p, a));
                }
            }
        }
    }

    public void shutdown() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            forget(p);
        }
        cache.clear();
        passivePlayers.clear();
        cooldowns.clear();
    }

    /** Per-player cooldown helper. Returns true (and starts the cooldown) if ready. */
    public boolean ready(Player player, String key, int cooldownTicks) {
        int now = Bukkit.getCurrentTick();
        Map<String, Integer> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Integer until = map.get(key);
        if (until != null && now < until) {
            return false;
        }
        map.put(key, now + cooldownTicks);
        return true;
    }

    private RelicContext context(Player p, Active a) {
        return new RelicContext(p, a.slot(), this);
    }

    // =====================================================================
    // Hooks (called from listeners)
    // =====================================================================

    public void onAttack(Player p, LivingEntity victim, EntityDamageEvent event, DamageContext damage, boolean victimIsBoss) {
        for (Active a : active(p)) {
            a.effect().onAttack(context(p, a), victim, event, damage, victimIsBoss);
        }
    }

    public void afterAttack(Player p, LivingEntity victim, double finalDamage, DamageContext damage) {
        for (Active a : active(p)) {
            a.effect().afterAttack(context(p, a), victim, finalDamage, damage);
        }
    }

    public void onDamaged(Player p, EntityDamageEvent event, DamageContext damage) {
        for (Active a : active(p)) {
            a.effect().onDamaged(context(p, a), event, damage);
        }
    }

    public void afterDamaged(Player p, EntityDamageEvent event, double finalDamage, DamageContext damage) {
        for (Active a : active(p)) {
            a.effect().afterDamaged(context(p, a), event, finalDamage, damage);
        }
    }

    public void onKill(Player p, EntityDeathEvent event, boolean victimWasBoss) {
        for (Active a : active(p)) {
            a.effect().onKill(context(p, a), event, victimWasBoss);
        }
    }

    public boolean tryResurrect(Player p) {
        for (Active a : active(p)) {
            if (a.effect().onLethal(context(p, a))) {
                return true;
            }
        }
        return false;
    }

    public void onJump(Player p, PlayerJumpEvent event) {
        for (Active a : active(p)) {
            a.effect().onJump(context(p, a), event);
        }
    }

    /** True if the player currently has this relic or curse active. */
    public boolean has(Player p, String id) {
        for (Active a : active(p)) {
            if (a.effect().id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean preventsSleep(Player p) {
        for (Active a : active(p)) {
            if (a.effect().preventsSleep()) {
                return true;
            }
        }
        return false;
    }

    public double rewardMultiplier(@Nullable Player p) {
        if (p == null) {
            return 1.0;
        }
        double mult = 1.0;
        for (Active a : active(p)) {
            mult *= a.effect().rewardChanceMultiplier();
        }
        return mult;
    }
}
