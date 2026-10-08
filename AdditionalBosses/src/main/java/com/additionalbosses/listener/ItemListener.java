package com.additionalbosses.listener;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.item.EquipmentType;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.util.Fx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Applying Runes, Relics and Catalysts by picking them up and clicking them onto equipment,
 * and keeping the special items out of crafting recipes.
 */
public final class ItemListener implements Listener {

    private final AdditionalBosses plugin;

    public ItemListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        ItemService items = plugin.items();
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor.isEmpty() || target == null || target.isEmpty() || !items.isConsumable(cursor)) {
            return;
        }
        if (!EquipmentType.of(target.getType()).isEquipment()) {
            return; // normal inventory behaviour (e.g. putting the rune into an empty slot or a chest)
        }
        event.setCancelled(true);
        if (event instanceof InventoryCreativeEvent || player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.settings().messages.prefixed("apply-creative"));
            return;
        }
        Component error = items.validate(cursor, target);
        if (error != null) {
            player.sendMessage(error);
            Fx.play(player.getLocation(), "block.note_block.bass", 0.8f, 0.6f);
            return;
        }
        Component confirm = items.confirmationPrompt(player, cursor, target, "slot" + event.getRawSlot());
        if (confirm != null) {
            player.sendMessage(confirm);
            Fx.play(player.getLocation(), "block.note_block.pling", 0.8f, 1.2f);
            return;
        }
        ItemStack updated = target.clone();
        Component message = items.apply(player, cursor, updated);
        event.setCurrentItem(updated);
        player.setItemOnCursor(cursor.getAmount() > 1 ? cursor.asQuantity(cursor.getAmount() - 1) : null);
        player.sendMessage(message);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.updateInventory();
            plugin.relics().refresh(player);
        });
    }

    /** Runes, Relics, Catalysts and Boss Gear can't be used as crafting ingredients. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (plugin.items().kind(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
