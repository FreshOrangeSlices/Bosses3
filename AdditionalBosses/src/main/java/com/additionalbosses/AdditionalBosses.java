package com.additionalbosses;

import com.additionalbosses.boss.BossBarManager;
import com.additionalbosses.boss.BossManager;
import com.additionalbosses.boss.Presentation;
import com.additionalbosses.command.BossesCommand;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.guide.GuideBook;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.listener.BossListener;
import com.additionalbosses.listener.CombatListener;
import com.additionalbosses.listener.ItemListener;
import com.additionalbosses.listener.PlayerListener;
import com.additionalbosses.relic.RelicManager;
import com.additionalbosses.reward.RewardManager;
import com.additionalbosses.trait.TraitManager;
import com.additionalbosses.util.Keys;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Additional Bosses - ordinary mobs occasionally become ranked bosses with traits, boss bars and rewards
 * (Boss Gear, Empowerment Runes, Relics) that players use to build personalised equipment.
 *
 * <p>Core loop: mob spawns -> roll to become a boss -> roll a rank -> traits -> fight -> XP + independent reward
 * rolls -> Empowerment and Relics layered onto equipment.</p>
 */
public final class AdditionalBosses extends JavaPlugin {

    private static AdditionalBosses instance;

    private PluginSettings settings;
    private TraitManager traits;
    private BossManager bosses;
    private BossBarManager bossBars;
    private Presentation presentation;
    private RewardManager rewards;
    private RelicManager relics;
    private ItemService items;
    private GuideBook guide;

    public static AdditionalBosses get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        saveDefaultConfig();

        traits = new TraitManager();
        relics = new RelicManager(this);
        items = new ItemService(this);
        bossBars = new BossBarManager(this);
        presentation = new Presentation(this);
        bosses = new BossManager(this);
        rewards = new RewardManager(this);
        guide = new GuideBook(this);
        reloadSettings();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BossListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);

        registerCommand("bosses", "Additional Bosses commands", List.of("ab", "boss"), new BossesCommand(this));

        int restored = bosses.restoreLoaded();
        getLogger().info("Additional Bosses enabled: " + traits.enabledTraits().size() + " traits, "
            + relics.enabledEffects(false).size() + " relics, " + relics.enabledEffects(true).size() + " curses"
            + (restored > 0 ? ", " + restored + " bosses restored" : "") + ".");
    }

    @Override
    public void onDisable() {
        if (bosses != null) {
            bosses.shutdown();
        }
        if (relics != null) {
            relics.shutdown();
        }
        instance = null;
    }

    /** Re-reads config.yml. Active bosses keep their rank and traits; new values apply from now on. */
    public void reloadSettings() {
        reloadConfig();
        settings = new PluginSettings(getConfig(), getLogger());
        traits.load(getConfig().getConfigurationSection("traits"), getLogger());
        relics.load(getConfig().getConfigurationSection("relics"), getLogger());
    }

    public PluginSettings settings() {
        return settings;
    }

    public TraitManager traits() {
        return traits;
    }

    public BossManager bosses() {
        return bosses;
    }

    public BossBarManager bossBars() {
        return bossBars;
    }

    public Presentation presentation() {
        return presentation;
    }

    public RewardManager rewards() {
        return rewards;
    }

    public RelicManager relics() {
        return relics;
    }

    public ItemService items() {
        return items;
    }

    public GuideBook guide() {
        return guide;
    }
}
