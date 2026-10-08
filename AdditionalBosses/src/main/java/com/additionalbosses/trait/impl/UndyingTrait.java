package com.additionalbosses.trait.impl;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Defense: cheats death once, like a Totem of Undying, and comes back with part of its health. */
public final class UndyingTrait extends BaseTrait {

    private double reviveHealth = 50;

    public UndyingTrait() {
        super("undying", TraitCategory.DEFENSE, "Undying", "Undying");
    }

    @Override
    public void load(ConfigurationSection s) {
        reviveHealth = s.getDouble("revive-health-percent", 50);
    }

    @Override
    public String description() {
        return "The first time it would die, it rises again with " + Text.num(reviveHealth) + "% health.";
    }

    @Override
    public boolean onLethalDamage(Boss boss) {
        if (boss.undyingUsed()) {
            return false;
        }
        boss.setUndyingUsed(true);
        LivingEntity e = boss.entity();
        e.getPersistentDataContainer().set(Keys.BOSS_UNDYING, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTask(AdditionalBosses.get(), () -> {
            if (!e.isValid() || e.isDead()) {
                return;
            }
            e.setHealth(Math.max(1.0, boss.maxHealth() * Math.min(100.0, reviveHealth) / 100.0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 2));
            AdditionalBosses.get().bossBars().updateHealth(boss, e.getHealth());
        });
        AdditionalBosses.get().presentation().messageNearby(boss, "undying", 32);
        return true;
    }
}
