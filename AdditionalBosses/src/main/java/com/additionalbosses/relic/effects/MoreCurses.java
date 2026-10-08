package com.additionalbosses.relic.effects;

import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * More curses: atmospheric scares (Terror, Echoes), Reduction, Matador and Mother Hen.
 */
public final class MoreCurses {

    private MoreCurses() {
    }

    /** A spot a few blocks behind the player, for sounds that seem to come from behind. */
    static Location behind(Player p, double distance) {
        Vector back = p.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0);
        }
        back.normalize().multiply(-distance).rotateAroundY(Math.toRadians(Rng.between(-50.0, 50.0)));
        return p.getLocation().add(back);
    }

    static void playPrivately(Player p, String key, Location at, float volume, float pitch) {
        p.playSound(Sound.sound(Key.key(key), Sound.Source.HOSTILE, volume, pitch), at.getX(), at.getY(), at.getZ());
    }

    // ------------------------------------------------------------------

    public static final class Terror extends BaseRelic {
        private double chance = 2;
        private double cooldown = 90;
        private double seconds = 5;

        public Terror() {
            super("terror", "Terror", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 2);
            cooldown = s.getDouble("cooldown-seconds", 90);
            seconds = s.getDouble("darkness-seconds", 5);
        }

        @Override
        public String description() {
            return "Now and then the world goes dark and something roars nearby. Only you hear it.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (!Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, (int) (seconds * 20), 0, false, false));
            Location at = behind(p, 6);
            playPrivately(p, "entity.warden.heartbeat", at, 1.0f, 1.0f);
            if (Rng.chance(50)) {
                playPrivately(p, "entity.warden.roar", at, 0.8f, 1.0f);
            } else {
                playPrivately(p, "entity.warden.nearby_closer", at, 1.0f, 1.0f);
            }
        }
    }

    public static final class Echoes extends BaseRelic {
        private static final String[] SOUNDS = {
            "entity.creeper.primed", "block.stone.step", "entity.zombie.ambient", "block.wooden_door.open",
            "entity.skeleton.ambient", "entity.tnt.primed", "ambient.cave", "entity.spider.ambient",
            "entity.enderman.stare", "block.chest.open"};
        private double chance = 3;
        private double cooldown = 45;

        public Echoes() {
            super("echoes", "Echoes", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 3);
            cooldown = s.getDouble("cooldown-seconds", 45);
        }

        @Override
        public String description() {
            return "You keep hearing things that aren't there. Only you.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (!Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            String sound = SOUNDS[Rng.between(0, SOUNDS.length - 1)];
            playPrivately(p, sound, behind(p, Rng.between(3.0, 7.0)), 1.0f, (float) Rng.between(0.9, 1.1));
        }
    }

    public static final class Reduction extends BaseRelic {
        private double size = 0.55;
        private double reachPenalty = 1.0;

        public Reduction() {
            super("reduction", "Reduction", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            size = Math.max(0.1, Math.min(1.0, s.getDouble("size", 0.55)));
            reachPenalty = s.getDouble("reach-penalty", 1.0);
        }

        @Override
        public String description() {
            return "Shrinks you to " + Text.num(size * 100) + "% size, and your reach is " + Text.num(reachPenalty)
                + " block shorter.";
        }

        @Override
        public List<AttributeBonus> attributeBonuses() {
            return List.of(
                new AttributeBonus(Attribute.SCALE, size - 1.0, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeBonus(Attribute.ENTITY_INTERACTION_RANGE, -reachPenalty, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeBonus(Attribute.BLOCK_INTERACTION_RANGE, -reachPenalty, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public static final class Matador extends BaseRelic {
        private static final Set<EntityType> ANGERED = Set.of(EntityType.HOGLIN, EntityType.ZOGLIN,
            EntityType.POLAR_BEAR, EntityType.GOAT, EntityType.RAVAGER);
        private double radius = 16;
        private double knockback = 1.2;

        public Matador() {
            super("matador", "Matador", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            radius = s.getDouble("radius", 16);
            knockback = s.getDouble("knockback", 1.2);
        }

        @Override
        public String description() {
            return "Hoglins, Zoglins, goats and bears charge at you, and their hits send you flying.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            for (Entity e : p.getNearbyEntities(radius, radius / 2, radius)) {
                if (e instanceof Mob mob && ANGERED.contains(e.getType()) && mob.getTarget() == null) {
                    mob.setTarget(p);
                }
            }
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            LivingEntity attacker = damage.attacker();
            if (!damage.melee() || attacker == null || attacker instanceof Player) {
                return;
            }
            Player p = ctx.player();
            Vector push = p.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
            if (push.lengthSquared() < 0.01) {
                return;
            }
            Vector velocity = push.normalize().multiply(knockback).setY(0.5);
            Bukkit.getScheduler().runTask(com.additionalbosses.AdditionalBosses.get(), () -> p.setVelocity(velocity));
        }
    }

    public static final class MotherHen extends BaseRelic {
        private final Map<UUID, List<UUID>> chicks = new HashMap<>();
        private int maxChicks = 5;

        public MotherHen() {
            super("mother-hen", "Mother Hen", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            maxChicks = Math.max(1, Math.min(15, s.getInt("chicks", 5)));
        }

        @Override
        public String description() {
            return "A flock of " + maxChicks + " chicks follows you everywhere. They never grow up.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            List<UUID> flock = chicks.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
            Iterator<UUID> it = flock.iterator();
            while (it.hasNext()) {
                Entity chick = Bukkit.getEntity(it.next());
                if (chick == null || !chick.isValid()) {
                    it.remove();
                    continue;
                }
                double distSq = chick.getWorld().equals(p.getWorld())
                    ? chick.getLocation().distanceSquared(p.getLocation()) : Double.MAX_VALUE;
                if (distSq > 24 * 24) {
                    chick.teleport(behind(p, 2));
                } else if (distSq > 9 && chick instanceof Mob mob) {
                    mob.getPathfinder().moveTo(p, 1.3);
                }
            }
            if (flock.size() < maxChicks) {
                Chicken chick = p.getWorld().spawn(behind(p, 1.5), Chicken.class, CreatureSpawnEvent.SpawnReason.CUSTOM, c -> {
                    c.setBaby();
                    c.setAgeLock(true);
                    c.setPersistent(false); // never saved: no leftover chickens after a restart
                    c.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, "mother-hen");
                });
                flock.add(chick.getUniqueId());
            }
        }

        @Override
        public void onDeactivate(Player player) {
            List<UUID> flock = chicks.remove(player.getUniqueId());
            if (flock == null) {
                return;
            }
            for (UUID id : flock) {
                Entity chick = Bukkit.getEntity(id);
                if (chick != null) {
                    chick.remove();
                }
            }
        }
    }
}
