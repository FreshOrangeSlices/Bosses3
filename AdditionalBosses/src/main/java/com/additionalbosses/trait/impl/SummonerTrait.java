package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Special: calls minions at health thresholds. Minions drop nothing and vanish when the boss dies.
 */
public final class SummonerTrait extends BaseTrait {

    private static final Map<EntityType, EntityType> MINIONS = new EnumMap<>(EntityType.class);

    static {
        MINIONS.put(EntityType.ZOMBIE, EntityType.ZOMBIE);
        MINIONS.put(EntityType.HUSK, EntityType.HUSK);
        MINIONS.put(EntityType.DROWNED, EntityType.DROWNED);
        MINIONS.put(EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE);
        MINIONS.put(EntityType.SKELETON, EntityType.SKELETON);
        MINIONS.put(EntityType.STRAY, EntityType.STRAY);
        MINIONS.put(EntityType.BOGGED, EntityType.BOGGED);
        MINIONS.put(EntityType.PARCHED, EntityType.PARCHED);
        MINIONS.put(EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON);
        MINIONS.put(EntityType.SPIDER, EntityType.CAVE_SPIDER);
        MINIONS.put(EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER);
        MINIONS.put(EntityType.SILVERFISH, EntityType.SILVERFISH);
        MINIONS.put(EntityType.ENDERMITE, EntityType.ENDERMITE);
        MINIONS.put(EntityType.PIGLIN, EntityType.PIGLIN);
        MINIONS.put(EntityType.PIGLIN_BRUTE, EntityType.PIGLIN);
        MINIONS.put(EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOMBIFIED_PIGLIN);
        MINIONS.put(EntityType.PILLAGER, EntityType.PILLAGER);
        MINIONS.put(EntityType.VINDICATOR, EntityType.PILLAGER);
        MINIONS.put(EntityType.EVOKER, EntityType.VINDICATOR);
        MINIONS.put(EntityType.ILLUSIONER, EntityType.PILLAGER);
        MINIONS.put(EntityType.RAVAGER, EntityType.PILLAGER);
        MINIONS.put(EntityType.BLAZE, EntityType.BLAZE);
        MINIONS.put(EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN);
        MINIONS.put(EntityType.ZOGLIN, EntityType.ZOMBIFIED_PIGLIN);
    }

    private int minions = 2;
    private List<Double> thresholds = List.of(75.0, 50.0, 25.0);

    public SummonerTrait() {
        super("summoner", TraitCategory.SPECIAL, "Summoner", "Summoning");
    }

    @Override
    public void load(ConfigurationSection s) {
        minions = Math.max(1, s.getInt("minions", 2));
        List<Double> list = s.getDoubleList("health-thresholds");
        thresholds = list.isEmpty() ? List.of(75.0, 50.0, 25.0) : List.copyOf(list);
    }

    @Override
    public String description() {
        StringJoiner joiner = new StringJoiner("/");
        for (double t : thresholds) {
            joiner.add(com.additionalbosses.util.Text.num(t) + "%");
        }
        return "Calls " + minions + "+ minions at " + joiner + " health. Minions drop nothing.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return MINIONS.containsKey(entity.getType());
    }

    @Override
    public void onTick(Boss boss, int now) {
        check(boss, boss.health());
    }

    @Override
    public void afterDamaged(Boss boss, EntityDamageEvent event, double finalDamage, DamageContext ctx) {
        check(boss, boss.health() - finalDamage);
    }

    private void check(Boss boss, double health) {
        if (health <= 0 || !boss.inCombat()) {
            return;
        }
        double percent = health / boss.maxHealth() * 100.0;
        for (double t : thresholds) {
            String flag = "summon_" + t;
            if (percent <= t && !boss.flag(flag)) {
                boss.setFlag(flag, true);
                summon(boss);
                return; // one wave at a time
            }
        }
    }

    private void summon(Boss boss) {
        LivingEntity e = boss.entity();
        EntityType type = MINIONS.get(e.getType());
        if (type == null || type.getEntityClass() == null) {
            return;
        }
        int count = minions + (boss.power() >= 1.5 ? 1 : 0);
        Player target = boss.target();
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = spot(e.getLocation());
            Entity minion = e.getWorld().spawn(at, type.getEntityClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
                m.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, boss.uuid().toString());
                if (m instanceof Ageable ageable) {
                    ageable.setAdult();
                }
                if (m instanceof LivingEntity living) {
                    EntityEquipment eq = living.getEquipment();
                    if (eq != null) {
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            try {
                                eq.setDropChance(slot, 0f);
                            } catch (IllegalArgumentException ignored) {
                                // slot not supported by this mob
                            }
                        }
                    }
                }
            });
            if (minion instanceof Mob mob && target != null) {
                mob.setTarget(target);
            }
            boss.minions().add(minion.getUniqueId());
            spawned.add(minion);
        }
        Fx.play(e.getLocation(), "entity.evoker.prepare_summon", 1.0f, 1.0f);
        for (Entity m : spawned) {
            Fx.particle(Fx.center(m), Particle.SOUL, 8, 0.3, 0.03);
        }
    }

    private static Location spot(Location around) {
        for (int attempt = 0; attempt < 6; attempt++) {
            Location c = around.clone().add(Rng.between(-2.5, 2.5), 0, Rng.between(-2.5, 2.5));
            Block feet = c.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.isLiquid()) {
                return c;
            }
        }
        return around.clone();
    }
}
