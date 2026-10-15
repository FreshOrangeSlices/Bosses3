package com.questbook;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Brings an existing config.yml up to date without touching what the server owner changed (a backup is kept).
 * v2: the guide books moved to the bottom of the first page, and the Boss book became two (Book I and Book II).
 */
final class ConfigUpdate {

    static final int CURRENT = 2;

    private static final List<String> OLD_GUIDES = List.of(
        "# The guide books, right on the first page. Clicking one shows the book on",
        "# screen (a command run as the player); nothing is put in their bag.",
        "# requires: plugin:<Name> hides it when that plugin isn't installed.",
        "guide-books:",
        "  bosses:",
        "    name: \"<dark_red>Boss Hunter's Compendium\"",
        "    lore: [\"Everything about bosses, in detail:\", \"ranks, traits, loot, the Nemesis...\"]",
        "    command: \"bosses guide read\"",
        "    slot: 30",
        "    requires: [plugin:AdditionalBosses]",
        "  pets:",
        "    name: \"<light_purple>Pet Guide\"",
        "    lore: [\"Which animals can be pets,\", \"what they like, and what they do.\"]",
        "    command: \"pets guide\"",
        "    slot: 32",
        "    requires: [plugin:BloomPets]");

    private static final List<String> NEW_GUIDES = List.of(
        "# The guide books, along the bottom of the first page. Clicking one shows",
        "# the book on screen (a command run as the player); nothing is put in their",
        "# bag. requires: plugin:<Name> hides it when that plugin isn't installed.",
        "guide-books:",
        "  bosses-1:",
        "    name: \"<dark_red>Boss Book I\"",
        "    lore: [\"Bosses: ranks, traits\", \"and how they fight.\"]",
        "    command: \"bosses guide read 1\"",
        "    requires: [plugin:AdditionalBosses]",
        "  bosses-2:",
        "    name: \"<dark_red>Boss Book II\"",
        "    lore: [\"Loot, relics, the hunt\", \"and the Nemesis.\"]",
        "    command: \"bosses guide read 2\"",
        "    requires: [plugin:AdditionalBosses]",
        "  pets:",
        "    name: \"<light_purple>Pet Guide\"",
        "    lore: [\"Which animals can be pets,\", \"what they like, and what they do.\"]",
        "    command: \"pets guide\"",
        "    requires: [plugin:BloomPets]");

    private ConfigUpdate() {
    }

    static void run(JavaPlugin plugin) {
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
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update config.yml: " + ex.getMessage());
        }
    }

    /** The updated file, or null if it's already current. */
    static List<String> upgrade(List<String> lines, java.util.function.Consumer<String> log) {
        for (String line : lines) {
            if (line.startsWith("config-version:")) {
                return null; // (only one version so far)
            }
        }
        List<String> out = new ArrayList<>();
        out.add("config-version: " + CURRENT + "   # used by the plugin to update this file; don't change");
        int at = find(lines, OLD_GUIDES);
        if (at >= 0) {
            out.addAll(lines.subList(0, at));
            out.addAll(NEW_GUIDES);
            out.addAll(lines.subList(at + OLD_GUIDES.size(), lines.size()));
            log.accept("config.yml: the guide books moved to the bottom of the Quest Book, with Boss Book I and II.");
        } else {
            out.addAll(lines);
            log.accept("config.yml: you had changed guide-books, so it was left as it is. To show both Boss books, "
                + "use the commands \"bosses guide read 1\" and \"bosses guide read 2\" (see the new default config.yml).");
        }
        return out;
    }

    private static int find(List<String> lines, List<String> block) {
        outer:
        for (int i = 0; i + block.size() <= lines.size(); i++) {
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
