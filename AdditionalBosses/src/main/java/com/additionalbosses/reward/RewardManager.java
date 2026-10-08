package com.additionalbosses.reward;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossManager;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.util.Keys;
import org.bukkit.persistence.PersistentDataType;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.util.Rng;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss rewards. Every category has its own independent percentage roll (no shared loot pool), so one kill can
 * produce several categories. The Boss Gear roll produces at most one equipment item.
 * XP is handled by BossManager on death.
 */
public final class RewardManager {

    private final AdditionalBosses plugin;
    private final BossGearFactory gear;

    public RewardManager(AdditionalBosses plugin) {
        this.plugin = plugin;
        this.gear = new BossGearFactory(plugin);
    }

    public BossGearFactory gear() {
        return gear;
    }

    /**
     * The main reward roll on death, with the safety nets: from the configured rank up a boss always drops
     * something, and below it a player who comes up empty several times in a row is guaranteed a drop.
     */
    public List<ItemStack> rollWithFloor(Boss boss, @Nullable Player killer) {
        List<ItemStack> out = roll(boss, killer);
        FeatureSettings f = plugin.settings().features;
        boolean floor = f.rewardFloorFrom != null && boss.rank().atLeast(f.rewardFloorFrom);
        if (!out.isEmpty()) {
            if (killer != null) {
                killer.getPersistentDataContainer().remove(Keys.PLAYER_DRY_KILLS);
            }
            return out;
        }
        boolean pity = false;
        if (!floor && killer != null && f.pityAfter > 0) {
            int dry = killer.getPersistentDataContainer().getOrDefault(Keys.PLAYER_DRY_KILLS, PersistentDataType.INTEGER, 0) + 1;
            pity = dry >= f.pityAfter;
            if (pity) {
                killer.getPersistentDataContainer().remove(Keys.PLAYER_DRY_KILLS);
            } else {
                killer.getPersistentDataContainer().set(Keys.PLAYER_DRY_KILLS, PersistentDataType.INTEGER, dry);
            }
        }
        if (floor || pity) {
            ItemStack guaranteed = Rng.chance(65) ? null : plugin.items().createRandomRune(boss.rank());
            out.add(guaranteed != null ? guaranteed
                : gear.create(boss.rank(), boss.entity().getType(), sourceName(boss), null));
        }
        return out;
    }

    private static String sourceName(Boss boss) {
        String prefix = boss.rank().starText() + " ";
        return boss.plainName().startsWith(prefix) ? boss.plainName().substring(prefix.length()) : boss.plainName();
    }

    public List<ItemStack> roll(Boss boss, @Nullable Player killer) {
        RankSettings.Rewards chances = plugin.settings().rank(boss.rank()).rewards();
        // Harder fights (Threat Scaling) pay better.
        double luck = plugin.relics().rewardMultiplier(killer)
            * (1.0 + BossManager.threat(boss.entity()) * plugin.settings().features.threatRewardBonus / 100.0);
        List<ItemStack> out = new ArrayList<>();

        if (Rng.chance(chances.bossGear() * luck)) {
            // "Nightmare Ravenous Zombie" (the boss name without its stars)
            out.add(gear.create(boss.rank(), boss.entity().getType(), sourceName(boss), null));
        }
        if (Rng.chance(chances.empowerment() * luck)) {
            ItemStack rune = plugin.items().createRandomRune(boss.rank());
            if (rune != null) {
                out.add(rune);
            }
        }
        if (Rng.chance(chances.relic() * luck)) {
            ItemStack relic = plugin.items().createRandomRelic();
            if (relic != null) {
                out.add(relic);
            }
        }
        if (plugin.settings().catalystEnabled && Rng.chance(chances.relicCatalyst() * luck)) {
            out.add(plugin.items().createCatalyst());
        }
        return out;
    }
}
