package com.bloompets;

import com.destroystokyo.paper.entity.Pathfinder;
import io.papermc.paper.entity.EntitySerializationFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Pets that are out in the world: summoning and putting them away, bonding with wild animals, following, fighting,
 * fainting and bond XP.
 *
 * <p>A pet's own AI is switched off ({@link Mob#setAware(boolean)}) and it is steered from here every tick, so every
 * species behaves the same way: it follows you, never wanders off, never sits down, rams you or rolls up. Ground pets
 * walk along a path found by the game's pathfinder; Bees, Allays and the Vex fly beside your shoulder.</p>
 */
public final class PetManager {

    /** A pet that is out in the world right now. */
    public static final class Active {
        public final Pet pet;
        public final UUID owner;
        public final Mob entity;
        @Nullable LivingEntity target;
        long lastAttack;
        long lastFeed;
        boolean fainting;
        int ticks;
        /** Where a stationed pet keeps its post (null: it follows its owner). */
        public @Nullable Location home;
        /** A max-level pet shrugs off one fainting blow each time it comes out. */
        boolean secondWindUsed;
        // fetch: the stick it's chasing, and whether it's bringing it back
        @Nullable Item fetchItem;
        boolean fetchReturning;
        long fetchStarted;
        // bond XP for walking together
        @Nullable Location walkLast;
        double walked;
        /** The pet stays put until then (the Sniffer digging something up). */
        public long pausedUntil;
        // flyers: whether the owner is standing still
        @Nullable Location ownerLast;
        int ownerStill;
        // walking
        @Nullable List<Location> path;
        int pathIndex;
        int nextPathTick;
        @Nullable Location pathGoal;
        @Nullable Location lastPos;
        int stuck;
        /** Hits landed this outing (the Goat's every-third-hit Momentum). */
        int hits;
        /** A stationed pet strolls around its post: where it's headed, and when it picks a new spot. */
        @Nullable Location roamSpot;
        int roamNext;

        Active(Pet pet, UUID owner, Mob entity) {
            this.pet = pet;
            this.owner = owner;
            this.entity = entity;
        }
    }

    private static final double TELEPORT_DISTANCE = 16;
    private static final double RECALL_DISTANCE = 40;
    private static final double TARGET_RANGE = 24;
    private static final long ATTACK_COOLDOWN = 1000;
    private static final long FEED_XP_COOLDOWN = 30_000;

    private final BloomPets plugin;
    private final Map<UUID, Active> byOwner = new HashMap<>();
    /** Stationed pets that are in the world right now, by pet id. */
    private final Map<UUID, Active> posted = new HashMap<>();
    private final Map<UUID, Active> byEntity = new HashMap<>();
    /** Stationed pets that couldn't be placed (the spawn was blocked): when to try again. */
    private final Map<UUID, Long> postRetry = new HashMap<>();
    private @Nullable BukkitTask task;

    public PetManager(BloomPets plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.store().loadStationed();
        Bukkit.getScheduler().runTaskTimer(plugin, this::placeStationed, 40L, 40L);
    }

    /** Every pet goes back into its bloom (on shutdown or reload), so none are lost or duplicated. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Active a : List.copyOf(byOwner.values())) {
            stash(a);
        }
        for (Active a : List.copyOf(posted.values())) {
            stash(a);
        }
    }

    public @Nullable Active active(UUID owner) {
        return byOwner.get(owner);
    }

    public @Nullable Active of(Entity entity) {
        return byEntity.get(entity.getUniqueId());
    }

    /** Pets out with their owners. */
    public Collection<Active> all() {
        return List.copyOf(byOwner.values());
    }

    /** Stationed pets that are in the world right now. */
    public Collection<Active> postedPets() {
        return List.copyOf(posted.values());
    }

    /** This pet in the world, whether it's with its owner or at its station. */
    public @Nullable Active activeFor(Pet pet) {
        Active a = byOwner.get(pet.owner);
        return a != null && a.pet == pet ? a : posted.get(pet.id);
    }

    public boolean isOut(Pet pet) {
        Active a = byOwner.get(pet.owner);
        return a != null && a.pet == pet;
    }

    // =====================================================================
    //  Summon / dismiss
    // =====================================================================

    public void toggle(Player owner, Pet pet) {
        if (isOut(pet)) {
            dismiss(owner, true);
        } else {
            summon(owner, pet, true);
        }
    }

    /** The Pet Toy: sends your pet home, or calls the one you had out last (or opens the menu to pick one). */
    public void toggleLast(Player owner) {
        if (byOwner.containsKey(owner.getUniqueId())) {
            dismiss(owner, true);
            return;
        }
        Pet last = plugin.store().last(owner.getUniqueId());
        if (last != null && !last.stationed()) { // a pet at its post stays there; pick it in the menu to call it
            if (!summon(owner, last, true)) {
                plugin.menus().openPets(owner); // resting or turned off: pick someone else
            }
        } else if (plugin.store().pets(owner.getUniqueId()).isEmpty()) {
            Msg.bar(owner, "No pets yet! Sneak + feed an animal its favourite food to make a friend.", Msg.PINK);
        } else {
            plugin.menus().openPets(owner);
        }
    }

    public boolean summon(Player owner, Pet pet, boolean announce) {
        long now = System.currentTimeMillis();
        if (!plugin.settings().enabled.contains(pet.species)) {
            Msg.error(owner, pet.species.displayName() + " pets are turned off on this server.");
            return false;
        }
        if (pet.resting(now)) {
            Msg.error(owner, pet.name + " is still resting (" + ((pet.restingUntil - now + 999) / 1000) + "s).");
            return false;
        }
        if (owner.isDead() || owner.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (pet.species == Species.VEX && owner.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            Msg.error(owner, "Vexes can't come out in Peaceful.");
            return false;
        }
        Active current = byOwner.get(owner.getUniqueId());
        if (current != null) {
            if (current.pet == pet) {
                return true;
            }
            stash(current);
        }
        String post = pet.station;
        if (post != null) {
            // called back from its post
            Active atPost = posted.get(pet.id);
            pet.station = null;
            if (atPost != null) {
                stash(atPost);
            }
        }
        Mob mob = spawn(pet, spawnSpot(owner, pet.species));
        if (mob == null) {
            pet.station = post; // couldn't come: it keeps its post
            Msg.error(owner, pet.name + " can't come out here.");
            return false;
        }
        plugin.bonuses().forget(pet.id);
        track(owner, pet, mob);
        plugin.store().setLast(pet);
        if (announce) {
            Msg.bar(owner, Component.text(pet.name + " is here!", pet.species.category().color()));
            Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
            mob.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 18, mob.getWidth() / 2 + 0.2,
                mob.getHeight() / 2, mob.getWidth() / 2 + 0.2, 0);
            mob.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            if (plugin.happiness().sad(pet)) {
                Msg.chat(owner, Component.text(pet.name + " looks a little down. ", NamedTextColor.GOLD)
                    .append(Component.text("Feed it, pet it (sneak + right-click with an empty hand) or play fetch "
                        + "to cheer it up; until then its bonus, skills and storage rest.", NamedTextColor.GRAY)));
            }
        }
        plugin.menus().refresh(owner);
        return true;
    }

    public void dismiss(Player owner, boolean announce) {
        Active a = byOwner.get(owner.getUniqueId());
        if (a == null) {
            if (announce) {
                Msg.error(owner, "None of your pets is out.");
            }
            return;
        }
        Location at = a.entity.getLocation().add(0, a.entity.getHeight() / 2, 0);
        stash(a);
        if (announce) {
            Msg.bar(owner, Component.text(a.pet.name + " went back into its bloom.", NamedTextColor.GRAY));
            at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 12, 0.3, 0.3, 0.3, 0);
            at.getWorld().playSound(at, Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.8f, 1.2f);
        }
        plugin.menus().refresh(owner);
    }

    /** Puts a pet back into its bloom: the whole mob is saved and removed from the world. */
    public void stash(Active a) {
        plugin.menus().closeFor(a.pet); // a storage or gear screen saves first
        plugin.rides().eject(a);
        endFetch(a, false);
        untrack(a);
        plugin.happiness().wentIn(a.pet);
        Mob mob = a.entity;
        if (mob.isValid()) {
            if (!a.fainting) {
                a.pet.health = mob.getHealth() / maxHealth(mob);
            }
            mob.setFireTicks(0);
            mob.setFreezeTicks(0);
            mob.setFallDistance(0);
            mob.setTarget(null);
            mob.setInvulnerable(false);
            snapshot(a);
            mob.remove();
        }
        plugin.store().save(a.owner);
    }

    /** The pet's chunk is unloading under it: save it and let it go (pets are never saved with the world). */
    public void unloaded(Active a) {
        plugin.menus().closeFor(a.pet);
        plugin.rides().eject(a);
        endFetch(a, false);
        untrack(a);
        plugin.happiness().wentIn(a.pet);
        if (a.entity.isValid()) {
            if (!a.fainting) {
                a.pet.health = a.entity.getHealth() / maxHealth(a.entity);
            }
            snapshot(a);
        }
        plugin.store().save(a.owner);
    }

    /** The pet entity disappeared without us (another plugin removed it). Its last saved state is kept. */
    public void lost(Active a) {
        untrack(a);
        plugin.happiness().wentIn(a.pet);
        plugin.store().save(a.owner);
    }

    private void snapshot(Active a) {
        try {
            a.pet.snapshot = Bukkit.getUnsafe().serializeEntity(a.entity, EntitySerializationFlag.FORCE);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save " + a.pet.name + " (" + a.pet.species.id() + ")", ex);
        }
    }

    private void track(Player owner, Pet pet, Mob mob) {
        Active a = new Active(pet, owner.getUniqueId(), mob);
        byOwner.put(a.owner, a);
        byEntity.put(mob.getUniqueId(), a);
        plugin.happiness().cameOut(pet);
        prepare(a, owner);
        plugin.bonuses().apply(owner, a);
    }

    private void untrack(Active a) {
        byEntity.remove(a.entity.getUniqueId(), a);
        if (a.home != null) {
            posted.remove(a.pet.id, a);
            return;
        }
        if (byOwner.remove(a.owner, a)) {
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner != null) {
                plugin.bonuses().remove(owner);
            }
        }
    }

    private boolean tracked(Active a) {
        return byEntity.get(a.entity.getUniqueId()) == a;
    }

    private void recall(Active a, Player owner) {
        Pet pet = a.pet;
        stash(a);
        if (!summon(owner, pet, false)) {
            Msg.error(owner, pet.name + " couldn't follow you here and went back into its bloom.");
            plugin.menus().refresh(owner);
        }
    }

    private @Nullable Mob spawn(Pet pet, Location at) {
        World world = at.getWorld();
        if (pet.snapshot != null) {
            try {
                Entity e = Bukkit.getUnsafe().deserializeEntity(pet.snapshot, world, false);
                if (e instanceof Mob mob && e.getType() == pet.species.type()) {
                    mark(mob, pet);
                    mob.setPersistent(false);
                    return mob.spawnAt(at, CreatureSpawnEvent.SpawnReason.CUSTOM) && mob.isValid() ? mob : null;
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Couldn't restore " + pet.name + "; it comes back as a new "
                    + pet.species.displayName(), ex);
            }
        }
        Class<? extends Entity> type = pet.species.type().getEntityClass();
        if (type == null) {
            return null;
        }
        try {
            Entity e = world.spawn(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
                mark(x, pet);
                x.setPersistent(false);
            });
            if (e instanceof Mob mob && mob.isValid()) {
                return mob;
            }
            e.remove();
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Couldn't spawn a " + pet.species.displayName(), ex);
        }
        return null;
    }

    /** The pet as saved in its bloom, rebuilt but not placed in the world (to read or change what it wears). */
    @Nullable LivingEntity restore(Pet pet, World world) {
        if (pet.snapshot == null) {
            return null;
        }
        try {
            return Bukkit.getUnsafe().deserializeEntity(pet.snapshot, world, false) instanceof LivingEntity le
                ? le : null;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Couldn't read the saved " + pet.name, ex);
            return null;
        }
    }

    /** Everything an entity wears or holds. */
    static List<ItemStack> worn(LivingEntity e) {
        List<ItemStack> items = new ArrayList<>();
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return items;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (e.canUseEquipmentSlot(slot)) {
                ItemStack item = eq.getItem(slot);
                if (!item.isEmpty()) {
                    items.add(item.clone());
                }
            }
        }
        return items;
    }

    /**
     * The Vex is gone while its gear screen was open: puts the gear from the screen into its saved copy (so it
     * neither vanishes nor exists twice). False if there is no saved copy to put it in.
     */
    boolean writeGear(Pet pet, World world, Map<EquipmentSlot, ItemStack> gear) {
        LivingEntity body = restore(pet, world);
        if (body == null || body.getEquipment() == null) {
            return false;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> e : gear.entrySet()) {
            body.getEquipment().setItem(e.getKey(), e.getValue());
        }
        try {
            pet.snapshot = Bukkit.getUnsafe().serializeEntity(body, EntitySerializationFlag.FORCE);
            plugin.store().save(pet.owner);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save " + pet.name + "'s gear", ex);
            return false;
        }
    }

    private static void mark(Entity e, Pet pet) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.PET, PersistentDataType.STRING, pet.id.toString());
        pdc.set(Keys.OWNER, PersistentDataType.STRING, pet.owner.toString());
    }

    /** Behind the owner if there's room, otherwise right where they stand. Flyers appear beside their head. */
    private static Location spawnSpot(Player owner, Species species) {
        Location base = owner.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        if (species.flies()) {
            // beside your head, a little in front, where you can see (and reach) it
            return owner.getEyeLocation().add(-Math.cos(yaw) * 0.9 - Math.sin(yaw) * 0.6, 0.1,
                -Math.sin(yaw) * 0.9 + Math.cos(yaw) * 0.6);
        }
        Location behind = base.clone().add(Math.sin(yaw) * 1.5, 0, -Math.cos(yaw) * 1.5);
        Block feet = behind.getBlock();
        boolean room = feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
            && !feet.getRelative(BlockFace.DOWN).isPassable();
        return room ? behind : base.clone();
    }

    /** Turns a freshly summoned (or freshly bonded) mob into a calm pet. */
    private void prepare(Active a, OfflinePlayer owner) {
        Mob m = a.entity;
        Pet pet = a.pet;
        m.setPersistent(false);
        m.setRemoveWhenFarAway(false);
        m.setCanPickupItems(false);
        m.setAware(false);
        m.setTarget(null);
        m.setInvulnerable(false);
        m.setGravity(!pet.species.flies());
        m.customName(pet.maxed() ? Component.text("✦ " + pet.name, NamedTextColor.GOLD)
            : Component.text(pet.name, pet.species.category().color()));
        m.setCustomNameVisible(false); // shows when you look at it
        if (m instanceof Ageable ageable && !ageable.isAdult()) {
            ageable.setAgeLock(true);
        }
        if (m instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(owner);
        }
        if (m instanceof Sittable sittable) {
            sittable.setSitting(false);
        }
        switch (m) {
            case Wolf wolf -> wolf.setAngry(false);
            case IronGolem golem -> golem.setPlayerCreated(true);
            case Fox fox -> {
                fox.setSleeping(false);
                fox.setCrouching(false);
                fox.setFirstTrustedPlayer(owner);
            }
            case Ocelot ocelot -> ocelot.setTrusting(true);
            case Bee bee -> {
                bee.setAnger(0);
                bee.setHasStung(false);
                bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
            }
            case Vex vex -> {
                vex.setLimitedLifetime(false);
                vex.setOwner(null);
                vex.setCharging(false);
                EntityEquipment eq = vex.getEquipment();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (vex.canUseEquipmentSlot(slot)) {
                        eq.setDropChance(slot, 0);
                    }
                }
            }
            case PolarBear bear -> bear.setStanding(false);
            case Allay allay -> allay.setCanDuplicate(false);
            case Turtle turtle -> turtle.setHasEgg(false);
            case Armadillo armadillo -> {
                if (armadillo.getState() != Armadillo.State.IDLE) {
                    armadillo.rollOut();
                }
            }
            case Bat bat -> bat.setAwake(true);
            default -> {
            }
        }
        applyStats(a);
        if (pet.health > 0) {
            m.setHealth(Math.max(1, Math.min(maxHealth(m), pet.health * maxHealth(m))));
        }
    }

    /** Level-based stats: a little more health each level, and the category's step height. */
    void applyStats(Active a) {
        Mob m = a.entity;
        AttributeInstance health = m.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double base = health.getBaseValue();
            setModifier(m, Attribute.MAX_HEALTH, Keys.MOD_LEVEL,
                targetHealth(a.pet.species, base, a.pet.level) - base, AttributeModifier.Operation.ADD_NUMBER);
        }
        AttributeInstance step = m.getAttribute(Attribute.STEP_HEIGHT);
        if (step != null) {
            setModifier(m, Attribute.STEP_HEIGHT, Keys.MOD_STEP,
                Math.max(0, a.pet.species.category().stepHeight() - step.getBaseValue()),
                AttributeModifier.Operation.ADD_NUMBER);
        }
        if (m.getHealth() > maxHealth(m)) {
            m.setHealth(maxHealth(m));
        }
    }

    /**
     * Max health at a level: never below a floor that rises from 10 to 20 (combat pets: 20 to 40) by level 10,
     * and sturdier animals (an Iron Golem, a Camel) keep their own health plus 2% a level.
     */
    static double targetHealth(Species species, double base, int level) {
        double t = (level - 1) / (double) (Pet.MAX_LEVEL - 1);
        double floor = species.combat() ? 20 + 20 * t : 10 + 10 * t;
        return Math.max(base * (1 + 0.02 * (level - 1)), floor);
    }

    static void setModifier(LivingEntity e, Attribute attribute, NamespacedKey key, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(key);
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(key, amount, operation));
        }
    }

    static double maxHealth(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20 : Math.max(1, inst.getValue());
    }

    // =====================================================================
    //  Fainting
    // =====================================================================

    /** Pets never die: at zero health they faint, go back into their bloom and rest a while. */
    public void faint(Active a) {
        if (a.fainting) {
            return;
        }
        Pet pet = a.pet;
        if (pet.maxed() && !a.secondWindUsed) {
            // max level: one second wind each time it comes out
            a.secondWindUsed = true;
            a.entity.setHealth(maxHealth(a.entity) * 0.5);
            Location at = a.entity.getLocation().add(0, a.entity.getHeight() / 2, 0);
            at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.4, 0.5, 0.4, 0.4);
            at.getWorld().playSound(at, Sound.ITEM_TOTEM_USE, 0.5f, 1.4f);
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner != null) {
                Msg.bar(owner, Component.text(pet.name + " got a second wind!", NamedTextColor.GOLD));
            }
            return;
        }
        a.fainting = true;
        plugin.happiness().fainted(pet);
        Settings s = plugin.settings();
        pet.restingUntil = System.currentTimeMillis() + s.restSeconds * 1000L;
        pet.health = s.restHealth;
        // a moment later, so whatever was hitting it finishes first
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!tracked(a)) {
                return;
            }
            Location at = a.entity.getLocation().add(0, a.entity.getHeight() / 2, 0);
            at.getWorld().spawnParticle(Particle.POOF, at, 20, a.entity.getWidth() / 2, a.entity.getHeight() / 2,
                a.entity.getWidth() / 2, 0.02);
            at.getWorld().playSound(at, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1f, 0.7f);
            stash(a);
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner != null) {
                Msg.chat(owner, Component.text(pet.name + (a.home != null
                    ? " fainted at its post. It'll be back there after a rest"
                    : " fainted and went back into its bloom to rest")
                    + (s.restSeconds > 0 ? " (" + s.restSeconds + "s)." : "."), NamedTextColor.GRAY));
                Blooms.refresh(owner, pet);
                plugin.menus().refresh(owner);
            }
        });
    }

    public boolean fainting(Active a) {
        return a.fainting;
    }

    /** Something killed the pet outright (a plugin, /kill bypassing damage). It rests like after fainting. */
    public void died(Active a) {
        if (!tracked(a)) {
            return;
        }
        a.fainting = true;
        plugin.happiness().fainted(a.pet);
        Settings s = plugin.settings();
        a.pet.restingUntil = System.currentTimeMillis() + s.restSeconds * 1000L;
        a.pet.health = s.restHealth;
        plugin.menus().closeFor(a.pet);
        plugin.rides().eject(a);
        untrack(a);
        plugin.happiness().wentIn(a.pet);
        plugin.store().save(a.owner);
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null) {
            Msg.chat(owner, Component.text(a.pet.name + (a.home != null ? " fainted at its post and will be back "
                + "there after a rest." : " fainted and went back into its bloom to rest."), NamedTextColor.GRAY));
            plugin.menus().refresh(owner);
        }
    }

    // =====================================================================
    //  Renaming
    // =====================================================================

    /** A tidy pet name (1-24 letters, numbers, spaces and a little punctuation, no commas), or null. */
    static @Nullable String cleanName(String raw) {
        String name = raw.replace("§", "").trim().replaceAll("\\s+", " ");
        return name.isEmpty() || name.length() > 24 || !name.matches("[\\p{L}\\p{N} '!?.\\-_]+") ? null : name;
    }

    public boolean rename(Player p, Pet pet, String raw) {
        String name = cleanName(raw);
        if (name == null) {
            Msg.error(p, "Pet names are 1-24 letters, numbers and spaces.");
            return false;
        }
        Pet other = plugin.store().byName(pet.owner, name);
        if (other != null && other != pet) {
            Msg.error(p, "You already have a pet called " + other.name + ".");
            return false;
        }
        pet.name = name;
        Active a = byOwner.get(pet.owner);
        if (a != null && a.pet == pet) {
            a.entity.customName(Component.text(name, pet.species.category().color()));
        }
        Blooms.refresh(p, pet);
        plugin.store().save(pet.owner);
        plugin.menus().refresh(p);
        Msg.bar(p, Component.text("Your " + pet.species.displayName() + " is now called " + name + ".", Msg.PINK));
        return true;
    }

    // =====================================================================
    //  Bonding with wild animals, feeding
    // =====================================================================

    /** Sneak + feed a wild animal its favourite food. Enough feedings and it's yours. */
    public void bond(Player p, Mob mob, Species species, EquipmentSlot hand) {
        Settings s = plugin.settings();
        if (!s.enabled.contains(species)) {
            Msg.error(p, species.plural() + " can't become pets on this server.");
            return;
        }
        if (Keys.isBossThing(mob)) {
            Msg.error(p, "This one is far too wild to tame.");
            return;
        }
        if (mob instanceof Tameable t && t.isTamed() && t.getOwnerUniqueId() != null
            && !t.getOwnerUniqueId().equals(p.getUniqueId())) {
            Msg.error(p, "That one already has an owner.");
            return;
        }
        if (mob.isLeashed() && mob.getLeashHolder() instanceof Player holder && !holder.equals(p)) {
            Msg.error(p, "Someone else is leading that one.");
            return;
        }
        if (mob instanceof Fox fox && fox.getFirstTrustedPlayer() != null
            && !p.getUniqueId().equals(fox.getFirstTrustedPlayer().getUniqueId())
            && (fox.getSecondTrustedPlayer() == null
                || !p.getUniqueId().equals(fox.getSecondTrustedPlayer().getUniqueId()))) {
            Msg.error(p, "That fox already trusts someone else.");
            return;
        }
        if (!mob.getPassengers().isEmpty() || mob.isInsideVehicle()) {
            Msg.error(p, "Wait until nothing is riding it.");
            return;
        }
        if (plugin.store().pets(p.getUniqueId()).size() >= s.maxPets) {
            Msg.error(p, "You already have " + s.maxPets + " pets. Release one first (/pets release <name>).");
            return;
        }
        int need = s.feedsToTame(species);
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        String raw = pdc.get(Keys.BOND, PersistentDataType.STRING);
        int count = 0;
        String me = p.getUniqueId().toString();
        if (raw != null && raw.startsWith(me + ":")) {
            try {
                count = Integer.parseInt(raw.substring(me.length() + 1));
            } catch (NumberFormatException ignored) {
                count = 0;
            }
        }
        count++;
        consume(p, hand);
        Location head = mob.getLocation().add(0, mob.getHeight() + 0.2, 0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.1f);
        mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head, 6, 0.3, 0.2, 0.3, 0);
        if (count < need) {
            pdc.set(Keys.BOND, PersistentDataType.STRING, me + ":" + count);
            Msg.bar(p, Component.text("Bonding with the " + species.displayName() + "  ", NamedTextColor.GRAY)
                .append(Component.text("❤".repeat(count), Msg.PINK))
                .append(Component.text("❤".repeat(need - count), NamedTextColor.DARK_GRAY)));
            return;
        }
        pdc.remove(Keys.BOND);
        adopt(p, mob, species);
    }

    private void adopt(Player p, Mob mob, Species species) {
        Pet.RideStyle style = !species.small() ? Pet.RideStyle.NORMAL
            : ThreadLocalRandom.current().nextBoolean() ? Pet.RideStyle.GROW : Pet.RideStyle.SHRINK;
        String name = null;
        if (mob.customName() != null) {
            String given = cleanName(PlainTextComponentSerializer.plainText().serialize(mob.customName()));
            if (given != null && !plugin.store().nameTaken(p.getUniqueId(), given)) {
                name = given;
            }
        }
        if (name == null) {
            name = Blooms.freshName(plugin.store(), p.getUniqueId());
        }
        Pet pet = new Pet(UUID.randomUUID(), p.getUniqueId(), species, name, style);
        pet.happiness = plugin.settings().happyStart;
        boolean first = plugin.store().pets(p.getUniqueId()).isEmpty();
        plugin.store().add(pet);

        Active current = byOwner.get(p.getUniqueId());
        if (current != null) {
            stash(current);
        }
        unpack(p, mob);
        if (mob.isLeashed()) {
            mob.setLeashHolder(null);
        }
        mark(mob, pet);
        track(p, pet, mob);
        plugin.store().setLast(pet);
        boolean gotToy = Blooms.giveToyIfMissing(p);
        if (first) {
            give(p, Guide.book());
        }

        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 7,
            0.4, 0.3, 0.4, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        p.showTitle(Title.title(Component.text("♥ " + name, species.category().color()),
            Component.text("your new " + species.displayName(), NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(600))));
        Msg.chat(p, Component.text("You bonded with a " + species.displayName() + "! Say hi to " + name + ".",
            Msg.PINK));
        Msg.chat(p, Component.text((gotToy ? "Here's a Pet Toy: right-click it to call your pet or send it home, "
            + "punch with it to see all your pets, use it on your pet to ride. " : "")
            + (first ? "There's a little guide book in your bag too!" : "Punch with your Pet Toy to see "
            + name + " with your other pets."), NamedTextColor.GRAY));
        if (style == Pet.RideStyle.GROW) {
            Msg.chat(p, Component.text("When you ride " + name + ", it grows big enough to carry you.",
                NamedTextColor.GRAY));
        } else if (style == Pet.RideStyle.SHRINK) {
            Msg.chat(p, Component.text("When you ride " + name + ", you shrink down to its size.",
                NamedTextColor.GRAY));
        }
        plugin.menus().refresh(p);
    }

    /**
     * A saddle, or a chest and what's in it, goes to the animal's new owner instead of being locked away (pets
     * don't use saddles, and their own storage replaces chests).
     */
    private static void unpack(Player p, Mob mob) {
        if (mob instanceof AbstractHorse horse) {
            ItemStack saddle = horse.getInventory().getSaddle();
            if (saddle != null && !saddle.isEmpty()) {
                give(p, saddle);
                horse.getInventory().setSaddle(null);
            }
            if (horse instanceof ChestedHorse chested && chested.isCarryingChest()) {
                Inventory inv = chested.getInventory();
                for (int slot = 0; slot < inv.getSize(); slot++) {
                    ItemStack item = inv.getItem(slot);
                    if (item != null && !item.isEmpty()) {
                        give(p, item);
                        inv.setItem(slot, null);
                    }
                }
                chested.setCarryingChest(false);
                give(p, ItemStack.of(Material.CHEST));
            }
        }
        if (mob instanceof Steerable steerable && steerable.hasSaddle()) {
            steerable.setSaddle(false);
            give(p, ItemStack.of(Material.SADDLE));
        }
    }

    /** Feeding your own pet heals it, and gives a little bond XP (at most every 30 seconds). */
    public void feed(Player p, Active a, EquipmentSlot hand) {
        long now = System.currentTimeMillis();
        if (now - a.lastFeed < 300) {
            return;
        }
        Mob m = a.entity;
        Pet pet = a.pet;
        double max = maxHealth(m);
        boolean hurt = m.getHealth() < max - 0.01;
        boolean xp = !pet.maxed() && now - pet.lastFeedXp >= FEED_XP_COOLDOWN;
        boolean cheer = plugin.happiness().canFeedCheer(pet);
        if (!hurt && !xp && !cheer) {
            Msg.bar(p, pet.name + " isn't hungry right now.", NamedTextColor.GRAY);
            return;
        }
        a.lastFeed = now;
        consume(p, hand);
        if (hurt) {
            m.setHealth(Math.min(max, m.getHealth() + max * plugin.settings().healPercent / 100.0));
        }
        m.getWorld().playSound(m.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.1f);
        m.getWorld().spawnParticle(hurt ? Particle.HEART : Particle.HAPPY_VILLAGER,
            m.getLocation().add(0, m.getHeight() + 0.2, 0), hurt ? 3 : 6, 0.3, 0.2, 0.3, 0);
        Component msg = Component.text(pet.name + "  ", pet.species.category().color())
            .append(Component.text("❤ " + Math.round(m.getHealth()) + "/" + Math.round(max), NamedTextColor.RED));
        if (xp) {
            pet.lastFeedXp = now;
            msg = msg.append(Component.text("  +" + plugin.settings().xpFeed + " bond", Msg.PINK));
            addXp(pet, plugin.settings().xpFeed);
        }
        if (cheer && plugin.happiness().fed(p, a)) {
            msg = msg.append(Component.text("  ")).append(plugin.happiness().bar(pet, now));
        }
        Msg.bar(p, msg);
    }

    static void consume(Player p, EquipmentSlot hand) {
        if (p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack item = inv.getItem(hand);
        if (!item.isEmpty()) {
            boolean bucket = item.getType() == Material.TROPICAL_FISH_BUCKET;
            item.setAmount(item.getAmount() - 1);
            inv.setItem(hand, item.getAmount() <= 0 ? null : item);
            if (bucket) {
                give(p, ItemStack.of(Material.WATER_BUCKET)); // like the game: the fish goes, the water stays
            }
        }
    }

    static void give(Player p, ItemStack item) {
        if (p.isDead()) {
            p.getWorld().dropItemNaturally(p.getLocation(), item); // their inventory is about to be emptied
            return;
        }
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
    }

    // =====================================================================
    //  Bond XP
    // =====================================================================

    public void addXp(Pet pet, int amount) {
        if (amount <= 0 || pet.maxed()) {
            return;
        }
        int before = pet.level;
        pet.xp += amount;
        while (!pet.maxed() && pet.xp >= Pet.xpToNext(pet.level)) {
            pet.xp -= Pet.xpToNext(pet.level);
            pet.level++;
        }
        if (pet.maxed()) {
            pet.xp = 0;
        }
        if (pet.level != before) {
            leveledUp(pet, before);
        }
    }

    private void leveledUp(Pet pet, int before) {
        Player owner = Bukkit.getPlayer(pet.owner);
        Active a = activeFor(pet);
        if (a != null) {
            applyStats(a);
            if (owner != null && a.home == null) {
                plugin.bonuses().apply(owner, a);
            }
            Mob m = a.entity;
            m.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, m.getHeight() / 2, 0), 30,
                m.getWidth() / 2, m.getHeight() / 2, m.getWidth() / 2, 0.3);
        }
        if (owner != null) {
            List<String> perks = new ArrayList<>();
            int ride = plugin.settings().rideLevel(pet.species);
            if (pet.species.rideable() && before < ride && pet.level >= ride) {
                perks.add("you can ride it now (use your Pet Toy on it)");
            }
            int oldSlots = pet.species.storage().slots(before);
            if (pet.storageSlots() > oldSlots) {
                perks.add("storage " + oldSlots + " → " + pet.storageSlots() + " slots");
            }
            perks.add(pet.maxed() ? "max level: its bonus is at full strength" : "its bonus got a little stronger");
            Msg.chat(owner, Component.text(pet.name + " reached level " + pet.level + "! ", NamedTextColor.GOLD)
                .append(Component.text(capitalize(String.join(", ", perks)) + ".", NamedTextColor.GRAY)));
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            Blooms.refresh(owner, pet);
        }
        plugin.store().save(pet.owner);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // =====================================================================
    //  Releasing
    // =====================================================================

    /**
     * Says goodbye to a pet for good: everything it carries (storage, plus anything it wears or holds, like the
     * Vex's gear or a wolf's armor) goes to the owner, and its blooms wilt.
     */
    public void release(Player owner, Pet pet) {
        Active a = activeFor(pet);
        if (a != null) {
            stash(a);
        }
        pet.station = null;
        plugin.bonuses().forget(pet.id);
        plugin.menus().closeFor(pet);
        List<ItemStack> carried = new ArrayList<>();
        for (ItemStack item : pet.storage) {
            if (item != null && !item.isEmpty()) {
                carried.add(item);
            }
        }
        LivingEntity body = restore(pet, owner.getWorld());
        if (body != null) {
            carried.addAll(worn(body));
        }
        for (ItemStack item : carried) {
            give(owner, item);
        }
        PlayerInventory inv = owner.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (pet.id.equals(Blooms.petId(inv.getItem(slot)))) {
                inv.setItem(slot, null);
            }
        }
        plugin.store().remove(pet);
        plugin.menus().refresh(owner);
    }

    // =====================================================================
    //  Combat
    // =====================================================================

    /** A mob hurt this pet: a combat pet at its post fights back. */
    public void defend(Active a, LivingEntity attacker) {
        if (a.home != null && a.pet.species.combat() && !a.fainting && validGuardTarget(a, attacker)) {
            a.target = attacker;
        }
    }

    private boolean validGuardTarget(Active a, LivingEntity t) {
        Location home = a.home;
        double r = guardRadius(a) + 8;
        return home != null && t.isValid() && !t.isDead() && t.getWorld() == home.getWorld()
            && t.getLocation().distanceSquared(home) <= r * r && !(t instanceof Player) && !(t instanceof ArmorStand)
            && !Keys.isPet(t) && !Keys.isStatue(t) && !t.isInvulnerable()
            && !(t instanceof Tameable tame && a.owner.equals(tame.getOwnerUniqueId()));
    }

    /** Not creepers (they'd blow up the base), and neutral mobs only once they're angry. */
    static boolean worthFighting(Mob mob) {
        if (mob instanceof Creeper) {
            return false;
        }
        boolean neutral = mob instanceof org.bukkit.entity.Enderman || mob instanceof org.bukkit.entity.PigZombie;
        return !neutral || mob.getTarget() != null;
    }

    double guardRadius(Active a) {
        return plugin.settings().guardRadius * (a.pet.maxed() ? 1.5 : 1);
    }

    /** A stationed combat pet keeps hostile mobs away from its post. */
    private void guard(Active a) {
        if (a.target != null && !validGuardTarget(a, a.target)) {
            a.target = null;
        }
        if (a.target != null || a.ticks % 10 != 0 || a.home == null) {
            return;
        }
        double r = guardRadius(a);
        LivingEntity best = null;
        double bestDist = r * r;
        for (Entity e : a.home.getWorld().getNearbyEntities(a.home, r, Math.min(r, 8), r)) {
            if (e instanceof Mob mob && e instanceof Enemy && validGuardTarget(a, mob) && worthFighting(mob)) {
                double d = mob.getLocation().distanceSquared(a.home);
                if (d < bestDist) {
                    bestDist = d;
                    best = mob;
                }
            }
        }
        a.target = best;
    }

    /** Whether this player turned on "pets join PvP fights" (the wool button in the pet menu). */
    public boolean pvp(Player owner) {
        return plugin.settings().petPvp && owner.getPersistentDataContainer()
            .getOrDefault(Keys.PVP, PersistentDataType.BOOLEAN, false);
    }

    public void setPvp(Player owner, boolean on) {
        owner.getPersistentDataContainer().set(Keys.PVP, PersistentDataType.BOOLEAN, on);
        Active a = byOwner.get(owner.getUniqueId());
        if (!on && a != null && a.target instanceof Player) {
            a.target = null;
        }
    }

    /** A player this owner's pet may fight: PvP is on for the owner and in the world, and it's someone else. */
    private boolean fairGame(Player owner, Player victim) {
        return pvp(owner) && !victim.equals(owner) && !victim.isDead()
            && (victim.getGameMode() == GameMode.SURVIVAL || victim.getGameMode() == GameMode.ADVENTURE)
            && !Boolean.FALSE.equals(victim.getWorld().getGameRuleValue(org.bukkit.GameRules.PVP));
    }

    /** Whether the pet {@code a} may hurt this player right now (it's fighting them, and PvP is on). */
    public boolean mayHurt(Active a, Player victim) {
        Player owner = Bukkit.getPlayer(a.owner);
        return owner != null && a.home == null && victim.equals(a.target) && fairGame(owner, victim);
    }

    /** The owner is fighting this mob (they hit it, or it hit them or their pet): combat pets join in. */
    public void engage(Player owner, LivingEntity target) {
        Active a = byOwner.get(owner.getUniqueId());
        if (a == null || !a.pet.species.combat() || a.fainting || plugin.rides().isRidden(a)) {
            return;
        }
        if (target != a.entity && validTarget(target, owner)) {
            a.target = target;
        }
    }

    private boolean validTarget(LivingEntity t, Player owner) {
        if (!t.isValid() || t.isDead() || t.getWorld() != owner.getWorld()
            || t.getLocation().distanceSquared(owner.getLocation()) > TARGET_RANGE * TARGET_RANGE) {
            return false;
        }
        if (t instanceof Player victim) {
            return fairGame(owner, victim);
        }
        if (t instanceof ArmorStand || Keys.isPet(t) || Keys.isStatue(t) || t.isInvulnerable()) {
            return false;
        }
        return !(t instanceof Tameable tame && owner.getUniqueId().equals(tame.getOwnerUniqueId()));
    }

    private void updateTarget(Active a, Player owner) {
        if (a.target != null && !validTarget(a.target, owner)) {
            a.target = null;
        }
        if (a.target != null || a.ticks % 10 != 0) {
            return;
        }
        // defend the owner: the closest hostile mob that is after them
        LivingEntity best = null;
        double bestDist = 12 * 12;
        for (Entity e : owner.getNearbyEntities(12, 6, 12)) {
            if (e instanceof Mob mob && e instanceof Enemy && !(e instanceof Creeper)
                && owner.equals(mob.getTarget()) && validTarget(mob, owner)) {
                double d = mob.getLocation().distanceSquared(owner.getLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = mob;
                }
            }
        }
        a.target = best;
    }

    private void tryAttack(Active a, @Nullable Player owner, LivingEntity t) {
        long now = System.currentTimeMillis();
        if (now - a.lastAttack < ATTACK_COOLDOWN) {
            return;
        }
        Mob m = a.entity;
        Location mp = m.getLocation();
        Location tp = t.getLocation();
        double dx = tp.getX() - mp.getX();
        double dz = tp.getZ() - mp.getZ();
        double reach = reach(m, t);
        double dy = (tp.getY() + t.getHeight() / 2) - (mp.getY() + m.getHeight() / 2);
        if (dx * dx + dz * dz > reach * reach || Math.abs(dy) > 1.5 + (m.getHeight() + t.getHeight()) / 2) {
            return;
        }
        Species sp = a.pet.species;
        if (sp != Species.VEX && !m.hasLineOfSight(t)) {
            return;
        }
        a.lastAttack = now;
        double base = sp.attackDamage();
        if (sp == Species.VEX) {
            AttributeInstance dmg = m.getAttribute(Attribute.ATTACK_DAMAGE);
            base = dmg == null ? base : dmg.getValue(); // includes its weapon
        }
        face(m, yaw(dx, dz));
        m.swingMainHand();
        if (m instanceof IronGolem) {
            m.playEffect(EntityEffect.ENTITY_ATTACK);
        }
        if (owner != null && owner.getWorld() == t.getWorld()
            && owner.getLocation().distanceSquared(t.getLocation()) < 32 * 32) {
            t.setKiller(owner); // the kill counts as yours (XP, drops), like a tamed wolf's, when you're there
        }
        Skills.Hit hit = plugin.skills().before(a, t, base * a.pet.power());
        double before = t.getHealth() + t.getAbsorptionAmount();
        t.damage(hit.damage, DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(m).withDirectEntity(m).build());
        double dealt = Math.max(0, before - (t.isDead() ? 0 : t.getHealth() + t.getAbsorptionAmount()));
        if (dealt <= 0) {
            return; // it didn't land (blocked, protected, still flinching from the last hit)
        }
        if (sp == Species.GOAT) {
            t.knockback(1.1, -dx, -dz);
        } else if (sp == Species.IRON_GOLEM) {
            t.setVelocity(t.getVelocity().add(new Vector(0, 0.4, 0)));
        }
        plugin.skills().after(a, owner, t, hit, dealt);
        combatRewards(a, owner, t, hit, dealt);
    }

    /**
     * Bond XP for every hit that lands (more for a big one), and mcMMO Taming XP for the owner like a wolf's hits
     * give. A pet guarding its post only earns while its owner is nearby.
     */
    private void combatRewards(Active a, @Nullable Player owner, LivingEntity t, Skills.Hit hit, double dealt) {
        Settings s = plugin.settings();
        if (owner == null || !owner.isOnline() || owner.getWorld() != a.entity.getWorld()) {
            return;
        }
        if (a.home != null
            && owner.getLocation().distanceSquared(a.entity.getLocation()) > s.guardOwnerRange * s.guardOwnerRange) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - a.pet.lastCombatXp >= s.combatXpCooldown) {
            a.pet.lastCombatXp = now;
            addXp(a.pet, hit.critical ? s.xpCombatCrit : s.xpCombatHit);
        }
        if (s.tamingXp && plugin.skills().on() && Skills.hasSkills(a.pet.species)) {
            plugin.mcmmo().hitXp(owner, t, dealt);
        }
    }

    private static double reach(Mob m, LivingEntity t) {
        return 1.3 + (m.getWidth() + t.getWidth()) / 2;
    }

    // =====================================================================
    //  The tick: following, fighting, recalls
    // =====================================================================

    private void tick() {
        if (!posted.isEmpty()) {
            tickPosted();
        }
        if (byOwner.isEmpty()) {
            return;
        }
        Settings s = plugin.settings();
        for (Active a : List.copyOf(byOwner.values())) {
            if (byOwner.get(a.owner) != a) {
                continue;
            }
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner == null) {
                stash(a);
                continue;
            }
            Mob m = a.entity;
            if (!m.isValid()) {
                lost(a);
                continue;
            }
            if (a.fainting) {
                continue;
            }
            a.ticks++;
            if (a.ticks % 1200 == 0) {
                addXp(a.pet, s.xpPerMinute);
            }
            if (a.ticks % 1200 == 600 && m.getPassengers().isEmpty() && !plugin.menus().gearOpen(a.pet)) {
                a.pet.health = m.getHealth() / maxHealth(m);
                snapshot(a); // in case the server stops without warning
                plugin.store().save(a.owner);
            }
            if (owner.getGameMode() == GameMode.SPECTATOR) {
                stash(a);
                plugin.menus().refresh(owner);
                continue;
            }
            if (plugin.rides().isRidden(a)) {
                a.target = null;
                continue;
            }
            if (m.getWorld() != owner.getWorld()
                || m.getLocation().distanceSquared(owner.getLocation()) > RECALL_DISTANCE * RECALL_DISTANCE) {
                recall(a, owner);
                continue;
            }
            if (owner.isDead()) {
                continue;
            }
            sparkle(a);
            walkingXp(a, owner);
            if (a.fetchItem != null && fetch(a, owner)) {
                continue;
            }
            if (a.pet.species.combat()) {
                updateTarget(a, owner);
                if (m.getTarget() != a.target) {
                    m.setTarget(a.target); // also keeps a chasing pet "active" far from players
                }
            }
            LivingEntity target = a.target;
            if (a.pet.species.flies()) {
                fly(a, owner, target);
            } else {
                walk(a, owner, target);
            }
            if (target != null && byOwner.get(a.owner) == a) {
                tryAttack(a, owner, target);
            }
        }
    }

    /** Where the pet belongs: its post, or its owner. */
    private static Location anchor(Active a, @Nullable Player owner) {
        if (a.home != null) {
            return a.roamSpot != null && a.target == null ? a.roamSpot : a.home;
        }
        return owner != null ? owner.getLocation() : a.entity.getLocation();
    }

    private void walk(Active a, @Nullable Player owner, @Nullable LivingEntity target) {
        walk(a, owner, target, null);
    }

    /** {@code spot}: somewhere to run to instead (the fetch stick). */
    private void walk(Active a, @Nullable Player owner, @Nullable LivingEntity target, @Nullable Location spot) {
        Mob m = a.entity;
        Pet pet = a.pet;
        Location pos = m.getLocation();
        Location goal = spot != null ? spot : target != null ? target.getLocation() : anchor(a, owner);
        double dx = goal.getX() - pos.getX();
        double dz = goal.getZ() - pos.getZ();
        double dy = goal.getY() - pos.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (target == null && spot == null && (flat > TELEPORT_DISTANCE || Math.abs(dy) > 8)) {
            teleportNear(a, owner);
            return;
        }
        double stop = spot != null ? 0.6 : target != null ? reach(m, target) * 0.8
            : a.home != null ? 1.0 : 2.2 + m.getWidth() / 2;
        if ((flat <= stop && Math.abs(dy) < 2.5) || System.currentTimeMillis() < a.pausedUntil) {
            a.path = null;
            a.stuck = 0;
            a.lastPos = null;
            if (a.ticks % 5 == 0) {
                face(m, yaw(dx, dz));
            }
            return;
        }
        double speed = 0.2 * pet.species.category().followSpeed() * (1 + 0.02 * (pet.level - 1));
        if (flat > 7) {
            speed *= 1.5;
        }
        if (target != null) {
            speed *= 1.2;
        }
        Location wp = waypoint(a, goal);
        double wx = wp.getX() - pos.getX();
        double wz = wp.getZ() - pos.getZ();
        double wl = Math.sqrt(wx * wx + wz * wz);
        double vx = 0;
        double vz = 0;
        if (wl > 0.01) {
            double step = Math.min(speed, wl);
            vx = wx / wl * step;
            vz = wz / wl * step;
        }
        double vy = m.getVelocity().getY();
        if (m.isInWater()) {
            double swim = pet.species.swims() ? 1.6 : 0.7;
            vx *= swim;
            vz *= swim;
            vy = dy > -1 ? 0.1 : vy; // swim up, unless you went diving
        }
        // stuck against something: hop, and in the end just come over
        if (a.lastPos != null && a.lastPos.getWorld() == pos.getWorld()) {
            double mx = pos.getX() - a.lastPos.getX();
            double mz = pos.getZ() - a.lastPos.getZ();
            a.stuck = mx * mx + mz * mz < speed * speed * 0.04 ? a.stuck + 1 : Math.max(0, a.stuck - 2);
        }
        a.lastPos = pos;
        if (a.stuck > 60) {
            a.stuck = 0;
            if (spot != null) {
                endFetch(a, true); // can't get to it: the stick comes back to you anyway
            } else if (target == null) {
                teleportNear(a, owner);
            } else {
                a.target = null;
            }
            return;
        }
        if (m.isOnGround() && ((a.stuck >= 8 && a.stuck % 8 == 0)
            || wp.getY() - pos.getY() > pet.species.category().stepHeight() + 0.05)) {
            vy = 0.42 + 0.1 * Math.max(0, pet.species.category().stepHeight() - 1);
        } else if (pet.species == Species.RABBIT && m.isOnGround() && Math.abs(vx) + Math.abs(vz) > 0.01) {
            vy = 0.3; // rabbits hop
            m.playEffect(EntityEffect.RABBIT_JUMP);
        }
        m.setVelocity(new Vector(vx, vy, vz));
        if (Math.abs(vx) + Math.abs(vz) > 0.001) {
            face(m, yaw(vx, vz));
        }
    }

    /** The next point along the path to {@code goal}, or the goal itself when there is no path. */
    private static Location waypoint(Active a, Location goal) {
        Mob m = a.entity;
        Location pos = m.getLocation();
        boolean stale = a.path == null || a.pathGoal == null || a.pathGoal.getWorld() != goal.getWorld()
            || a.pathGoal.distanceSquared(goal) > 2.25 || a.ticks - a.nextPathTick > 40;
        if (stale && a.ticks >= a.nextPathTick) {
            Pathfinder.PathResult result = m.getPathfinder().findPath(goal);
            a.path = result == null ? null : result.getPoints();
            a.pathIndex = 0;
            a.pathGoal = goal.clone();
            a.nextPathTick = a.ticks + 10;
        }
        List<Location> path = a.path;
        if (path != null) {
            while (a.pathIndex < path.size()) {
                Location p = path.get(a.pathIndex);
                double cx = p.getX() + 0.5 - pos.getX();
                double cz = p.getZ() + 0.5 - pos.getZ();
                if (cx * cx + cz * cz < 0.36) {
                    a.pathIndex++;
                    continue;
                }
                return new Location(pos.getWorld(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        return goal;
    }

    private void fly(Active a, @Nullable Player owner, @Nullable LivingEntity target) {
        fly(a, owner, target, null);
    }

    private void fly(Active a, @Nullable Player owner, @Nullable LivingEntity target, @Nullable Location spot) {
        Mob m = a.entity;
        Location pos = m.getLocation();
        Location o = owner != null ? owner.getLocation() : pos;
        boolean ownerMoving = a.ownerLast == null || a.ownerLast.getWorld() != o.getWorld()
            || a.ownerLast.distanceSquared(o) > 0.0016;
        a.ownerLast = o;
        a.ownerStill = ownerMoving ? 0 : a.ownerStill + 1;
        Location goal;
        if (spot != null) {
            goal = spot.clone().add(0, 0.3, 0);
        } else if (target != null) {
            goal = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        } else if (a.home != null || owner == null) {
            // at its post: hovers over the spot
            goal = anchor(a, owner).clone().add(0, 1.2 + Math.sin(a.ticks / 12.0) * 0.15, 0);
        } else {
            Location eye = owner.getEyeLocation();
            double near = pos.getWorld() == eye.getWorld() ? pos.distance(eye) : Double.MAX_VALUE;
            if ((a.ownerStill > 8 && near < 5 && near > 0.8) || System.currentTimeMillis() < a.pausedUntil) {
                // you're standing still: it hovers right where it is (easy to reach), bobbing a little
                m.setVelocity(new Vector(0, Math.cos(a.ticks / 10.0) * 0.02, 0));
                if (a.ticks % 5 == 0) {
                    face(m, yaw(eye.getX() - pos.getX(), eye.getZ() - pos.getZ()));
                }
                a.stuck = 0;
                a.lastPos = pos;
                return;
            }
            // on the move: it flits beside your head, a little ahead of you, like a fairy
            double yaw = Math.toRadians(o.getYaw());
            goal = eye.clone().add(-Math.cos(yaw) * 0.9 - Math.sin(yaw) * 0.4,
                0.15 + Math.sin(a.ticks / 9.0) * 0.15,
                -Math.sin(yaw) * 0.9 + Math.cos(yaw) * 0.4);
        }
        Vector d = goal.toVector().subtract(pos.toVector());
        double len = d.length();
        if (target == null && spot == null && len > TELEPORT_DISTANCE) {
            teleportNear(a, owner);
            return;
        }
        if (len < 0.25) {
            m.setVelocity(new Vector());
            if (a.ticks % 5 == 0) {
                face(m, o.getYaw());
            }
            a.stuck = 0;
            return;
        }
        double max = 0.5 * (1 + 0.02 * (a.pet.level - 1));
        double speed = Math.min(max, len * 0.3);
        m.setVelocity(d.multiply(speed / len));
        face(m, len > 1.5 ? yaw(d.getX(), d.getZ()) : o.getYaw());
        // Bees and Allays can get stuck behind walls (the Vex flies through them)
        if (a.lastPos != null && a.lastPos.getWorld() == pos.getWorld() && len > 2
            && a.lastPos.distanceSquared(pos) < 0.0025) {
            if (++a.stuck > 40) {
                a.stuck = 0;
                if (spot != null) {
                    endFetch(a, true);
                } else if (target == null) {
                    teleportNear(a, owner);
                } else {
                    a.target = null;
                }
            }
        } else {
            a.stuck = Math.max(0, a.stuck - 1);
        }
        a.lastPos = pos;
    }

    /** Environmentally Aware and friends: out of the fire, back to its owner (or its post). */
    void escape(Active a) {
        teleportNear(a, Bukkit.getPlayer(a.owner));
    }

    private void teleportNear(Active a, @Nullable Player owner) {
        a.roamSpot = null;
        Mob m = a.entity;
        Location spot = a.home != null || owner == null
            ? anchor(a, owner).clone().add(0, a.pet.species.flies() && a.home != null ? 1.2 : 0, 0)
            : spawnSpot(owner, a.pet.species);
        m.setVelocity(new Vector());
        m.teleport(spot);
        a.path = null;
        a.stuck = 0;
        a.lastPos = null;
        a.nextPathTick = a.ticks;
    }

    // =====================================================================
    //  Stationing: a pet keeps a post (level 8+)
    // =====================================================================

    /** Leaves the pet that's out at the spot where it stands. */
    public void station(Player owner, Active a) {
        Settings s = plugin.settings();
        Pet pet = a.pet;
        if (a.home != null) {
            return;
        }
        if (pet.level < s.stationLevel) {
            Msg.error(owner, pet.name + " can keep a post from level " + s.stationLevel + " (now level " + pet.level + ").");
            return;
        }
        long count = plugin.store().pets(owner.getUniqueId()).stream().filter(Pet::stationed).count();
        if (count >= s.maxStations) {
            Msg.error(owner, "You already have " + s.maxStations + " pets at posts. Call one back first.");
            return;
        }
        if (!a.entity.isOnGround() && !pet.species.flies() && !a.entity.isInWater()) {
            Msg.error(owner, "Let " + pet.name + " land first.");
            return;
        }
        plugin.rides().eject(a);
        endFetch(a, true);
        if (byOwner.remove(owner.getUniqueId(), a)) {
            plugin.bonuses().remove(owner);
        }
        Location home = a.entity.getLocation();
        if (pet.species.flies()) {
            home = home.getBlock().getLocation().add(0.5, 0, 0.5);
            while (home.getY() > home.getWorld().getMinHeight() && home.clone().add(0, -1, 0).getBlock().isPassable()
                && a.entity.getLocation().getY() - home.getY() < 4) {
                home.add(0, -1, 0); // a flyer's post is the ground under it
            }
        }
        a.home = home;
        a.target = null;
        pet.stationAt(home);
        posted.put(pet.id, a);
        plugin.store().save(owner.getUniqueId());
        String job = switch (pet.species) {
            case BEE -> "tend the crops around here";
            case ALLAY -> "collect the drops around here";
            case SNIFFER -> "dig around here";
            case CAT, OCELOT -> "keep creepers and phantoms away from here";
            case LLAMA -> "spit at monsters that come close";
            default -> pet.species.combat() ? "guard this spot" : "wait right here";
        };
        Msg.chat(owner, Component.text(pet.name + " will " + job + ". ", Msg.PINK)
            .append(Component.text("Sneak + punch it with your Pet Toy (or pick it in the menu) to call it back.",
                NamedTextColor.GRAY)));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
        plugin.menus().refresh(owner);
    }

    /** Puts stationed pets whose spot is loaded back at their post (after a rest, a restart, or a chunk load). */
    private void placeStationed() {
        long now = System.currentTimeMillis();
        for (Pet pet : plugin.store().stationed()) {
            if (posted.containsKey(pet.id) || pet.resting(now) || !plugin.settings().enabled.contains(pet.species)
                || now < postRetry.getOrDefault(pet.id, 0L)) {
                continue;
            }
            Location home = pet.stationLocation();
            if (home == null || !home.getWorld().isChunkLoaded(home.getBlockX() >> 4, home.getBlockZ() >> 4)
                || !home.getChunk().isEntitiesLoaded()
                || (pet.species == Species.VEX && home.getWorld().getDifficulty() == Difficulty.PEACEFUL)) {
                continue;
            }
            Mob mob = spawn(pet, home.clone().add(0, pet.species.flies() ? 1.2 : 0, 0));
            if (mob == null) {
                postRetry.put(pet.id, now + 60_000);
                continue;
            }
            Active a = new Active(pet, pet.owner, mob);
            a.home = home;
            posted.put(pet.id, a);
            byEntity.put(mob.getUniqueId(), a);
            plugin.happiness().cameOut(pet);
            prepare(a, Bukkit.getOfflinePlayer(pet.owner));
        }
    }

    private void tickPosted() {
        for (Active a : List.copyOf(posted.values())) {
            if (!tracked(a) || a.home == null) {
                continue;
            }
            Mob m = a.entity;
            if (!m.isValid()) {
                lost(a);
                continue;
            }
            if (a.fainting) {
                continue;
            }
            a.ticks++;
            if (a.ticks % 1200 == 600 && !plugin.menus().gearOpen(a.pet)) {
                a.pet.health = m.getHealth() / maxHealth(m);
                snapshot(a);
                plugin.store().save(a.owner);
            }
            if (m.getWorld() != a.home.getWorld() || m.getLocation().distanceSquared(a.home) > 48 * 48) {
                teleportNear(a, null);
                continue;
            }
            sparkle(a);
            Player owner = Bukkit.getPlayer(a.owner);
            if (a.pet.species.combat()) {
                guard(a);
                if (m.getTarget() != a.target) {
                    m.setTarget(a.target);
                }
            }
            roam(a);
            LivingEntity target = a.target;
            if (a.pet.species.flies()) {
                fly(a, owner, target);
            } else {
                walk(a, owner, target);
            }
            if (target != null && tracked(a)) {
                tryAttack(a, owner, target);
            }
        }
    }

    /**
     * Guard duty: a stationed pet strolls around its post (within the roam radius), now and then stopping for a
     * look around. A fight comes first; afterwards it heads back to its post.
     */
    private void roam(Active a) {
        Location home = a.home;
        int radius = plugin.settings().roamRadius;
        if (home == null || radius <= 0) {
            a.roamSpot = null;
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (a.target != null) {
            a.roamSpot = null; // back to the post once the fight is over, then strolling again
            a.roamNext = a.ticks + 100 + r.nextInt(100);
            return;
        }
        if (a.ticks < a.roamNext || System.currentTimeMillis() < a.pausedUntil) {
            return;
        }
        a.roamNext = a.ticks + 100 + r.nextInt(160); // 5-13 seconds per spot
        if (r.nextInt(4) == 0) {
            a.roamSpot = null; // back to the post itself for a bit
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 2 + r.nextDouble() * Math.max(0, radius - 2);
            Location spot = home.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            if (a.pet.species.flies()) {
                if (spot.clone().add(0, 1.2, 0).getBlock().isPassable()) {
                    a.roamSpot = spot;
                    return;
                }
                continue;
            }
            Location ground = standable(spot);
            if (ground != null) {
                a.roamSpot = ground;
                return;
            }
        }
    }

    /** Somewhere to stand near this spot (solid, dry ground with room above), or null. */
    private static @Nullable Location standable(Location spot) {
        Block base = spot.getBlock();
        for (int dy = 2; dy >= -3; dy--) {
            Block feet = base.getRelative(0, dy, 0);
            Block below = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
                && below.getType().isSolid() && below.getType() != Material.MAGMA_BLOCK
                && below.getType() != Material.CACTUS && !below.getType().name().contains("CAMPFIRE")
                && feet.getType() != Material.FIRE && feet.getType() != Material.SWEET_BERRY_BUSH
                && feet.getType() != Material.POWDER_SNOW) {
                return new Location(spot.getWorld(), spot.getX(), feet.getY(), spot.getZ());
            }
        }
        return null;
    }

    // =====================================================================
    //  Fetch
    // =====================================================================

    /** Throws the Fetch Stick for the pet that's out. False (and nothing thrown) if nobody can play. */
    public boolean throwFetch(Player owner, ItemStack stick) {
        Active a = byOwner.get(owner.getUniqueId());
        if (a == null) {
            Msg.error(owner, "Call a pet out to play fetch!");
            return false;
        }
        if (!a.pet.species.playsFetch()) {
            Msg.error(owner, a.pet.name + " doesn't play fetch.");
            return false;
        }
        if (a.fetchItem != null || a.fainting || plugin.rides().isRidden(a)) {
            return false;
        }
        Location eye = owner.getEyeLocation();
        ItemStack thrown = stick.clone();
        thrown.setAmount(1);
        Item item = owner.getWorld().dropItem(eye, thrown, i -> {
            i.setCanPlayerPickup(false);
            i.setCanMobPickup(false);
            i.setPersistent(false);
            i.setUnlimitedLifetime(true);
            i.setThrower(owner.getUniqueId());
            i.setVelocity(eye.getDirection().multiply(1.1).add(new Vector(0, 0.15, 0)));
        });
        a.fetchItem = item;
        a.fetchReturning = false;
        a.fetchStarted = System.currentTimeMillis();
        a.target = null;
        owner.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.7f, 0.8f);
        return true;
    }

    /** One tick of fetch: run to the stick, carry it back, drop it at your feet. True if fetch drove the pet. */
    private boolean fetch(Active a, Player owner) {
        Item item = a.fetchItem;
        if (item == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!item.isValid() || now - a.fetchStarted > 25_000 || item.getWorld() != a.entity.getWorld()) {
            endFetch(a, true);
            return false;
        }
        Mob m = a.entity;
        if (!a.fetchReturning) {
            if (item.getLocation().distanceSquared(m.getLocation()) < 1.6 * 1.6 && now - a.fetchStarted > 600) {
                a.fetchReturning = true;
                m.getWorld().playSound(m.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
                item.setVelocity(new Vector());
                item.setGravity(false);
                item.teleport(m.getLocation().add(0, m.getHeight() + 0.1, 0)); // it carries the stick home
            } else {
                Location spot = item.getLocation();
                if (a.pet.species.flies()) {
                    fly(a, owner, null, spot);
                } else {
                    walk(a, owner, null, spot);
                }
                return true;
            }
        }
        item.teleport(m.getLocation().add(0, m.getHeight() + 0.1, 0));
        item.setVelocity(new Vector());
        if (m.getLocation().distanceSquared(owner.getLocation()) < 2.4 * 2.4) {
            endFetch(a, true);
            Location at = m.getLocation().add(0, m.getHeight() + 0.3, 0);
            m.getWorld().spawnParticle(Particle.HEART, at, 4, 0.3, 0.2, 0.3, 0);
            m.getWorld().playSound(at, Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.7f, 1.2f);
            Component msg = Component.text(a.pet.name + " brought the stick back! ♥", Msg.PINK);
            if (now - a.pet.lastFetchXp >= 15_000 && !a.pet.maxed()) {
                a.pet.lastFetchXp = now;
                msg = msg.append(Component.text("  +2 bond", NamedTextColor.LIGHT_PURPLE));
                addXp(a.pet, 2);
            }
            if (plugin.settings().happiness) {
                plugin.happiness().fetched(owner, a);
                msg = msg.append(Component.text("  ")).append(plugin.happiness().bar(a.pet, now));
            }
            Msg.bar(owner, msg);
            return true;
        }
        if (a.pet.species.flies()) {
            fly(a, owner, null, owner.getLocation());
        } else {
            walk(a, owner, null, owner.getLocation());
        }
        return true;
    }

    /** Fetch is over: the thrown stick disappears and is back in its owner's inventory. */
    private void endFetch(Active a, boolean giveBack) {
        Item item = a.fetchItem;
        if (item == null) {
            return;
        }
        a.fetchItem = null;
        a.fetchReturning = false;
        ItemStack stick = item.getItemStack();
        if (item.isValid()) {
            item.remove();
        }
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null) {
            give(owner, stick); // (an offline owner can take a new stick from the menu)
        }
    }

    // =====================================================================
    //  Little things
    // =====================================================================

    /** Max-level pets sparkle a little. */
    private static void sparkle(Active a) {
        if (a.pet.maxed() && a.ticks % 30 == 0) {
            Mob m = a.entity;
            m.getWorld().spawnParticle(Particle.WAX_OFF, m.getLocation().add(0, m.getHeight() + 0.2, 0), 2,
                m.getWidth() / 2, 0.2, m.getWidth() / 2, 0);
        }
    }

    /** Bond XP for walking around together (riding has its own). */
    private void walkingXp(Active a, Player owner) {
        Location here = owner.getLocation();
        Location last = a.walkLast;
        a.walkLast = here;
        if (last == null || last.getWorld() != here.getWorld() || owner.isInsideVehicle() || owner.isFlying()
            || owner.isGliding() || owner.isSwimming() || owner.isInWater()
            || owner.getIdleDuration().toSeconds() >= 20) {
            return; // walking, not drifting in a water stream or standing AFK
        }
        double dx = here.getX() - last.getX();
        double dz = here.getZ() - last.getZ();
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 1.5) {
            return; // teleported, not walked
        }
        a.walked += step;
        int per = plugin.settings().walkBlocksPerXp;
        if (a.walked >= per) {
            int xp = (int) (a.walked / per);
            a.walked -= xp * per;
            addXp(a.pet, xp);
        }
    }

    static void face(LivingEntity e, float yaw) {
        e.setRotation(yaw, 0);
        e.setBodyYaw(yaw);
    }

    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
