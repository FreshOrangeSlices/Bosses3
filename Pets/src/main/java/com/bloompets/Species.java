package com.bloompets;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The pet roster. Each species has a category (how it moves), the food that bonds and heals it, the flower its Pet
 * Bloom looks like, how much it can carry, and one small passive bonus (see {@link Bonuses}).
 *
 * <p>{@code rideScale} &gt; 0 marks a small pet: to ride it, either the pet grows by that factor or the rider shrinks
 * by it (decided once per pet, 50/50, when it is tamed).</p>
 */
public enum Species {

    // ---- Combat: fight beside you, slowest ----
    WOLF(EntityType.WOLF, Category.COMBAT, "Wolf", meats(), "oxeye_daisy", Storage.MEDIUM, 1.6, 4,
        "Pack Hunter: +8% melee damage"),
    POLAR_BEAR(EntityType.POLAR_BEAR, Category.COMBAT, "Polar Bear", of(Material.SALMON, Material.COOKED_SALMON),
        "lily_of_the_valley", Storage.MEDIUM, 0, 6, "Thick Fur: you can't freeze, and Slowness can't touch you"),
    IRON_GOLEM(EntityType.IRON_GOLEM, Category.COMBAT, "Iron Golem", of(Material.IRON_INGOT), "poppy", Storage.MEDIUM,
        0, 8, "Bulwark: +3 armor"),
    GOAT(EntityType.GOAT, Category.COMBAT, "Goat", of(Material.WHEAT), "azure_bluet", Storage.MEDIUM, 0, 4,
        "Sure-footed: 40% less fall damage"),
    PANDA(EntityType.PANDA, Category.COMBAT, "Panda", of(Material.BAMBOO), "white_tulip", Storage.MEDIUM, 0, 5,
        "Bamboo Nap: stand still for a moment and you slowly heal"),
    VEX(EntityType.VEX, Category.COMBAT, "Vex", of(Material.EMERALD), "wither_rose", Storage.NONE, 0, 4,
        "Phantom Edge: +10% attack speed. Wears armor and weapons; can't be ridden"),

    // ---- Pack: carry the most, step up 1.5 blocks ----
    DONKEY(EntityType.DONKEY, Category.PACK, "Donkey", of(Material.GOLDEN_CARROT), "allium", Storage.PACK, 0, 0,
        "Sturdy Back: +2 hearts"),
    MULE(EntityType.MULE, Category.PACK, "Mule", of(Material.APPLE), "rose_bush", Storage.PACK, 0, 0,
        "Stubborn: +30% knockback resistance"),
    LLAMA(EntityType.LLAMA, Category.PACK, "Llama", of(Material.HAY_BLOCK), "peony", Storage.PACK, 0, 0,
        "Spit: mobs that hit you get spat on"),
    CAMEL(EntityType.CAMEL, Category.PACK, "Camel", of(Material.CACTUS), "sunflower", Storage.PACK, 0, 0,
        "Desert Stride: Speed on sand, soul sand and snow"),
    SNIFFER(EntityType.SNIFFER, Category.PACK, "Sniffer", of(Material.TORCHFLOWER_SEEDS), "pitcher_plant",
        Storage.PACK, 0, 0, "Treasure Nose: now and then it digs up buried treasure, armor trims included"),

    // ---- Speedsters: fastest, good jump ----
    HORSE(EntityType.HORSE, Category.SPEEDSTER, "Horse", of(Material.SUGAR), "lilac", Storage.MEDIUM, 0, 0,
        "Stamina: +8% movement speed"),
    FOX(EntityType.FOX, Category.SPEEDSTER, "Fox", of(Material.SWEET_BERRIES, Material.GLOW_BERRIES), "torchflower",
        Storage.SMALL, 2.2, 0, "Sly: mobs notice you from 25% shorter range"),
    RABBIT(EntityType.RABBIT, Category.SPEEDSTER, "Rabbit", of(Material.CARROT, Material.DANDELION), "pink_tulip",
        Storage.SMALL, 3.0, 0, "Spring: Jump Boost"),
    OCELOT(EntityType.OCELOT, Category.SPEEDSTER, "Ocelot", of(Material.TROPICAL_FISH), "orange_tulip", Storage.SMALL,
        2.4, 0, "Night Watch: creepers and phantoms keep away, and you wake up to little gifts"),
    STRIDER(EntityType.STRIDER, Category.SPEEDSTER, "Strider", of(Material.WARPED_FUNGUS), "crimson_fungus",
        Storage.MEDIUM, 0, 0, "Lava-born: Fire Resistance in the Nether, and it walks on lava"),

    // ---- Utility: about their bonus ----
    CAT(EntityType.CAT, Category.UTILITY, "Cat", of(Material.COD, Material.COOKED_COD), "red_tulip", Storage.SMALL, 2.4,
        0, "Night Watch: creepers and phantoms keep away, and you wake up to little gifts"),
    BEE(EntityType.BEE, Category.UTILITY, "Bee", m -> Tag.FLOWERS.isTagged(m), "dandelion", Storage.SMALL, 2.6, 0,
        "Pollinator: crops near you grow faster"),
    ALLAY(EntityType.ALLAY, Category.UTILITY, "Allay", of(Material.AMETHYST_SHARD), "blue_orchid", Storage.SMALL, 3.0,
        0, "Collector: pulls nearby drops to you and bottles loose XP into its storage"),
    CHICKEN(EntityType.CHICKEN, Category.UTILITY, "Chicken", of(Material.WHEAT_SEEDS, Material.MELON_SEEDS,
        Material.PUMPKIN_SEEDS, Material.BEETROOT_SEEDS), "oxeye_daisy", Storage.SMALL, 2.6, 0,
        "Feather Fall: you glide down long drops"),
    COW(EntityType.COW, Category.UTILITY, "Cow", of(Material.WHEAT), "cornflower", Storage.MEDIUM, 0, 0,
        "Fresh Milk: clears one bad effect every 2 minutes"),
    TURTLE(EntityType.TURTLE, Category.UTILITY, "Turtle", of(Material.SEAGRASS), "lily_pad", Storage.SMALL, 1.8, 0,
        "Gills: you can breathe underwater"),
    ARMADILLO(EntityType.ARMADILLO, Category.UTILITY, "Armadillo", of(Material.SPIDER_EYE), "pink_petals",
        Storage.SMALL, 2.6, 0, "Scutes: 20% less damage from arrows and other projectiles"),
    FROG(EntityType.FROG, Category.UTILITY, "Frog", of(Material.SLIME_BALL), "big_dripleaf", Storage.SMALL, 2.4, 0,
        "Frog Kick: Dolphin's Grace while swimming");

    /** How much a pet carries. Sizes grow with level (see {@link #slots}). */
    public enum Storage {
        NONE, SMALL, MEDIUM, PACK;

        /** Inventory slots at this level: small 9 -> 18, medium 18 -> 27, pack 27 -> 54 (a double chest). */
        public int slots(int level) {
            return switch (this) {
                case NONE -> 0;
                case SMALL -> level >= 5 ? 18 : 9;
                case MEDIUM -> level >= 6 ? 27 : 18;
                case PACK -> level >= 10 ? 54 : level >= 7 ? 45 : level >= 4 ? 36 : 27;
            };
        }

        public String displayName() {
            return switch (this) {
                case NONE -> "None";
                case SMALL -> "Small";
                case MEDIUM -> "Medium";
                case PACK -> "Large";
            };
        }
    }

    private final EntityType type;
    private final Category category;
    private final String displayName;
    private final Predicate<Material> food;
    private final String bloomModel;
    private final Storage storage;
    private final double rideScale;
    private final double attackDamage;
    private final String bonus;

    Species(EntityType type, Category category, String displayName, Predicate<Material> food, String bloomModel,
            Storage storage, double rideScale, double attackDamage, String bonus) {
        this.type = type;
        this.category = category;
        this.displayName = displayName;
        this.food = food;
        this.bloomModel = bloomModel;
        this.storage = storage;
        this.rideScale = rideScale;
        this.attackDamage = attackDamage;
        this.bonus = bonus;
    }

    private static Predicate<Material> of(Material... materials) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        set.addAll(java.util.List.of(materials));
        return set::contains;
    }

    private static Predicate<Material> meats() {
        return of(Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP, Material.CHICKEN,
            Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON, Material.RABBIT, Material.COOKED_RABBIT,
            Material.ROTTEN_FLESH);
    }

    public EntityType type() {
        return type;
    }

    public Category category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    /** "Foxes", "Wolves", "Polar Bears"... */
    public String plural() {
        if (displayName.endsWith("x")) {
            return displayName + "es";
        }
        if (displayName.endsWith("f")) {
            return displayName.substring(0, displayName.length() - 1) + "ves";
        }
        return displayName + "s";
    }

    public boolean likes(Material material) {
        return food.test(material);
    }

    public String bloomModel() {
        return bloomModel;
    }

    public Storage storage() {
        return storage;
    }

    /** Small pets: the factor the pet grows by (or the rider shrinks by) to ride. 0 = big enough already. */
    public double rideScale() {
        return rideScale;
    }

    public boolean small() {
        return rideScale > 0;
    }

    public boolean combat() {
        return category == Category.COMBAT;
    }

    /** Base melee damage of a combat pet. */
    public double attackDamage() {
        return attackDamage;
    }

    public boolean rideable() {
        return this != VEX;
    }

    /** Bees and Allays hover a few blocks up when ridden; the Vex flies on its own. */
    public boolean hovers() {
        return this == BEE || this == ALLAY;
    }

    /** Turtles and Frogs are quick in water, following or ridden. */
    public boolean swims() {
        return this == TURTLE || this == FROG;
    }

    public boolean flies() {
        return this == BEE || this == ALLAY || this == VEX;
    }

    public String bonus() {
        return bonus;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static @Nullable Species of(EntityType type) {
        for (Species s : values()) {
            if (s.type == type) {
                return s;
            }
        }
        return null;
    }

    public static @Nullable Species parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Species s : values()) {
            if (s.name().equals(key)) {
                return s;
            }
        }
        return null;
    }
}
