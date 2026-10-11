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

    public static final int CURRENT = 2;
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
            if (section.equals("pets")) {
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
            if (section.equals("riding")) {
                Matcher rabbit = RABBIT.matcher(line);
                if (rabbit.matches()) {
                    out.add(line);
                    out.add(rabbit.group(1) + "- horse");
                    log.accept("config.yml: horses can be ridden right away.");
                    continue;
                }
            }
            if (section.equals("bond-xp")) {
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
        out.add("");
        out.addAll(List.of(
            "# The Bee's crop growing (around you, or around its post).",
            "bee:",
            "  radius: 5               # blocks out from the centre (5 = an 11x11 square)",
            "  crops-per-pulse: 3",
            "  seconds-between: 3      # at level 1; a little faster with every level",
            "",
            "# Stationed pets keep a post: sneak + punch your pet with the Pet Toy.",
            "# Combat pets guard it, Bees tend crops, Allays collect drops, Sniffers",
            "# dig, Cats keep creepers and phantoms away, Llamas spit at monsters.",
            "# A pet that faints at its post comes back there after resting.",
            "stationing:",
            "  unlock-level: 8",
            "  max-per-player: 3",
            "  guard-radius: 16        # how far guards chase monsters from their post"));
        log.accept("config.yml: added the bee and stationing settings.");
        return out;
    }
}
