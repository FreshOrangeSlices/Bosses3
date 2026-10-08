package com.additionalbosses.feature;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossMobs;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.config.Messages;
import com.additionalbosses.util.Clock;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.SafeSpots;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Escalation Chain: a player who kills several bosses within one Minecraft day draws the attention of something
 * worse, and a pack of Purple/Gold bosses arrives to hunt them.
 */
public final class EscalationManager {

    private final AdditionalBosses plugin;
    private final Map<UUID, Deque<Long>> kills = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public EscalationManager(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private FeatureSettings f() {
        return plugin.settings().features;
    }

    /** How many boss kills this player has inside the current escalation window. */
    public int progress(Player player) {
        Deque<Long> d = kills.get(player.getUniqueId());
        if (d == null) {
            return 0;
        }
        prune(d, Clock.now());
        return d.size();
    }

    private void prune(Deque<Long> d, long now) {
        long window = Math.round(f().escalationWindowDays * Clock.DAY);
        while (!d.isEmpty() && (now - d.peekFirst() > window || d.peekFirst() > now)) {
            d.removeFirst();
        }
    }

    public void recordKill(Player player) {
        FeatureSettings f = f();
        if (!f.escalationEnabled) {
            return;
        }
        long now = Clock.now();
        UUID id = player.getUniqueId();
        Deque<Long> d = kills.computeIfAbsent(id, k -> new ArrayDeque<>());
        d.addLast(now);
        prune(d, now);
        if (d.size() < f.escalationKills || now < cooldownUntil.getOrDefault(id, Long.MIN_VALUE)) {
            return;
        }
        d.clear();
        cooldownUntil.put(id, now + Math.round(f.escalationWindowDays * Clock.DAY));
        trigger(player);
    }

    /** Warns the player, then (after the configured delay) brings in the escalation bosses. */
    public void trigger(Player player) {
        FeatureSettings f = f();
        Messages m = plugin.settings().messages;
        player.showTitle(Title.title(m.get("escalation-title"), m.get("escalation-subtitle"),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(800))));
        player.sendMessage(m.prefixed("escalation-subtitle"));
        Fx.playTo(player, Fx.sound("event.raid.horn", 1.0f, 0.8f));
        UUID id = player.getUniqueId();
        long delay = Math.max(1, Math.round(f.escalationDelaySeconds * 20));
        Bukkit.getScheduler().runTaskLater(plugin, () -> arrive(id), delay);
    }

    private void arrive(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || player.isDead()
            || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            || !plugin.settings().worldAllowed(player.getWorld())) {
            return;
        }
        FeatureSettings f = f();
        Fx.playTo(player, Fx.sound("entity.wither.spawn", 0.6f, 1.2f));
        for (int i = 0; i < f.escalationBosses; i++) {
            Location spot = SafeSpots.around(player.getLocation(), 12, 20, 16);
            if (spot == null) {
                continue;
            }
            EntityType type = BossMobs.pick(plugin, player.getWorld());
            BossRank rank = BossMobs.rollRank(f.escalationRanks, BossRank.PURPLE);
            Fx.particle(spot.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 60, 0.6, 0.2);
            Fx.play(spot, "entity.evoker.prepare_summon", 1.0f, 0.7f);
            Boss boss = plugin.bosses().summon(type, spot, rank, null, true, BossMobs.categoryId(plugin, type));
            if (boss != null) {
                plugin.bosses().engage(boss, player);
                if (boss.entity() instanceof org.bukkit.entity.Mob mob) {
                    mob.setTarget(player);
                }
            }
        }
    }

    public void forget(UUID id) {
        kills.remove(id);
    }
}
