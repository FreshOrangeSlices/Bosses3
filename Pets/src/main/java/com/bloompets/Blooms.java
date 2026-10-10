package com.bloompets;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pet Blooms: the reusable flower that summons a pet. It looks like a flower but is really paper underneath, so it
 * can't be planted, composted or crafted with.
 */
public final class Blooms {

    private static final String[] NAMES = {"Mochi", "Biscuit", "Pebble", "Nugget", "Waffles", "Pudding", "Clover",
        "Maple", "Pickles", "Sprout", "Tofu", "Bean", "Noodle", "Peanut", "Muffin", "Dumpling", "Juniper", "Pepper",
        "Hazel", "Oreo", "Cocoa", "Marshmallow", "Pumpkin", "Basil", "Fig", "Sushi", "Tater", "Buttons", "Ziggy",
        "Luna", "Comet", "Pixel", "Toast", "Bubbles", "Snickers", "Peaches", "Ginger", "Olive", "Rolo", "Taffy"};

    private Blooms() {
    }

    /** A cute name this player isn't using yet. */
    public static String freshName(PetStore store, UUID owner) {
        List<String> free = new ArrayList<>();
        for (String n : NAMES) {
            if (!store.nameTaken(owner, n)) {
                free.add(n);
            }
        }
        if (!free.isEmpty()) {
            return free.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(free.size()));
        }
        int n = 2;
        while (store.nameTaken(owner, "Mochi " + n)) {
            n++;
        }
        return "Mochi " + n;
    }

    public static ItemStack create(Pet pet) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(pet.species.bloomModel()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        describe(item, pet, false, System.currentTimeMillis());
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.BLOOM, PersistentDataType.STRING, pet.id.toString());
            pdc.set(Keys.BLOOM_OWNER, PersistentDataType.STRING, pet.owner.toString());
        });
        return item;
    }

    /** Name and lore for a bloom, or for the pet's icon in the menu ({@code menu} adds the click hints). */
    public static void describe(ItemStack item, Pet pet, boolean menu, long now) {
        TextColor color = pet.species.category().color();
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Pet Bloom — " + pet.name, color)
            .decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        BloomPets plugin = BloomPets.get();
        if (menu && plugin != null && plugin.pets().isOut(pet)) {
            lore.add(line("● Out with you now", NamedTextColor.GREEN));
        }
        lore.add(line(pet.species.displayName() + " · " + pet.species.category().displayName()
            + " · Level " + pet.level, NamedTextColor.GRAY));
        if (pet.maxed()) {
            lore.add(line("Bond: max level", NamedTextColor.LIGHT_PURPLE));
        } else {
            int need = Pet.xpToNext(pet.level);
            int filled = (int) Math.round(10.0 * pet.xp / need);
            lore.add(Component.text("Bond: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("■".repeat(filled), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + pet.xp + "/" + need, NamedTextColor.GRAY)));
        }
        lore.add(line(pet.species.bonus(), NamedTextColor.DARK_AQUA));
        if (pet.storageSlots() > 0) {
            lore.add(line("Storage: " + pet.storageSlots() + " slots", NamedTextColor.GRAY));
        }
        if (menu && pet.resting(now)) { // only in the menu, which is drawn fresh each time
            lore.add(line("Resting: " + ((pet.restingUntil - now + 999) / 1000) + "s", NamedTextColor.RED));
        }
        lore.add(Component.empty());
        if (plugin != null && !plugin.settings().enabled.contains(pet.species)) {
            lore.add(line(pet.species.displayName() + " pets are turned off on this server", NamedTextColor.RED));
        }
        if (menu) {
            lore.add(line("Left-click: summon / dismiss", NamedTextColor.DARK_GRAY));
            lore.add(line("Right-click: take its Pet Bloom", NamedTextColor.DARK_GRAY));
            if (pet.species == Species.VEX) {
                lore.add(line("Shift-click: its gear (while it's out)", NamedTextColor.DARK_GRAY));
            } else if (pet.storageSlots() > 0) {
                lore.add(line("Shift-click: its storage (while it's out)", NamedTextColor.DARK_GRAY));
            }
        } else {
            lore.add(line("Right-click: summon / dismiss " + pet.name, NamedTextColor.DARK_GRAY));
            lore.add(line("Punch: open all your pets", NamedTextColor.DARK_GRAY));
            if (pet.species.rideable()) {
                int ride = plugin == null ? 3 : plugin.settings().rideLevel(pet.species);
                lore.add(line("Use it on your pet: ride" + (ride > 1 ? " (level " + ride + "+)" : ""),
                    NamedTextColor.DARK_GRAY));
            }
        }
        item.lore(lore);
    }

    // =====================================================================
    //  The Pet Toy: one item for all your pets
    // =====================================================================

    /** The Pet Toy: summons your pet, opens all your pets, lets you ride. Works for whoever holds it. */
    public static ItemStack toy() {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("popped_chorus_fruit"));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Pet Toy", Msg.PINK).decorate(TextDecoration.BOLD));
        item.lore(List.of(
            line("Squeaks when you squeeze it.", NamedTextColor.GRAY),
            Component.empty(),
            line("Right-click: call your pet / send it home", NamedTextColor.DARK_GRAY),
            line("Punch: see all your pets and pick one", NamedTextColor.DARK_GRAY),
            line("Use it on your pet: ride", NamedTextColor.DARK_GRAY),
            line("Right-click your pet: swap to another", NamedTextColor.DARK_GRAY),
            line("Sneak + right-click your pet: its bag", NamedTextColor.DARK_GRAY)));
        item.editPersistentDataContainer(pdc -> pdc.set(Keys.TOY, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    public static boolean isToy(@Nullable ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(Keys.TOY);
    }

    /** A bloom or the toy: never crafted with, traded or lost on death. */
    public static boolean isPetItem(@Nullable ItemStack item) {
        return isBloom(item) || isToy(item);
    }

    public static boolean hasToy(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isToy(item)) {
                return true;
            }
        }
        return isToy(p.getItemOnCursor());
    }

    /** Gives the player a Pet Toy unless they already carry one. True if one was given. */
    public static boolean giveToyIfMissing(Player p) {
        if (hasToy(p)) {
            return false;
        }
        PetManager.give(p, toy());
        return true;
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static @Nullable UUID petId(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String raw = item.getPersistentDataContainer().get(Keys.BLOOM, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean isBloom(@Nullable ItemStack item) {
        return petId(item) != null;
    }

    /** Refreshes the lore of this pet's blooms in the owner's inventory (after a level-up or rename). */
    public static void refresh(Player owner, Pet pet) {
        PlayerInventory inv = owner.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (pet.id.equals(petId(item))) {
                describe(item, pet, false, System.currentTimeMillis());
                inv.setItem(slot, item);
            }
        }
    }
}
