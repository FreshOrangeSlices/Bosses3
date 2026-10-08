package com.additionalbosses.relic.effects;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Curses that a corrupted Relic carries on top of its good effect. Some are dangerous, some are just weird.
 */
public final class Curses {

    private Curses() {
    }

    public static final class Dread extends BaseRelic {
        private double chance = 20;
        private double seconds = 3;

        public Dread() {
            super("dread", "Dread", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 20);
            seconds = s.getDouble("duration-seconds", 3);
        }

        @Override
        public String description() {
            return "When hurt, " + Text.num(chance) + "% chance to be swallowed by Darkness.";
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            if (finalDamage > 0 && Rng.chance(chance)) {
                Player p = ctx.player();
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, (int) (seconds * 20), 0));
                p.playSound(Sound.sound(Key.key("ambient.cave"), Sound.Source.AMBIENT, 1.0f, 0.8f), Sound.Emitter.self());
            }
        }
    }

    public static final class Butterfingers extends BaseRelic {
        private double chance = 2;

        public Butterfingers() {
            super("butterfingers", "Butterfingers", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 2);
        }

        @Override
        public String description() {
            return Text.num(chance) + "% chance to fumble your held item when you attack.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            if (!damage.melee() || !Rng.chance(chance)) {
                return;
            }
            Player p = ctx.player();
            PlayerInventory inv = p.getInventory();
            ItemStack held = inv.getItemInMainHand();
            if (held.isEmpty()) {
                return;
            }
            inv.setItemInMainHand(null);
            Item drop = p.getWorld().dropItem(p.getEyeLocation().subtract(0, 0.4, 0), held);
            drop.setPickupDelay(30);
            drop.setVelocity(p.getLocation().getDirection().multiply(0.25).setY(0.2));
            p.sendMessage(AdditionalBosses.get().settings().messages.prefixed("butterfingers"));
        }
    }

    public static final class FowlOmen extends BaseRelic {
        private double chance = 5;
        private double cooldown = 15;

        public FowlOmen() {
            super("fowl-omen", "Fowl Omen", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 5);
            cooldown = s.getDouble("cooldown-seconds", 15);
        }

        @Override
        public String description() {
            return "Your attacks sometimes summon a very confused chicken.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            Player p = ctx.player();
            if (!Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            victim.getWorld().spawn(victim.getLocation().add(0, 0.5, 0), Chicken.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM, chicken -> chicken.setVelocity(new Vector(0, 0.4, 0)));
            Fx.play(victim.getLocation(), "entity.chicken.hurt", 1.0f, 1.0f);
        }
    }

    public static final class Insomnia extends BaseRelic {
        public Insomnia() {
            super("insomnia", "Insomnia", true);
        }

        @Override
        public String description() {
            return "You cannot sleep while this is equipped.";
        }

        @Override
        public boolean preventsSleep() {
            return true;
        }
    }

    public static final class Limelight extends BaseRelic {
        public Limelight() {
            super("limelight", "Limelight", true);
        }

        @Override
        public String description() {
            return "You glow, visible through walls to everyone.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Relics.refreshEffect(ctx.player(), PotionEffectType.GLOWING, 0);
        }
    }

    public static final class Gluttony extends BaseRelic {
        private double exhaustion = 0.8;

        public Gluttony() {
            super("gluttony", "Gluttony", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            exhaustion = s.getDouble("exhaustion", 0.8);
        }

        @Override
        public String description() {
            return "Every attack makes you noticeably hungrier.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            Player p = ctx.player();
            p.setExhaustion((float) Math.min(40.0, p.getExhaustion() + exhaustion));
        }
    }

    public static final class Recoil extends BaseRelic {
        private double strength = 0.45;

        public Recoil() {
            super("recoil", "Recoil", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            strength = s.getDouble("strength", 0.45);
        }

        @Override
        public String description() {
            return "Your melee hits knock you backwards too.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            if (!damage.melee()) {
                return;
            }
            Player p = ctx.player();
            Vector back = p.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (back.lengthSquared() < 0.01) {
                return;
            }
            p.setVelocity(back.normalize().multiply(strength).setY(0.15));
        }
    }

    public static final class GlassBones extends BaseRelic {
        private double multiplier = 2.0;

        public GlassBones() {
            super("glass-bones", "Glass Bones", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            multiplier = s.getDouble("fall-multiplier", 2.0);
        }

        @Override
        public String description() {
            return "Fall damage is multiplied by " + Text.num(multiplier) + ".";
        }

        @Override
        public void onDamaged(RelicContext ctx, EntityDamageEvent event, DamageContext damage) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                damage.multiply(multiplier);
            }
        }
    }

    public static final class BloodTithe extends BaseRelic {
        private double cost = 1.0;

        public BloodTithe() {
            super("blood-tithe", "Blood Tithe", true);
        }

        @Override
        public void load(ConfigurationSection s) {
            cost = s.getDouble("health-cost", 1.0);
        }

        @Override
        public String description() {
            return "Every kill costs you " + Text.num(cost / 2) + " heart (never fatal).";
        }

        @Override
        public void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
            Player p = ctx.player();
            if (p.getHealth() > 1.0) {
                p.setHealth(Math.max(1.0, p.getHealth() - cost));
            }
        }
    }
}
