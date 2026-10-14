package com.questbook;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Each player's Quest Book: what they've done, what they've claimed (plugins/QuestBook/players/&lt;uuid&gt;.yml). */
final class PlayerStore {

    static final class State {
        /** Quests whose objective was met at some point (stays done even if the item leaves their bag). */
        final Set<String> done = new LinkedHashSet<>();
        /** Quests whose reward was claimed. */
        final Set<String> claimed = new LinkedHashSet<>();
        /** Skill milestones reached, "SKILL:level". */
        final Set<String> milestones = new LinkedHashSet<>();
        boolean welcomeClaimed;
        boolean bookGiven;
        boolean dirty;
        /** Their file couldn't be read: nothing can be claimed (or given) until an admin looks at it. */
        boolean locked;
    }

    private final QuestBook plugin;
    private final File folder;
    private final Map<UUID, State> states = new HashMap<>();

    PlayerStore(QuestBook plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
    }

    State get(UUID id) {
        State s = states.get(id);
        if (s != null) {
            return s;
        }
        s = new State();
        states.put(id, s);
        File file = new File(folder, id + ".yml");
        if (file.isFile()) {
            YamlConfiguration yml = new YamlConfiguration();
            try {
                yml.load(file);
                s.done.addAll(yml.getStringList("done"));
                s.claimed.addAll(yml.getStringList("claimed"));
                s.milestones.addAll(yml.getStringList("milestones"));
                s.welcomeClaimed = yml.getBoolean("welcome-claimed");
                s.bookGiven = yml.getBoolean("book-given");
            } catch (IOException | InvalidConfigurationException ex) {
                // not a new player: until someone fixes the file, nothing is claimed twice or overwritten
                s.locked = true;
                plugin.getLogger().log(Level.SEVERE, "Couldn't read the quests of " + id + " (" + file.getName()
                    + "). Their rewards are on hold until the file is fixed (or deleted to start over).", ex);
            }
        }
        return s;
    }

    void save(UUID id) {
        State s = states.get(id);
        if (s == null || !s.dirty || s.locked) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("done", s.done.stream().toList());
        yml.set("claimed", s.claimed.stream().toList());
        yml.set("milestones", s.milestones.stream().toList());
        yml.set("welcome-claimed", s.welcomeClaimed);
        yml.set("book-given", s.bookGiven);
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("can't create " + folder);
            }
            Path target = new File(folder, id + ".yml").toPath();
            Path tmp = new File(folder, id + ".yml.tmp").toPath();
            Files.writeString(tmp, yml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            s.dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't save the quests of " + id, ex);
        }
    }

    void unload(UUID id) {
        save(id);
        State s = states.get(id);
        if (s != null && (!s.dirty || s.locked)) {
            states.remove(id); // (if saving failed it stays loaded, and is tried again later)
        }
    }

    void saveAll() {
        for (UUID id : Set.copyOf(states.keySet())) {
            save(id);
        }
    }
}
