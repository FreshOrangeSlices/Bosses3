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

    // --- Items ---
    public static NamespacedKey ITEM_KIND;       // string: GEAR, RUNE, RELIC, CATALYST, GUIDE
    public static NamespacedKey ITEM_RANK;       // string (BossRank name)
    public static NamespacedKey GEAR_SOURCE;     // string, plain name of the boss that dropped it
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

        ITEM_KIND = new NamespacedKey(plugin, "item_kind");
        ITEM_RANK = new NamespacedKey(plugin, "item_rank");
        GEAR_SOURCE = new NamespacedKey(plugin, "gear_source");
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
