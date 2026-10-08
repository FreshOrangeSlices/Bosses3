package com.additionalbosses.listener;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.util.PlayerData;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Keeps each player's active-relic cache in sync with their equipment, and routes relic hooks that aren't
 * damage-related (kills, jumping, sleeping). Also hands out the guide book on first join.
 */
public final class PlayerListener implements Listener {

    private final AdditionalBosses plugin;

    public PlayerListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.relics().refresh(player);
        if (plugin.settings().guideOnFirstJoin && !PlayerData.guideGiven(player)) {
            PlayerData.markGuideGiven(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.guide().give(player);
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.relics().forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.relics().refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refreshNextTick(event.getPlayer());
    }

    private void refreshNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.relics().refresh(player);
            }
        });
    }

    /** Runs after the boss XP (HIGH) so XP relics multiply the boss payout too. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer != null && killer != dead) {
            plugin.relics().onKill(killer, event, plugin.bosses().get(dead) != null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        plugin.relics().onJump(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        if (plugin.relics().preventsSleep(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.settings().messages.prefixed("insomnia"));
        }
    }
}
