package com.bloompets;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * BloomPets: feed an animal until it bonds with you, then summon it with its Pet Bloom, ride it, store things in it
 * and level it up to 10. One pet out at a time.
 */
public final class BloomPets extends JavaPlugin {

    private static BloomPets instance;

    private Settings settings;
    private PetStore store;
    private PetManager pets;
    private RideController rides;
    private Bonuses bonuses;
    private Menus menus;
    private McMMO mcmmo;
    private Skills skills;
    private Happiness happiness;

    public static BloomPets get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigUpdate.run(this);
        reloadConfig();
        Keys.init(this);
        settings = new Settings(getConfig());
        store = new PetStore(this);
        bonuses = new Bonuses(this);
        rides = new RideController(this);
        pets = new PetManager(this);
        menus = new Menus(this);
        mcmmo = new McMMO(this);
        mcmmo.hook();
        skills = new Skills(this);
        happiness = new Happiness(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PetListener(this), this);
        pm.registerEvents(bonuses, this);
        pm.registerEvents(menus, this);
        pm.registerEvents(rides, this);
        pm.registerEvents(new Timber(this), this);
        pm.registerEvents(skills, this);

        pets.start();
        rides.start();
        bonuses.start();
        skills.start();
        happiness.start();
        registerCommand("pets", "Your pets: summon, dismiss, rename and more", List.of("pet"), new PetsCommand(this));
        getLogger().info("BloomPets ready: " + settings.enabled.size() + " species can be tamed.");
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll(); // open storage and gear screens save first
        }
        if (pets != null) {
            pets.shutdown(); // every pet goes back into its bloom, so none are lost or duplicated
        }
        if (store != null) {
            store.saveAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public PetStore store() {
        return store;
    }

    public PetManager pets() {
        return pets;
    }

    public RideController rides() {
        return rides;
    }

    public Bonuses bonuses() {
        return bonuses;
    }

    public Menus menus() {
        return menus;
    }

    public McMMO mcmmo() {
        return mcmmo;
    }

    public Skills skills() {
        return skills;
    }

    public Happiness happiness() {
        return happiness;
    }
}
