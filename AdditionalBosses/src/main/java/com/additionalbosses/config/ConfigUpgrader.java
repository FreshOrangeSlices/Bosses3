package com.additionalbosses.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
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

    public static final int CURRENT = 6;
    private static final Pattern VERSION = Pattern.compile("^config-version:\\s*(\\d+)\\s*(#.*)?$");
    private static final Pattern STORMCALLER = Pattern.compile("^(\\s+stormcaller:\\s*\\{.*\\bweight:\\s*)6(\\b.*)$");
    private static final Pattern TROPHY_SCALE = Pattern.compile("^(\\s+)scale:\\s*(0\\.125|0\\.0625|0\\.1)(\\s.*)?$");
    private static final Pattern STATUE_SCALE = Pattern.compile("^(\\s+statue-scale:\\s*)0\\.125(\\s.*)?$");
    private static final Pattern TOP_LEVEL = Pattern.compile("^([a-z][a-z0-9-]*):.*$");

    private ConfigUpgrader() {
    }

    public static void upgrade(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
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
                return;
            }
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config.yml.before-v" + CURRENT).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            String section = "";
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher top = TOP_LEVEL.matcher(line);
                if (top.matches()) {
                    section = top.group(1);
                }
                // v2: Stormcaller was picked too often (weight 6 -> 2).
                Matcher m = STORMCALLER.matcher(line);
                if (version < 2 && m.matches()) {
                    lines.set(i, m.group(1) + "2" + m.group(2));
                    plugin.getLogger().info("config.yml: lowered the Stormcaller relic's weight from 6 to 2.");
                }
                // v6: trophies are sized in blocks now (half a block), and Nemesis statues are full size again.
                Matcher t = TROPHY_SCALE.matcher(line);
                if (version < 6 && section.equals("trophies") && t.matches()) {
                    lines.set(i, t.group(1) + "size: 0.5   # placed trophies are about this many blocks big");
                    plugin.getLogger().info("config.yml: trophies are now half a block big (trophies.size: 0.5).");
                }
                Matcher st = STATUE_SCALE.matcher(line);
                if (version < 6 && section.equals("nemesis") && st.matches()) {
                    lines.set(i, st.group(1) + "1.0" + (st.group(2) == null ? "" : st.group(2)));
                    plugin.getLogger().info("config.yml: Nemesis statues are full size again (statue-scale: 1.0).");
                }
            }
            if (versionLine >= 0) {
                lines.set(versionLine, "config-version: " + CURRENT);
            } else {
                lines.add(0, "config-version: " + CURRENT + "   # used by the plugin to update this file; don't change");
            }
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            plugin.getLogger().info("config.yml updated to version " + CURRENT + " (backup: config.yml.before-v" + CURRENT + ").");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update config.yml: " + ex.getMessage());
        }
    }
}
