package com.bloompets;

import com.destroystokyo.paper.entity.Pathfinder;
import io.papermc.paper.entity.EntitySerializationFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
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
import org.bukkit.entity.Bee;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.IronGolem;
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
        // walking
        @Nullable List<Location> path;
        int pathIndex;
        int nextPathTick;
        @Nullable Location pathGoal;
        @Nullable Location lastPos;
        int stuck;

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
    private final Map<UUID, Active> byEntity = new HashMap<>();
    private @Nullable BukkitTask task;

    public PetManager(BloomPets plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Every pet goes back into its bloom (on shutdown or reload), so none are lost or duplicated. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Active a : List.copyOf(byOwner.values())) {
            stash(a);
        }
    }

    public @Nullable Active active(UUID owner) {
        return byOwner.get(owner);
    }

    public @Nullable Active of(Entity entity) {
        return byEntity.get(entity.getUniqueId());
    }

    public Collection<Active> all() {
        return List.copyOf(byOwner.values());
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
        Active current = byOwner.get(owner.getUniqueId());
        if (current != null) {
            if (current.pet == pet) {
                return true;
            }
            stash(current);
        }
        Mob mob = spawn(pet, spawnSpot(owner, pet.species));
        if (mob == null) {
            Msg.error(owner, pet.name + " can't come out here.");
            return false;
        }
        track(owner, pet, mob);
        if (announce) {
            Msg.bar(owner, Component.text(pet.name + " is here!", pet.species.category().color()));
            Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
            mob.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 18, mob.getWidth() / 2 + 0.2,
                mob.getHeight() / 2, mob.getWidth() / 2 + 0.2, 0);
            mob.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
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
        untrack(a);
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
        untrack(a);
        if (a.entity.isValid()) {
            a.pet.health = a.entity.getHealth() / maxHealth(a.entity);
            snapshot(a);
        }
        plugin.store().save(a.owner);
    }

    /** The pet entity disappeared without us (another plugin removed it). Its last saved state is kept. */
    public void lost(Active a) {
        untrack(a);
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
        prepare(a, owner);
        plugin.bonuses().apply(owner, a);
    }

    private void untrack(Active a) {
        byOwner.remove(a.owner, a);
        byEntity.remove(a.entity.getUniqueId(), a);
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null) {
            plugin.bonuses().remove(owner);
        }
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

    private static void mark(Entity e, Pet pet) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.PET, PersistentDataType.STRING, pet.id.toString());
        pdc.set(Keys.OWNER, PersistentDataType.STRING, pet.owner.toString());
    }

    /** Behind the owner if there's room, otherwise right where they stand. Flyers appear at shoulder height. */
    private static Location spawnSpot(Player owner, Species species) {
        Location base = owner.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        Location behind = base.clone().add(Math.sin(yaw) * 1.5, 0, -Math.cos(yaw) * 1.5);
        if (species.flies()) {
            return behind.add(0, 1.2, 0);
        }
        Block feet = behind.getBlock();
        boolean room = feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
            && !feet.getRelative(BlockFace.DOWN).isPassable();
        return room ? behind : base.clone();
    }

    /** Turns a freshly summoned (or freshly bonded) mob into a calm pet. */
    private void prepare(Active a, Player owner) {
        Mob m = a.entity;
        Pet pet = a.pet;
        m.setPersistent(false);
        m.setRemoveWhenFarAway(false);
        m.setCanPickupItems(false);
        m.setAware(false);
        m.setTarget(null);
        m.setInvulnerable(false);
        m.setGravity(!pet.species.flies());
        m.customName(Component.text(pet.name, pet.species.category().color()));
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
        setModifier(m, Attribute.MAX_HEALTH, Keys.MOD_LEVEL, 0.04 * (a.pet.level - 1),
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
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
        a.fainting = true;
        Pet pet = a.pet;
        Settings s = plugin.settings();
        pet.restingUntil = System.currentTimeMillis() + s.restSeconds * 1000L;
        pet.health = s.restHealth;
        // a moment later, so whatever was hitting it finishes first
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (byOwner.get(a.owner) != a) {
                return;
            }
            Location at = a.entity.getLocation().add(0, a.entity.getHeight() / 2, 0);
            at.getWorld().spawnParticle(Particle.POOF, at, 20, a.entity.getWidth() / 2, a.entity.getHeight() / 2,
                a.entity.getWidth() / 2, 0.02);
            at.getWorld().playSound(at, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1f, 0.7f);
            stash(a);
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner != null) {
                Msg.chat(owner, Component.text(pet.name + " fainted and went back into its bloom to rest"
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
        if (byOwner.get(a.owner) != a) {
            return;
        }
        a.fainting = true;
        Settings s = plugin.settings();
        a.pet.restingUntil = System.currentTimeMillis() + s.restSeconds * 1000L;
        a.pet.health = s.restHealth;
        plugin.menus().closeFor(a.pet);
        plugin.rides().eject(a);
        untrack(a);
        plugin.store().save(a.owner);
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null) {
            Msg.chat(owner, Component.text(a.pet.name + " fainted and went back into its bloom to rest.",
                NamedTextColor.GRAY));
            plugin.menus().refresh(owner);
        }
    }

    // =====================================================================
    //  Renaming
    // =====================================================================

    public boolean rename(Player p, Pet pet, String raw) {
        String name = raw.replace("§", "").trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 24 || !name.matches("[\\p{L}\\p{N} '!?.\\-_]+")) {
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
            String given = PlainTextComponentSerializer.plainText().serialize(mob.customName()).trim();
            if (!given.isEmpty() && given.length() <= 24 && !plugin.store().nameTaken(p.getUniqueId(), given)) {
                name = given;
            }
        }
        if (name == null) {
            name = Blooms.freshName(plugin.store(), p.getUniqueId());
        }
        Pet pet = new Pet(UUID.randomUUID(), p.getUniqueId(), species, name, style);
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
        give(p, Blooms.create(pet));

        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 7,
            0.4, 0.3, 0.4, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        p.showTitle(Title.title(Component.text("♥ " + name, species.category().color()),
            Component.text("your new " + species.displayName(), NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(600))));
        Msg.chat(p, Component.text("You bonded with a " + species.displayName() + "! Say hi to " + name + ".",
            Msg.PINK));
        Msg.chat(p, Component.text("Its Pet Bloom is in your inventory: right-click it to summon or dismiss "
            + name + ", punch with it to see all your pets.", NamedTextColor.GRAY));
        if (style == Pet.RideStyle.GROW) {
            Msg.chat(p, Component.text("When you ride " + name + ", it grows big enough to carry you.",
                NamedTextColor.GRAY));
        } else if (style == Pet.RideStyle.SHRINK) {
            Msg.chat(p, Component.text("When you ride " + name + ", you shrink down to its size.",
                NamedTextColor.GRAY));
        }
        plugin.menus().refresh(p);
    }

    /** A saddle the animal was wearing goes to its new owner instead of vanishing. */
    private static void unpack(Player p, Mob mob) {
        if (mob instanceof AbstractHorse horse) {
            ItemStack saddle = horse.getInventory().getSaddle();
            if (saddle != null && !saddle.isEmpty()) {
                give(p, saddle);
                horse.getInventory().setSaddle(null);
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
        if (!hurt && !xp) {
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
        Msg.bar(p, msg);
    }

    static void consume(Player p, EquipmentSlot hand) {
        if (p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack item = inv.getItem(hand);
        if (!item.isEmpty()) {
            item.setAmount(item.getAmount() - 1);
            inv.setItem(hand, item.getAmount() <= 0 ? null : item);
        }
    }

    static void give(Player p, ItemStack item) {
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
        Active a = byOwner.get(pet.owner);
        if (a != null && a.pet == pet) {
            applyStats(a);
            if (owner != null) {
                plugin.bonuses().apply(owner, a);
            }
            Mob m = a.entity;
            m.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, m.getHeight() / 2, 0), 30,
                m.getWidth() / 2, m.getHeight() / 2, m.getWidth() / 2, 0.3);
        }
        if (owner != null) {
            List<String> perks = new ArrayList<>();
            int ride = plugin.settings().rideUnlockLevel;
            if (pet.species.rideable() && before < ride && pet.level >= ride) {
                perks.add("you can ride it now (use its Pet Bloom on it)");
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

    /** Says goodbye to a pet for good: its storage goes to the owner and its blooms wilt. */
    public void release(Player owner, Pet pet) {
        Active a = byOwner.get(pet.owner);
        if (a != null && a.pet == pet) {
            stash(a);
        }
        plugin.menus().closeFor(pet);
        for (ItemStack item : pet.storage) {
            if (item != null && !item.isEmpty()) {
                give(owner, item);
            }
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

    private static boolean validTarget(LivingEntity t, Player owner) {
        if (!t.isValid() || t.isDead() || t.getWorld() != owner.getWorld()
            || t.getLocation().distanceSquared(owner.getLocation()) > TARGET_RANGE * TARGET_RANGE) {
            return false;
        }
        if (t instanceof Player || t instanceof ArmorStand || Keys.isPet(t) || Keys.isStatue(t) || t.isInvulnerable()) {
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

    private void tryAttack(Active a, Player owner, LivingEntity t) {
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
        t.damage(base * a.pet.power(), DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(owner).withDirectEntity(m).build());
        if (sp == Species.GOAT) {
            t.knockback(1.1, -dx, -dz);
        } else if (sp == Species.IRON_GOLEM) {
            t.setVelocity(t.getVelocity().add(new Vector(0, 0.4, 0)));
        }
    }

    private static double reach(Mob m, LivingEntity t) {
        return 1.3 + (m.getWidth() + t.getWidth()) / 2;
    }

    // =====================================================================
    //  The tick: following, fighting, recalls
    // =====================================================================

    private void tick() {
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
            if (a.ticks % 1200 == 600 && m.getPassengers().isEmpty()) {
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
            if (a.pet.species.combat()) {
                updateTarget(a, owner);
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

    private void walk(Active a, Player owner, @Nullable LivingEntity target) {
        Mob m = a.entity;
        Pet pet = a.pet;
        Location pos = m.getLocation();
        Location goal = target != null ? target.getLocation() : owner.getLocation();
        double dx = goal.getX() - pos.getX();
        double dz = goal.getZ() - pos.getZ();
        double dy = goal.getY() - pos.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (target == null && (flat > TELEPORT_DISTANCE || Math.abs(dy) > 8)) {
            teleportNear(a, owner);
            return;
        }
        double stop = target != null ? reach(m, target) * 0.8 : 2.2 + m.getWidth() / 2;
        if (flat <= stop && Math.abs(dy) < 2.5) {
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
            vx *= 0.7;
            vz *= 0.7;
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
            if (target == null) {
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

    private void fly(Active a, Player owner, @Nullable LivingEntity target) {
        Mob m = a.entity;
        Location pos = m.getLocation();
        Location goal;
        if (target != null) {
            goal = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        } else {
            // beside your shoulder, a little to the right and behind
            Location o = owner.getLocation();
            double yaw = Math.toRadians(o.getYaw());
            goal = o.clone().add(-Math.cos(yaw) * 1.1 + Math.sin(yaw) * 0.7,
                1.5 + Math.sin(a.ticks / 12.0) * 0.12,
                -Math.sin(yaw) * 1.1 - Math.cos(yaw) * 0.7);
        }
        Vector d = goal.toVector().subtract(pos.toVector());
        double len = d.length();
        if (target == null && len > TELEPORT_DISTANCE) {
            teleportNear(a, owner);
            return;
        }
        if (len < 0.25) {
            m.setVelocity(new Vector());
            if (a.ticks % 5 == 0) {
                face(m, owner.getLocation().getYaw());
            }
            a.stuck = 0;
            return;
        }
        double max = (target != null ? 0.5 : 0.45) * (1 + 0.02 * (a.pet.level - 1));
        double speed = Math.min(max, len * 0.2);
        m.setVelocity(d.multiply(speed / len));
        face(m, len > 1.5 ? yaw(d.getX(), d.getZ()) : owner.getLocation().getYaw());
        // Bees and Allays can get stuck behind walls (the Vex flies through them)
        if (a.lastPos != null && a.lastPos.getWorld() == pos.getWorld() && len > 2
            && a.lastPos.distanceSquared(pos) < 0.0025) {
            if (++a.stuck > 40) {
                a.stuck = 0;
                if (target == null) {
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

    private void teleportNear(Active a, Player owner) {
        Mob m = a.entity;
        Location spot = spawnSpot(owner, a.pet.species);
        m.setVelocity(new Vector());
        m.teleport(spot);
        a.path = null;
        a.stuck = 0;
        a.lastPos = null;
        a.nextPathTick = a.ticks;
    }

    static void face(LivingEntity e, float yaw) {
        e.setRotation(yaw, 0);
        e.setBodyYaw(yaw);
    }

    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
