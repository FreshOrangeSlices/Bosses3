package com.bloompets;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings an existing config.yml up to date, line by line, so comments and the server owner's own values survive.
 * Only values still at an old default change; new settings are added with their comments. A backup is kept.
 */
public final class ConfigUpdate {

    public static final int CURRENT = 3;
    private static final Pattern VERSION = Pattern.compile("^config-version:\\s*(\\d+).*$");
    private static final Pattern TOP_LEVEL = Pattern.compile("^([a-z][a-z0-9-]*):.*$");
    private static final Pattern FROG = Pattern.compile("^(\\s+)frog:\\s*(true|false)(\\s.*)?$");
    private static final Pattern RABBIT = Pattern.compile("^(\\s+)- rabbit\\s*$");
    private static final Pattern RIDDEN = Pattern.compile("^(\\s+)blocks-ridden-per-xp:\\s*25(\\s.*)?$");
    private static final Pattern RIDDEN_ANY = Pattern.compile("^(\\s+)blocks-ridden-per-xp:.*$");

    private ConfigUpdate() {
    }

    public static void run(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> out = upgrade(lines, plugin.getLogger()::info);
            if (out == null) {
                return;
            }
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config.yml.before-v" + CURRENT).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            plugin.getLogger().info("config.yml updated to version " + CURRENT + " (backup: config.yml.before-v"
                + CURRENT + ").");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update config.yml: " + ex.getMessage());
        }
    }

    /** The upgraded file, or null if it's already current. */
    static List<String> upgrade(List<String> lines, Consumer<String> log) {
        int version = 1;
        for (String line : lines) {
            Matcher m = VERSION.matcher(line);
            if (m.matches()) {
                version = Integer.parseInt(m.group(1));
                break;
            }
        }
        if (version >= CURRENT) {
            return null;
        }
        List<String> out = new ArrayList<>();
        out.add("config-version: " + CURRENT + "   # used by the plugin to update this file; don't change");
        String section = "";
        boolean walkedAdded = false;
        for (String line : lines) {
            if (VERSION.matcher(line).matches()) {
                continue;
            }
            Matcher top = TOP_LEVEL.matcher(line);
            if (top.matches()) {
                section = top.group(1);
            }
            // v2: new pets, horses can be ridden right away, more XP for travelling together
            if (version < 2 && section.equals("pets")) {
                Matcher frog = FROG.matcher(line);
                if (frog.matches()) {
                    out.add(line);
                    out.add(frog.group(1) + "pig: true");
                    out.add(frog.group(1) + "axolotl: true");
                    out.add(frog.group(1) + "bat: true");
                    log.accept("config.yml: added the new pets (pig, axolotl, bat).");
                    continue;
                }
            }
            if (version < 2 && section.equals("riding")) {
                Matcher rabbit = RABBIT.matcher(line);
                if (rabbit.matches()) {
                    out.add(line);
                    out.add(rabbit.group(1) + "- horse");
                    log.accept("config.yml: horses can be ridden right away.");
                    continue;
                }
            }
            if (version < 2 && section.equals("bond-xp")) {
                Matcher ridden = RIDDEN.matcher(line);
                if (ridden.matches()) {
                    log.accept("config.yml: riding gives bond XP every 10 blocks (was 25).");
                    line = ridden.group(1) + "blocks-ridden-per-xp: 10" + (ridden.group(2) == null ? "" : ridden.group(2));
                }
                Matcher any = RIDDEN_ANY.matcher(line);
                if (any.matches() && !walkedAdded) {
                    out.add(line);
                    out.add(any.group(1) + "blocks-walked-per-xp: 20   # walking around with your pet out");
                    walkedAdded = true;
                    continue;
                }
            }
            out.add(line);
        }
        if (version < 2) {
            v2Sections(lines, out, log);
        }
        if (version < 3) {
            v3(out, log);
        }
        return out;
    }

    private static void v2Sections(List<String> lines, List<String> out, Consumer<String> log) {
        if (lines.stream().noneMatch(l -> l.startsWith("bee:"))) {
            out.addAll(List.of("",
                "# The Bee's crop growing (around you, or around its post).",
                "bee:",
                "  radius: 5               # blocks out from the centre (5 = an 11x11 square)",
                "  crops-per-pulse: 3",
                "  seconds-between: 3      # at level 1; a little faster with every level"));
        }
        if (lines.stream().noneMatch(l -> l.startsWith("stationing:"))) {
            out.addAll(List.of("",
                "# Stationed pets keep a post: sneak + punch your pet with the Pet Toy.",
                "# Combat pets guard it, Bees tend crops, Allays collect drops, Sniffers",
                "# dig, Cats keep creepers and phantoms away, Llamas spit at monsters.",
                "# A pet that faints at its post comes back there after resting.",
                "stationing:",
                "  unlock-level: 8",
                "  max-per-player: 3",
                "  guard-radius: 16        # how far guards chase monsters from their post"));
        }
        log.accept("config.yml: added the bee and stationing settings.");
    }

    /** v3: combat bond XP, strolling guards, pet PvP, happiness, mcMMO Taming skills. */
    private static void v3(List<String> out, Consumer<String> log) {
        if (out.stream().noneMatch(l -> l.trim().startsWith("combat-hit:"))) {
            insertAfter(out, "bond-xp", "per-minute-out:", List.of(
                "combat-hit: 1              # every hit your combat pet lands (the fastest way to level)...",
                "combat-critical-hit: 2     # ...or a big hit (Gore, Double Strike, Momentum)",
                "combat-hit-cooldown-seconds: 1.5",
                "guard-owner-range: 30      # a pet guarding its post only earns while you're this close"));
        }
        if (out.stream().noneMatch(l -> l.trim().startsWith("roam-radius:"))) {
            insertAfter(out, "stationing", "guard-radius:", List.of(
                "roam-radius: 6          # stationed pets stroll this far around their post (0: stand still)"));
        }
        if (out.stream().noneMatch(l -> l.startsWith("pet-pvp:"))) {
            out.addAll(List.of("",
                "# The red/green wool in the pet menu: whether your combat pet joins your",
                "# fights with other players. Off for everyone until they turn it on. Pets",
                "# only join once a hit between you and the other player went through, so",
                "# PvP-off worlds and protected areas still count. false: no button.",
                "pet-pvp:",
                "  allow-toggle: true"));
        }
        if (out.stream().noneMatch(l -> l.startsWith("happiness:"))) {
            out.addAll(List.of("",
                "# How happy each pet is. Players see it as 1-10 hearts in the pet menu",
                "# (red: sad, gold/yellow: okay, green: happy, pink: loves you). Inside it's",
                "# 0-100; the numbers below are on that scale.",
                "#  - Feeding, petting (sneak + right-click with an empty hand) and fetch",
                "#    cheer a pet up. It slowly drops while the pet is out.",
                "#  - After a long time out with you a pet gets tired and drops faster until",
                "#    it rests in its bloom (resting there also cheers it up).",
                "#  - Getting it to full through your care makes it love you: its mood drops",
                "#    much slower for a while (lost if it faints).",
                "#  - A sad pet sulks: its bonus, its job at a post, its mcMMO skills and its",
                "#    storage are off. Nothing is lost; it all comes back when it cheers up.",
                "happiness:",
                "  enabled: true",
                "  start: 80                       # a new pet",
                "  sad-below: 30",
                "  feeding: 15",
                "  feeding-cooldown-seconds: 60",
                "  fetch: 8",
                "  fetch-cooldown-seconds: 15",
                "  petting: 5",
                "  petting-cooldown-seconds: 30",
                "  minutes-per-point-out: 3        # -1 every 3 minutes out with you",
                "  minutes-per-point-at-post: 2    # -1 every 2 minutes at a post",
                "  post-drops-while-owner-offline: false",
                "  tired-after-minutes: 90         # out with you this long without a rest...",
                "  tired-drop-multiplier: 3        # ...and it drops this many times as fast",
                "  rest-per-minute: 10             # resting in its bloom",
                "  minutes-to-fully-rest: 5        # in its bloom this long and it's no longer tired",
                "  loved-minutes: 30",
                "  loved-drop-multiplier: 0.25     # while it loves you, it drops at a quarter of the speed"));
        }
        if (out.stream().noneMatch(l -> l.startsWith("mcmmo:"))) {
            out.addAll(List.of("",
                "# mcMMO Taming skills for combat pets (Iron Golem, Vex, Polar Bear, Goat,",
                "# Panda). Needs mcMMO; without it this does nothing. YOUR mcMMO Taming level",
                "# (not the pet's level) unlocks them, like a tamed wolf's. Under the hood",
                "# they're mcMMO's wolf skills with mcMMO's numbers, each animal with its own",
                "# names, plus one starting and one special skill each. Wolves are left to",
                "# mcMMO itself. See them in game with /pets taming <animal>.",
                "# Levels are on mcMMO's RetroMode scale (0-1000); on Standard (0-100) they",
                "# are divided by 10 automatically.",
                "mcmmo:",
                "  enabled: true",
                "  taming-xp: true                 # your combat pet's hits give you Taming XP...",
                "  taming-xp-multiplier: 3         # ...worked out like a wolf's (mcMMO uses 3)",
                "  unlock:",
                "    first: 1                      # Iron Colossus, Double Strike, Unyielding, Momentum, Bamboo Blessing",
                "    awareness: 100                # steps out of fire and lava, no fall damage",
                "    gore: 150                     # chance of double damage (Crushing Blow, Mauling Blow, Headbutt...)",
                "    special: 200                  # Guardian Intervention, Phase Strike, Crushing Presence, Ram, Soothing Presence",
                "    fast-food: 200                # chance to heal by the damage it deals",
                "    thick-fur: 250                # less damage from attacks and arrows",
                "    holy-hound: 350               # magic, poison and wither heal it",
                "    shock-proof: 500              # less damage from explosions and lightning",
                "    sharpened-claws: 750          # extra damage on every hit",
                "  gore:",
                "    max-chance: 100               # % at max-chance-level, climbing evenly from 0",
                "    max-chance-level: 1000",
                "    damage-multiplier: 2.0",
                "  fast-food-chance: 50            # %",
                "  thick-fur-divisor: 2.0          # damage / 2",
                "  shock-proof-divisor: 6.0        # damage / 6",
                "  sharpened-claws-bonus: 2.0",
                "  iron-golem:",
                "    guardian-resistance-seconds: 20   # Guardian Intervention: once per Minecraft day",
                "    guardian-resistance-level: 2",
                "  vex:",
                "    double-strike-chance-per-50-levels: 2   # % (2% at 50, 4% at 100...)",
                "    double-strike-max-chance: 10",
                "    phase-strike-armor-ignored: 25          # % of the target's armor",
                "    phase-strike-armor-ignored-high: 50",
                "    phase-strike-high-level: 500",
                "  polar-bear:",
                "    crushing-presence-seconds: 5            # Slowness II + Weakness I...",
                "    crushing-presence-high-level: 500       # ...Slowness IV + Weakness II from here",
                "  goat:",
                "    momentum-every-hits: 3",
                "    momentum-bonus: 30                      # % more damage on that hit",
                "    ram-launch: 1.1                         # how hard Ram throws it up (1.1: about 6 blocks)",
                "  panda:",
                "    bamboo-blessing-seconds: 8              # Regeneration I after a win",
                "    soothing-chance-at-unlock: 15           # % per hit, rising evenly...",
                "    soothing-chance-at-1000: 60             # ...to this at level 1000",
                "    soothing-heal-at-unlock: 3              # health (3 = 1.5 hearts)...",
                "    soothing-heal-per-100-levels: 1         # ...+1 every 100 levels...",
                "    soothing-heal-max: 9                    # ...up to this",
                "    soothing-radius: 6"));
        }
        log.accept("config.yml: added combat bond XP, strolling guards, pet PvP, happiness and mcMMO Taming skills.");
    }

    /** Puts {@code add} right after the first "key:" line inside a top-level section, with the same indent. */
    private static void insertAfter(List<String> out, String section, String key, List<String> add) {
        String current = "";
        for (int i = 0; i < out.size(); i++) {
            String line = out.get(i);
            Matcher top = TOP_LEVEL.matcher(line);
            if (top.matches()) {
                current = top.group(1);
                continue;
            }
            if (current.equals(section) && line.trim().startsWith(key)) {
                String indent = line.substring(0, line.length() - line.stripLeading().length());
                List<String> indented = new ArrayList<>();
                for (String a : add) {
                    indented.add(indent + a);
                }
                out.addAll(i + 1, indented);
                return;
            }
        }
    }
}
