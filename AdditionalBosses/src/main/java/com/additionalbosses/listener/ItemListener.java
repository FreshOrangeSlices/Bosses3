package com.additionalbosses.listener;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.item.EquipmentType;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
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
        if (items.isSalvage(cursor, target)) {
            event.setCancelled(true);
            salvage(event, player, cursor);
            return;
        }
        if (cursor.isEmpty() || target == null || target.isEmpty() || !items.isConsumable(cursor)) {
            return;
        }
        if (!EquipmentType.of(target.getType()).isEquipment() && !items.isCompassUpgrade(cursor, target)) {
            return; // normal inventory behaviour (e.g. putting the rune into an empty slot or a chest)
        }
        event.setCancelled(true);
        if (event instanceof InventoryCreativeEvent || player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.settings().messages.prefixed("apply-creative"));
            return;
        }
        // Everything below shows above the hotbar instead of in chat.
        Component error = items.validate(cursor, target);
        if (error != null) {
            Fx.actionBar(player, error);
            Fx.play(player.getLocation(), "block.note_block.bass", 0.8f, 0.6f);
            return;
        }
        Component confirm = items.confirmationPrompt(player, cursor, target, "slot" + event.getRawSlot());
        if (confirm != null) {
            Fx.actionBar(player, confirm);
            Fx.play(player.getLocation(), "block.note_block.pling", 0.8f, 1.2f);
            return;
        }
        ItemStack updated = target.clone();
        Component message = items.apply(player, cursor, updated);
        event.setCurrentItem(updated);
        player.setItemOnCursor(cursor.getAmount() > 1 ? cursor.asQuantity(cursor.getAmount() - 1) : null);
        Fx.actionBar(player, message);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.updateInventory();
            plugin.relics().refresh(player);
        });
    }

    /** Boss Gear clicked onto a Hunter's Compass breaks down into a Boss Soul of the gear's rank (after a confirm). */
    private void salvage(InventoryClickEvent event, Player player, ItemStack gear) {
        ItemService items = plugin.items();
        if (event instanceof InventoryCreativeEvent || player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.settings().messages.prefixed("apply-creative"));
            return;
        }
        Component confirm = items.salvagePrompt(player, gear, "slot" + event.getRawSlot());
        if (confirm != null) {
            Fx.actionBar(player, confirm);
            Fx.play(player.getLocation(), "block.note_block.pling", 0.8f, 1.2f);
            return;
        }
        ItemStack soul = items.createSoul(items.gearRank(gear), 1);
        Component name = gear.effectiveName();
        player.setItemOnCursor(soul);
        Fx.actionBar(player, plugin.settings().messages.get("salvaged", Placeholder.component("item", name)));
        Fx.play(player.getLocation(), "block.sculk_catalyst.bloom", 1.0f, 1.0f);
        Fx.play(player.getLocation(), "entity.allay.item_taken", 0.8f, 0.7f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.updateInventory();
            plugin.relics().refresh(player);
        });
    }

    /** Trying to take off cursed armor: it's bound (Curse of Binding), and says so. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCursedArmorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getSlotType() != InventoryType.SlotType.ARMOR
            || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack worn = event.getCurrentItem();
        if (worn != null && !worn.isEmpty() && worn.getEnchantmentLevel(Enchantment.BINDING_CURSE) > 0
            && !ItemService.list(worn, Keys.CURSES).isEmpty()) {
            Fx.actionBar(player, plugin.settings().messages.get("cursed-armor-locked"));
        }
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
