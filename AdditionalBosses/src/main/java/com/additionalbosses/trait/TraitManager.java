package com.additionalbosses.trait;

import com.additionalbosses.trait.impl.BerserkTrait;
import com.additionalbosses.trait.impl.BlinkingTrait;
import com.additionalbosses.trait.impl.BulwarkTrait;
import com.additionalbosses.trait.impl.ChargingTrait;
import com.additionalbosses.trait.impl.DeadeyeTrait;
import com.additionalbosses.trait.impl.ExecutionerTrait;
import com.additionalbosses.trait.impl.FrostboundTrait;
import com.additionalbosses.trait.impl.GraviticTrait;
import com.additionalbosses.trait.impl.InfernalTrait;
import com.additionalbosses.trait.impl.LeapingTrait;
import com.additionalbosses.trait.impl.QuakingTrait;
import com.additionalbosses.trait.impl.RegeneratingTrait;
import com.additionalbosses.trait.impl.ShadowedTrait;
import com.additionalbosses.trait.impl.SummonerTrait;
import com.additionalbosses.trait.impl.SwiftTrait;
import com.additionalbosses.trait.impl.ThornedTrait;
import com.additionalbosses.trait.impl.UndyingTrait;
import com.additionalbosses.trait.impl.VampiricTrait;
import com.additionalbosses.trait.impl.VenomousTrait;
import com.additionalbosses.trait.impl.VolatileTrait;
import com.additionalbosses.trait.impl.VolleyTrait;
import com.additionalbosses.trait.impl.WardedTrait;
import com.additionalbosses.util.Rng;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
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
import java.util.logging.Logger;

/**
 * Registry of boss traits, plus the logic that rolls a compatible set of traits for a new boss.
 */
public final class TraitManager {

    private final Map<String, BossTrait> registry = new LinkedHashMap<>();
    private final Set<String> enabled = new HashSet<>();
    private final Map<String, Double> weights = new HashMap<>();
    private final Set<String> blockedPairs = new HashSet<>();

    public TraitManager() {
        registerDefaults();
    }

    /** Built-in traits. Add new ones here. */
    private void registerDefaults() {
        // Offense
        register(new BerserkTrait());
        register(new VampiricTrait());
        register(new VenomousTrait());
        register(new ExecutionerTrait());
        register(new DeadeyeTrait());
        register(new VolleyTrait());
        // Defense
        register(new BulwarkTrait());
        register(new RegeneratingTrait());
        register(new WardedTrait());
        register(new UndyingTrait());
        register(new ThornedTrait());
        // Movement
        register(new SwiftTrait());
        register(new LeapingTrait());
        register(new BlinkingTrait());
        register(new ChargingTrait());
        // Control
        register(new FrostboundTrait());
        register(new ShadowedTrait());
        register(new GraviticTrait());
        register(new QuakingTrait());
        // Special
        register(new SummonerTrait());
        register(new VolatileTrait());
        register(new InfernalTrait());
    }

    public void register(BossTrait trait) {
        registry.put(trait.id(), trait);
    }

    public void load(@Nullable ConfigurationSection section, Logger log) {
        enabled.clear();
        weights.clear();
        blockedPairs.clear();
        ConfigurationSection root = section == null ? new MemoryConfiguration() : section;
        for (BossTrait trait : registry.values()) {
            ConfigurationSection s = root.getConfigurationSection(trait.id());
            if (s == null) {
                s = new MemoryConfiguration();
            }
            if (s.getBoolean("enabled", true)) {
                enabled.add(trait.id());
            }
            weights.put(trait.id(), Math.max(0, s.getDouble("weight", 10)));
            trait.load(s);
        }
        for (String key : root.getKeys(false)) {
            if (!key.equals("incompatible") && !registry.containsKey(key)) {
                log.warning("traits." + key + " does not match any trait (ignored)");
            }
        }
        for (Object entry : root.getList("incompatible", List.of())) {
            if (entry instanceof List<?> pair && pair.size() == 2) {
                blockedPairs.add(pairKey(String.valueOf(pair.get(0)), String.valueOf(pair.get(1))));
            }
        }
    }

    public @Nullable BossTrait get(String id) {
        return registry.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<BossTrait> all() {
        return registry.values();
    }

    public boolean isEnabled(BossTrait trait) {
        return enabled.contains(trait.id());
    }

    public List<BossTrait> enabledTraits() {
        List<BossTrait> out = new ArrayList<>();
        for (BossTrait trait : registry.values()) {
            if (enabled.contains(trait.id())) {
                out.add(trait);
            }
        }
        return out;
    }

    public boolean compatible(BossTrait a, BossTrait b) {
        if (a.id().equals(b.id())) {
            return false;
        }
        if (a.incompatibleWith().contains(b.id()) || b.incompatibleWith().contains(a.id())) {
            return false;
        }
        return !blockedPairs.contains(pairKey(a.id(), b.id()));
    }

    /** Weighted roll without repeats, skipping traits the mob can't use or that clash with ones already picked. */
    public List<BossTrait> roll(LivingEntity entity, int count) {
        List<BossTrait> picked = new ArrayList<>();
        List<BossTrait> pool = new ArrayList<>();
        for (BossTrait trait : enabledTraits()) {
            if (trait.supports(entity) && weights.getOrDefault(trait.id(), 0.0) > 0) {
                pool.add(trait);
            }
        }
        while (picked.size() < count && !pool.isEmpty()) {
            BossTrait next = Rng.weighted(pool, t -> weights.getOrDefault(t.id(), 0.0));
            if (next == null) {
                break;
            }
            pool.remove(next);
            boolean ok = true;
            for (BossTrait chosen : picked) {
                if (!compatible(chosen, next)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                picked.add(next);
            }
        }
        return picked;
    }

    private static String pairKey(String a, String b) {
        a = a.trim().toLowerCase(Locale.ROOT);
        b = b.trim().toLowerCase(Locale.ROOT);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
