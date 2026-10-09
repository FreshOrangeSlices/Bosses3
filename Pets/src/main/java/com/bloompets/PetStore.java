package com.bloompets;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Every player's pets, one file per player (plugins/BloomPets/players/&lt;uuid&gt;.yml). A player's file is loaded the
 * first time it's needed and kept while they're online.
 */
public final class PetStore {

    private final BloomPets plugin;
    private final File folder;
    private final Map<UUID, Map<UUID, Pet>> byOwner = new HashMap<>();

    public PetStore(BloomPets plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
    }

    /** The player's pets in menu order. */
    public List<Pet> pets(UUID owner) {
        List<Pet> list = new ArrayList<>(load(owner).values());
        list.sort(Comparator.comparingInt(p -> p.order));
        return list;
    }

    public @Nullable Pet get(UUID owner, UUID petId) {
        return load(owner).get(petId);
    }

    public @Nullable Pet byName(UUID owner, String name) {
        for (Pet pet : load(owner).values()) {
            if (pet.name.equalsIgnoreCase(name.trim())) {
                return pet;
            }
        }
        return null;
    }

    public void add(Pet pet) {
        Map<UUID, Pet> pets = load(pet.owner);
        int order = 0;
        for (Pet p : pets.values()) {
            order = Math.max(order, p.order + 1);
        }
        pet.order = order;
        pets.put(pet.id, pet);
        save(pet.owner);
    }

    public void remove(Pet pet) {
        load(pet.owner).remove(pet.id);
        save(pet.owner);
    }

    public boolean nameTaken(UUID owner, String name) {
        return byName(owner, name) != null;
    }

    public void unload(UUID owner) {
        save(owner);
        byOwner.remove(owner);
    }

    public Collection<UUID> loadedOwners() {
        return List.copyOf(byOwner.keySet());
    }

    // =====================================================================

    private Map<UUID, Pet> load(UUID owner) {
        Map<UUID, Pet> pets = byOwner.get(owner);
        if (pets != null) {
            return pets;
        }
        pets = new LinkedHashMap<>();
        byOwner.put(owner, pets);
        File file = file(owner);
        if (!file.isFile()) {
            return pets;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("pets");
        if (section == null) {
            return pets;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            Species species = s == null ? null : Species.parse(s.getString("species"));
            if (species == null) {
                plugin.getLogger().warning("Skipping pet " + key + " of " + owner + ": unknown species");
                continue;
            }
            try {
                Pet.RideStyle style;
                try {
                    style = Pet.RideStyle.valueOf(s.getString("ride-style", "NORMAL").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    style = Pet.RideStyle.NORMAL;
                }
                Pet pet = new Pet(UUID.fromString(key), owner, species, s.getString("name", species.displayName()), style);
                pet.level = Math.max(1, Math.min(Pet.MAX_LEVEL, s.getInt("level", 1)));
                pet.xp = Math.max(0, s.getInt("xp", 0));
                pet.restingUntil = s.getLong("resting-until", 0);
                pet.xpBank = s.getInt("xp-bank", 0);
                pet.order = s.getInt("order", pets.size());
                pet.health = s.getDouble("health", -1);
                String snap = s.getString("snapshot");
                pet.snapshot = snap == null || snap.isEmpty() ? null : Base64.getDecoder().decode(snap);
                List<String> items = s.getStringList("storage");
                for (int i = 0; i < Math.min(items.size(), pet.storage.length); i++) {
                    pet.storage[i] = decode(items.get(i));
                }
                pets.put(pet.id, pet);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping a broken pet entry " + key + " of " + owner, ex);
            }
        }
        return pets;
    }

    public void save(UUID owner) {
        Map<UUID, Pet> pets = byOwner.get(owner);
        if (pets == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Pet pet : pets.values()) {
            String path = "pets." + pet.id;
            yml.set(path + ".name", pet.name);
            yml.set(path + ".species", pet.species.name());
            yml.set(path + ".level", pet.level);
            yml.set(path + ".xp", pet.xp);
            yml.set(path + ".ride-style", pet.rideStyle.name());
            yml.set(path + ".resting-until", pet.restingUntil);
            yml.set(path + ".xp-bank", pet.xpBank);
            yml.set(path + ".order", pet.order);
            yml.set(path + ".health", pet.health);
            yml.set(path + ".snapshot", pet.snapshot == null ? "" : Base64.getEncoder().encodeToString(pet.snapshot));
            List<String> items = new ArrayList<>();
            for (ItemStack item : pet.storage) {
                items.add(encode(item));
            }
            yml.set(path + ".storage", items);
        }
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("can't create " + folder);
            }
            yml.save(file(owner));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the pets of " + owner, ex);
        }
    }

    public void saveAll() {
        for (UUID owner : byOwner.keySet()) {
            save(owner);
        }
    }

    private File file(UUID owner) {
        return new File(folder, owner + ".yml");
    }

    private static String encode(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static @Nullable ItemStack decode(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
