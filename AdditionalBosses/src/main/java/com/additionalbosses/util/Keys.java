package com.additionalbosses.util;

import com.additionalbosses.boss.BossRank;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every PersistentDataContainer key and attribute-modifier key the plugin uses, in one place.
 */
public final class Keys {

    // --- Boss entities ---
    public static NamespacedKey BOSS;            // byte marker
    public static NamespacedKey BOSS_RANK;       // string (BossRank name)
    public static NamespacedKey BOSS_TRAITS;     // string, comma separated trait ids
    public static NamespacedKey BOSS_CATEGORY;   // string, mob category id
    public static NamespacedKey BOSS_UNDYING;    // byte, Undying trait already used
    public static NamespacedKey MINION;          // string, UUID of the boss that summoned it
    public static NamespacedKey WORLDGEN_CHECKED; // byte, a structure/world-gen mob already had its boss roll
    public static NamespacedKey BOSS_LAST_STAND; // byte, Last Stand already triggered
    public static NamespacedKey NEMESIS;         // string, nemesis record id
    public static NamespacedKey STATUE;          // string, statue data (on statue entities and statue items)
    public static NamespacedKey STATUE_LORE;     // list<string>, MiniMessage lore lines kept on a placed statue
    public static NamespacedKey BOSS_THREAT;     // double 0..1, Threat Scaling already applied
    public static NamespacedKey BOSS_PHASE;      // int, Ascendant phases already passed
    public static NamespacedKey STATUE_ITEM;     // string, base64 of the exact item a placed statue/trophy came from
    public static NamespacedKey WAYSTONE;        // string, waystone id (on waystone items)
    public static NamespacedKey WAYSTONE_LABEL;  // string, waystone id (on its floating name)
    public static NamespacedKey PLAYER_DRY_KILLS; // int, boss kills in a row without a reward (pity counter)
    public static NamespacedKey COMPASS_TIER;    // int
    public static NamespacedKey TOTEM_RANK;      // string, rank a Boss Totem summons ("" = random)

    // --- Items ---
    public static NamespacedKey ITEM_KIND;       // string: ItemService.Kind name
    public static NamespacedKey ITEM_RANK;       // string (BossRank name)
    public static NamespacedKey GEAR_SOURCE;     // string, plain name of the boss that dropped it
    public static NamespacedKey GEAR_QUALITY;    // string, GearQuality name
    public static NamespacedKey RUNE_STAT;       // string, empowerment stat id
    public static NamespacedKey RUNE_AMOUNT;     // double
    public static NamespacedKey RELIC_ID;        // string, relic effect id (on a relic item)
    public static NamespacedKey RELIC_CURSE;     // string, curse id (on a corrupted relic item)
    public static NamespacedKey EMPOWERMENTS;    // list<string> "stat:amount" (on equipment)
    public static NamespacedKey RELICS;          // list<string> relic ids (on equipment)
    public static NamespacedKey CURSES;          // list<string> curse ids (on equipment)
    public static NamespacedKey RELIC_SLOTS;     // int, relic slots on equipment
    public static NamespacedKey LORE_LINES;      // int, how many lore lines at the top were written by this plugin

    // --- Players ---
    public static NamespacedKey PLAYER_GUIDE_GIVEN;
    public static NamespacedKey PLAYER_RELICS_BOUND;
    public static NamespacedKey PLAYER_SECOND_DAWN;
    private static final Map<BossRank, NamespacedKey> KILLS = new EnumMap<>(BossRank.class);

    // --- Attribute modifiers placed on bosses ---
    public static NamespacedKey MOD_HEALTH;
    public static NamespacedKey MOD_ARMOR;
    public static NamespacedKey MOD_TOUGHNESS;
    public static NamespacedKey MOD_KNOCKBACK;
    public static NamespacedKey MOD_SPEED;
    public static NamespacedKey MOD_SIZE;
    public static NamespacedKey MOD_TRAIT_SWIFT;
    public static NamespacedKey MOD_TRAIT_BERSERK;
    public static NamespacedKey MOD_LAST_STAND;
    public static NamespacedKey MOD_NEMESIS_HEALTH;
    public static NamespacedKey MOD_NEMESIS_SIZE;
    public static NamespacedKey MOD_FOLLOW;
    public static NamespacedKey MOD_DIFFICULTY;
    public static NamespacedKey MOD_HEALTH_FLOOR;
    public static NamespacedKey MOD_THREAT;
    /** On a player: epoch millis until which this plugin is using the action bar (read by our mcMMO build). */
    public static NamespacedKey ACTION_BAR_BUSY;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        BOSS = new NamespacedKey(plugin, "boss");
        BOSS_RANK = new NamespacedKey(plugin, "boss_rank");
        BOSS_TRAITS = new NamespacedKey(plugin, "boss_traits");
        BOSS_CATEGORY = new NamespacedKey(plugin, "boss_category");
        BOSS_UNDYING = new NamespacedKey(plugin, "boss_undying_used");
        MINION = new NamespacedKey(plugin, "minion_of");
        WORLDGEN_CHECKED = new NamespacedKey(plugin, "worldgen_checked");
        BOSS_LAST_STAND = new NamespacedKey(plugin, "boss_last_stand");
        NEMESIS = new NamespacedKey(plugin, "nemesis");
        STATUE = new NamespacedKey(plugin, "statue");
        STATUE_LORE = new NamespacedKey(plugin, "statue_lore");
        COMPASS_TIER = new NamespacedKey(plugin, "compass_tier");
        BOSS_THREAT = new NamespacedKey(plugin, "boss_threat");
        BOSS_PHASE = new NamespacedKey(plugin, "boss_phase");
        STATUE_ITEM = new NamespacedKey(plugin, "statue_item");
        WAYSTONE = new NamespacedKey(plugin, "waystone");
        WAYSTONE_LABEL = new NamespacedKey(plugin, "waystone_label");
        PLAYER_DRY_KILLS = new NamespacedKey(plugin, "dry_kills");
        MOD_DIFFICULTY = new NamespacedKey(plugin, "boss_difficulty");
        MOD_HEALTH_FLOOR = new NamespacedKey(plugin, "boss_health_floor");
        MOD_THREAT = new NamespacedKey(plugin, "boss_threat");
        TOTEM_RANK = new NamespacedKey(plugin, "totem_rank");
        MOD_LAST_STAND = new NamespacedKey(plugin, "last_stand_speed");
        MOD_NEMESIS_HEALTH = new NamespacedKey(plugin, "nemesis_health");
        MOD_NEMESIS_SIZE = new NamespacedKey(plugin, "nemesis_size");
        MOD_FOLLOW = new NamespacedKey(plugin, "boss_follow_range");

        ITEM_KIND = new NamespacedKey(plugin, "item_kind");
        ITEM_RANK = new NamespacedKey(plugin, "item_rank");
        GEAR_SOURCE = new NamespacedKey(plugin, "gear_source");
        GEAR_QUALITY = new NamespacedKey(plugin, "gear_quality");
        RUNE_STAT = new NamespacedKey(plugin, "rune_stat");
        RUNE_AMOUNT = new NamespacedKey(plugin, "rune_amount");
        RELIC_ID = new NamespacedKey(plugin, "relic_id");
        RELIC_CURSE = new NamespacedKey(plugin, "relic_curse");
        EMPOWERMENTS = new NamespacedKey(plugin, "empowerments");
        RELICS = new NamespacedKey(plugin, "relics");
        CURSES = new NamespacedKey(plugin, "curses");
        RELIC_SLOTS = new NamespacedKey(plugin, "relic_slots");
        LORE_LINES = new NamespacedKey(plugin, "lore_lines");

        PLAYER_GUIDE_GIVEN = new NamespacedKey(plugin, "guide_given");
        PLAYER_RELICS_BOUND = new NamespacedKey(plugin, "relics_bound");
        PLAYER_SECOND_DAWN = new NamespacedKey(plugin, "second_dawn_ready_at");
        ACTION_BAR_BUSY = new NamespacedKey(plugin, "actionbar_busy_until");
        for (BossRank rank : BossRank.values()) {
            KILLS.put(rank, new NamespacedKey(plugin, "kills_" + rank.name().toLowerCase()));
        }

        MOD_HEALTH = new NamespacedKey(plugin, "boss_health");
        MOD_ARMOR = new NamespacedKey(plugin, "boss_armor");
        MOD_TOUGHNESS = new NamespacedKey(plugin, "boss_toughness");
        MOD_KNOCKBACK = new NamespacedKey(plugin, "boss_knockback");
        MOD_SPEED = new NamespacedKey(plugin, "boss_speed");
        MOD_SIZE = new NamespacedKey(plugin, "boss_size");
        MOD_TRAIT_SWIFT = new NamespacedKey(plugin, "trait_swift");
        MOD_TRAIT_BERSERK = new NamespacedKey(plugin, "trait_berserk");
    }

    public static NamespacedKey kills(BossRank rank) {
        return KILLS.get(rank);
    }
}
