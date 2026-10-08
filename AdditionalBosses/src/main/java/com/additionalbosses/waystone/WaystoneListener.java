package com.additionalbosses.waystone;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Placing, using, naming and protecting waystones, and the waystone menu.
 */
public final class WaystoneListener implements Listener {

    private final AdditionalBosses plugin;

    public WaystoneListener(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private WaystoneManager ways() {
        return plugin.waystones();
    }

    private boolean isOwnerOrAdmin(Player player, Waystone w) {
        return w.owner.equals(player.getUniqueId()) || player.hasPermission("additionalbosses.admin");
    }

    // ---------------- placing + breaking ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.items().kind(item) != ItemService.Kind.WAYSTONE) {
            return;
        }
        if (!plugin.settings().features.waystonesEnabled) {
            event.setCancelled(true);
            return;
        }
        ways().place(event.getPlayer(), event.getBlockPlaced(), item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Waystone w = ways().at(event.getBlock());
        if (w == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!isOwnerOrAdmin(player, w)) {
            event.setCancelled(true);
            player.sendMessage(plugin.settings().messages.prefixed("waystone-not-owner",
                Placeholder.unparsed("owner", w.ownerName)));
            return;
        }
        event.setDropItems(false);
        ways().remove(w);
        if (player.getGameMode() != GameMode.CREATIVE) {
            Block b = event.getBlock();
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), ways().createItem(w.name));
        }
        player.sendMessage(plugin.settings().messages.prefixed("waystone-removed", Placeholder.unparsed("name", w.name)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> ways().at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> ways().at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (ways().at(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (ways().at(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Withers, endermen and the like can't break or take a waystone. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (ways().at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    // ---------------- using ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.LODESTONE) {
            return;
        }
        Waystone w = ways().at(block);
        if (w == null) {
            return;
        }
        event.setCancelled(true); // no lodestone-compass binding, no placing blocks against it
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        // Name tag: rename the waystone.
        if (hand.getType() == Material.NAME_TAG && isOwnerOrAdmin(player, w)) {
            Component custom = hand.getData(DataComponentTypes.CUSTOM_NAME);
            String name = custom == null ? "" : Text.plain(custom).trim();
            if (!name.isEmpty()) {
                ways().rename(w, name);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.getInventory().setItemInMainHand(hand.getAmount() > 1 ? hand.asQuantity(hand.getAmount() - 1) : null);
                }
                Fx.play(block.getLocation(), "block.enchantment_table.use", 1.0f, 1.3f);
                player.sendMessage(plugin.settings().messages.prefixed("waystone-renamed", Placeholder.unparsed("name", w.name)));
                return;
            }
        }
        // Sneak + right-click with an item: use it as this waystone's icon in the menu.
        if (player.isSneaking() && !hand.isEmpty() && isOwnerOrAdmin(player, w)) {
            ways().setIcon(w, hand.getType());
            Fx.play(block.getLocation(), "entity.item_frame.add_item", 1.0f, 1.0f);
            player.sendActionBar(Component.text("Icon set for " + w.name));
            return;
        }
        ways().openMenu(player, w, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof WaystoneManager.Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
            ways().click(player, menu, event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof WaystoneManager.Menu) {
            event.setCancelled(true);
        }
    }

    /** Taking damage while the waystone is pulling you away breaks the spell. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && ways().isTravelling(player.getUniqueId())) {
            ways().cancelTravel(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ways().forget(event.getPlayer().getUniqueId());
    }
}
