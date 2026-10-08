package com.additionalbosses.relic.effects;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.combat.CombatGuard;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.PlayerData;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The positive Relic effects. Each one is a small self-contained class.
 */
public final class Relics {

    private Relics() {
    }

    static void heal(Player player, double amount) {
        if (amount <= 0 || player.isDead()) {
            return;
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        player.setHealth(Math.min(cap, player.getHealth() + amount));
    }

    static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    static void refreshEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || (current.getDuration() >= 0 && current.getDuration() < 220)) {
            player.addPotionEffect(new PotionEffect(type, 300, amplifier, true, false, true));
        }
    }

    // ------------------------------------------------------------------

    public static final class BloodPact extends BaseRelic {
        private double healPercent = 12;

        public BloodPact() {
            super("blood-pact", "Blood Pact", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            healPercent = s.getDouble("heal-percent", 12);
        }

        @Override
        public String description() {
            return "Your attacks heal you for " + Text.num(healPercent) + "% of the damage dealt.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            heal(ctx.player(), finalDamage * healPercent / 100.0);
        }
    }

    public static final class SoulHarvest extends BaseRelic {
        private double heal = 3;
        private double bossHeal = 12;

        public SoulHarvest() {
            super("soul-harvest", "Soul Harvest", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            heal = s.getDouble("heal", 3);
            bossHeal = s.getDouble("boss-heal", 12);
        }

        @Override
        public String description() {
            return "Kills restore " + Text.num(heal / 2) + " hearts (" + Text.num(bossHeal / 2) + " for bosses).";
        }

        @Override
        public void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
            heal(ctx.player(), victimWasBoss ? bossHeal : heal);
            Fx.particle(Fx.center(event.getEntity()), Particle.SOUL, 6, 0.3, 0.03);
        }
    }

    public static final class SecondDawn extends BaseRelic {
        private double cooldownMinutes = 10;

        public SecondDawn() {
            super("second-dawn", "Second Dawn", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            cooldownMinutes = s.getDouble("cooldown-minutes", 10);
        }

        @Override
        public String description() {
            return "Saves you from a killing blow like a Totem (every " + Text.num(cooldownMinutes) + " min).";
        }

        @Override
        public boolean onLethal(RelicContext ctx) {
            Player p = ctx.player();
            long now = System.currentTimeMillis();
            if (now < PlayerData.secondDawnReadyAt(p)) {
                return false;
            }
            PlayerData.setSecondDawnReadyAt(p, now + Math.round(cooldownMinutes * 60_000));
            p.sendMessage(AdditionalBosses.get().settings().messages.prefixed("second-dawn"));
            return true;
        }
    }

    public static final class BrambleHeart extends BaseRelic {
        private double reflectPercent = 25;

        public BrambleHeart() {
            super("bramble-heart", "Bramble Heart", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            reflectPercent = s.getDouble("reflect-percent", 25);
        }

        @Override
        public String description() {
            return "Melee attackers take " + Text.num(reflectPercent) + "% of their damage back.";
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            LivingEntity attacker = damage.attacker();
            if (damage.melee() && !damage.secondary() && attacker != null && attacker != ctx.player()) {
                double amount = finalDamage * reflectPercent / 100.0;
                if (amount >= 0.5) {
                    CombatGuard.damage(attacker, amount, ctx.player(), DamageType.THORNS);
                }
            }
        }
    }

    public static final class Emberheart extends BaseRelic {
        private double seconds = 4;

        public Emberheart() {
            super("emberheart", "Emberheart", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            seconds = s.getDouble("ignite-seconds", 4);
        }

        @Override
        public String description() {
            return "Held: hits set foes ablaze. Worn: melee attackers catch fire (" + Text.num(seconds) + "s).";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            if (ctx.inHand()) {
                victim.setFireTicks(Math.max(victim.getFireTicks(), (int) (seconds * 20)));
            }
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            if (!ctx.inHand() && damage.melee() && damage.attacker() != null && !damage.secondary()) {
                LivingEntity a = damage.attacker();
                a.setFireTicks(Math.max(a.getFireTicks(), (int) (seconds * 20)));
            }
        }
    }

    public static final class Frostbite extends BaseRelic {
        private double seconds = 2;
        private int amplifier = 1;

        public Frostbite() {
            super("frostbite", "Frostbite", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            seconds = s.getDouble("slow-seconds", 2);
            amplifier = Math.max(0, s.getInt("amplifier", 1));
        }

        @Override
        public String description() {
            return "Held: hits slow foes. Worn: melee attackers are slowed (Slowness " + Text.roman(amplifier + 1)
                + ", " + Text.num(seconds) + "s).";
        }

        private void chill(LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (seconds * 20), amplifier));
            Fx.particle(Fx.center(target), Particle.SNOWFLAKE, 8, 0.3, 0.02);
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            if (ctx.inHand()) {
                chill(victim);
            }
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            if (!ctx.inHand() && damage.melee() && damage.attacker() != null && !damage.secondary()) {
                chill(damage.attacker());
            }
        }
    }

    public static final class Stormcaller extends BaseRelic {
        private double chance = 10;
        private double bonusDamage = 4;

        public Stormcaller() {
            super("stormcaller", "Stormcaller", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 10);
            bonusDamage = s.getDouble("bonus-damage", 4);
        }

        @Override
        public String description() {
            return Text.num(chance) + "% chance to call lightning for +" + Text.num(bonusDamage)
                + " damage (on hit if held, on attackers if worn).";
        }

        @Override
        public void onAttack(RelicContext ctx, LivingEntity victim, EntityDamageEvent event, DamageContext damage,
                             boolean victimIsBoss) {
            if (ctx.inHand() && Rng.chance(chance)) {
                damage.addFlat(bonusDamage);
                victim.getWorld().strikeLightningEffect(victim.getLocation());
            }
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            LivingEntity attacker = damage.attacker();
            if (!ctx.inHand() && damage.melee() && attacker != null && !damage.secondary() && Rng.chance(chance)) {
                attacker.getWorld().strikeLightningEffect(attacker.getLocation());
                CombatGuard.damage(attacker, bonusDamage, ctx.player(), DamageType.LIGHTNING_BOLT);
            }
        }
    }

    public static final class Windstep extends BaseRelic {
        private double cooldown = 4;
        private double strength = 1.2;

        public Windstep() {
            super("windstep", "Windstep", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            cooldown = s.getDouble("cooldown-seconds", 4);
            strength = s.getDouble("strength", 1.2);
        }

        @Override
        public String description() {
            return "Jump while sneaking to dash forward (every " + Text.num(cooldown) + "s).";
        }

        @Override
        public void onJump(RelicContext ctx, PlayerJumpEvent event) {
            Player p = ctx.player();
            if (!p.isSneaking() || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) {
                return;
            }
            Vector velocity = dir.normalize().multiply(strength).setY(0.38);
            Bukkit.getScheduler().runTask(AdditionalBosses.get(), () -> p.setVelocity(velocity));
            Fx.play(p.getLocation(), "entity.breeze.shoot", 0.8f, 1.4f);
            Fx.particle(p.getLocation(), Particle.CLOUD, 12, 0.3, 0.05);
        }
    }

    public static final class HuntersMark extends BaseRelic {
        private double rewardBonus = 25;
        private double bossDamageBonus = 10;

        public HuntersMark() {
            super("hunters-mark", "Hunter's Mark", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            rewardBonus = s.getDouble("reward-chance-bonus", 25);
            bossDamageBonus = s.getDouble("boss-damage-bonus", 10);
        }

        @Override
        public String description() {
            return "+" + Text.num(bossDamageBonus) + "% damage to bosses, and their reward chances are "
                + Text.num(rewardBonus) + "% higher for you.";
        }

        @Override
        public void onAttack(RelicContext ctx, LivingEntity victim, EntityDamageEvent event, DamageContext damage,
                             boolean victimIsBoss) {
            if (victimIsBoss) {
                damage.multiply(1.0 + bossDamageBonus / 100.0);
            }
        }

        @Override
        public double rewardChanceMultiplier() {
            return 1.0 + rewardBonus / 100.0;
        }
    }

    public static final class EchoStrike extends BaseRelic {
        private double chance = 15;
        private double echoPercent = 50;

        public EchoStrike() {
            super("echo-strike", "Echo Strike", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 15);
            echoPercent = s.getDouble("echo-damage-percent", 50);
        }

        @Override
        public String description() {
            return Text.num(chance) + "% chance for a melee hit to strike again a moment later for "
                + Text.num(echoPercent) + "% damage.";
        }

        @Override
        public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
            if (!damage.melee() || finalDamage <= 0 || !Rng.chance(chance)) {
                return;
            }
            Player p = ctx.player();
            double amount = finalDamage * echoPercent / 100.0;
            // 12 ticks: just after the victim's hurt-immunity window ends.
            Bukkit.getScheduler().runTaskLater(AdditionalBosses.get(), () -> {
                if (victim.isValid() && !victim.isDead() && p.isOnline()) {
                    CombatGuard.damage(victim, amount, p, DamageType.PLAYER_ATTACK);
                    Fx.particle(Fx.center(victim), Particle.SWEEP_ATTACK, 1, 0, 0);
                    Fx.play(victim.getLocation(), "entity.player.attack.sweep", 0.8f, 1.6f);
                }
            }, 12L);
        }
    }

    public static final class OwlsSight extends BaseRelic {
        public OwlsSight() {
            super("owls-sight", "Owl's Sight", false);
        }

        @Override
        public String description() {
            return "Grants Night Vision while equipped.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            refreshEffect(ctx.player(), PotionEffectType.NIGHT_VISION, 0);
        }
    }

    public static final class Lodestone extends BaseRelic {
        private double radius = 6;

        public Lodestone() {
            super("lodestone", "Lodestone", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            radius = s.getDouble("radius", 6);
        }

        @Override
        public String description() {
            return "Pulls nearby items and experience orbs (" + Text.num(radius) + " blocks) toward you.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            Vector target = p.getLocation().add(0, 0.5, 0).toVector();
            for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                if (e instanceof Item item) {
                    UUID thrower = item.getThrower();
                    if (p.getUniqueId().equals(thrower) || item.getPickupDelay() > 20) {
                        continue;
                    }
                } else if (!(e instanceof ExperienceOrb)) {
                    continue;
                }
                Vector pull = target.clone().subtract(e.getLocation().toVector());
                if (pull.lengthSquared() > 1) {
                    e.setVelocity(pull.normalize().multiply(0.55));
                }
            }
        }
    }

    public static final class Aegis extends BaseRelic {
        private double threshold = 30;
        private int level = 2;
        private double duration = 10;
        private double cooldown = 60;

        public Aegis() {
            super("aegis", "Aegis", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            threshold = s.getDouble("health-threshold", 30);
            level = Math.max(1, s.getInt("absorption-level", 2));
            duration = s.getDouble("duration-seconds", 10);
            cooldown = s.getDouble("cooldown-seconds", 60);
        }

        @Override
        public String description() {
            return "Dropping below " + Text.num(threshold) + "% health grants Absorption " + Text.roman(level)
                + " (every " + Text.num(cooldown) + "s).";
        }

        @Override
        public void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
            Player p = ctx.player();
            double after = p.getHealth() - finalDamage;
            if (after <= 0 || after / maxHealth(p) * 100.0 > threshold) {
                return;
            }
            if (!ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) (duration * 20), level - 1));
            Fx.play(p.getLocation(), "block.beacon.power_select", 0.8f, 1.6f);
            Fx.particle(Fx.center(p), Particle.ENCHANTED_HIT, 15, 0.4, 0.1);
        }
    }

    public static final class Featherweight extends BaseRelic {
        private double reduction = 75;

        public Featherweight() {
            super("featherweight", "Featherweight", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            reduction = s.getDouble("fall-reduction", 75);
        }

        @Override
        public String description() {
            return "Take " + Text.num(reduction) + "% less fall damage.";
        }

        @Override
        public void onDamaged(RelicContext ctx, EntityDamageEvent event, DamageContext damage) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                damage.multiply(Math.max(0, 1.0 - reduction / 100.0));
            }
        }
    }

    public static final class Prospector extends BaseRelic {
        private double xpBonus = 50;

        public Prospector() {
            super("prospector", "Prospector", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            xpBonus = s.getDouble("xp-bonus", 50);
        }

        @Override
        public String description() {
            return "Mobs you kill drop " + Text.num(xpBonus) + "% more experience.";
        }

        @Override
        public void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 + xpBonus / 100.0)));
        }
    }
}
