package com.questbook;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything config.yml and quests.yml say: the hub, the boards and their quests, and the welcome gift. */
public final class QuestConfig {

    /** A reward: an item, or a console command (which needs {@code slots} free inventory slots). */
    public record Reward(@Nullable ItemStack item, @Nullable String command, int slots, String label,
                         List<String> requires) {
    }

    /** One quest. {@code triggers}: any one of the lists completes it; every check in that list must pass. */
    public record Quest(String id, Board board, String name, Material icon, String objective, String insight,
                        List<List<String>> triggers, List<String> requires, List<Reward> rewards, int slot) {
    }

    /** One skill row on a milestone board: its tiers' texts, in the order of the board's levels. */
    public record Skill(String id, String name, Material icon, List<String> milestones) {
    }

    /** A board in the hub: quests, or skill milestones. */
    public record Board(String id, String title, Material icon, int hubSlot, List<String> description,
                        List<String> requires, boolean skillBoard, List<Quest> quests, List<Skill> skills,
                        List<Integer> levels) {
    }

    /** A guide book in the hub, opened by a command run as the player (it shows the book; nothing is given). */
    public record Guide(String id, String name, List<String> lore, String command, int slot, List<String> requires) {
    }

    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final List<Guide> guides = new ArrayList<>();
    private final List<Reward> welcome = new ArrayList<>();
    private int questCount;
    // config.yml
    public final boolean giveBookOnJoin;
    public final boolean keepBookOnDeath;
    public final String bookTitle;
    public final String welcomeName;
    public final int welcomeSlot;

    private QuestConfig(QuestBook plugin, ConfigurationSection c, YamlConfiguration q) {
        giveBookOnJoin = c.getBoolean("book.give-on-first-join", true);
        keepBookOnDeath = c.getBoolean("book.keep-on-death", true);
        bookTitle = c.getString("book.title", "Quest Book");
        welcomeName = c.getString("welcome.name", "Welcome Gift");
        welcomeSlot = c.getInt("welcome.slot", 4);
        welcome.addAll(rewards(plugin, c.getMapList("welcome.rewards"), "welcome"));
        ConfigurationSection g = c.getConfigurationSection("guide-books");
        if (g != null) {
            for (String id : g.getKeys(false)) {
                ConfigurationSection s = g.getConfigurationSection(id);
                if (s == null || s.getString("command", "").isBlank()) {
                    continue;
                }
                guides.add(new Guide(id, s.getString("name", id), s.getStringList("lore"), s.getString("command", ""),
                    s.getInt("slot", -1), s.getStringList("requires")));
            }
        }
        ConfigurationSection b = q.getConfigurationSection("boards");
        if (b == null) {
            return;
        }
        for (String id : b.getKeys(false)) {
            ConfigurationSection s = b.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            boolean skillBoard = s.getString("type", "quests").equalsIgnoreCase("skills");
            List<Quest> quests = new ArrayList<>();
            List<Skill> skills = new ArrayList<>();
            Board board = new Board(id, s.getString("title", id), material(plugin, s.getString("icon"), Material.BOOK),
                s.getInt("hub-slot", -1), s.getStringList("description"), s.getStringList("requires"), skillBoard,
                quests, skills, s.getIntegerList("levels"));
            if (skillBoard) {
                ConfigurationSection sk = s.getConfigurationSection("skills");
                if (sk != null) {
                    for (String skill : sk.getKeys(false)) {
                        ConfigurationSection e = sk.getConfigurationSection(skill);
                        if (e != null) {
                            skills.add(new Skill(skill.toUpperCase(Locale.ROOT), e.getString("name", skill),
                                material(plugin, e.getString("icon"), Material.BOOK), e.getStringList("milestones")));
                        }
                    }
                }
            } else {
                ConfigurationSection qs = s.getConfigurationSection("quests");
                if (qs != null) {
                    for (String qid : qs.getKeys(false)) {
                        ConfigurationSection e = qs.getConfigurationSection(qid);
                        if (e == null) {
                            continue;
                        }
                        List<List<String>> triggers = new ArrayList<>();
                        for (String t : e.getStringList("triggers")) {
                            List<String> all = new ArrayList<>();
                            for (String part : t.split("&")) {
                                if (!part.isBlank()) {
                                    all.add(part.trim());
                                }
                            }
                            if (!all.isEmpty()) {
                                triggers.add(all);
                            }
                        }
                        if (triggers.isEmpty()) {
                            plugin.getLogger().warning("Quest " + id + "/" + qid + " has no triggers; it can't be completed.");
                        }
                        quests.add(new Quest(qid, board, e.getString("name", qid),
                            material(plugin, e.getString("icon"), Material.BOOK), e.getString("objective", ""),
                            e.getString("insight", ""), triggers, e.getStringList("requires"),
                            rewards(plugin, e.getMapList("rewards"), id + "/" + qid), e.getInt("slot", -1)));
                        questCount++;
                    }
                }
            }
            boards.put(id, board);
        }
    }

    public static QuestConfig load(QuestBook plugin) {
        YamlConfiguration quests = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "quests.yml"));
        return new QuestConfig(plugin, plugin.getConfig(), quests);
    }

    public java.util.Collection<Board> boards() {
        return boards.values();
    }

    public @Nullable Board board(String id) {
        return boards.get(id);
    }

    public List<Guide> guides() {
        return guides;
    }

    public List<Reward> welcome() {
        return welcome;
    }

    public int questCount() {
        return questCount;
    }

    public @Nullable Quest quest(String key) {
        int slash = key.indexOf('/');
        Board b = slash < 0 ? null : boards.get(key.substring(0, slash));
        if (b == null) {
            return null;
        }
        for (Quest q : b.quests()) {
            if (q.id().equals(key.substring(slash + 1))) {
                return q;
            }
        }
        return null;
    }

    /** "bosses/first-boss": how a quest is stored. */
    public static String key(Quest q) {
        return q.board().id() + "/" + q.id();
    }

    // =====================================================================

    private static Material material(QuestBook plugin, @Nullable String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(raw.trim());
        if (m == null || !m.isItem() || m.isAir()) {
            plugin.getLogger().warning("Unknown item " + raw + "; using " + fallback.name().toLowerCase(Locale.ROOT));
            return fallback;
        }
        return m;
    }

    private static List<Reward> rewards(QuestBook plugin, List<Map<?, ?>> raw, String where) {
        List<Reward> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            List<String> requires = new ArrayList<>();
            if (m.get("requires") instanceof List<?> list) {
                for (Object o : list) {
                    requires.add(String.valueOf(o));
                }
            }
            Object command = m.get("command");
            if (command != null) {
                int slots = m.get("slots") instanceof Number n ? n.intValue() : 1;
                Object label = m.get("label");
                out.add(new Reward(null, command.toString(), Math.max(0, slots),
                    label == null ? "" : label.toString(), requires));
                continue;
            }
            Object item = m.get("item");
            Material material = item == null ? null : Material.matchMaterial(item.toString());
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Reward in " + where + " has an unknown item: " + item);
                continue;
            }
            int amount = m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            ItemStack stack = ItemStack.of(material);
            if (m.get("trim") instanceof Map<?, ?> trim) {
                TrimMaterial tm = trimMaterial(String.valueOf(trim.get("material")));
                TrimPattern tp = trimPattern(String.valueOf(trim.get("pattern")));
                if (tm != null && tp != null) {
                    stack.editMeta(ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(tm, tp)));
                } else {
                    plugin.getLogger().warning("Reward in " + where + " has an unknown trim: " + trim);
                }
            }
            if (m.get("potion") != null) {
                try {
                    org.bukkit.potion.PotionType type = org.bukkit.potion.PotionType.valueOf(
                        String.valueOf(m.get("potion")).trim().toUpperCase(Locale.ROOT));
                    stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.POTION_CONTENTS,
                        io.papermc.paper.datacomponent.item.PotionContents.potionContents().potion(type).build());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Reward in " + where + " has an unknown potion: " + m.get("potion"));
                }
            }
            if (m.get("enchantments") instanceof Map<?, ?> ench) {
                for (Map.Entry<?, ?> e : ench.entrySet()) {
                    NamespacedKey key = NamespacedKey.fromString(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT));
                    Enchantment enchantment = key == null ? null
                        : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
                    if (enchantment != null && e.getValue() instanceof Number level) {
                        stack.addUnsafeEnchantment(enchantment, level.intValue());
                    }
                }
            }
            // in full stacks (64 arrows, then the rest...)
            int left = Math.min(amount, material.getMaxStackSize() * 36);
            while (left > 0) {
                ItemStack part = stack.clone();
                part.setAmount(Math.min(left, material.getMaxStackSize()));
                left -= part.getAmount();
                out.add(new Reward(part, null, 0, "", requires));
            }
        }
        return out;
    }

    private static @Nullable TrimMaterial trimMaterial(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(key);
    }

    private static @Nullable TrimPattern trimPattern(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(key);
    }
}
