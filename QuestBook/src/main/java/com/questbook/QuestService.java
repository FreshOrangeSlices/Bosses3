package com.questbook;

import com.questbook.QuestConfig.Board;
import com.questbook.QuestConfig.Quest;
import com.questbook.QuestConfig.Reward;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Works out where a player stands on each quest, and hands out rewards. */
public final class QuestService {

    public enum Status {
        /** Its plugin or feature isn't on this server. */
        HIDDEN,
        /** Not done yet. */
        OPEN,
        /** Done; the reward is waiting to be claimed. */
        READY,
        /** Done and claimed. */
        CLAIMED
    }

    private final QuestBook plugin;
    private final Set<String> warned = new HashSet<>();

    QuestService(QuestBook plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  Requirements and triggers
    // =====================================================================

    /** Whether everything in {@code requires} is on this server (plugin:Name, config:Plugin:path, mcmmo). */
    public boolean available(List<String> requires) {
        for (String raw : requires) {
            String r = raw.trim();
            if (r.equalsIgnoreCase("mcmmo")) {
                if (!plugin.mcmmo().present()) {
                    return false;
                }
            } else if (r.regionMatches(true, 0, "plugin:", 0, 7)) {
                if (!Bukkit.getPluginManager().isPluginEnabled(r.substring(7).trim())) {
                    return false;
                }
            } else if (r.regionMatches(true, 0, "config:", 0, 7)) {
                String[] parts = r.substring(7).split(":", 2);
                Plugin other = parts.length == 2 ? Bukkit.getPluginManager().getPlugin(parts[0].trim()) : null;
                if (other == null || !other.isEnabled() || !other.getConfig().getBoolean(parts[1].trim(), false)) {
                    return false;
                }
            } else {
                warnOnce("Unknown requirement: " + r);
                return false;
            }
        }
        return true;
    }

    /** One check: flag:name[>=n], item:namespace:key[=value], mcmmo:SKILL>=level, permission:node. */
    public boolean check(Player p, String raw) {
        String t = raw.trim();
        int colon = t.indexOf(':');
        if (colon < 0) {
            warnOnce("Unknown trigger: " + t);
            return false;
        }
        String type = t.substring(0, colon).toLowerCase(Locale.ROOT);
        String arg = t.substring(colon + 1).trim();
        switch (type) {
            case "flag" -> {
                String[] parts = arg.split(">=", 2);
                int need = parts.length == 2 ? parseInt(parts[1], 1) : 1;
                return QuestFlags.get(p, parts[0].trim()) >= need;
            }
            case "item" -> {
                String[] parts = arg.split("=", 2);
                NamespacedKey key = NamespacedKey.fromString(parts[0].trim().toLowerCase(Locale.ROOT));
                if (key == null) {
                    warnOnce("Bad item key in trigger: " + t);
                    return false;
                }
                String value = parts.length == 2 ? parts[1].trim() : null;
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item == null || item.isEmpty()) {
                        continue;
                    }
                    PersistentDataContainerView pdc = item.getPersistentDataContainer();
                    if (!pdc.has(key)) {
                        continue;
                    }
                    if (value == null || (pdc.has(key, PersistentDataType.STRING)
                        && value.equalsIgnoreCase(pdc.get(key, PersistentDataType.STRING)))) {
                        return true;
                    }
                }
                return false;
            }
            case "mcmmo" -> {
                String[] parts = arg.split(">=", 2);
                if (parts.length != 2) {
                    warnOnce("mcMMO triggers look like mcmmo:TAMING>=100, not " + t);
                    return false;
                }
                int level = plugin.mcmmo().level(p, parts[0].trim());
                return level >= 0 && level >= plugin.mcmmo().scaled(parseInt(parts[1], Integer.MAX_VALUE));
            }
            case "permission" -> {
                return p.hasPermission(arg);
            }
            default -> {
                warnOnce("Unknown trigger: " + t);
                return false;
            }
        }
    }

    private boolean met(Player p, Quest q) {
        for (List<String> all : q.triggers()) {
            boolean ok = !all.isEmpty();
            for (String t : all) {
                if (!check(p, t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  Status
    // =====================================================================

    public boolean visible(Board b) {
        if (!available(b.requires())) {
            return false;
        }
        return b.skillBoard() ? plugin.mcmmo().present() : true;
    }

    public Status status(Player p, Quest q) {
        if (!available(q.requires())) {
            return Status.HIDDEN;
        }
        PlayerStore.State s = plugin.store().get(p.getUniqueId());
        String key = QuestConfig.key(q);
        if (s.claimed.contains(key)) {
            return Status.CLAIMED;
        }
        if (s.done.contains(key)) {
            return Status.READY;
        }
        if (met(p, q)) {
            s.done.add(key); // done for good, even if what made it count goes away later
            s.dirty = true;
            return Status.READY;
        }
        return Status.OPEN;
    }

    /** Checks every quest, so what a player has done gets noticed (on join, after picking things up...). */
    public void refresh(Player p) {
        for (Board b : plugin.quests().boards()) {
            if (!visible(b)) {
                continue;
            }
            if (b.skillBoard()) {
                for (QuestConfig.Skill skill : b.skills()) {
                    for (int level : b.levels()) {
                        milestoneReached(p, skill, level);
                    }
                }
            } else {
                for (Quest q : b.quests()) {
                    status(p, q);
                }
            }
        }
    }

    /** Whether the player reached this level in the skill (remembered once reached). */
    public boolean milestoneReached(Player p, QuestConfig.Skill skill, int level) {
        PlayerStore.State s = plugin.store().get(p.getUniqueId());
        String key = skill.id() + ":" + level;
        if (s.milestones.contains(key)) {
            return true;
        }
        int now = plugin.mcmmo().level(p, skill.id());
        if (now >= 0 && now >= plugin.mcmmo().scaled(level)) {
            s.milestones.add(key);
            s.dirty = true;
            return true;
        }
        return false;
    }

    /** {done, total, ready} for a quest board (hidden quests left out). */
    public int[] progress(Player p, Board b) {
        int done = 0;
        int total = 0;
        int ready = 0;
        if (b.skillBoard()) {
            for (QuestConfig.Skill skill : b.skills()) {
                for (int level : b.levels()) {
                    total++;
                    if (milestoneReached(p, skill, level)) {
                        done++;
                    }
                }
            }
            return new int[]{done, total, 0};
        }
        for (Quest q : b.quests()) {
            Status st = status(p, q);
            if (st == Status.HIDDEN) {
                continue;
            }
            total++;
            if (st == Status.CLAIMED) {
                done++;
            } else if (st == Status.READY) {
                ready++;
                done++;
            }
        }
        return new int[]{done, total, ready};
    }

    // =====================================================================
    //  Rewards
    // =====================================================================

    /** Free inventory slots needed for these rewards. */
    int slotsNeeded(List<Reward> rewards) {
        int n = 0;
        for (Reward r : rewards) {
            if (available(r.requires())) {
                n += r.item() != null ? 1 : r.slots();
            }
        }
        return n;
    }

    static int freeSlots(Player p) {
        int n = 0;
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item == null || item.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Claims a ready quest's reward. Refused (with a message) if the bag hasn't room for all of it. */
    public boolean claim(Player p, Quest q) {
        if (status(p, q) != Status.READY) {
            return false;
        }
        if (!give(p, q.rewards())) {
            return false;
        }
        PlayerStore.State s = plugin.store().get(p.getUniqueId());
        s.claimed.add(QuestConfig.key(q));
        s.dirty = true;
        plugin.store().save(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        return true;
    }

    public boolean claimWelcome(Player p) {
        PlayerStore.State s = plugin.store().get(p.getUniqueId());
        if (s.welcomeClaimed || !give(p, plugin.quests().welcome())) {
            return false;
        }
        s.welcomeClaimed = true;
        s.dirty = true;
        plugin.store().save(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        return true;
    }

    private boolean give(Player p, List<Reward> rewards) {
        int need = slotsNeeded(rewards);
        int free = freeSlots(p);
        if (free < need) {
            Msg.error(p, "Make room first: this needs " + need + " free inventory slot" + (need == 1 ? "" : "s")
                + " (you have " + free + ").");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return false;
        }
        for (Reward r : rewards) {
            if (!available(r.requires())) {
                continue; // (its plugin isn't installed)
            }
            if (r.item() != null) {
                for (ItemStack left : p.getInventory().addItem(r.item().clone()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left); // (can't happen: the room was checked)
                }
            } else if (r.command() != null) {
                String cmd = r.command().replace("%player%", p.getName()).replace("%uuid%", p.getUniqueId().toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        }
        return true;
    }

    // =====================================================================

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void warnOnce(String message) {
        if (warned.add(message)) {
            plugin.getLogger().warning(message + " (check quests.yml)");
        }
    }
}
