package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.PluginSettings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * One shared manager for every boss bar. A bar is only shown to players who are actually fighting the boss
 * (engaged within the combat timeout) and are within view distance. Bars are hidden when combat ends and
 * removed when the boss dies, despawns or unloads.
 */
public final class BossBarManager {

    private final AdditionalBosses plugin;

    public BossBarManager(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    public void create(Boss boss) {
        if (boss.bar() != null) {
            return;
        }
        BossBar bar = BossBar.bossBar(title(boss, boss.health()), progress(boss, boss.health()),
            boss.rank().barColor(), boss.rank().barOverlay());
        boss.bar(bar);
    }

    public void updateHealth(Boss boss, double health) {
        BossBar bar = boss.bar();
        if (bar == null) {
            return;
        }
        bar.progress(progress(boss, health));
        if (plugin.settings().showHealthNumbers) {
            bar.name(title(boss, health));
        }
    }

    /** Called by the BossManager ticker: shows the bar to engaged nearby players and hides it from everyone else. */
    public void refreshViewers(Boss boss) {
        BossBar bar = boss.bar();
        if (bar == null) {
            return;
        }
        PluginSettings s = plugin.settings();
        double maxDistSq = s.bossbarViewDistance * s.bossbarViewDistance;
        Set<UUID> shouldSee = new HashSet<>();
        for (UUID id : boss.engaged().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(boss.entity().getWorld())
                && p.getLocation().distanceSquared(boss.entity().getLocation()) <= maxDistSq) {
                shouldSee.add(id);
            }
        }
        Iterator<UUID> it = boss.barViewers().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!shouldSee.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
                it.remove();
            }
        }
        for (UUID id : shouldSee) {
            if (boss.barViewers().add(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.showBossBar(bar);
                }
            }
        }
        updateHealth(boss, boss.health());
    }

    public void remove(Boss boss) {
        BossBar bar = boss.bar();
        if (bar == null) {
            return;
        }
        for (UUID id : boss.barViewers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        boss.barViewers().clear();
        boss.bar(null);
    }

    public void forget(Player player, Iterable<Boss> bosses) {
        for (Boss boss : bosses) {
            boss.barViewers().remove(player.getUniqueId());
        }
    }

    private Component title(Boss boss, double health) {
        if (!plugin.settings().showHealthNumbers) {
            return boss.name();
        }
        long shown = (long) Math.ceil(Math.max(0, health));
        long max = Math.round(boss.maxHealth());
        return Component.empty()
            .append(boss.name())
            .append(Component.text("  ❤ " + shown + "/" + max, NamedTextColor.GRAY));
    }

    private static float progress(Boss boss, double health) {
        return (float) Math.max(0.0, Math.min(1.0, health / boss.maxHealth()));
    }
}
