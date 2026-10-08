package com.additionalbosses.feature;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Rng;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Loot and Fortune Runes: multipliers on normal mob drops (when the rune's weapon gets the kill) and on ore drops
 * (when the rune's tool breaks the ore). Fractions roll: x1.5 on 3 diamonds gives 4, plus a 50% chance of a 5th.
 *
 * <p>To stay exploit-proof they never touch boss rewards, the plugin's own items, anything a mob was wearing or
 * holding, mobs with an inventory, player deaths, or ore drops that are placeable blocks (so a silk-touched ore
 * can't be farmed over and over).</p>
 */
public final class LootListener implements Listener {

    private final AdditionalBosses plugin;

    public LootListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDrops(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        // Mobs that carry an inventory (chested horses, llamas, allays, piglins, villagers...) would let players
        // multiply items they put in themselves, so they are skipped entirely.
        if (killer == null || dead instanceof Player || dead instanceof InventoryHolder || event.getDrops().isEmpty()) {
            return;
        }
        double bonus = plugin.items().effectBonus(killer.getInventory().getItemInMainHand(), EmpowermentStat.LOOT);
        if (bonus <= 0) {
            return;
        }
        double multiplier = Math.min(plugin.settings().features.maxLootMultiplier, 1.0 + bonus);
        List<ItemStack> extra = new ArrayList<>();
        List<ItemStack> worn = equipment(dead);
        ListIterator<ItemStack> it = event.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (!eligible(drop) || wasEquipped(drop, worn)) {
                continue;
            }
            it.set(scaled(drop, multiplier, extra));
        }
        event.getDrops().addAll(extra);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOreDrops(BlockDropItemEvent event) {
        Material block = event.getBlockState().getType();
        if (!(block.name().endsWith("_ORE") || block == Material.AMETHYST_CLUSTER) || event.getItems().isEmpty()) {
            return;
        }
        double bonus = plugin.items().effectBonus(event.getPlayer().getInventory().getItemInMainHand(), EmpowermentStat.FORTUNE);
        if (bonus <= 0) {
            return;
        }
        double multiplier = Math.min(plugin.settings().features.maxFortuneMultiplier, 1.0 + bonus);
        for (Item item : event.getItems()) {
            ItemStack drop = item.getItemStack();
            if (!eligible(drop) || drop.getType().isBlock()) {
                continue;
            }
            List<ItemStack> extra = new ArrayList<>();
            item.setItemStack(scaled(drop, multiplier, extra));
            for (ItemStack more : extra) {
                item.getWorld().dropItem(item.getLocation(), more);
            }
        }
    }

    /** Everything the mob was holding or wearing (it may have picked up a player's stack). */
    private static List<ItemStack> equipment(LivingEntity dead) {
        List<ItemStack> out = new ArrayList<>();
        EntityEquipment eq = dead.getEquipment();
        if (eq == null) {
            return out;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                ItemStack item = eq.getItem(slot);
                if (!item.isEmpty()) {
                    out.add(item);
                }
            } catch (IllegalArgumentException ignored) {
                // slot not supported by this mob
            }
        }
        return out;
    }

    private static boolean wasEquipped(ItemStack drop, List<ItemStack> worn) {
        for (ItemStack item : worn) {
            if (item.isSimilar(drop)) {
                return true;
            }
        }
        return false;
    }

    private boolean eligible(ItemStack drop) {
        return !drop.isEmpty() && drop.getMaxStackSize() > 1
            && !drop.getPersistentDataContainer().has(Keys.ITEM_KIND);
    }

    /** The stack multiplied (fraction rolled as a chance); anything above a full stack goes into {@code extra}. */
    private static ItemStack scaled(ItemStack drop, double multiplier, List<ItemStack> extra) {
        double exact = drop.getAmount() * multiplier;
        int amount = (int) Math.floor(exact);
        if (Rng.r().nextDouble() < exact - amount) {
            amount++;
        }
        int max = drop.getMaxStackSize();
        while (amount > max) {
            extra.add(drop.asQuantity(max));
            amount -= max;
        }
        return drop.asQuantity(Math.max(1, amount));
    }
}
