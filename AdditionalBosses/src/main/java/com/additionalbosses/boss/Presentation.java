package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.Messages;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Encounter presentation: spawn announcements, first-engagement cues, ambient particles and death effects.
 * Higher ranks get stronger cues, but nothing runs every tick and nothing is spammed.
 */
public final class Presentation {

    private final AdditionalBosses plugin;

    public Presentation(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private RankSettings.Presentation of(Boss boss) {
        return plugin.settings().rank(boss.rank()).presentation();
    }

    public Component rankTitle(BossRank rank) {
        Component c = Component.text(plugin.settings().rank(rank).title(), rank.color());
        return rank == BossRank.GOLD ? c.decorate(TextDecoration.BOLD) : c;
    }

    /** Shown to players near a boss right after it appears naturally. */
    public void announceSpawn(Boss boss) {
        RankSettings.Presentation p = of(boss);
        if (p.announce() == RankSettings.Announce.NONE) {
            return;
        }
        Messages m = plugin.settings().messages;
        double radius = plugin.settings().announceRadius;
        LivingEntity e = boss.entity();
        for (Player player : e.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(e.getLocation()) > radius * radius) {
                continue;
            }
            switch (p.announce()) {
                case ACTIONBAR -> player.sendActionBar(m.get("spawn-actionbar", Placeholder.component("boss", boss.name())));
                case CHAT -> player.sendMessage(m.prefixed("spawn-chat", Placeholder.component("boss", boss.name())));
                case TITLE -> {
                    player.showTitle(Title.title(
                        m.get("spawn-title", Placeholder.component("rank", rankTitle(boss.rank())), Placeholder.component("boss", boss.name())),
                        m.get("spawn-subtitle", Placeholder.component("rank", rankTitle(boss.rank())), Placeholder.component("boss", boss.name())),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2800), Duration.ofMillis(900))));
                    player.sendMessage(m.prefixed("spawn-chat", Placeholder.component("boss", boss.name())));
                }
                default -> {
                }
            }
            Fx.playTo(player, p.sound());
        }
    }

    /** The first time a player engages a boss: its rank cue plays for them, plus a one-time burst. */
    public void reveal(Boss boss, Player player) {
        if (boss.revealTo(player)) {
            Fx.playTo(player, of(boss).sound());
        }
        if (boss.revealOnce()) {
            LivingEntity e = boss.entity();
            int burst = 6 + boss.rank().stars() * 6;
            Fx.dust(Fx.center(e), boss.rank().bukkitColor(), 1.6f, burst, 0.7);
            if (boss.rank() == BossRank.GOLD) {
                Fx.particle(Fx.center(e), Particle.END_ROD, 20, 0.6, 0.05);
            }
        }
    }

    /** Called about once per second for every loaded boss. Subtle by design. */
    public void ambient(Boss boss) {
        int count = of(boss).particles();
        if (count <= 0) {
            return;
        }
        LivingEntity e = boss.entity();
        Location at = Fx.center(e);
        Fx.dust(at, boss.rank().bukkitColor(), 1.1f, count, Math.max(0.3, e.getWidth() * 0.6));
        if (boss.rank() == BossRank.GOLD) {
            Fx.particle(at, Particle.WAX_ON, 1, 0.5, 0);
        }
    }

    public void death(Boss boss, @Nullable Player killer) {
        RankSettings.Presentation p = of(boss);
        LivingEntity e = boss.entity();
        Location at = Fx.center(e);
        Fx.dust(at, boss.rank().bukkitColor(), 1.8f, 10 + boss.rank().stars() * 8, 0.8);
        if (boss.rank() == BossRank.GOLD) {
            Fx.particle(at, Particle.TOTEM_OF_UNDYING, 80, 0.8, 0.4);
        } else if (boss.rank() == BossRank.PURPLE) {
            Fx.particle(at, Particle.WITCH, 40, 0.8, 0.1);
        }
        Fx.play(e.getLocation(), p.deathSound());

        if (p.deathBroadcast() == RankSettings.Broadcast.NONE) {
            return;
        }
        Messages m = plugin.settings().messages;
        Component msg = killer == null
            ? m.prefixed("slain-unknown", Placeholder.component("boss", boss.name()))
            : m.prefixed("slain", Placeholder.component("boss", boss.name()), Placeholder.component("player", killer.displayName()));
        if (p.deathBroadcast() == RankSettings.Broadcast.SERVER) {
            Bukkit.getServer().sendMessage(msg);
        } else {
            double radius = 64;
            for (Player player : e.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(e.getLocation()) <= radius * radius) {
                    player.sendMessage(msg);
                }
            }
        }
    }

    public void messageNearby(Boss boss, String key, double radius) {
        LivingEntity e = boss.entity();
        Component msg = plugin.settings().messages.get(key, Placeholder.component("boss", boss.name()));
        for (Player player : e.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(e.getLocation()) <= radius * radius) {
                player.sendActionBar(msg);
            }
        }
    }
}
