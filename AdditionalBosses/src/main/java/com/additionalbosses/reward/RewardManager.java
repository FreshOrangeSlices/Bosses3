package com.additionalbosses.reward;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
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

    public List<ItemStack> roll(Boss boss, @Nullable Player killer) {
        RankSettings.Rewards chances = plugin.settings().rank(boss.rank()).rewards();
        double luck = plugin.relics().rewardMultiplier(killer);
        List<ItemStack> out = new ArrayList<>();

        if (Rng.chance(chances.bossGear() * luck)) {
            // "Nightmare Ravenous Zombie" (the boss name without its stars)
            String source = boss.plainName().substring(Math.min(boss.plainName().length(), boss.rank().stars() + 1));
            out.add(gear.create(boss.rank(), boss.entity().getType(), source, null));
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
