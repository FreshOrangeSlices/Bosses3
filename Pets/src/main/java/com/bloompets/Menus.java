package com.bloompets;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The screens: the pet menu (all your pets in one place, like an ender chest that only holds pets), a pet's storage,
 * and the Vex's gear.
 */
public final class Menus implements Listener {

    private static final int PET_SLOTS = 45;
    private static final int DISMISS_SLOT = 45;
    private static final int INFO_SLOT = 49;

    /** The pet menu. */
    static final class PetMenu implements InventoryHolder {
        final UUID owner;
        final List<UUID> pets = new ArrayList<>();
        Inventory inventory;

        PetMenu(UUID owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** A pet's storage. Its items are copied back into the pet when it closes. */
    static final class StorageMenu implements InventoryHolder {
        final Pet pet;
        Inventory inventory;

        StorageMenu(Pet pet) {
            this.pet = pet;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** The Vex's gear. While open, the gear lives in here; it goes back on the Vex when it closes. */
    static final class GearMenu implements InventoryHolder {
        final Pet pet;
        final Mob vex;
        Inventory inventory;
        boolean done;

        GearMenu(Pet pet, Mob vex) {
            this.pet = pet;
            this.vex = vex;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final BloomPets plugin;
    private final Map<UUID, StorageMenu> storage = new HashMap<>();
    private final Map<UUID, GearMenu> gear = new HashMap<>();

    public Menus(BloomPets plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  Pet menu
    // =====================================================================

    public void openPets(Player p) {
        PetMenu menu = new PetMenu(p.getUniqueId());
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("Your Pets"));
        render(menu, p);
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
    }

    /** Redraws the pet menu if this player has it open (after a summon, dismiss, level-up...). */
    public void refresh(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof PetMenu menu) {
            render(menu, p);
        }
    }

    private void render(PetMenu menu, Player p) {
        Inventory inv = menu.inventory;
        inv.clear();
        menu.pets.clear();
        List<Pet> pets = plugin.store().pets(menu.owner);
        long now = System.currentTimeMillis();
        for (Pet pet : pets) {
            if (menu.pets.size() >= PET_SLOTS) {
                break;
            }
            inv.setItem(menu.pets.size(), icon(pet, now));
            menu.pets.add(pet.id);
        }
        ItemStack filler = filler();
        for (int slot = PET_SLOTS; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }
        PetManager.Active active = plugin.pets().active(menu.owner);
        if (active != null) {
            inv.setItem(DISMISS_SLOT, button(Material.BARRIER, "Dismiss " + active.pet.name, NamedTextColor.RED,
                List.of("Sends it back into its bloom.")));
        }
        Settings s = plugin.settings();
        inv.setItem(INFO_SLOT, button(Material.BOOK, "Your Pets (" + pets.size() + "/" + s.maxPets + ")", Msg.PINK,
            List.of("Sneak + feed an animal its favourite", "food a few times to bond with it.",
                "", "Pets level up as you spend time together.",
                "/pets for everything else.")));
        if (pets.isEmpty()) {
            inv.setItem(22, button(Material.POPPY, "No pets yet", Msg.PINK,
                List.of("Sneak + feed a Wolf some meat, a Fox", "berries, a Horse sugar, an Allay an",
                    "amethyst shard... and it's yours.")));
        }
    }

    private ItemStack icon(Pet pet, long now) {
        ItemStack item = Blooms.create(pet);
        Blooms.describe(item, pet, true, now);
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, plugin.pets().isOut(pet));
        // a picture of the bloom, not a real one
        item.editPersistentDataContainer(pdc -> {
            pdc.remove(Keys.BLOOM);
            pdc.remove(Keys.BLOOM_OWNER);
        });
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return item;
    }

    private static ItemStack button(Material material, String name, TextColor color, List<String> lore) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(name, color).decorate(TextDecoration.BOLD));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        item.lore(lines);
        return item;
    }

    private void clickPetMenu(Player p, PetMenu menu, int slot, ClickType click) {
        PetManager pets = plugin.pets();
        if (slot == DISMISS_SLOT && pets.active(menu.owner) != null) {
            pets.dismiss(p, true);
            return;
        }
        if (slot < 0 || slot >= menu.pets.size()) {
            return;
        }
        Pet pet = plugin.store().get(menu.owner, menu.pets.get(slot));
        if (pet == null) {
            render(menu, p);
            return;
        }
        if (click.isShiftClick()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    openPetInventory(p, pet);
                }
            });
        } else if (click.isRightClick()) {
            giveBloom(p, pet);
        } else if (click.isLeftClick()) {
            pets.toggle(p, pet);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
        }
    }

    /** Hands out a pet's bloom, unless the player already carries one. */
    public void giveBloom(Player p, Pet pet) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (pet.id.equals(Blooms.petId(item))) {
                Msg.bar(p, "You already carry " + pet.name + "'s Pet Bloom.", NamedTextColor.GRAY);
                return;
            }
        }
        PetManager.give(p, Blooms.create(pet));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    // =====================================================================
    //  Storage and gear
    // =====================================================================

    /** Storage (or the Vex's gear) of a pet that is out. */
    public void openPetInventory(Player p, Pet pet) {
        PetManager.Active a = plugin.pets().active(pet.owner);
        boolean vex = pet.species == Species.VEX;
        if (a == null || a.pet != pet) {
            Msg.error(p, pet.name + " needs to be out to open its " + (vex ? "gear." : "storage."));
            return;
        }
        if (vex) {
            openGear(p, a);
        } else {
            openStorage(p, pet);
        }
    }

    private void openStorage(Player p, Pet pet) {
        int size = pet.storageSlots();
        if (size <= 0) {
            Msg.error(p, pet.name + " can't carry anything.");
            return;
        }
        StorageMenu menu = storage.get(pet.id);
        if (menu != null && menu.inventory.getSize() != size) {
            closeFor(pet); // it grew since; reopen at the new size
            menu = null;
        }
        if (menu == null) {
            menu = new StorageMenu(pet);
            menu.inventory = Bukkit.createInventory(menu, size, Component.text(pet.name + "'s Pack"));
            for (int i = 0; i < size; i++) {
                menu.inventory.setItem(i, pet.storage[i]);
            }
            storage.put(pet.id, menu);
        }
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
    }

    private void openGear(Player p, PetManager.Active a) {
        GearMenu menu = gear.get(a.pet.id);
        if (menu == null) {
            menu = new GearMenu(a.pet, a.entity);
            menu.inventory = Bukkit.createInventory(menu, InventoryType.HOPPER,
                Component.text(a.pet.name + ": helmet · chest · legs · boots · weapon"));
            EntityEquipment eq = a.entity.getEquipment();
            menu.inventory.setItem(0, eq.getHelmet());
            menu.inventory.setItem(1, eq.getChestplate());
            menu.inventory.setItem(2, eq.getLeggings());
            menu.inventory.setItem(3, eq.getBoots());
            menu.inventory.setItem(4, eq.getItemInMainHand());
            eq.setHelmet(null);
            eq.setChestplate(null);
            eq.setLeggings(null);
            eq.setBoots(null);
            eq.setItemInMainHand(null);
            gear.put(a.pet.id, menu);
        }
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
    }

    /** Copies a storage screen back into its pet. */
    private void sync(StorageMenu menu) {
        Pet pet = menu.pet;
        int size = Math.min(menu.inventory.getSize(), pet.storage.length);
        for (int i = 0; i < size; i++) {
            ItemStack item = menu.inventory.getItem(i);
            pet.storage[i] = item == null || item.isEmpty() ? null : item.clone();
        }
        plugin.store().save(pet.owner);
    }

    /** Puts the gear from the screen back on the Vex (or into the player's inventory if the Vex is gone). */
    private void finishGear(GearMenu menu, @Nullable Player p) {
        if (menu.done) {
            return;
        }
        menu.done = true;
        gear.remove(menu.pet.id);
        Inventory inv = menu.inventory;
        List<ItemStack> giveBack = new ArrayList<>();
        Map<EquipmentSlot, ItemStack> gear = new java.util.EnumMap<>(EquipmentSlot.class);
        gear.put(EquipmentSlot.HEAD, inv.getItem(0));
        gear.put(EquipmentSlot.CHEST, inv.getItem(1));
        gear.put(EquipmentSlot.LEGS, inv.getItem(2));
        gear.put(EquipmentSlot.FEET, inv.getItem(3));
        gear.put(EquipmentSlot.HAND, inv.getItem(4));
        if (!menu.vex.isValid() && plugin.pets().writeGear(menu.pet, menu.vex.getWorld(), gear)) {
            inv.clear(); // the Vex is gone (fainted, unloaded...): the gear went into its saved copy
            return;
        }
        if (menu.vex.isValid()) {
            EntityEquipment eq = menu.vex.getEquipment();
            eq.setHelmet(inv.getItem(0));
            equip(inv.getItem(1), EquipmentSlot.CHEST, eq, giveBack);
            equip(inv.getItem(2), EquipmentSlot.LEGS, eq, giveBack);
            equip(inv.getItem(3), EquipmentSlot.FEET, eq, giveBack);
            eq.setItemInMainHand(inv.getItem(4));
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND)) {
                eq.setDropChance(slot, 0);
            }
        } else {
            for (ItemStack item : inv.getContents()) {
                if (item != null && !item.isEmpty()) {
                    giveBack.add(item);
                }
            }
        }
        inv.clear();
        Player owner = p != null ? p : Bukkit.getPlayer(menu.pet.owner);
        for (ItemStack item : giveBack) {
            if (owner != null) {
                PetManager.give(owner, item);
            } else if (menu.vex.isValid()) {
                menu.vex.getWorld().dropItem(menu.vex.getLocation(), item);
            }
        }
        if (owner != null && !giveBack.isEmpty() && menu.vex.isValid()) {
            Msg.error(owner, "That doesn't go there, so it went back into your inventory.");
        }
    }

    private static void equip(@Nullable ItemStack item, EquipmentSlot slot, EntityEquipment eq, List<ItemStack> giveBack) {
        if (item == null || item.isEmpty()) {
            eq.setItem(slot, null);
            return;
        }
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.slot() == slot) {
            eq.setItem(slot, item);
        } else {
            eq.setItem(slot, null);
            giveBack.add(item);
        }
    }

    /** Whether the Vex's gear is in its screen right now (and not on the Vex). */
    public boolean gearOpen(Pet pet) {
        return gear.containsKey(pet.id);
    }

    /** Closes this pet's storage and gear screens, saving what's in them. */
    public void closeFor(Pet pet) {
        StorageMenu s = storage.remove(pet.id);
        if (s != null) {
            for (HumanEntity viewer : List.copyOf(s.inventory.getViewers())) {
                viewer.closeInventory();
            }
            sync(s);
        }
        GearMenu g = gear.get(pet.id);
        if (g != null) {
            for (HumanEntity viewer : List.copyOf(g.inventory.getViewers())) {
                viewer.closeInventory();
            }
            finishGear(g, null);
        }
    }

    /** On shutdown: every screen of ours closes, so nothing is left half-saved. */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof PetMenu || holder instanceof StorageMenu || holder instanceof GearMenu) {
                p.closeInventory();
            }
        }
        for (StorageMenu s : List.copyOf(storage.values())) {
            sync(s);
        }
        storage.clear();
        for (GearMenu g : List.copyOf(gear.values())) {
            finishGear(g, null);
        }
    }

    /**
     * Puts an item into a pet's storage (the open screen, if there is one). Returns what didn't fit, or null.
     */
    public @Nullable ItemStack addToStorage(Pet pet, ItemStack item) {
        StorageMenu open = storage.get(pet.id);
        if (open != null) {
            Map<Integer, ItemStack> left = open.inventory.addItem(item.clone());
            return left.isEmpty() ? null : left.values().iterator().next();
        }
        int slots = Math.min(pet.storageSlots(), pet.storage.length);
        int remaining = item.getAmount();
        for (int i = 0; i < slots && remaining > 0; i++) {
            ItemStack s = pet.storage[i];
            if (s != null && !s.isEmpty() && s.isSimilar(item) && s.getAmount() < s.getMaxStackSize()) {
                int add = Math.min(remaining, s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + add);
                remaining -= add;
            }
        }
        for (int i = 0; i < slots && remaining > 0; i++) {
            ItemStack s = pet.storage[i];
            if (s == null || s.isEmpty()) {
                ItemStack put = item.clone();
                put.setAmount(Math.min(remaining, item.getMaxStackSize()));
                pet.storage[i] = put;
                remaining -= put.getAmount();
            }
        }
        if (remaining <= 0) {
            return null;
        }
        ItemStack left = item.clone();
        left.setAmount(remaining);
        return left;
    }

    /** Whether at least one of this item fits in the pet's storage. */
    public boolean hasRoom(Pet pet, ItemStack item) {
        StorageMenu open = storage.get(pet.id);
        if (open != null) {
            for (ItemStack s : open.inventory.getStorageContents()) {
                if (s == null || s.isEmpty() || (s.isSimilar(item) && s.getAmount() < s.getMaxStackSize())) {
                    return true;
                }
            }
            return false;
        }
        int slots = Math.min(pet.storageSlots(), pet.storage.length);
        for (int i = 0; i < slots; i++) {
            ItemStack s = pet.storage[i];
            if (s == null || s.isEmpty() || (s.isSimilar(item) && s.getAmount() < s.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  Events
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof PetMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                clickPetMenu(p, menu, event.getRawSlot(), event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PetMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof StorageMenu menu) {
            sync(menu);
            if (event.getInventory().getViewers().size() <= 1 && storage.get(menu.pet.id) == menu) {
                storage.remove(menu.pet.id);
            }
            if (event.getPlayer() instanceof Player p) {
                p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.5f, 1.2f);
            }
        } else if (holder instanceof GearMenu menu) {
            finishGear(menu, event.getPlayer() instanceof Player p ? p : null);
        }
    }
}
