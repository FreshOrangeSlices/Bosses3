package com.questbook;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The Quest Book item, and noticing what players have done. */
public final class QuestListener implements Listener {

    private final QuestBook plugin;
    private final Map<UUID, List<ItemStack>> kept = new HashMap<>();
    private final Set<UUID> refreshQueued = new HashSet<>();

    QuestListener(QuestBook plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  The book
    // =====================================================================

    /** A written book whose one page says how to open it (in case a client opens it as a book). */
    static ItemStack book(QuestBook plugin) {
        String title = plugin.quests().bookTitle;
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContent.writtenBookContent(title, "Server")
            .addPage(Component.text(title + "\n\n", NamedTextColor.DARK_RED)
                .append(Component.text("Right-click to open it, or type ", NamedTextColor.BLACK))
                .append(Component.text("/quests", NamedTextColor.DARK_BLUE))
                .append(Component.text(".", NamedTextColor.BLACK)))
            .build());
        book.setData(DataComponentTypes.ITEM_NAME, Component.text(title, Msg.GOLD));
        book.lore(List.of(Component.text("Right-click to open", NamedTextColor.GRAY)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        book.editPersistentDataContainer(pdc -> pdc.set(Keys.BOOK, PersistentDataType.BYTE, (byte) 1));
        return book;
    }

    static boolean isBook(@Nullable ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(Keys.BOOK);
    }

    static boolean carriesBook(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isBook(item)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || !isBook(event.getItem())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        plugin.menus().openHub(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerStore.State state = plugin.store().get(p.getUniqueId());
        // a moment later, so other plugins have marked what this player already did (and mcMMO has loaded them)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (!state.bookGiven && plugin.quests().giveBookOnJoin) {
                state.bookGiven = true;
                state.dirty = true;
                if (!carriesBook(p)) {
                    for (ItemStack left : p.getInventory().addItem(book(plugin)).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    }
                    Msg.chat(p, Component.text("Here's your Quest Book: it shows everything this server adds. "
                        + "Right-click it, or type /quests.", NamedTextColor.GRAY));
                }
            }
            plugin.service().refresh(p);
            plugin.store().save(p.getUniqueId());
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.store().unload(id);
        plugin.menus().forget(id);
        refreshQueued.remove(id);
    }

    /** The Quest Book stays with you when you die. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.quests().keepBookOnDeath || event.getKeepInventory()) {
            return;
        }
        List<ItemStack> books = new ArrayList<>();
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack drop = it.next();
            if (isBook(drop)) {
                books.add(drop);
                it.remove();
            }
        }
        if (!books.isEmpty()) {
            kept.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new ArrayList<>()).addAll(books);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        List<ItemStack> books = kept.remove(p.getUniqueId());
        if (books != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (ItemStack book : books) {
                    for (ItemStack left : p.getInventory().addItem(book).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    }
                }
            });
        }
    }

    // =====================================================================
    //  Noticing items ("obtain your first Relic")
    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            queueRefresh(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p && !(event.getInventory().getHolder(false) instanceof Menus.Screen)) {
            queueRefresh(p);
        }
    }

    /** A second later (once, however many items came in at once). */
    private void queueRefresh(Player p) {
        if (!refreshQueued.add(p.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshQueued.remove(p.getUniqueId());
            if (p.isOnline()) {
                plugin.service().refresh(p);
            }
        }, 20L);
    }
}
