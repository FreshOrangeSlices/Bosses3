package com.bloompets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Each species' small passive bonus for its owner, active while the pet is out. Every bonus gets a little stronger
 * as the pet levels up ({@link Pet#power()}).
 */
public final class Bonuses implements Listener {

    private static final List<Attribute> OWNER_ATTRIBUTES = List.of(Attribute.ATTACK_DAMAGE, Attribute.ARMOR,
        Attribute.MAX_HEALTH, Attribute.KNOCKBACK_RESISTANCE, Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED);

    private static final Set<Material> SANDY = EnumSet.of(Material.SAND, Material.RED_SAND, Material.SUSPICIOUS_SAND,
        Material.SANDSTONE, Material.RED_SANDSTONE, Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW,
        Material.SNOW_BLOCK, Material.POWDER_SNOW);

    private static final Set<Material> SNIFFABLE = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK,
        Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK,
        Material.MUD);

    private static final Set<Material> EXTRA_CROPS = EnumSet.of(Material.SWEET_BERRY_BUSH, Material.NETHER_WART,
        Material.COCOA);

    private static final Material[] CAT_GIFTS = {Material.STRING, Material.FEATHER, Material.RABBIT_HIDE,
        Material.RABBIT_FOOT, Material.CHICKEN, Material.ROTTEN_FLESH, Material.PHANTOM_MEMBRANE};

    private static final int XP_PER_BOTTLE = 7;

    private final BloomPets plugin;
    private final Map<UUID, Long> cowReady = new HashMap<>();
    private final Map<UUID, Long> llamaReady = new HashMap<>();
    private final Map<UUID, Location> pandaSpot = new HashMap<>();
    private final Map<UUID, Integer> pandaStill = new HashMap<>();
    private int tick;

    public Bonuses(BloomPets plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 4L);
    }

    // =====================================================================
    //  Stat bonuses (attribute modifiers on the owner, never saved)
    // =====================================================================

    public void apply(Player owner, PetManager.Active a) {
        remove(owner);
        double pw = a.pet.power();
        switch (a.pet.species) {
            case WOLF -> mod(owner, Attribute.ATTACK_DAMAGE, 0.08 * pw, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            case IRON_GOLEM -> mod(owner, Attribute.ARMOR, 3 * pw, AttributeModifier.Operation.ADD_NUMBER);
            case DONKEY -> mod(owner, Attribute.MAX_HEALTH, 4 * pw, AttributeModifier.Operation.ADD_NUMBER);
            case MULE -> mod(owner, Attribute.KNOCKBACK_RESISTANCE, Math.min(0.6, 0.3 * pw),
                AttributeModifier.Operation.ADD_NUMBER);
            case HORSE -> mod(owner, Attribute.MOVEMENT_SPEED, 0.08 * pw, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            case VEX -> mod(owner, Attribute.ATTACK_SPEED, 0.10 * pw, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            default -> {
            }
        }
    }

    public void remove(Player owner) {
        for (Attribute attribute : OWNER_ATTRIBUTES) {
            AttributeInstance inst = owner.getAttribute(attribute);
            if (inst != null && inst.getModifier(Keys.MOD_BONUS) != null) {
                inst.removeModifier(Keys.MOD_BONUS);
            }
        }
        double max = PetManager.maxHealth(owner);
        if (owner.getHealth() > max) {
            owner.setHealth(max);
        }
    }

    private static void mod(Player p, Attribute attribute, double amount, AttributeModifier.Operation op) {
        PetManager.setModifier(p, attribute, Keys.MOD_BONUS, amount, op);
    }

    // =====================================================================
    //  Effects that need checking all the time
    // =====================================================================

    private void tick() {
        tick += 4;
        long now = System.currentTimeMillis();
        for (PetManager.Active a : plugin.pets().all()) {
            Player p = Bukkit.getPlayer(a.owner);
            if (p == null || p.isDead() || plugin.pets().fainting(a)) {
                continue;
            }
            Pet pet = a.pet;
            double pw = pet.power();
            switch (pet.species) {
                case RABBIT -> effect(p, PotionEffectType.JUMP_BOOST, pet.level >= 8 ? 1 : 0);
                case STRIDER -> {
                    if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                        effect(p, PotionEffectType.FIRE_RESISTANCE, 0);
                    }
                }
                case TURTLE -> {
                    if (p.isInWater()) {
                        effect(p, PotionEffectType.WATER_BREATHING, 0);
                    }
                }
                case FROG -> {
                    if (p.isInWater()) {
                        effect(p, PotionEffectType.DOLPHINS_GRACE, 0);
                    }
                }
                case CAMEL -> {
                    if (SANDY.contains(p.getLocation().add(0, -0.2, 0).getBlock().getType())
                        || SANDY.contains(p.getLocation().getBlock().getType())) {
                        effect(p, PotionEffectType.SPEED, pet.level >= 8 ? 1 : 0);
                    }
                }
                case PANDA -> panda(p);
                case POLAR_BEAR -> {
                    if (p.getFreezeTicks() > 0) {
                        p.setFreezeTicks(0);
                    }
                    if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                }
                case COW -> cow(p, pet, pw, now);
                case BEE -> {
                    int every = Math.max(4, (int) Math.round(100 / pw / 4) * 4); // every 5s, a bit faster with level
                    if (tick % every == 0) {
                        bee(p);
                    }
                }
                case ALLAY -> allay(p, a);
                case CHICKEN -> {
                    if (p.getFallDistance() > 4 && !p.isGliding() && !p.isFlying() && !p.isInsideVehicle()) {
                        effect(p, PotionEffectType.SLOW_FALLING, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A quiet, particle-free effect that keeps topping itself up while the bonus applies. */
    private static void effect(Player p, PotionEffectType type, int amplifier) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > amplifier || current.isInfinite() || current.getDuration() > 60)) {
            return;
        }
        p.addPotionEffect(new PotionEffect(type, 100, amplifier, true, false, true));
    }

    /** Panda: stand still for three seconds and you slowly heal. */
    private void panda(Player p) {
        UUID id = p.getUniqueId();
        Location here = p.getLocation();
        Location before = pandaSpot.put(id, here);
        boolean still = before != null && before.getWorld() == here.getWorld() && before.distanceSquared(here) < 0.0025;
        int ticks = still ? pandaStill.getOrDefault(id, 0) + 4 : 0;
        pandaStill.put(id, ticks);
        if (ticks >= 60) {
            effect(p, PotionEffectType.REGENERATION, 0);
        }
    }

    /** Cow: clears one bad effect, then needs two minutes (a bit less at higher levels). */
    private void cow(Player p, Pet pet, double pw, long now) {
        if (now < cowReady.getOrDefault(p.getUniqueId(), 0L)) {
            return;
        }
        for (PotionEffect effect : p.getActivePotionEffects()) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                p.removePotionEffect(effect.getType());
                cowReady.put(p.getUniqueId(), now + (long) (120_000 / pw));
                Msg.bar(p, Component.text(pet.name + "'s milk cleared your ", NamedTextColor.WHITE)
                    .append(Component.translatable(effect.getType(), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.WHITE)));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.6f, 1.2f);
                return;
            }
        }
    }

    /** Bee: a couple of crops around you grow a stage. */
    private void bee(Player p) {
        Block center = p.getLocation().getBlock();
        List<Block> crops = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -2; y <= 1; y++) {
                    Block b = center.getRelative(x, y, z);
                    Material type = b.getType();
                    if ((Tag.CROPS.isTagged(type) || EXTRA_CROPS.contains(type)) && type != Material.PITCHER_CROP
                        && b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
                        crops.add(b);
                    }
                }
            }
        }
        Collections.shuffle(crops);
        for (Block b : crops.subList(0, Math.min(2, crops.size()))) {
            BlockState state = b.getState();
            if (!(state.getBlockData() instanceof Ageable age)) {
                continue;
            }
            age.setAge(age.getAge() + 1);
            state.setBlockData(age);
            if (new BlockGrowEvent(b, state).callEvent()) {
                state.update(true);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.6, 0.5), 4,
                    0.25, 0.2, 0.25, 0);
            }
        }
    }

    /** Allay: pulls drops to you, and bottles loose XP into its storage (or pulls the XP to you). */
    private void allay(Player p, PetManager.Active a) {
        Pet pet = a.pet;
        Location to = p.getLocation().add(0, 0.6, 0);
        ItemStack bottle = ItemStack.of(Material.EXPERIENCE_BOTTLE);
        boolean bottling = plugin.settings().allayBottlesXp && pet.storageSlots() > 0
            && plugin.menus().hasRoom(pet, bottle);
        for (Entity e : p.getNearbyEntities(8, 4, 8)) {
            if (e instanceof Item item) {
                // things someone dropped on purpose stay where they are
                if (item.getThrower() != null || !item.canPlayerPickup()
                    || (item.getOwner() != null && !item.getOwner().equals(p.getUniqueId()))) {
                    continue;
                }
                pull(item, to, 0.4);
            } else if (e instanceof ExperienceOrb orb) {
                if (bottling) {
                    pet.xpBank += orb.getExperience() * Math.max(1, orb.getCount());
                    orb.getWorld().spawnParticle(Particle.WAX_ON, orb.getLocation(), 3, 0.1, 0.1, 0.1, 0);
                    orb.remove();
                } else {
                    pull(orb, to, 0.35);
                }
            }
        }
        int made = 0;
        while (pet.xpBank >= XP_PER_BOTTLE && plugin.menus().hasRoom(pet, bottle)) {
            if (plugin.menus().addToStorage(pet, bottle.clone()) != null) {
                break;
            }
            pet.xpBank -= XP_PER_BOTTLE;
            made++;
        }
        if (made > 0) {
            a.entity.getWorld().playSound(a.entity.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.5f, 1.3f);
        }
    }

    private static void pull(Entity e, Location to, double speed) {
        Vector d = to.toVector().subtract(e.getLocation().toVector());
        double len = d.length();
        if (len < 1.2) {
            return;
        }
        e.setVelocity(d.multiply(Math.min(speed, len * 0.4) / len).add(new Vector(0, 0.04, 0)));
    }

    // =====================================================================
    //  Event bonuses
    // =====================================================================

    private @Nullable PetManager.Active outFor(Entity e) {
        if (!(e instanceof Player p)) {
            return null;
        }
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        return a == null || plugin.pets().fainting(a) ? null : a;
    }

    private static @Nullable Entity source(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity e ? e : null;
        }
        return damager;
    }

    /** Goat: softer landings. Armadillo: projectiles hurt less. Llama: spits at whatever hits you. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent event) {
        PetManager.Active a = outFor(event.getEntity());
        if (a == null) {
            return;
        }
        double pw = a.pet.power();
        switch (a.pet.species) {
            case GOAT -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setDamage(event.getDamage() * Math.max(0.3, 1 - 0.4 * pw));
                }
            }
            case ARMADILLO -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
                    event.setDamage(event.getDamage() * Math.max(0.5, 1 - 0.2 * pw));
                }
            }
            case POLAR_BEAR -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                    event.setCancelled(true);
                }
            }
            case LLAMA -> {
                if (event instanceof EntityDamageByEntityEvent byEntity
                    && source(byEntity.getDamager()) instanceof Mob attacker && !Keys.isPet(attacker)) {
                    spit(a, (Player) event.getEntity(), attacker);
                }
            }
            default -> {
            }
        }
    }

    private void spit(PetManager.Active a, Player owner, LivingEntity attacker) {
        long now = System.currentTimeMillis();
        if (now < llamaReady.getOrDefault(owner.getUniqueId(), 0L)) {
            return;
        }
        Mob llama = a.entity;
        if (llama.getWorld() != attacker.getWorld()
            || llama.getLocation().distanceSquared(attacker.getLocation()) > 16 * 16) {
            return;
        }
        llamaReady.put(owner.getUniqueId(), now + 1000);
        Vector dir = attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0).toVector()
            .subtract(llama.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        PetManager.face(llama, PetManager.yaw(dir.getX(), dir.getZ()));
        llama.launchProjectile(LlamaSpit.class, dir.normalize().multiply(1.5));
        llama.getWorld().playSound(llama.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
    }

    /** A pet llama's spit stings a little more as it levels up. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpitHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LlamaSpit spit && spit.getShooter() instanceof Mob llama
            && plugin.pets().of(llama) instanceof PetManager.Active a) {
            event.setDamage(2 * a.pet.power());
        }
    }

    /** Fox: mobs notice you from shorter range. Ocelot: creepers don't come for you. Cat: phantoms leave you be. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || Keys.isPet(mob)) {
            return;
        }
        PetManager.Active a = outFor(event.getTarget());
        if (a == null) {
            return;
        }
        Player p = (Player) event.getTarget();
        boolean noticed = event.getReason() == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
            || event.getReason() == EntityTargetEvent.TargetReason.RANDOM_TARGET;
        switch (a.pet.species) {
            case FOX -> {
                if (noticed) {
                    AttributeInstance range = mob.getAttribute(Attribute.FOLLOW_RANGE);
                    double r = (range == null ? 16 : range.getValue()) * Math.max(0.5, 1 - 0.25 * a.pet.power());
                    if (mob.getLocation().distanceSquared(p.getLocation()) > r * r) {
                        event.setCancelled(true);
                    }
                }
            }
            case OCELOT -> {
                if (mob instanceof Creeper && noticed) {
                    event.setCancelled(true);
                }
            }
            case CAT -> {
                if (mob instanceof Phantom) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Polar bear: Slowness can't touch you. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        PetManager.Active a = outFor(event.getEntity());
        if (a != null && a.pet.species == Species.POLAR_BEAR && event.getNewEffect() != null
            && event.getNewEffect().getType() == PotionEffectType.SLOWNESS) {
            event.setCancelled(true);
        }
    }

    /** Sniffer: digging dirt sometimes turns up seeds and flowers. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDig(BlockBreakEvent event) {
        Player p = event.getPlayer();
        PetManager.Active a = outFor(p);
        if (a == null || a.pet.species != Species.SNIFFER || p.getGameMode() == GameMode.CREATIVE
            || !SNIFFABLE.contains(event.getBlock().getType())) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= 0.04 * a.pet.power()) {
            return;
        }
        Material found = sniff(r);
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        at.getWorld().dropItemNaturally(at, ItemStack.of(found));
        a.entity.getWorld().playSound(a.entity.getLocation(), Sound.ENTITY_SNIFFER_HAPPY, 0.8f, 1f);
        Msg.bar(p, Component.text(a.pet.name + " sniffed out ", NamedTextColor.GREEN)
            .append(Component.translatable(found, NamedTextColor.GREEN)).append(Component.text("!", NamedTextColor.GREEN)));
    }

    private static Material sniff(ThreadLocalRandom r) {
        double roll = r.nextDouble();
        if (roll < 0.25) {
            return Material.TORCHFLOWER_SEEDS;
        }
        if (roll < 0.45) {
            return Material.PITCHER_POD;
        }
        if (roll < 0.80) {
            Material[] seeds = {Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.PUMPKIN_SEEDS,
                Material.MELON_SEEDS};
            return seeds[r.nextInt(seeds.length)];
        }
        List<Material> flowers = new ArrayList<>(Tag.SMALL_FLOWERS.getValues());
        flowers.removeIf(m -> m == Material.WITHER_ROSE || !m.isItem());
        return flowers.isEmpty() ? Material.POPPY : flowers.get(r.nextInt(flowers.size()));
    }

    /** Cat: you often wake up to a little gift. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWake(PlayerBedLeaveEvent event) {
        Player p = event.getPlayer();
        PetManager.Active a = outFor(p);
        if (a == null || a.pet.species != Species.CAT || p.getWorld().getTime() > 2000) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= 0.7) {
            return;
        }
        Material gift = CAT_GIFTS[r.nextInt(CAT_GIFTS.length)];
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            PetManager.give(p, ItemStack.of(gift));
            p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 1f, 1f);
            Msg.bar(p, Component.text(a.pet.name + " brought you a gift: ", Msg.PINK)
                .append(Component.translatable(gift, Msg.PINK)));
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pandaSpot.remove(id);
        pandaStill.remove(id);
        llamaReady.remove(id);
    }
}
