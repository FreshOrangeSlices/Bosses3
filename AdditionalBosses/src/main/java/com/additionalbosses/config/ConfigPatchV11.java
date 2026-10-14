package com.additionalbosses.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The v11 changes to config.yml ("the final update": rarer top ranks, no speed boosts, new drop tables, Nemesis
 * rework...), as small patches. Each one is applied only where that part of the file still reads exactly as the
 * old default, so anything the server owner changed is left alone (the code has defaults for every new setting).
 * Generated from the old and new default config.yml.
 */
final class ConfigPatchV11 {

    /** {@code fallback}: only tried when the patch before it couldn't be applied (and never reported). */
    private record Patch(List<String> old, List<String> replacement, String before, String after, boolean fallback) {
    }

    private static final List<Patch> PATCHES = new ArrayList<>();

    private static void patch(String[] old, String[] replacement, String before, String after) {
        PATCHES.add(new Patch(List.of(old), List.of(replacement), before, after, false));
    }

    /** If the whole block before couldn't be updated (the owner changed part of it), still update this one line. */
    private static void fallback(String old, String replacement) {
        PATCHES.add(new Patch(List.of(old), List.of(replacement), "", "", true));
    }

    static {
        patch(new String[]{},
            new String[]{"  # A boss that has killed this many players (any mix of players) leaves,", "  # so nobody can throw themselves at a boss they aren't ready for over and", "  # over. 0 = never.", "  despawn-after-player-kills: 2", "  # Bosses glow (in their rank's colour) while a player is within this many", "  # blocks, so you can spot one nearby. 0 = never glow.", "  glow-within: 30", "  glow-rank-colors: true"},
            "  max-active: 15", "  # A new boss can't appear within this many blocks of another boss.");
        patch(new String[]{"#  stats:  health/damage/speed are multipliers; armor, armor-toughness,"},
            new String[]{"#  stats:  health/damage/speed are multipliers (speed stays 1.0: bosses", "#          don't outrun you, they leap, charge and blink instead); armor, armor-toughness,"},
            "#  RANKS", "#          knockback-resistance and size are added on top. min-health and");
        patch(new String[]{},
            new String[]{"#          extra-relics: chances for a 2nd, 3rd... relic on top of the first.", "#          Any rank can drop any relic; higher ranks just drop more of them."},
            "#  rewards: independent chances, each rolled separately on death.", "#  gear:   what Boss Gear looks like for this rank. Material (CHAINMAIL,");
        patch(new String[]{"    rewards: { boss-gear: 20, empowerment: 10, relic: 2, relic-catalyst: 0 }"},
            new String[]{"    rewards: { boss-gear: 20, empowerment: 10, relic: 70, extra-relics: [], relic-catalyst: 0 }"},
            "    xp: { multiplier: 3, bonus: 10 }", "    gear:");
        patch(new String[]{"    stats: { health: 2.0, damage: 1.35, armor: 4, armor-toughness: 1, knockback-resistance: 0.2, speed: 1.05, size: 0.22 }"},
            new String[]{"    stats: { health: 2.0, damage: 1.35, armor: 4, armor-toughness: 1, knockback-resistance: 0.2, speed: 1.0, size: 0.22 }"},
            "    name: Very Hard", "    traits: { min: 1, max: 2, power: 1.15 }");
        patch(new String[]{"    rewards: { boss-gear: 30, empowerment: 18, relic: 4, relic-catalyst: 0 }"},
            new String[]{"    rewards: { boss-gear: 30, empowerment: 18, relic: 70, extra-relics: [10], relic-catalyst: 0 }"},
            "    xp: { multiplier: 4, bonus: 25 }", "    gear:");
        patch(new String[]{"    stats: { health: 2.75, damage: 1.55, armor: 6, armor-toughness: 2, knockback-resistance: 0.3, speed: 1.08, size: 0.3 }"},
            new String[]{"    stats: { health: 2.75, damage: 1.55, armor: 6, armor-toughness: 2, knockback-resistance: 0.3, speed: 1.0, size: 0.3 }"},
            "    name: Brutal", "    traits: { min: 2, max: 2, power: 1.3 }");
        patch(new String[]{"    rewards: { boss-gear: 45, empowerment: 28, relic: 8, relic-catalyst: 0 }"},
            new String[]{"    rewards: { boss-gear: 45, empowerment: 28, relic: 75, extra-relics: [35], relic-catalyst: 0 }"},
            "    xp: { multiplier: 6, bonus: 50 }", "    gear:");
        patch(new String[]{"    stats: { health: 3.5, damage: 1.8, armor: 8, armor-toughness: 4, knockback-resistance: 0.45, speed: 1.1, size: 0.4 }"},
            new String[]{"    stats: { health: 3.5, damage: 1.8, armor: 8, armor-toughness: 4, knockback-resistance: 0.45, speed: 1.0, size: 0.4 }"},
            "    name: Nightmare", "    traits: { min: 2, max: 3, power: 1.5 }");
        patch(new String[]{"    rewards: { boss-gear: 65, empowerment: 40, relic: 15, relic-catalyst: 1.5 }"},
            new String[]{"    rewards: { boss-gear: 65, empowerment: 40, relic: 80, extra-relics: [40, 10], relic-catalyst: 1.5 }"},
            "    xp: { multiplier: 8, bonus: 100 }", "    gear:");
        patch(new String[]{"    stats: { health: 6.0, damage: 2.4, armor: 10, armor-toughness: 6, knockback-resistance: 0.6, speed: 1.12, size: 0.5, min-health: 500, min-damage: 20 }"},
            new String[]{"    stats: { health: 6.0, damage: 2.4, armor: 10, armor-toughness: 6, knockback-resistance: 0.6, speed: 1.0, size: 0.5, min-health: 500, min-damage: 20 }"},
            "    name: Legendary", "    traits: { min: 3, max: 4, power: 1.75 }");
        patch(new String[]{"    rewards: { boss-gear: 100, empowerment: 60, relic: 30, relic-catalyst: 5 }"},
            new String[]{"    rewards: { boss-gear: 100, empowerment: 60, relic: 90, extra-relics: [60, 20], relic-catalyst: 5 }"},
            "    xp: { multiplier: 12, bonus: 300 }", "    gear:");
        patch(new String[]{"    stats: { health: 10.0, damage: 3.0, armor: 14, armor-toughness: 8, knockback-resistance: 0.8, speed: 1.15, size: 0.65, min-health: 800, min-damage: 30 }"},
            new String[]{"    stats: { health: 10.0, damage: 3.0, armor: 14, armor-toughness: 8, knockback-resistance: 0.8, speed: 1.0, size: 0.65, min-health: 800, min-damage: 30 }"},
            "    name: Ascendant", "    traits: { min: 4, max: 5, power: 2.1 }");
        patch(new String[]{"    rewards: { boss-gear: 100, empowerment: 100, relic: 60, relic-catalyst: 15 }"},
            new String[]{"    rewards: { boss-gear: 100, empowerment: 100, relic: 100, extra-relics: [100, 50], relic-catalyst: 15 }"},
            "    xp: { multiplier: 20, bonus: 800 }", "    gear:");
        patch(new String[]{},
            new String[]{"#  nether-weights (optional): used instead for spawns in the Nether."},
            "#  weights: roll #2 (rank). They don't have to add up to 100.", "#  spawn-chance-multiplier: scales roll #1 for this category.");
        patch(new String[]{"    weights: { GRAY: 40, GREEN: 30, RED: 18, PURPLE: 9, GOLD: 3 }", "    # Rideable mobs (skeleton/zombie horses, camel husks...) can't be bosses.", "    mobs: [BOGGED, BREEZE, CAVE_SPIDER, CREEPER, DROWNED, ENDERMAN, ENDERMITE, GUARDIAN, HUSK,", "           PARCHED, PHANTOM, PILLAGER, SILVERFISH, SLIME, SPIDER, STRAY, VINDICATOR, WITCH,", "           ZOMBIE_VILLAGER]", "", "  zombie-skeleton:", "    display-name: Zombie & Skeleton", "    description: The most basic mobs are strangely likely to be Legendary.", "    weights: { GRAY: 37, GREEN: 28, RED: 18, PURPLE: 9, GOLD: 8 }", "    mobs: [ZOMBIE, SKELETON]", "", "  nether:", "    display-name: Nether", "    weights: { GRAY: 15, GREEN: 35, RED: 30, PURPLE: 15, GOLD: 5 }", "    mobs: [BLAZE, GHAST, HOGLIN, MAGMA_CUBE, WITHER_SKELETON, ZOGLIN]", "", "  nether-crowd:", "    display-name: Nether Crowds", "    description: They spawn in huge numbers, so they roll lower.", "    spawn-chance-multiplier: 0.5", "    weights: { GRAY: 30, GREEN: 35, RED: 23, PURPLE: 10, GOLD: 2 }", "    mobs: [PIGLIN, ZOMBIFIED_PIGLIN]", "", "  dangerous:", "    display-name: Dangerous", "    # DEFAULT lets structure mobs (Elder Guardians, Piglin Brutes in bastions,", "    # mansion illagers) roll once, the first time their chunk loads.", "    spawn-reasons: [NATURAL, PATROL, RAID, DEFAULT]", "    weights: { GRAY: 5, GREEN: 20, RED: 35, PURPLE: 30, GOLD: 10 }"},
            new String[]{"    # Nightmare and Legendary bosses are ancient-debris rare in the wild", "    # (about 1 in 200 and 1 in 1000 bosses). Boss Totems and Souls are the", "    # way to bring on harder fights on purpose.", "    weights: { GRAY: 55, GREEN: 30, RED: 14.4, PURPLE: 0.5, GOLD: 0.1 }", "    # Used instead when one of these spawns in the Nether (Soul Sand Valley", "    # skeletons...): the Nether breeds tougher bosses.", "    nether-weights: { GRAY: 40, GREEN: 35, RED: 23.5, PURPLE: 1.2, GOLD: 0.3 }", "    # Rideable mobs (skeleton/zombie horses, camel husks...) can't be bosses.", "    mobs: [BOGGED, BREEZE, CAVE_SPIDER, CREEPER, DROWNED, ENDERMAN, ENDERMITE, GUARDIAN, HUSK,", "           PARCHED, PHANTOM, PILLAGER, SILVERFISH, SLIME, SPIDER, STRAY, VINDICATOR, WITCH,", "           ZOMBIE_VILLAGER, ZOMBIE, SKELETON]", "", "  nether:", "    display-name: Nether", "    weights: { GRAY: 40, GREEN: 35, RED: 23.5, PURPLE: 1.2, GOLD: 0.3 }", "    mobs: [GHAST, HOGLIN, MAGMA_CUBE, ZOGLIN]", "", "  nether-elite:", "    display-name: Nether Elite", "    description: Blazes, Wither Skeletons and Piglins breed the toughest bosses of the Nether.", "    weights: { GRAY: 35, GREEN: 37, RED: 25.5, PURPLE: 2.0, GOLD: 0.5 }", "    mobs: [BLAZE, WITHER_SKELETON, PIGLIN]", "", "  zombified-piglins:", "    display-name: Zombified Piglins", "    description: They spawn in huge numbers, so they roll lower.", "    spawn-chance-multiplier: 0.5", "    weights: { GRAY: 60, GREEN: 30, RED: 9.88, PURPLE: 0.1, GOLD: 0.02 }", "    mobs: [ZOMBIFIED_PIGLIN]", "", "  dangerous:", "    display-name: Dangerous", "    # DEFAULT lets structure mobs (Elder Guardians, Piglin Brutes in bastions,", "    # mansion illagers) roll once, the first time their chunk loads.", "    spawn-reasons: [NATURAL, PATROL, RAID, DEFAULT]", "    weights: { GRAY: 10, GREEN: 35, RED: 53.8, PURPLE: 1.0, GOLD: 0.2 }"},
            "    display-name: Common Overworld", "    mobs: [PIGLIN_BRUTE, ELDER_GUARDIAN, RAVAGER, EVOKER, ILLUSIONER]");
        // the rank odds matter most: if the block above was changed, at least the untouched odds become rare
        fallback("    weights: { GRAY: 40, GREEN: 30, RED: 18, PURPLE: 9, GOLD: 3 }",
            "    weights: { GRAY: 55, GREEN: 30, RED: 14.4, PURPLE: 0.5, GOLD: 0.1 }");
        fallback("    weights: { GRAY: 37, GREEN: 28, RED: 18, PURPLE: 9, GOLD: 8 }",
            "    weights: { GRAY: 55, GREEN: 30, RED: 14.4, PURPLE: 0.5, GOLD: 0.1 }");
        fallback("    weights: { GRAY: 15, GREEN: 35, RED: 30, PURPLE: 15, GOLD: 5 }",
            "    weights: { GRAY: 35, GREEN: 37, RED: 25.5, PURPLE: 2.0, GOLD: 0.5 }");
        fallback("    weights: { GRAY: 30, GREEN: 35, RED: 23, PURPLE: 10, GOLD: 2 }",
            "    weights: { GRAY: 60, GREEN: 30, RED: 9.88, PURPLE: 0.1, GOLD: 0.02 }");
        fallback("    weights: { GRAY: 5, GREEN: 20, RED: 35, PURPLE: 30, GOLD: 10 }",
            "    weights: { GRAY: 10, GREEN: 35, RED: 53.8, PURPLE: 1.0, GOLD: 0.2 }");
        patch(new String[]{},
            new String[]{"  # Area attacks (Quaking's slam, Ascendant shockwaves...) never hit a", "  # player for more than this, however strong the boss.", "  area-damage-cap: 8"},
            "traits:", "  # Pairs that can never appear on the same boss.");
        patch(new String[]{"  berserk:      { enabled: true, weight: 10, health-threshold: 50, damage-bonus: 35, speed-bonus: 20 }"},
            new String[]{"  berserk:      { enabled: true, weight: 10, health-threshold: 50, damage-bonus: 35, speed-bonus: 8 }"},
            "  # ---- Offense ----", "  vampiric:     { enabled: true, weight: 10, heal-percent: 25 }");
        patch(new String[]{"  swift:        { enabled: true, weight: 10, speed-bonus: 25 }"},
            new String[]{"  # Swift only made bosses run faster, which made them impossible to fight", "  # well, so it is off. Leaping, Blinking and Charging still close the gap.", "  swift:        { enabled: false, weight: 10, speed-bonus: 25 }"},
            "  # ---- Movement ----", "  leaping:      { enabled: true, weight: 8, cooldown-seconds: 6, min-distance: 4, max-distance: 14, strength: 1.0 }");
        patch(new String[]{"  shadowed:     { enabled: true, weight: 6, effect-seconds: 4 }"},
            new String[]{"  # Shadowed: dark smoke gathers around it for a few seconds (the warning),", "  # then everyone close by is wrapped in Darkness.", "  shadowed:     { enabled: true, weight: 6, cooldown-seconds: 12, warning-seconds: 3, radius: 6, effect-seconds: 3 }"},
            "  frostbound:   { enabled: true, weight: 8, effect-seconds: 3, amplifier: 1 }", "  gravitic:     { enabled: true, weight: 5, cooldown-seconds: 8, radius: 10, strength: 0.9 }");
        patch(new String[]{"  quaking:      { enabled: true, weight: 6, cooldown-seconds: 9, radius: 5, damage: 4, knockback: 1.1 }"},
            new String[]{"  # Quaking: it rears up and a red ring marks the zone for warning-seconds,", "  # then it slams the ground.", "  quaking:      { enabled: true, weight: 6, cooldown-seconds: 9, warning-seconds: 4, radius: 5, damage: 4, knockback: 1.1 }"},
            "  gravitic:     { enabled: true, weight: 5, cooldown-seconds: 8, radius: 10, strength: 0.9 }", "  # ---- Special ----");
        patch(new String[]{"  # How often each kind of item is chosen."},
            new String[]{"  # First a category is picked by the boss's rank: weaker bosses mostly drop", "  # armor and tools, stronger ones weapons, and Ascendants an even mix.", "  category-weights:", "    GRAY:      { ARMOR: 60, TOOL: 30, WEAPON: 10 }", "    GREEN:     { ARMOR: 60, TOOL: 30, WEAPON: 10 }", "    RED:       { ARMOR: 34, TOOL: 33, WEAPON: 33 }", "    PURPLE:    { ARMOR: 30, TOOL: 0, WEAPON: 70 }", "    GOLD:      { ARMOR: 30, TOOL: 0, WEAPON: 70 }", "    ASCENDANT: { ARMOR: 34, TOOL: 33, WEAPON: 33 }", "  # Then the item inside that category. How often each kind is chosen:"},
            "boss-gear:", "  item-weights: { SWORD: 20, AXE: 12, SPEAR: 8, MACE: 2, BOW: 8, CROSSBOW: 5, TRIDENT: 2,");
        patch(new String[]{},
            new String[]{"  # Boss armor rolls Protection this many times as often as other enchantments.", "  protection-weight: 3"},
            "  allow-curse-enchantments: false", "  # From this rank up, enchantments that normally exclude each other can");
        patch(new String[]{"  corruption-chance: 10"},
            new String[]{"  corruption-chance: 25"},
            "  base-slots: 1", "  # false = you only discover a curse when you bind the relic.");
        patch(new String[]{"#  once, when they drop low: stronger traits, faster, and a dormant trait", "#  wakes up."},
            new String[]{"#  once, when they drop low: stronger traits, and a dormant trait wakes up."},
            "#  LAST STAND - Purple and Gold bosses (and every Nemesis) fight harder", "# =====================================================================");
        patch(new String[]{"  speed-bonus: 15         # % faster"},
            new String[]{},
            "  trait-power-bonus: 50   # % stronger traits", "  awaken-dormant-trait: true");
        patch(new String[]{"", "# =====================================================================", "#  RANK PERSONALITY - Red and higher bosses surge after a target that", "#  backs off (Purple, Gold and Nemesis bosses harder). Higher ranks also", "#  notice players from further away.", "# =====================================================================", "rank-personality:", "  pursuit: true"},
            new String[]{},
            "  awaken-dormant-trait: true", "");
        patch(new String[]{"#  BOSS TOTEM - a rare drop. Right-click it to summon a boss (3 second", "#  ritual). Totems dropped by bosses call a random rank."},
            new String[]{"#  BOSS TOTEM - right-click it to summon a boss (3 second ritual): the", "#  way to pick a harder fight on purpose. A boss drops a Totem of its own", "#  rank (an Ascendant drops a Legendary one), sometimes one rank higher.", "#  Bosses that were summoned (Totems, Escalation, commands) never drop", "#  Totems or Souls, so they can't feed themselves."},
            "# =====================================================================", "# =====================================================================");
        patch(new String[]{"    GRAY: 1", "    GREEN: 1.5", "    RED: 2.5", "    PURPLE: 4", "    GOLD: 8", "  rank-weights:     # rank of the summoned boss"},
            new String[]{"    GRAY: 15", "    GREEN: 15", "    RED: 17", "    PURPLE: 20", "    GOLD: 25", "    ASCENDANT: 30", "  higher-rank-chance: 20   # % that the Totem is one rank higher (up to Legendary)", "  rank-weights:     # rank called by old Totems that don't have one"},
            "  drop-chance:      # % per boss kill", "    GRAY: 0");
        patch(new String[]{"#  BOSS SOULS - a stackable soul of the boss's rank. Chance per kill; a", "#  Nemesis always drops one. Offer it to a boss to promote it (below)."},
            new String[]{"#  BOSS SOULS - stackable souls of the boss's rank. Chance per kill; a", "#  Nemesis always drops them. Offer one to a boss to promote it (below).", "#  Low-rank bosses are the everyday source: farm Souls, then raise a boss."},
            "# =====================================================================", "# =====================================================================");
        patch(new String[]{"    GRAY: 4", "    GREEN: 7", "    RED: 12", "    PURPLE: 25", "    GOLD: 100"},
            new String[]{"    GRAY: 30", "    GREEN: 30", "    RED: 35", "    PURPLE: 40", "    GOLD: 60"},
            "  chance:", "    ASCENDANT: 100");
        patch(new String[]{},
            new String[]{"  count:            # [min, max] souls when it drops", "    GRAY: [1, 1]", "    GREEN: [1, 1]", "    RED: [1, 2]", "    PURPLE: [2, 2]", "    GOLD: [2, 3]", "    ASCENDANT: [3, 3]"},
            "    ASCENDANT: 100", "");
        patch(new String[]{"#  fight) becomes YOUR Nemesis, with a name of its own (\"Scrawl the", "#  Bulwark\"). It prowls off, grows, earns titles and counter-traits, and", "#  comes back for you after a few Minecraft days.", "#  It ignores the boss cap and never despawns while it is out. Killing", "#  it drops guaranteed loot plus a Nemesis Statue of it."},
            new String[]{"#  fight) may become YOUR Nemesis, with a name of its own (\"Scrawl the", "#  Bulwark\"). It prowls off, grows, learns how you fight, and comes back", "#  for you after a while. Once it has killed 2 players in one outing it", "#  leaves. It ignores the boss cap. Killing it drops guaranteed loot plus a", "#  Nemesis Statue of it.", "#  When it kills you, you wake up with its Challenge: a note that calls it", "#  to you right away (bypassing the wait), and that names its Terms of", "#  Surrender: die that way while carrying the note to end the feud for good."},
            "#  NEMESIS - a boss that kills you (or that you flee from after a real", "# =====================================================================");
        patch(new String[]{"  become-on-kill-chance: 100      # % when a boss kills you", "  become-on-escape-chance: 50     # % when you flee from a boss"},
            new String[]{"  become-on-kill-chance: 30       # % when a boss kills you", "  become-on-escape-chance: 30     # % when you flee from a boss"},
            "  enabled: true", "  escape-min-rank: RED            # fleeing only counts against Red or higher");
        patch(new String[]{"  max-level: 50", "  levels-when-it-kills-you: 3"},
            new String[]{"  max-level: 15", "  levels-when-it-kills-you: 2     # (any player it kills counts)"},
            "  max-per-player: 5", "  levels-when-you-flee: 1");
        patch(new String[]{"  returns-after-days: 3"},
            new String[]{"  returns-after-days: 10"},
            "  max-traits: 7", "  health-per-level: 8             # % extra health per level");
        patch(new String[]{},
            new String[]{"  # Team fights: another player who out-damages its current target by this", "  # % of its max health (over the last 10 seconds) pulls its attention.", "  aggro-switch-percent: 10", "  # How it learns: each time it levels up from a fight, it adapts to the", "  # kind of damage hurting it most (melee, arrows, magic, fire, explosions,", "  # pets). The first time it resists it; if you keep at it, it becomes", "  # immune (at most max-immunities, fire doesn't count). Pets: it takes", "  # less from pets and hits them harder, never full immunity.", "  adaptation:", "    enabled: true", "    resisted-damage-percent: 65   # a resisted kind of damage does this much", "    max-immunities: 2", "    pet-damage-taken-percent: 50", "    pet-damage-dealt-bonus: 50"},
            "  levels-from-kills-per-outing: 5 # each mob it kills is +1 level, up to this many per outing", "");
        patch(new String[]{"#  boss is engaged it sizes up the best-geared player nearby (armor,", "#  enchantments, runes, relics) and gets stronger to match, and pays", "#  better too. A fully kitted player scores about full-gear-score."},
            new String[]{"#  boss is engaged it counts the Boss Gear (dropped by bosses) the players", "#  nearby are wearing and holding, and gets stronger to match, and pays", "#  better too. Boss Gear in your bag doesn't count, only what you use."},
            "#  DIFFICULTY - on top of the rank stats. Threat Scaling: the first time a", "# =====================================================================");
        patch(new String[]{"    full-gear-score: 110"},
            new String[]{"    full-gear-pieces: 6      # this many pieces (armor + both hands) = the full bonus"},
            "    max-reward-bonus: 25     # % better reward odds", "    radius: 32");
        patch(new String[]{},
            new String[]{"  # Armor past the point where it stops helping (20) isn't wasted: every", "  # point over it is 5% less damage from bosses.", "  overflow-armor:", "    enabled: true", "    from: 20", "    boss-damage-reduction-per-point: 5"},
            "    radius: 32", "");
        patch(new String[]{},
            new String[]{"  nemesis-challenge-received: \"<dark_red>☠</dark_red> <gray>You wake clutching a note from</gray> <boss><gray>: its Challenge. Use it to call it back, or read its Terms of Surrender.</gray>\"", "  nemesis-challenge-gone: \"<gray>The note crumbles. That feud is already over.</gray>\"", "  nemesis-challenge-not-yours: \"<red>This Challenge was written for someone else.</red>\"", "  nemesis-challenge-out: \"<boss> <red>is already out hunting you.</red>\"", "  nemesis-challenge-blocked: \"<red>Nothing answers the Challenge here.</red>\"", "  nemesis-surrendered: \"<boss> <gray>accepts your surrender. The feud is over.</gray>\""},
            "  nemesis-slain: \"<player> <gray>has slain their Nemesis</gray> <boss><gray>!</gray>\"", "  revenge: \"<gold><bold>REVENGE!</bold></gold> <yellow>Bonus rewards for avenging your death.</yellow>\"");

    }

    private ConfigPatchV11() {
    }

    /** Applies every patch that still fits; returns how many did. */
    static int apply(List<String> lines, Consumer<String> log) {
        int cursor = 0;
        int applied = 0;
        List<String> skipped = new ArrayList<>();
        boolean lastSkipped = false;
        int fallbackFrom = 0;
        for (Patch p : PATCHES) {
            if (p.fallback()) {
                if (!lastSkipped) {
                    continue;
                }
                int at = find(lines, p.old(), fallbackFrom);
                if (at >= 0) {
                    lines.set(at, p.replacement().get(0));
                    applied++;
                }
                continue;
            }
            fallbackFrom = cursor;
            lastSkipped = false;
            if (!p.old().isEmpty()) {
                int at = find(lines, p.old(), cursor);
                if (at < 0) {
                    skipped.add(p.old().get(0).strip());
                    lastSkipped = true;
                    continue;
                }
                for (int i = 0; i < p.old().size(); i++) {
                    lines.remove(at);
                }
                lines.addAll(at, p.replacement());
                cursor = at + p.replacement().size();
                applied++;
                continue;
            }
            // a new setting: goes after the line it follows (or before the one it precedes) in the default file
            int at = find(lines, List.of(p.before()), cursor);
            if (at >= 0) {
                at++;
            } else {
                at = find(lines, List.of(p.after()), cursor);
            }
            if (at < 0) {
                skipped.add(p.replacement().get(0).strip());
                lastSkipped = true;
                continue;
            }
            lines.addAll(at, p.replacement());
            cursor = at + p.replacement().size();
            applied++;
        }
        log.accept("config.yml: applied " + applied + " of the update's changes.");
        for (String line : skipped) {
            log.accept("config.yml: you had changed this part, so it was left as it is (see the new default"
                + " config.yml for the update's version): " + line);
        }
        return applied;
    }

    private static int find(List<String> lines, List<String> block, int from) {
        outer:
        for (int i = Math.max(0, from); i + block.size() <= lines.size(); i++) {
            for (int j = 0; j < block.size(); j++) {
                if (!lines.get(i + j).stripTrailing().equals(block.get(j).stripTrailing())) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
