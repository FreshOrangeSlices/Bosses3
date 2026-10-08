package com.additionalbosses.nemesis;

import com.additionalbosses.util.Rng;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Personal names for Nemeses, picked to fit the mob. No two Nemeses on the server share a name: when a mob's list
 * is used up, a new name is built from syllables in the same style.
 */
public final class NemesisNames {

    private static final Map<EntityType, List<String>> NAMES = new EnumMap<>(EntityType.class);

    static {
        put(EntityType.ZOMBIE, "Gorrath", "Rotgut", "Mawkin", "Grubb", "Sallowjaw", "Hollowmere");
        put(EntityType.HUSK, "Dustmaw", "Sirocco", "Parchjaw", "Duneshade", "Sandrot", "Old Khamsin");
        put(EntityType.DROWNED, "Brinewell", "Gloam", "Saltbones", "Kelpgrin", "Undertow", "Drownmother");
        put(EntityType.ZOMBIE_VILLAGER, "Hagrim", "Old Mott", "Brother Mire", "Grimsby", "Hollow Ezra", "Ashwick");
        put(EntityType.SKELETON, "Scrawl", "Rattlebone", "Ossian", "Marrowmind", "Clatter", "Sliver");
        put(EntityType.STRAY, "Rimeshank", "Frostmarrow", "Hoarfang", "Shiverbone", "Glacis", "Wintermute");
        put(EntityType.BOGGED, "Mirewick", "Sporegrin", "Fenrattle", "Moldmarrow", "Bogsworth", "Rotroot");
        put(EntityType.PARCHED, "Sunsear", "Cinderbone", "Ashmarrow", "Scorchwhistle", "Drybone Dax", "Mirage");
        put(EntityType.WITHER_SKELETON, "Charnel", "Vexmourn", "Soot King", "Blackmarrow", "Grimshard", "Coalheart");
        put(EntityType.CREEPER, "Hissfang", "Fusewick", "Sizzlegrin", "Cinderhush", "Thunderwick", "Boomwhisper");
        put(EntityType.SPIDER, "Skitterfang", "Vashra", "Eightfold", "Silkshade", "Venna", "Old Widow");
        put(EntityType.CAVE_SPIDER, "Needlefang", "Venomweft", "Skrit", "Gloomspinner", "Bitterweb", "Nyx");
        put(EntityType.ENDERMAN, "Voidgaze", "Starless", "Hushstep", "Nullwalker", "Eclipse", "Long Shadow");
        put(EntityType.ENDERMITE, "Voidtick", "Blip", "Speck", "Chitter", "Pip");
        put(EntityType.WITCH, "Mother Hemlock", "Granny Grimbrew", "Nettle", "Old Wyrt", "Belladonna", "Auntie Bane");
        put(EntityType.PILLAGER, "Captain Raske", "Crowmark", "Quarrel", "Harrow", "Bolt", "Fletch");
        put(EntityType.VINDICATOR, "Johnny", "Grudge", "Varn", "Hatchet", "Cleaver", "Brother Axe");
        put(EntityType.EVOKER, "Fangcaller", "Maledict", "Oracle Vex", "Thorne", "Hexmouth", "Grand Malice");
        put(EntityType.ILLUSIONER, "Mirage", "Twofold", "Glimmer", "Hollowface", "Phantasm");
        put(EntityType.RAVAGER, "Ironhide", "Gorehorn", "Bulldoze", "Stampede", "Rampart", "Old Thunder");
        put(EntityType.BLAZE, "Pyre", "Ember Rook", "Scorchling", "Kindlewrath", "Flarecrown", "Ashfall");
        put(EntityType.GHAST, "Wail", "Weepwhisper", "Sorrowcloud", "Moan", "Teardrift", "Grief");
        put(EntityType.MAGMA_CUBE, "Slagheart", "Molten Mo", "Cinderblob", "Lavalump", "Searbounce", "Crucible");
        put(EntityType.SLIME, "Gloop", "Wobblegut", "Squelch", "Bouncewart", "Mucus Rex", "Jellyhide");
        put(EntityType.HOGLIN, "Tuskbreaker", "Gnash", "Bristleback", "Hamhock", "Snortwrath", "Big Ham");
        put(EntityType.ZOGLIN, "Rottusk", "Grimbristle", "Snarl", "Witherhock", "Mangetooth");
        put(EntityType.PIGLIN, "Goldtooth", "Glint", "Nugget", "Brassjaw", "Baron Bling", "Gildfang");
        put(EntityType.PIGLIN_BRUTE, "Kragg", "Goldcrush", "Bastion", "Hammerhock", "Grimgold", "Big Bruiser");
        put(EntityType.ZOMBIFIED_PIGLIN, "Rotgild", "Pallor", "Tarnish", "Gildrot", "Mourngold", "Sallow Sow");
        put(EntityType.PHANTOM, "Insomnia", "Nightrend", "Skyrot", "Dreamshade", "Moonwing", "Lullaby");
        put(EntityType.BREEZE, "Gale", "Whirl", "Tempest", "Zephyr", "Gusto", "Squall");
        put(EntityType.GUARDIAN, "Spike", "Tidewatch", "Prismfin", "Glare", "Sentinel");
        put(EntityType.ELDER_GUARDIAN, "Fathom", "Deepgaze", "Tidelord", "Brinewatch", "Leviathan");
        put(EntityType.SILVERFISH, "Nibble", "Gnawstone", "Burrow", "Itch", "Scuttle");
        put(EntityType.VEX, "Tink", "Jinx", "Hexling", "Prick", "Sting");
        put(EntityType.WARDEN, "Hush", "Sculkheart", "Dirge", "Echo", "Silence");
        put(EntityType.WITHER, "Ruin", "Blight", "Mortis", "Nevermore", "Desolation");
        put(EntityType.SHULKER, "Shellshock", "Lidlock", "Purpur", "Hover", "Clamp");
    }

    private static final String[] START = {"Grim", "Mor", "Vex", "Rot", "Skar", "Dread", "Gloom", "Ash", "Kra",
        "Vor", "Hex", "Mal", "Thorn", "Bane", "Wrath"};
    private static final String[] END = {"maw", "gash", "fang", "mourn", "tooth", "grin", "shade", "rend", "wick",
        "hollow", "claw", "bite", "scar", "spite", "gloom"};

    private NemesisNames() {
    }

    private static void put(EntityType type, String... names) {
        NAMES.put(type, List.of(names));
    }

    /** A name that fits the mob and isn't used by any other Nemesis ({@code taken} is lower-case). */
    public static String pick(EntityType type, Set<String> taken) {
        List<String> free = new ArrayList<>();
        for (String name : NAMES.getOrDefault(type, List.of())) {
            if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
                free.add(name);
            }
        }
        if (!free.isEmpty()) {
            return free.get(Rng.between(0, free.size() - 1));
        }
        for (int attempt = 0; attempt < 200; attempt++) {
            String name = START[Rng.between(0, START.length - 1)] + END[Rng.between(0, END.length - 1)];
            if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        int n = 2;
        String base = START[0] + END[0];
        while (taken.contains((base + " " + n).toLowerCase(Locale.ROOT))) {
            n++;
        }
        return base + " " + n;
    }
}
