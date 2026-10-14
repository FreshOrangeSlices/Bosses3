package com.bloompets;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * mcMMO Taming for the combat pets (Iron Golem, Vex, Polar Bear, Goat, Panda).
 *
 * <p>Under the hood these are mcMMO's wolf skills with mcMMO's numbers (Gore, Fast Food Service, Thick Fur, Holy
 * Hound, Shock Proof, Sharpened Claws, Environmentally Aware), unlocked by the owner's mcMMO Taming level, plus one
 * starting skill and one special skill per animal. Each animal gives them its own names. The Wolf is left to mcMMO,
 * which already handles tamed wolves. A sad pet's skills are off until it cheers up.</p>
 */
public final class Skills implements Listener {

    /** The skill slots every combat pet has, and the Taming level (RetroMode scale) each unlocks at. */
    public enum Slot {
        FIRST("first", 1),
        AWARENESS("awareness", 100),
        GORE("gore", 150),
        SPECIAL("special", 200),
        FAST_FOOD("fast-food", 200),
        THICK_FUR("thick-fur", 250),
        HOLY_HOUND("holy-hound", 350),
        SHOCK_PROOF("shock-proof", 500),
        CLAWS("sharpened-claws", 750);

        public final String key;
        public final int defaultLevel;

        Slot(String key, int defaultLevel) {
            this.key = key;
            this.defaultLevel = defaultLevel;
        }
    }

    private static final Map<Species, String[]> NAMES = new EnumMap<>(Species.class);

    static {
        // in Slot order
        NAMES.put(Species.IRON_GOLEM, new String[]{"Iron Colossus", "Construct Awareness", "Crushing Blow",
            "Guardian Intervention", "Self Repair", "Iron Plating", "Arcane Core", "Reinforced Frame", "Forged Fists"});
        NAMES.put(Species.VEX, new String[]{"Double Strike", "Phantom Awareness", "Spectral Rend", "Phase Strike",
            "Soul Siphon", "Ethereal Form", "Voidfed", "Stormproof", "Spectral Blade"});
        NAMES.put(Species.POLAR_BEAR, new String[]{"Unyielding", "Arctic Awareness", "Mauling Blow",
            "Crushing Presence", "Feast", "Thick Hide", "Winterborn", "Avalanche Proof", "Rending Claws"});
        NAMES.put(Species.GOAT, new String[]{"Momentum", "Mountain Awareness", "Headbutt", "Ram", "Graze",
            "Thick Horns", "Mountain Spirit", "Rock Solid", "Hardened Horns"});
        NAMES.put(Species.PANDA, new String[]{"Bamboo Blessing", "Bamboo Sense", "Heavy Paw", "Soothing Presence",
            "Snack Break", "Dense Fur", "Inner Balance", "Unshaken", "Heavy Paws"});
    }

    /** What a pet's swing turned into. */
    static final class Hit {
        double damage;
        /** A big hit (Gore, Double Strike, Momentum): more bond XP. */
        boolean critical;
        boolean ram;

        Hit(double damage) {
            this.damage = damage;
        }
    }

    private final BloomPets plugin;
    private final NamespacedKey guardianDay;

    public Skills(BloomPets plugin) {
        this.plugin = plugin;
        this.guardianDay = new NamespacedKey(plugin, "guardian_day");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Animals with Taming skills (not the Wolf: mcMMO does that one itself). */
    public static boolean hasSkills(Species s) {
        return NAMES.containsKey(s);
    }

    public static List<Species> skilled() {
        return List.copyOf(NAMES.keySet());
    }

    public static String name(Species s, Slot slot) {
        String[] names = NAMES.get(s);
        return names == null ? slot.key : names[slot.ordinal()];
    }

    /** Whether the skills work at all right now (on in the config, and mcMMO is there). */
    public boolean on() {
        return plugin.settings().skills && plugin.mcmmo().present();
    }

    public int unlock(Slot slot) {
        return plugin.settings().unlock(slot, plugin.mcmmo().scale());
    }

    private int scaled(int retroLevel) {
        return (int) Math.round(retroLevel * plugin.mcmmo().scale());
    }

    /** The owner's Taming level if this pet can use its skills now, else -1. */
    private int level(PetManager.Active a) {
        if (!on() || !hasSkills(a.pet.species) || plugin.pets().fainting(a) || plugin.happiness().sad(a.pet)) {
            return -1;
        }
        return plugin.mcmmo().tamingLevel(a.owner);
    }

    private boolean unlocked(int level, Slot slot) {
        return level >= 0 && level >= unlock(slot);
    }

    private static boolean roll(double percent) {
        return percent > 0 && ThreadLocalRandom.current().nextDouble() * 100 < percent;
    }

    // =====================================================================
    //  Numbers (also shown by /pets taming)
    // =====================================================================

    double goreChance(int level) {
        Settings s = plugin.settings();
        double max = s.goreMaxLevel * plugin.mcmmo().scale();
        return s.goreMaxChance * Math.min(1, Math.max(0, level) / max);
    }

    double doubleStrikeChance(int level) {
        Settings s = plugin.settings();
        int steps = (int) Math.floor(Math.max(0, level) / (50 * plugin.mcmmo().scale()));
        return Math.min(s.doubleStrikeMax, steps * s.doubleStrikePer50);
    }

    double phaseArmor(int level) {
        Settings s = plugin.settings();
        return level >= scaled(s.phaseHighLevel) ? s.phaseArmorHigh : s.phaseArmor;
    }

    boolean crushHigh(int level) {
        return level >= scaled(plugin.settings().crushHighLevel);
    }

    double soothChance(int level) {
        Settings s = plugin.settings();
        double from = unlock(Slot.SPECIAL);
        double to = Math.max(from + 1, 1000 * plugin.mcmmo().scale());
        double t = Math.max(0, Math.min(1, (level - from) / (to - from)));
        return s.soothChanceLow + (s.soothChanceHigh - s.soothChanceLow) * t;
    }

    double soothHeal(int level) {
        Settings s = plugin.settings();
        int steps = (int) Math.floor(Math.max(0, level - unlock(Slot.SPECIAL)) / (100 * plugin.mcmmo().scale()));
        return Math.min(s.soothHealMax, s.soothHealBase + steps * s.soothHealPer100);
    }

    // =====================================================================
    //  Attacking
    // =====================================================================

    /** Before a pet's swing lands: extra damage from its skills. */
    Hit before(PetManager.Active a, LivingEntity t, double damage) {
        Hit hit = new Hit(damage);
        int level = level(a);
        if (level < 0) {
            return hit;
        }
        Settings s = plugin.settings();
        double initial = damage;
        if (unlocked(level, Slot.CLAWS)) {
            hit.damage += s.clawsBonus;
        }
        if (unlocked(level, Slot.GORE) && roll(goreChance(level))) {
            hit.damage += initial * (s.goreMultiplier - 1);
            hit.critical = true;
            t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, t.getHeight() / 2, 0), 12,
                t.getWidth() / 2, t.getHeight() / 3, t.getWidth() / 2, 0.2);
        }
        switch (a.pet.species) {
            case VEX -> {
                if (unlocked(level, Slot.FIRST) && roll(doubleStrikeChance(level))) {
                    hit.damage *= 2;
                    hit.critical = true;
                    t.getWorld().spawnParticle(Particle.ENCHANTED_HIT, t.getLocation().add(0, t.getHeight() / 2, 0),
                        14, t.getWidth() / 2, t.getHeight() / 3, t.getWidth() / 2, 0.2);
                    a.entity.getWorld().playSound(a.entity.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.7f, 1.4f);
                }
                if (unlocked(level, Slot.SPECIAL)) {
                    hit.damage *= phaseBoost(t, hit.damage, phaseArmor(level) / 100);
                }
            }
            case GOAT -> {
                if (unlocked(level, Slot.FIRST)) {
                    // counted when a hit lands (in after), so blocked swings don't use up the third hit
                    if ((a.hits + 1) % s.momentumEvery == 0) {
                        hit.damage *= 1 + s.momentumBonus / 100;
                        hit.critical = true;
                        hit.ram = unlocked(level, Slot.SPECIAL);
                        a.entity.getWorld().playSound(a.entity.getLocation(), Sound.ENTITY_GOAT_RAM_IMPACT, 0.8f, 1f);
                    }
                }
            }
            default -> {
            }
        }
        return hit;
    }

    /**
     * Phase Strike: the hit acts as if the target wore {@code ignored} less armor. Worked out with the game's armor
     * formula, so it's the same as really ignoring that much armor.
     */
    private static double phaseBoost(LivingEntity t, double damage, double ignored) {
        AttributeInstance armorAttr = t.getAttribute(Attribute.ARMOR);
        AttributeInstance toughAttr = t.getAttribute(Attribute.ARMOR_TOUGHNESS);
        double armor = armorAttr == null ? 0 : armorAttr.getValue();
        double tough = toughAttr == null ? 0 : toughAttr.getValue();
        if (armor <= 0 || ignored <= 0) {
            return 1;
        }
        double full = armorFactor(armor, tough, damage);
        return full <= 0.001 ? 1 : armorFactor(armor * (1 - ignored), tough, damage) / full;
    }

    private static double armorFactor(double armor, double toughness, double damage) {
        double effective = Math.max(armor / 5, armor - 4 * damage / (toughness + 8));
        return 1 - Math.min(20, effective) / 25;
    }

    /** After a pet's swing landed and did {@code dealt} damage. */
    void after(PetManager.Active a, @Nullable Player owner, LivingEntity t, Hit hit, double dealt) {
        int level = level(a);
        if (level < 0 || dealt <= 0) {
            return;
        }
        Settings s = plugin.settings();
        Mob m = a.entity;
        if (a.pet.species == Species.GOAT && unlocked(level, Slot.FIRST)) {
            a.hits++;
        }
        if (unlocked(level, Slot.FAST_FOOD) && roll(s.fastFoodChance)) {
            double max = PetManager.maxHealth(m);
            if (m.getHealth() < max) {
                m.setHealth(Math.min(max, m.getHealth() + dealt));
                m.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, m.getLocation().add(0, m.getHeight() + 0.2, 0),
                    4, 0.3, 0.2, 0.3, 0);
            }
        }
        switch (a.pet.species) {
            case POLAR_BEAR -> {
                if (unlocked(level, Slot.SPECIAL) && t.isValid() && !t.isDead()) {
                    boolean high = crushHigh(level);
                    int ticks = s.crushSeconds * 20;
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, high ? 3 : 1));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, high ? 1 : 0));
                }
            }
            case GOAT -> {
                if (hit.ram && t.isValid() && !t.isDead()) {
                    Vector away = t.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 0.0001) {
                        away.normalize().multiply(0.4);
                    }
                    t.setVelocity(new Vector(away.getX(), s.ramLaunch, away.getZ()));
                    t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
                }
            }
            case PANDA -> {
                if (unlocked(level, Slot.SPECIAL) && roll(soothChance(level))) {
                    soothe(a, soothHeal(level));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Soothing Presence: heals the panda, its owner and friends nearby, and hurts undead monsters. Other players
     * only count as friends while its owner has pet PvP off (no healing the other side of a fight).
     */
    private void soothe(PetManager.Active a, double amount) {
        Mob panda = a.entity;
        double r = plugin.settings().soothRadius;
        Location at = panda.getLocation();
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 24, r / 2, 0.6, r / 2, 0);
        at.getWorld().playSound(at, Sound.ENTITY_PANDA_EAT, 0.8f, 1.2f);
        heal(panda, amount);
        Player owner = Bukkit.getPlayer(a.owner);
        boolean pvpOn = owner != null && plugin.pets().pvp(owner);
        for (Entity e : panda.getNearbyEntities(r, r / 2 + 1, r)) {
            if (!(e instanceof LivingEntity le) || le.isDead() || e.equals(a.target)) {
                continue;
            }
            if (Tag.ENTITY_TYPES_UNDEAD.isTagged(e.getType())) {
                // undead monsters only: not someone's skeleton horse, a named mob, or a villager being cured
                if (e instanceof org.bukkit.entity.Monster && !Keys.isPet(e) && !Keys.isStatue(e)
                    && e.customName() == null && !(e instanceof Tameable tame && tame.isTamed())
                    && !(e instanceof org.bukkit.entity.ZombieVillager zv && zv.isConverting())) {
                    le.damage(amount, DamageSource.builder(DamageType.INDIRECT_MAGIC)
                        .withCausingEntity(panda).withDirectEntity(panda).build());
                }
                continue;
            }
            boolean friend = e.getUniqueId().equals(a.owner) || Keys.isPet(e)
                || (e instanceof Tameable tame && a.owner.equals(tame.getOwnerUniqueId()))
                || (e instanceof Player && !pvpOn);
            if (friend) {
                heal(le, amount);
            }
        }
    }

    private static void heal(LivingEntity e, double amount) {
        double max = PetManager.maxHealth(e);
        if (amount > 0 && e.getHealth() < max) {
            e.setHealth(Math.min(max, e.getHealth() + amount));
            e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight() + 0.2, 0), 1,
                0.2, 0.1, 0.2, 0);
        }
    }

    /** Bamboo Blessing: when the panda wins a fight, it and its owner get Regeneration I. */
    void killed(PetManager.Active a) {
        if (a.pet.species != Species.PANDA || !unlocked(level(a), Slot.FIRST)) {
            return;
        }
        int ticks = plugin.settings().blessingSeconds * 20;
        a.entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0));
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null && owner.getWorld() == a.entity.getWorld()
            && owner.getLocation().distanceSquared(a.entity.getLocation()) < 32 * 32) {
            owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0));
        }
    }

    // =====================================================================
    //  Getting hurt
    // =====================================================================

    /** The pet is about to take damage: its defensive skills. */
    void hurt(PetManager.Active a, EntityDamageEvent event) {
        int level = level(a);
        if (level < 0) {
            return;
        }
        Settings s = plugin.settings();
        Mob m = a.entity;
        boolean golem = a.pet.species == Species.IRON_GOLEM;
        switch (event.getCause()) {
            case CONTACT, FIRE, HOT_FLOOR, LAVA, CAMPFIRE -> {
                if (unlocked(level, Slot.AWARENESS)) {
                    // steps out of the danger, back to its owner (or its post)
                    event.setCancelled(true);
                    m.setFireTicks(0);
                    plugin.pets().escape(a);
                } else if (golem && unlocked(level, Slot.THICK_FUR)) {
                    event.setDamage(event.getDamage() / s.thickFurDivisor);
                }
            }
            case FIRE_TICK -> {
                if (unlocked(level, Slot.THICK_FUR)) {
                    m.setFireTicks(0);
                    if (golem) {
                        event.setDamage(event.getDamage() / s.thickFurDivisor);
                    }
                }
            }
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> {
                if (unlocked(level, Slot.THICK_FUR)) {
                    event.setDamage(event.getDamage() / s.thickFurDivisor);
                }
            }
            case MAGIC, POISON, WITHER -> {
                if (unlocked(level, Slot.HOLY_HOUND)) {
                    event.setCancelled(true);
                    double max = PetManager.maxHealth(m);
                    m.setHealth(Math.min(max, m.getHealth() + event.getDamage()));
                    m.getWorld().spawnParticle(Particle.HEART, m.getLocation().add(0, m.getHeight() + 0.2, 0), 2,
                        0.3, 0.2, 0.3, 0);
                }
            }
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION, LIGHTNING -> {
                if (unlocked(level, Slot.SHOCK_PROOF)) {
                    event.setDamage(event.getDamage() / s.shockProofDivisor);
                }
            }
            case FREEZE -> {
                if (a.pet.species == Species.POLAR_BEAR && unlocked(level, Slot.FIRST)) {
                    event.setCancelled(true);
                    m.setFreezeTicks(0);
                }
            }
            default -> {
            }
        }
    }

    /** Unyielding: the Polar Bear can't be knocked back... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        PetManager.Active a = plugin.pets().of(event.getEntity());
        if (a != null && a.pet.species == Species.POLAR_BEAR && unlocked(level(a), Slot.FIRST)) {
            event.setCancelled(true);
        }
    }

    /** ...or slowed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBearSlowed(EntityPotionEffectEvent event) {
        PetManager.Active a = plugin.pets().of(event.getEntity());
        if (a != null && a.pet.species == Species.POLAR_BEAR && event.getNewEffect() != null
            && event.getNewEffect().getType() == PotionEffectType.SLOWNESS && unlocked(level(a), Slot.FIRST)) {
            event.setCancelled(true);
        }
    }

    private void tick() {
        if (!on()) {
            return;
        }
        for (PetManager.Active a : plugin.pets().all()) {
            unfreeze(a);
        }
        for (PetManager.Active a : plugin.pets().postedPets()) {
            unfreeze(a);
        }
    }

    private void unfreeze(PetManager.Active a) {
        if (a.pet.species == Species.POLAR_BEAR && a.entity.getFreezeTicks() > 0 && unlocked(level(a), Slot.FIRST)) {
            a.entity.setFreezeTicks(0);
        }
    }

    // =====================================================================
    //  Guardian Intervention
    // =====================================================================

    /**
     * Iron Golem: a blow that would kill you leaves you at half a heart with Resistance II instead, once per
     * Minecraft day, while the golem follows you (not while it guards a post). A totem in your hand goes first.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnerFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getFinalDamage() < p.getHealth()) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, WORLD_BORDER -> {
                return;
            }
            default -> {
            }
        }
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        if (a == null || a.pet.species != Species.IRON_GOLEM || a.home != null
            || !unlocked(level(a), Slot.SPECIAL) || a.entity.getWorld() != p.getWorld()
            || a.entity.getLocation().distanceSquared(p.getLocation()) > 32 * 32) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        if (inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return; // the totem saves you; the golem's help is kept for later
        }
        // the main world's clock (the Nether and End keep their own, which would allow a second save)
        long day = Bukkit.getWorlds().get(0).getFullTime() / 24000;
        Long used = p.getPersistentDataContainer().get(guardianDay, PersistentDataType.LONG);
        if (used != null && used == day) {
            return;
        }
        p.getPersistentDataContainer().set(guardianDay, PersistentDataType.LONG, day);
        event.setCancelled(true);
        p.setHealth(Math.min(PetManager.maxHealth(p), 1.0));
        p.setFireTicks(0);
        Settings s = plugin.settings();
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, s.guardianSeconds * 20, s.guardianLevel - 1));
        Location at = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.4, 0.6, 0.4, 0.3);
        p.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.8f);
        Msg.chat(p, Component.text(a.pet.name + " stepped in and saved you! ", NamedTextColor.GOLD)
            .append(Component.text("(Guardian Intervention: once per day)", NamedTextColor.GRAY)));
    }

    // =====================================================================
    //  /pets taming <animal>
    // =====================================================================

    /** One line per skill: whether it's unlocked for this level, and what it does with today's numbers. */
    List<Component> describe(Species sp, int level) {
        Settings s = plugin.settings();
        List<Component> lines = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            int at = unlock(slot);
            boolean open = level >= at;
            String what = switch (slot) {
                case FIRST -> switch (sp) {
                    case IRON_GOLEM -> "A walking fortress with 100+ health.";
                    case VEX -> fmt(doubleStrikeChance(level)) + "% chance to hit twice as hard (+"
                        + fmt(s.doubleStrikePer50) + "% per 50 levels, up to " + fmt(s.doubleStrikeMax) + "%).";
                    case POLAR_BEAR -> "Can't be knocked back, slowed or frozen.";
                    case GOAT -> "Every " + ordinal(s.momentumEvery) + " hit lands " + fmt(s.momentumBonus)
                        + "% harder.";
                    case PANDA -> "When it wins a fight, it and you get Regeneration I for " + s.blessingSeconds
                        + "s.";
                    default -> "";
                };
                case AWARENESS -> "Steps out of fire, lava and cacti, back to your side. No fall damage.";
                case GORE -> fmt(goreChance(level)) + "% chance to deal " + fmt(s.goreMultiplier) + "x damage.";
                case SPECIAL -> switch (sp) {
                    case IRON_GOLEM -> "Once per day, a blow that would kill you leaves you at half a heart with "
                        + "Resistance " + roman(s.guardianLevel) + " for " + s.guardianSeconds + "s (while it "
                        + "follows you).";
                    case VEX -> "Its strikes ignore " + fmt(phaseArmor(level)) + "% of armor ("
                        + fmt(s.phaseArmorHigh) + "% from level " + scaled(s.phaseHighLevel) + ").";
                    case POLAR_BEAR -> crushHigh(level)
                        ? "Hits slow (IV) and weaken (II) for " + s.crushSeconds + "s."
                        : "Hits slow (II) and weaken (I) for " + s.crushSeconds + "s (IV and II from level "
                        + scaled(s.crushHighLevel) + ").";
                    case GOAT -> "Its " + ordinal(s.momentumEvery) + " hit launches the target high into the air.";
                    case PANDA -> fmt(soothChance(Math.max(level, at))) + "% chance on hit to heal itself, you "
                        + "and friends nearby for " + fmt(soothHeal(Math.max(level, at)))
                        + " health, and hurt the undead.";
                    default -> "";
                };
                case FAST_FOOD -> fmt(s.fastFoodChance) + "% chance to heal by the damage it deals.";
                case THICK_FUR -> "Takes 1/" + fmt(s.thickFurDivisor) + " damage from attacks and arrows"
                    + (sp == Species.IRON_GOLEM ? " and fire" : "") + "; shakes off burning.";
                case HOLY_HOUND -> "Magic, poison and wither heal it instead of hurting it.";
                case SHOCK_PROOF -> "Takes 1/" + fmt(s.shockProofDivisor) + " damage from explosions and "
                    + "lightning.";
                case CLAWS -> "+" + fmt(s.clawsBonus) + " damage on every hit.";
            };
            Component line = Component.text(open ? " ✔ " : " ✖ ", open ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                .append(Component.text(name(sp, slot), open ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                .append(Component.text(at > 1 || !open ? " (" + at + ")" : "", NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + what, open ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            lines.add(line);
        }
        return lines;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String ordinal(int n) {
        return n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        });
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}
