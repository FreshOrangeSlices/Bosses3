package com.additionalbosses.relic.effects;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossManager;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.SafeSpots;
import com.additionalbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hauntings and mischief: Withering Waters, Uninvited Guest (the wandering trader jump scare), Don't Blink
 * (a weeping angel), Poltergeist, Restless Dead and Hiccups.
 *
 * <p>Every mob these curses spawn is tagged as a minion (no drops, never a boss) and is never saved, so a restart
 * can't leave one behind.</p>
 */
public final class HauntCurses {

    static final String GUEST = "uninvited-guest";
    static final String ANGEL = "dont-blink";
    static final String RISEN = "restless-dead";

    private HauntCurses() {
    }

    private static boolean tagged(Entity e, String tag) {
        return tag.equals(e.getPersistentDataContainer().get(Keys.MINION, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------

    /** Like an Enderman: rain and water wither you. */
    public static final class WitheringWaters extends BaseRelic {
        private int amplifier = 0;
        private double seconds = 3;

        public WitheringWaters() {
            super("withering-waters", "Withering Waters", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            amplifier = Math.max(0, Math.min(4, s.getInt("level", 1) - 1));
            seconds = Math.max(1, s.getDouble("seconds", 3));
        }

        @Override
        public String description() {
            return "Like an Enderman, water hurts you: rain and water wither you.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (!p.isInWater() && !p.isInRain()) {
                return;
            }
            // Only top it up when it's about to run out: Wither hurts on a 40-tick rhythm of its remaining time, and
            // resetting it every second would skip every hurting tick.
            PotionEffect current = p.getPotionEffect(PotionEffectType.WITHER);
            if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= 20) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, Math.max(60, (int) Math.round(seconds * 20)),
                    amplifier, false, true));
            }
            hiss(p, ctx.manager());
        }

        /** Test: a few seconds of the withering, as if standing in the rain. */
        @Override
        public boolean trigger(Player p, com.additionalbosses.relic.RelicManager manager) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, amplifier, false, true));
            hiss(p, manager);
            return true;
        }

        private void hiss(Player p, com.additionalbosses.relic.RelicManager manager) {
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.SMOKE, 6, 0.3, 0.01);
            if (manager.ready(p, id(), 60)) {
                MoreCurses.playPrivately(p, "block.fire.extinguish", p.getLocation(), 0.6f, 1.4f);
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * The wandering trader jump scare: an ominous cave noise, then a trader only you can see walks up behind you,
     * a shriek, and he's gone. About three seconds from start to finish.
     */
    public static final class UninvitedGuest extends BaseRelic {
        /** Player -> the trader visiting them right now. */
        private final Map<UUID, UUID> visiting = new HashMap<>();
        private double chance = 1;
        private double cooldown = 480;

        public UninvitedGuest() {
            super(GUEST, "Uninvited Guest", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 1);
            cooldown = s.getDouble("cooldown-seconds", 480);
        }

        @Override
        public String description() {
            return "Every now and then, a wandering trader is standing right behind you. Only you can see him.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (visiting.containsKey(p.getUniqueId()) || p.isInsideVehicle() || p.isFlying() || p.isGliding()
                || !Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            visit(p);
        }

        @Override
        public boolean trigger(Player p, com.additionalbosses.relic.RelicManager manager) {
            return !visiting.containsKey(p.getUniqueId()) && visit(p);
        }

        private boolean visit(Player p) {
            Location spot = SafeSpots.behind(p, 5);
            if (spot == null) {
                return false;
            }
            AdditionalBosses plugin = AdditionalBosses.get();
            MoreCurses.playPrivately(p, "ambient.cave", MoreCurses.behind(p, 6), 1.0f, 0.7f);
            WanderingTrader trader = p.getWorld().spawn(spot, WanderingTrader.class, CreatureSpawnEvent.SpawnReason.CUSTOM, t -> {
                t.setVisibleByDefault(false); // only the cursed player ever sees him
                t.setPersistent(false);
                t.setInvulnerable(true);
                t.setSilent(true);
                t.setCollidable(false);
                t.setCanPickupItems(false);
                t.setCanDrinkPotion(false); // no turning invisible halfway (traders drink potions at night)
                t.setCanDrinkMilk(false);
                Bukkit.getMobGoals().removeAllGoals(t); // no wandering off or fleeing zombies: he only walks to you
                t.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, GUEST);
            });
            if (!trader.isValid()) {
                return false;
            }
            p.showEntity(plugin, trader);
            UUID id = p.getUniqueId();
            visiting.put(id, trader.getUniqueId());
            new BukkitRunnable() {
                int tick = 0;
                int scaredAt = -1;

                @Override
                public void run() {
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || !trader.isValid() || !player.getWorld().equals(trader.getWorld()) || tick > 80) {
                        end(player);
                        return;
                    }
                    if (scaredAt < 0) {
                        trader.lookAt(player);
                        if (tick % 5 == 0) {
                            trader.getPathfinder().moveTo(player.getLocation(), 1.0);
                        }
                        double distSq = trader.getLocation().distanceSquared(player.getLocation());
                        if (distSq < 1.7 * 1.7 || tick >= 45) {
                            if (distSq > 2.5 * 2.5) {
                                // Running away doesn't help: he's simply there.
                                Location close = SafeSpots.behind(player, 1.3);
                                if (close != null) {
                                    trader.teleport(close);
                                }
                            }
                            scare(player, trader);
                            scaredAt = tick;
                        }
                    } else {
                        trader.lookAt(player);
                        if (tick - scaredAt >= 20) {
                            end(player);
                            return;
                        }
                    }
                    tick++;
                }

                private void end(@Nullable Player player) {
                    cancel();
                    visiting.remove(id, trader.getUniqueId());
                    if (trader.isValid()) {
                        Location at = trader.getLocation().add(0, 1, 0);
                        if (player != null && player.getWorld().equals(at.getWorld())) {
                            player.spawnParticle(Particle.POOF, at, 12, 0.3, 0.5, 0.3, 0.02);
                            MoreCurses.playPrivately(player, "entity.wandering_trader.disappeared", at, 0.8f, 1.0f);
                        }
                        trader.remove();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            return true;
        }

        /** Logout, curse removed or plugin disabled: the guest leaves at once. */
        @Override
        public void onDeactivate(Player player) {
            UUID trader = visiting.remove(player.getUniqueId());
            Entity e = trader == null ? null : Bukkit.getEntity(trader);
            if (e != null) {
                e.remove();
            }
        }

        private static void scare(Player player, WanderingTrader trader) {
            Location at = trader.getLocation();
            MoreCurses.playPrivately(player, "block.sculk_shrieker.shriek", at, 1.0f, 1.0f);
            MoreCurses.playPrivately(player, "entity.wandering_trader.ambient", at, 1.0f, 0.55f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false));
        }
    }

    // ------------------------------------------------------------------

    /**
     * A weeping angel: in the dark a Creaking follows you. It only moves while nobody looks at it, can't be hurt,
     * and crumbles if you stare it down for a few seconds (or after a while on its own).
     */
    public static final class DontBlink extends BaseRelic {
        private static final class Angel {
            final UUID entity;
            int age;
            int stare;

            Angel(UUID entity) {
                this.entity = entity;
            }
        }

        private final Map<UUID, Angel> angels = new HashMap<>();
        private double chance = 1.5;
        private double cooldown = 300;
        private int lifetime = 45;
        private int stareSeconds = 4;

        public DontBlink() {
            super(ANGEL, "Don't Blink", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 1.5);
            cooldown = s.getDouble("cooldown-seconds", 300);
            lifetime = Math.max(5, s.getInt("lifetime-seconds", 45));
            stareSeconds = Math.max(1, s.getInt("stare-seconds", 4));
        }

        @Override
        public String description() {
            return "In the dark, something follows you. It only moves when you aren't looking."
                + " Stare at it for " + stareSeconds + " seconds and it crumbles.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            Angel angel = angels.get(p.getUniqueId());
            if (angel != null) {
                watch(p, angel);
                return;
            }
            if (!dark(p) || p.isInsideVehicle() || !Rng.chance(chance)
                || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            summon(p);
        }

        /** Test: it appears behind you whatever the light (it still only watches over cursed players). */
        @Override
        public boolean trigger(Player p, com.additionalbosses.relic.RelicManager manager) {
            Angel old = angels.get(p.getUniqueId());
            if (old != null) {
                vanish(p, Bukkit.getEntity(old.entity), false);
            }
            if (!summon(p)) {
                return false;
            }
            // Someone without the curse isn't watched over every second, so make sure it leaves on time anyway.
            UUID entity = angels.get(p.getUniqueId()).entity;
            Bukkit.getScheduler().runTaskLater(AdditionalBosses.get(), () -> {
                Angel angel = angels.get(p.getUniqueId());
                if (angel != null && angel.entity.equals(entity)) {
                    vanish(p, Bukkit.getEntity(entity), false);
                }
            }, lifetime * 20L);
            return true;
        }

        private boolean summon(Player p) {
            Location spot = null;
            for (int attempt = 0; attempt < 6 && spot == null; attempt++) {
                spot = SafeSpots.standable(MoreCurses.behind(p, Rng.between(10.0, 14.0)), 4);
            }
            if (spot == null) {
                return false;
            }
            Creaking creaking = p.getWorld().spawn(spot, Creaking.class, CreatureSpawnEvent.SpawnReason.CUSTOM, c -> {
                c.setPersistent(false);
                c.setInvulnerable(true);
                c.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, ANGEL);
            });
            if (!creaking.isValid()) {
                return false;
            }
            creaking.activate(p);
            angels.put(p.getUniqueId(), new Angel(creaking.getUniqueId()));
            MoreCurses.playPrivately(p, "block.sculk_shrieker.shriek", spot, 0.45f, 0.7f);
            return true;
        }

        private static boolean dark(Player p) {
            World w = p.getWorld();
            long time = w.getTime();
            boolean night = w.getEnvironment() == World.Environment.NORMAL && time > 13000 && time < 23000;
            return night || p.getLocation().getBlock().getLightLevel() <= 7;
        }

        private void watch(Player p, Angel angel) {
            Entity e = Bukkit.getEntity(angel.entity);
            angel.age++;
            if (e == null || !e.isValid() || !e.getWorld().equals(p.getWorld())
                || e.getLocation().distanceSquared(p.getLocation()) > 48 * 48 || angel.age > lifetime) {
                vanish(p, e, false);
                return;
            }
            if (e instanceof Creaking creaking && !creaking.isActive()) {
                creaking.activate(p);
            }
            if (lookingAt(p, e)) {
                angel.stare++;
                if (angel.stare >= stareSeconds) {
                    vanish(p, e, true);
                }
            } else {
                angel.stare = 0;
            }
        }

        private static boolean lookingAt(Player p, Entity e) {
            Location eye = p.getEyeLocation();
            Vector to = e.getLocation().add(0, e.getHeight() * 0.6, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.01) {
                return true;
            }
            return to.lengthSquared() <= 40 * 40 && eye.getDirection().angle(to) < 0.4 && p.hasLineOfSight(e);
        }

        private void vanish(Player p, @Nullable Entity e, boolean crumbled) {
            angels.remove(p.getUniqueId());
            if (e == null || !e.isValid()) {
                return;
            }
            Location at = e.getLocation().add(0, 1, 0);
            if (crumbled) {
                BlockData wood = Material.PALE_OAK_LOG.createBlockData();
                at.getWorld().spawnParticle(Particle.BLOCK, at, 40, 0.3, 0.8, 0.3, 0, wood);
                Fx.play(at, "entity.creaking.death", 1.0f, 0.8f);
            } else {
                at.getWorld().spawnParticle(Particle.SMOKE, at, 20, 0.3, 0.8, 0.3, 0.01);
            }
            e.remove();
        }

        @Override
        public void onDeactivate(Player player) {
            Angel angel = angels.remove(player.getUniqueId());
            if (angel != null) {
                Entity e = Bukkit.getEntity(angel.entity);
                if (e != null) {
                    e.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /** Shuffles your hotbar and opens nearby doors (they close again by themselves). */
    public static final class Poltergeist extends BaseRelic {
        private double chance = 2;
        private double cooldown = 60;

        public Poltergeist() {
            super("poltergeist", "Poltergeist", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 2);
            cooldown = s.getDouble("cooldown-seconds", 60);
        }

        @Override
        public String description() {
            return "Something mischievous shuffles your hotbar and opens doors around you.";
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
            trigger(p, ctx.manager());
        }

        @Override
        public boolean trigger(Player p, com.additionalbosses.relic.RelicManager manager) {
            if (!(Rng.chance(50) && rattleDoor(p))) {
                shuffle(p);
            }
            return true;
        }

        private static void shuffle(Player p) {
            PlayerInventory inv = p.getInventory();
            int a = Rng.between(0, 8);
            int b = Rng.between(0, 7);
            if (b >= a) {
                b++;
            }
            ItemStack first = inv.getItem(a);
            ItemStack second = inv.getItem(b);
            if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
                return;
            }
            inv.setItem(a, second);
            inv.setItem(b, first);
            MoreCurses.playPrivately(p, "entity.vex.ambient", MoreCurses.behind(p, 2), 0.8f, 1.3f);
            Fx.actionBar(p, Component.text("Something rummages through your pockets...", NamedTextColor.DARK_PURPLE));
        }

        /** Opens a closed wooden door near the player for a moment. False if there is none. */
        private static boolean rattleDoor(Player p) {
            Block origin = p.getLocation().getBlock();
            List<Block> doors = new ArrayList<>();
            for (int x = -6; x <= 6; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -6; z <= 6; z++) {
                        Block b = origin.getRelative(x, y, z);
                        if (Tag.WOODEN_DOORS.isTagged(b.getType()) && b.getBlockData() instanceof org.bukkit.block.data.type.Door d
                            && d.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM && !d.isOpen()) {
                            doors.add(b);
                        }
                    }
                }
            }
            if (doors.isEmpty()) {
                return false;
            }
            Block door = doors.get(Rng.between(0, doors.size() - 1));
            if (!mayUse(p, door)) {
                return false; // someone else's protected door stays shut
            }
            setOpen(door, true);
            Fx.play(door.getLocation().add(0.5, 0.5, 0.5), "block.wooden_door.open", 1.0f, 0.7f);
            Material type = door.getType();
            Bukkit.getScheduler().runTaskLater(AdditionalBosses.get(), () -> {
                if (door.getType() == type) {
                    setOpen(door, false);
                    Fx.play(door.getLocation().add(0.5, 0.5, 0.5), "block.wooden_door.close", 1.0f, 0.7f);
                }
            }, 30L);
            return true;
        }

        /** Asks protection plugins whether the player could open this door themselves. */
        private static boolean mayUse(Player p, Block door) {
            org.bukkit.event.player.PlayerInteractEvent check = new org.bukkit.event.player.PlayerInteractEvent(p,
                org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, null, door, org.bukkit.block.BlockFace.UP,
                org.bukkit.inventory.EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(check);
            return check.useInteractedBlock() != org.bukkit.event.Event.Result.DENY;
        }

        private static void setOpen(Block bottom, boolean open) {
            for (Block half : new Block[]{bottom, bottom.getRelative(0, 1, 0)}) {
                if (half.getBlockData() instanceof Openable openable && openable.isOpen() != open) {
                    openable.setOpen(open);
                    half.setBlockData(openable, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /** Undead you kill sometimes get back up for round two (they drop nothing the second time). */
    public static final class RestlessDead extends BaseRelic {
        private static final Set<EntityType> UNDEAD = EnumSet.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.ZOMBIE_VILLAGER, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.PARCHED,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOGLIN, EntityType.PHANTOM);
        private double chance = 30;

        public RestlessDead() {
            super(RISEN, "Restless Dead", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance", 30);
        }

        @Override
        public String description() {
            return Text.num(chance) + "% of the undead you kill climb back up for round two.";
        }

        @Override
        public void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
            LivingEntity dead = event.getEntity();
            if (victimWasBoss || !UNDEAD.contains(dead.getType()) || BossManager.isMinion(dead)
                || !ctx.manager().ready(ctx.player(), id() + ":kill", 1) || !Rng.chance(chance)) {
                return; // (the cooldown: the curse on two items still raises one mob per kill)
            }
            Player p = ctx.player();
            Location at = dead.getLocation();
            EntityType type = dead.getType();
            boolean baby = dead instanceof org.bukkit.entity.Ageable a && !a.isAdult();
            Fx.particle(at.clone().add(0, 0.3, 0), Particle.SOUL, 8, 0.4, 0.02);
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(AdditionalBosses.get(), () -> {
                if (!at.isChunkLoaded() || type.getEntityClass() == null) {
                    return;
                }
                Entity risen = at.getWorld().spawn(at, type.getEntityClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                    e.setPersistent(false);
                    if (e instanceof LivingEntity living) {
                        living.setCanPickupItems(false); // it drops nothing, so it mustn't carry off anything either
                    }
                    e.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, RISEN);
                    if (e instanceof org.bukkit.entity.Ageable a) {
                        if (baby) {
                            a.setBaby();
                        } else {
                            a.setAdult();
                        }
                    }
                });
                Fx.particle(at.clone().add(0, 1, 0), Particle.SOUL, 15, 0.4, 0.05);
                Fx.play(at, "entity.zombie_villager.converted", 0.8f, 0.6f);
                Player player = Bukkit.getPlayer(id);
                if (risen instanceof Mob mob && player != null && player.getWorld().equals(at.getWorld())) {
                    mob.setTarget(player);
                }
            }, 40L);
        }
    }

    // ------------------------------------------------------------------

    /** Hic! Every so often you hop into the air. */
    public static final class Hiccups extends BaseRelic {
        private double chance = 4;
        private double cooldown = 8;

        public Hiccups() {
            super("hiccups", "Hiccups", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            chance = s.getDouble("chance-per-second", 4);
            cooldown = s.getDouble("cooldown-seconds", 8);
        }

        @Override
        public String description() {
            return "Hic! Every so often you hop into the air.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @SuppressWarnings("deprecation") // isOnGround is the client's word, which is fine for a harmless hop
        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (!p.isOnGround() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming()
                || !Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
                return;
            }
            trigger(p, ctx.manager());
        }

        @Override
        public boolean trigger(Player p, com.additionalbosses.relic.RelicManager manager) {
            p.setVelocity(p.getVelocity().setY(0.4));
            Fx.play(p.getLocation(), "entity.player.burp", 0.6f, 1.7f);
            Fx.actionBar(p, Component.text("*hic*", NamedTextColor.GRAY));
            return true;
        }
    }

    // ------------------------------------------------------------------

    /** Keeps the haunted mobs from being traded with, hit or distracted. */
    public static final class Events implements Listener {

        @EventHandler(priority = EventPriority.LOW)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (tagged(event.getRightClicked(), GUEST)) {
                event.setCancelled(true);
            }
        }

        /** Other mobs ignore the guest (zombies would otherwise chase an invisible trader). */
        @EventHandler(ignoreCancelled = true)
        public void onTarget(EntityTargetEvent event) {
            Entity target = event.getTarget();
            if (target != null && tagged(target, GUEST)) {
                event.setCancelled(true);
            }
        }
    }
}
