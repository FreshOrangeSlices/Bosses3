package com.questbook;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * QuestBook: one Quest Book that shows a player everything the server's plugins offer, through small one-time quests
 * and skill milestones, with the detailed guide books one click away.
 *
 * <p>It doesn't know about any plugin in particular. Quests are defined in quests.yml and complete when a player
 * meets their triggers: a flag another plugin set on the player (see {@link QuestFlags}), an item they carry, or an
 * mcMMO skill level.</p>
 */
public final class QuestBook extends JavaPlugin {

    private static QuestBook instance;

    private QuestConfig quests;
    private PlayerStore store;
    private McMMO mcmmo;
    private Menus menus;
    private QuestService service;

    public static QuestBook get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!new File(getDataFolder(), "quests.yml").isFile()) {
            saveResource("quests.yml", false);
        }
        Keys.init(this);
        mcmmo = new McMMO(this);
        store = new PlayerStore(this);
        service = new QuestService(this);
        menus = new Menus(this);
        load();
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);
        getServer().getPluginManager().registerEvents(menus, this);
        registerCommand("quests", "Open your Quest Book", List.of("quest", "questbook"), new QuestCommand(this));
        // mcMMO (a soft dependency) is enabled by now
        getServer().getScheduler().runTask(this, () -> mcmmo.hook());
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (store != null) {
            store.saveAll();
        }
    }

    /** (Re)reads config.yml and quests.yml. */
    public void load() {
        reloadConfig();
        quests = QuestConfig.load(this);
        getLogger().info("Loaded " + quests.questCount() + " quests on " + quests.boards().size() + " boards.");
    }

    public QuestConfig quests() {
        return quests;
    }

    public PlayerStore store() {
        return store;
    }

    public McMMO mcmmo() {
        return mcmmo;
    }

    public Menus menus() {
        return menus;
    }

    public QuestService service() {
        return service;
    }
}
