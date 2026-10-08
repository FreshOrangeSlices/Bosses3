package com.additionalbosses.listener;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossManager;
import com.additionalbosses.trait.BossTrait;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.additionalbosses.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.List;

/**
 * Boss lifecycle: conversion on spawn, restore on load, cleanup on removal, death, resurrection,
 * transformation, targeting and projectile hooks.
 */
public final class BossListener implements Listener {

    private final AdditionalBosses plugin;

    public BossListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private BossManager bosses() {
        return plugin.bosses();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        bosses().handleSpawn(event.getEntity(), event.getSpawnReason());
    }

    /** Re-registers bosses that come back into the world (chunk loads, server restart). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        if (BossManager.isBoss(living)) {
            if (bosses().get(living) == null) {
                bosses().restore(living);
            }
            return;
        }
        // Mobs placed by world generation (e.g. Elder Guardians in monuments) never fire a normal spawn
        // event on the main thread, so they get their one boss roll the first time they are loaded.
        CreatureSpawnEvent.SpawnReason reason = living.getEntitySpawnReason();
        if (BossManager.isWorldgen(reason) && plugin.settings().categoryFor(living.getType()) != null
            && !living.getPersistentDataContainer().has(Keys.WORLDGEN_CHECKED, PersistentDataType.BYTE)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (living.isValid()) {
                    bosses().handleSpawn(living, reason);
                }
            });
        }
    }

    /** Fires on death, despawn and chunk unload: always release the boss and its bar. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Boss boss = bosses().get(event.getEntity());
        if (boss != null) {
            bosses().unregister(boss);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (BossManager.isMinion(dead)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Boss boss = bosses().get(dead);
        if (boss != null) {
            bosses().handleDeath(boss, event);
        }
    }

    /** Undying-style traits: un-cancel the resurrect event to cheat death like a totem. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!event.isCancelled()) {
            return; // a real totem is already saving it
        }
        Boss boss = bosses().get(event.getEntity());
        if (boss != null) {
            for (BossTrait trait : boss.traits()) {
                if (trait.onLethalDamage(boss)) {
                    event.setCancelled(false);
                    return;
                }
            }
            return;
        }
        if (event.getEntity() instanceof Player player && plugin.relics().tryResurrect(player)) {
            event.setCancelled(false);
        }
    }

    /** A Nemesis never turns into something else (a drowned, a zombified piglin...); it stays itself. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNemesisTransform(EntityTransformEvent event) {
        Boss boss = bosses().get(event.getEntity());
        if (boss != null && boss.isNemesis()) {
            event.setCancelled(true);
        }
    }

    /** Zombie -> Drowned, Skeleton -> Stray, Piglin -> Zombified Piglin keep their boss status. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        Boss boss = bosses().get(event.getEntity());
        if (boss == null) {
            return;
        }
        List<Entity> results = event.getTransformedEntities();
        boolean transfer = event.getTransformReason() != EntityTransformEvent.TransformReason.SPLIT
            && results.size() == 1 && results.get(0) instanceof LivingEntity living && living instanceof Enemy;
        if (transfer) {
            bosses().transfer(boss, (LivingEntity) results.get(0));
        } else {
            // e.g. a cured Zombie Villager or split slimes: they are not bosses, so drop the boss name.
            for (Entity result : results) {
                result.customName(null);
                result.setCustomNameVisible(false);
            }
        }
    }

    /** A prowling Nemesis is busy hunting other mobs: it leaves players alone unless they attack it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProwlTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            return;
        }
        Boss boss = bosses().get(event.getEntity());
        if (boss != null && boss.isNemesis() && plugin.nemesis().isProwling(boss)) {
            event.setCancelled(true);
        }
    }

    /** Every mob a Nemesis kills makes it stronger. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNemesisKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Boss boss = bosses().get(event.getDamageSource().getCausingEntity());
        if (boss != null && boss.isNemesis() && boss.entity().isValid()) {
            plugin.nemesis().onPreyKilled(boss, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        Boss boss = bosses().get(event.getEntity());
        if (boss != null) {
            bosses().engage(boss, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        Boss boss = bosses().get(shooter);
        if (boss == null) {
            return;
        }
        for (BossTrait trait : boss.traits()) {
            trait.onProjectileLaunch(boss, projectile);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        Boss boss = bosses().get(shooter);
        if (boss == null) {
            return;
        }
        for (BossTrait trait : boss.traits()) {
            trait.afterProjectileLaunch(boss, projectile);
        }
    }

    /** A boss that kills a player remembers it (revenge bonus) and may become that player's Nemesis. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        Boss boss = bosses().get(causing);
        if (boss == null && causing != null && BossManager.isMinion(causing)) {
            // Killed by a boss's minion: the boss gets the credit.
            String owner = causing.getPersistentDataContainer().get(Keys.MINION, PersistentDataType.STRING);
            try {
                boss = owner == null ? null : bosses().get(Bukkit.getEntity(java.util.UUID.fromString(owner)));
            } catch (IllegalArgumentException ignored) {
                boss = null;
            }
        }
        if (boss == null) {
            return;
        }
        boss.addVictim(event.getEntity().getUniqueId());
        plugin.nemesis().onKilledPlayer(boss, event.getEntity());
    }

    // ---------------- anti-trap ----------------

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (plugin.settings().features.blockVehicles && bosses().get(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (plugin.settings().features.blockVehicles && bosses().get(event.getEntered()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (plugin.settings().features.blockVehicles && bosses().get(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.bossBars().forget(event.getPlayer(), bosses().active());
        plugin.compass().forget(event.getPlayer().getUniqueId());
    }
}
