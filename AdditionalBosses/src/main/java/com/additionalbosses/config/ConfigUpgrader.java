package com.additionalbosses.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, careful edits to an existing config.yml when a default changed between versions. It only touches a value
 * that still has its old default (so anything the server owner changed is left alone), edits the file as text so
 * comments and layout survive, and keeps a backup of the previous file.
 *
 * <p>New settings never need this: every new option has a built-in default.</p>
 */
public final class ConfigUpgrader {

    public static final int CURRENT = 7;
    private static final Pattern VERSION = Pattern.compile("^config-version:\\s*(\\d+)\\s*(#.*)?$");
    private static final Pattern STORMCALLER = Pattern.compile("^(\\s+stormcaller:\\s*\\{.*\\bweight:\\s*)6(\\b.*)$");
    private static final Pattern STATUE_SCALE = Pattern.compile("^(\\s+)statue-scale:.*$");
    private static final Pattern PROWL = Pattern.compile("^(\\s+prowl-minutes:\\s*)3(\\s.*)?$");
    private static final Pattern SHOW_NAME = Pattern.compile("^(\\s+always-show-name:\\s*)true(\\s.*)?$");
    private static final Pattern GRAY_TROPHY = Pattern.compile("^(\\s+)gray-trophy-chance:\\s*([0-9.]+).*$");
    private static final Pattern TOP_LEVEL = Pattern.compile("^([a-z][a-z0-9-]*):.*$");

    /** v7: runes carry one fixed amount per rank (old default ranges -> values). */
    private static final Map<String, String> RUNE_VALUES = new LinkedHashMap<>();
    /** v7: Legendary and Ascendant bosses are tougher, with the Warden as the yardstick. */
    private static final Map<String, String> RANK_STATS = new LinkedHashMap<>();
    /** v7: trophies became Boss Souls, names show on demand. Old lines and their replacements ("" = drop it). */
    private static final Map<String, String> SOUL_TEXT = new LinkedHashMap<>();

    static {
        RUNE_VALUES.put("ranges: { GRAY: [0.05, 0.1], GREEN: [0.1, 0.15], RED: [0.15, 0.2], PURPLE: [0.2, 0.3], GOLD: [0.3, 0.4], ASCENDANT: [0.4, 0.5] }",
            "values: { GRAY: 0.1, GREEN: 0.15, RED: 0.2, PURPLE: 0.3, GOLD: 0.4, ASCENDANT: 0.5 }");
        RUNE_VALUES.put("ranges: { GRAY: [0.25, 0.5], GREEN: [0.5, 0.75], RED: [0.5, 1.0], PURPLE: [0.75, 1.25], GOLD: [1.0, 1.5], ASCENDANT: [1.5, 2.0] }",
            "values: { GRAY: 0.5, GREEN: 0.75, RED: 1.0, PURPLE: 1.25, GOLD: 1.5, ASCENDANT: 2.0 }");
        RUNE_VALUES.put("ranges: { GRAY: [0.5, 1.0], GREEN: [1.0, 1.5], RED: [1.5, 2.0], PURPLE: [2.0, 3.0], GOLD: [3.0, 4.0], ASCENDANT: [4, 5] }",
            "values: { GRAY: 1, GREEN: 1.5, RED: 2, PURPLE: 3, GOLD: 4, ASCENDANT: 5 }");
        RUNE_VALUES.put("ranges: { GRAY: [1, 2], GREEN: [2, 2], RED: [2, 3], PURPLE: [3, 4], GOLD: [4, 6], ASCENDANT: [6, 7.5] }",
            "values: { GRAY: 1, GREEN: 2, RED: 3, PURPLE: 4, GOLD: 6, ASCENDANT: 8 }");
        RUNE_VALUES.put("ranges: { GRAY: [0.5, 1.0], GREEN: [1.0, 1.5], RED: [1.5, 2.0], PURPLE: [2.0, 2.5], GOLD: [2.5, 3.0], ASCENDANT: [3, 3.75] }",
            "values: { GRAY: 1, GREEN: 1.5, RED: 2, PURPLE: 2.5, GOLD: 3, ASCENDANT: 4 }");
        RUNE_VALUES.put("ranges: { GRAY: [0.02, 0.04], GREEN: [0.04, 0.06], RED: [0.06, 0.08], PURPLE: [0.08, 0.1], GOLD: [0.1, 0.15], ASCENDANT: [0.15, 0.188] }",
            "values: { GRAY: 0.04, GREEN: 0.06, RED: 0.08, PURPLE: 0.1, GOLD: 0.15, ASCENDANT: 0.2 }");
        RUNE_VALUES.put("ranges: { GRAY: [0.02, 0.03], GREEN: [0.03, 0.04], RED: [0.04, 0.06], PURPLE: [0.06, 0.08], GOLD: [0.08, 0.1], ASCENDANT: [0.1, 0.125] }",
            "values: { GRAY: 0.03, GREEN: 0.04, RED: 0.06, PURPLE: 0.08, GOLD: 0.1, ASCENDANT: 0.12 }");

        RANK_STATS.put("stats: { health: 5.0, damage: 2.1, armor: 10, armor-toughness: 6, knockback-resistance: 0.6, speed: 1.12, size: 0.5 }",
            "stats: { health: 6.0, damage: 2.4, armor: 10, armor-toughness: 6, knockback-resistance: 0.6, speed: 1.12, size: 0.5, min-health: 500, min-damage: 20 }");
        RANK_STATS.put("stats: { health: 8.0, damage: 2.6, armor: 14, armor-toughness: 8, knockback-resistance: 0.8, speed: 1.15, size: 0.65 }",
            "stats: { health: 10.0, damage: 3.0, armor: 14, armor-toughness: 8, knockback-resistance: 0.8, speed: 1.15, size: 0.65, min-health: 800, min-damage: 30 }");

        SOUL_TEXT.put("#  TROPHIES - cosmetic collectibles named after the boss (Blaze Core,",
            "#  BOSS SOULS - a stackable soul of the boss's rank. Chance per kill; a");
        SOUL_TEXT.put("#  Ravager Horn...). Chance per kill; a Nemesis always drops one.",
            "#  Nemesis always drops one. Offer it to a boss to promote it.");
        SOUL_TEXT.put("# Right-click a block with a trophy to place a small frozen copy of the", "");
        SOUL_TEXT.put("# boss. size: how big it is in blocks (its height or width, whichever is", "");
        SOUL_TEXT.put("# larger), the same for every mob. To pick it up, sneak + right-click it,", "");
        SOUL_TEXT.put("# or sneak + right-click the block under it with an empty hand.", "");
        SOUL_TEXT.put("# Right-click a block with a trophy to place a tiny frozen copy of the", "");
        SOUL_TEXT.put("# boss (trophy size, see below). Sneak + right-click it to pick it up.", "");
        SOUL_TEXT.put("#  PROMOTION - right-click a boss with a trophy, or throw (drop) the",
            "#  PROMOTION - right-click a boss with a Boss Soul, or throw (drop) the");
        SOUL_TEXT.put("#  trophy at it, and the boss rises. Ranks can be skipped. Promoting past",
            "#  soul at it, and the boss rises. Ranks can be skipped. Promoting past");
        SOUL_TEXT.put("#  ranks-gained: [min, max] by the rank of the boss the trophy came from.",
            "#  ranks-gained: [min, max] by the rank of the soul.");
        SOUL_TEXT.put("# Never spawns naturally: a boss only becomes Ascendant when trophies",
            "# Never spawns naturally: a boss only becomes Ascendant when souls");
        SOUL_TEXT.put("promote-fail: \"<gray>The trophy crumbles to dust... nothing answers.</gray>\"",
            "promote-fail: \"<gray>The soul fades away... nothing answers.</gray>\"");
        SOUL_TEXT.put("# Show the boss name above its head at all times.",
            "# Show the boss name above its head at all times (false = only when you look at it).");
    }

    private ConfigUpgrader() {
    }

    public static void upgrade(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> out = upgradeLines(lines, plugin.getLogger()::info);
            if (out == null) {
                return;
            }
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config.yml.before-v" + CURRENT).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            plugin.getLogger().info("config.yml updated to version " + CURRENT + " (backup: config.yml.before-v" + CURRENT + ").");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update config.yml: " + ex.getMessage());
        }
    }

    /** The upgraded file, or null if it is already current. */
    static List<String> upgradeLines(List<String> lines, java.util.function.Consumer<String> log) {
        int version = 1;
        int versionLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = VERSION.matcher(lines.get(i));
            if (m.matches()) {
                version = Integer.parseInt(m.group(1));
                versionLine = i;
                break;
            }
        }
        if (version >= CURRENT) {
            return null;
        }
        List<String> out = new ArrayList<>(lines.size());
        String section = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == versionLine) {
                out.add("config-version: " + CURRENT + "   # used by the plugin to update this file; don't change");
                continue;
            }
            Matcher top = TOP_LEVEL.matcher(line);
            if (top.matches()) {
                section = top.group(1);
            }
            String upgraded = upgradeLine(log, line, section, version);
            if (upgraded != null) {
                out.add(upgraded);
            }
        }
        if (versionLine < 0) {
            out.add(0, "config-version: " + CURRENT + "   # used by the plugin to update this file; don't change");
        }
        return out;
    }

    /** The line as it should be after upgrading from {@code version}, or null to drop it. */
    static String upgradeLine(java.util.function.Consumer<String> log, String line, String section, int version) {
        // v2: Stormcaller was picked too often (weight 6 -> 2).
        Matcher m = STORMCALLER.matcher(line);
        if (version < 2 && m.matches()) {
            log.accept("config.yml: lowered the Stormcaller relic's weight from 6 to 2.");
            return m.group(1) + "2" + m.group(2);
        }
        // (v6 resized trophies and statues; v7 below replaces both settings.)
        if (version >= 7) {
            return line;
        }

        // ---- v7 ----
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        String trimmed = line.strip();
        String text = SOUL_TEXT.get(trimmed);
        if (text != null) {
            return text.isEmpty() ? null : indent + text;
        }
        if (section.equals("nemesis")) {
            Matcher statue = STATUE_SCALE.matcher(line);
            if (statue.matches()) {
                log.accept("config.yml: Nemesis Statues are half a block big now (nemesis.statue-size: 0.5).");
                return statue.group(1) + "statue-size: 0.5                # placed Nemesis Statues are about this many blocks big (0 = the Nemesis's real size)";
            }
            Matcher prowl = PROWL.matcher(line);
            if (prowl.matches()) {
                log.accept("config.yml: a Nemesis now prowls for 1 minute after it lets you go.");
                return prowl.group(1) + "1" + (prowl.group(2) == null ? "" : prowl.group(2));
            }
        }
        if (section.equals("bosses")) {
            Matcher show = SHOW_NAME.matcher(line);
            if (show.matches()) {
                log.accept("config.yml: boss names now only show when you look at the boss.");
                return show.group(1) + "false" + (show.group(2) == null ? "" : show.group(2));
            }
        }
        if (section.equals("empowerment") && RUNE_VALUES.containsKey(trimmed)) {
            return indent + RUNE_VALUES.get(trimmed);
        }
        if (section.equals("ranks") && RANK_STATS.containsKey(trimmed)) {
            log.accept("config.yml: Legendary and Ascendant bosses are tougher (Warden-level floors).");
            return indent + RANK_STATS.get(trimmed);
        }
        if (section.equals("trophies")) {
            if (trimmed.equals("trophies:") || line.startsWith("trophies:")) {
                return "souls:";
            }
            if (trimmed.startsWith("placeable:") || trimmed.startsWith("size:") || trimmed.startsWith("scale:")) {
                return null; // trophy figures are gone
            }
        }
        if (section.equals("promotion")) {
            Matcher gray = GRAY_TROPHY.matcher(line);
            if (gray.matches()) {
                return gray.group(1) + "gray-soul-chance: " + gray.group(2) + "       # % that a Gray soul works at all";
            }
        }
        return line;
    }
}
