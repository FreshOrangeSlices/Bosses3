package com.additionalbosses;

import com.additionalbosses.boss.BossBarManager;
import com.additionalbosses.boss.BossManager;
import com.additionalbosses.boss.Presentation;
import com.additionalbosses.command.BossesCommand;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.feature.CompassManager;
import com.additionalbosses.feature.EscalationManager;
import com.additionalbosses.feature.FeatureListener;
import com.additionalbosses.guide.GuideBook;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.item.Trophies;
import com.additionalbosses.listener.BossListener;
import com.additionalbosses.listener.CombatListener;
import com.additionalbosses.listener.ItemListener;
import com.additionalbosses.listener.PlayerListener;
import com.additionalbosses.nemesis.NemesisManager;
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
    private NemesisManager nemesis;
    private Trophies trophies;
    private EscalationManager escalation;
    private CompassManager compass;
    private FeatureListener features;

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
        nemesis = new NemesisManager(this);
        trophies = new Trophies(this);
        escalation = new EscalationManager(this);
        compass = new CompassManager(this);
        features = new FeatureListener(this);
        reloadSettings();
        nemesis.load();
        compass.start();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BossListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(features, this);

        registerCommand("bosses", "Additional Bosses commands", List.of("ab", "boss"), new BossesCommand(this));

        int restored = bosses.restoreLoaded();
        getLogger().info("Additional Bosses enabled: " + traits.enabledTraits().size() + " traits, "
            + relics.enabledEffects(false).size() + " relics, " + relics.enabledEffects(true).size() + " curses"
            + (restored > 0 ? ", " + restored + " bosses restored" : "") + ", "
            + nemesis.count() + " Nemeses remembered.");
    }

    @Override
    public void onDisable() {
        if (compass != null) {
            compass.stop();
        }
        if (features != null) {
            features.unregisterRecipes();
        }
        if (nemesis != null) {
            nemesis.shutdown();
        }
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
        if (features != null) {
            features.registerRecipes();
        }
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

    public NemesisManager nemesis() {
        return nemesis;
    }

    public Trophies trophies() {
        return trophies;
    }

    public EscalationManager escalation() {
        return escalation;
    }

    public CompassManager compass() {
        return compass;
    }
}
