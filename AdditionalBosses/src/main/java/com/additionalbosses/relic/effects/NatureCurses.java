package com.additionalbosses.relic.effects;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Rng;
import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Curses about the living world: Pariah (friendly creatures fear you) and Herbivore (no meat for you).
 */
public final class NatureCurses {

    private static final Set<Material> MEAT = EnumSet.of(
        Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP, Material.CHICKEN,
        Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON, Material.RABBIT, Material.COOKED_RABBIT,
        Material.RABBIT_STEW, Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON,
        Material.TROPICAL_FISH, Material.PUFFERFISH, Material.ROTTEN_FLESH);
    private static final Set<Material> FISH = EnumSet.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH,
        Material.PUFFERFISH);
    private static final Material[] PLANT_FOOD = {Material.COAL, Material.WHEAT_SEEDS, Material.SWEET_BERRIES};

    private NatureCurses() {
    }

    // ------------------------------------------------------------------

    /** Animals flee, villagers charge more, and fish won't bite: only treasure does. */
    public static final class Pariah extends BaseRelic {
        private double radius = 10;
        private int villagerPenalty = 40;

        public Pariah() {
            super("pariah", "Pariah", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public void load(ConfigurationSection s) {
            radius = s.getDouble("radius", 10);
            // Iron golems turn on players around -100 reputation, so stay below that.
            villagerPenalty = Math.max(0, Math.min(99, s.getInt("villager-price-penalty", 40)));
        }

        @Override
        public String description() {
            return "Animals flee from you and villagers charge you more. Fish won't bite your hook, but treasure does.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        /** Once a second: nearby farm animals run from you. */
        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            Location from = p.getLocation();
            for (Entity e : p.getNearbyEntities(radius, 4, radius)) {
                if (!(e instanceof Animals animal) || e instanceof org.bukkit.entity.Enemy
                    || (e instanceof Tameable t && t.isTamed()) || animal.isLeashed() || !animal.getPassengers().isEmpty()
                    || animal.getPathfinder().hasPath()) {
                    continue;
                }
                Vector away = animal.getLocation().toVector().subtract(from.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = new Vector(Rng.between(-1.0, 1.0), 0, Rng.between(-1.0, 1.0));
                }
                Location flee = animal.getLocation().add(away.normalize().multiply(radius));
                animal.getPathfinder().moveTo(flee, 1.6);
            }
        }

        int villagerPenalty() {
            return villagerPenalty;
        }
    }

    /** No meat or fish: it rots into coal, seeds or berries the moment you carry it. */
    public static final class Herbivore extends BaseRelic {

        public Herbivore() {
            super("herbivore", "Herbivore", true);
        }

        @Override
        public double defaultWeight() {
            return 6;
        }

        @Override
        public String description() {
            return "You can't eat meat or fish. Any you carry turns into coal, seeds or berries.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            convertMeat(ctx.player());
        }
    }

    /** Swaps every stack of meat or fish in the player's inventory for coal, seeds or berries. */
    static void convertMeat(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && MEAT.contains(item.getType())) {
                // Rotten flesh never becomes coal, so a zombie farm doesn't turn into a coal farm.
                int from = item.getType() == Material.ROTTEN_FLESH ? 1 : 0;
                inv.setItem(slot, ItemStack.of(PLANT_FOOD[Rng.between(from, PLANT_FOOD.length - 1)], item.getAmount()));
                changed = true;
            }
        }
        if (changed) {
            p.sendActionBar(Component.text("Your meat withers away...", NamedTextColor.DARK_GREEN));
            Fx.playTo(p, Fx.sound("block.composter.fill_success", 0.8f, 0.8f));
        }
    }

    // ------------------------------------------------------------------

    /** The event-driven halves of these curses (fishing, trading, eating, picking things up). */
    public static final class Events implements Listener {

        private final AdditionalBosses plugin;

        public Events(AdditionalBosses plugin) {
            this.plugin = plugin;
        }

        private boolean has(Player p, String id) {
            return plugin.relics().has(p, id);
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onFish(PlayerFishEvent event) {
            if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)
                || !FISH.contains(caught.getItemStack().getType()) || !has(event.getPlayer(), "pariah")) {
                return;
            }
            caught.setItemStack(treasure());
        }

        /** A roll from the vanilla treasure pool: enchanted books, bows and rods, name tags, saddles, shells. */
        private static ItemStack treasure() {
            Random random = new Random();
            return switch (Rng.between(0, 5)) {
                case 0 -> Bukkit.getItemFactory().enchantWithLevels(ItemStack.of(Material.BOOK), 30, true, random);
                case 1 -> Bukkit.getItemFactory().enchantWithLevels(ItemStack.of(Material.BOW), Rng.between(22, 30), true, random);
                case 2 -> Bukkit.getItemFactory().enchantWithLevels(ItemStack.of(Material.FISHING_ROD), Rng.between(22, 30), true, random);
                case 3 -> ItemStack.of(Material.NAME_TAG);
                case 4 -> ItemStack.of(Material.SADDLE);
                default -> ItemStack.of(Material.NAUTILUS_SHELL);
            };
        }

        /** Villagers remember a Pariah: worse prices (like after hitting one, but not enough to anger golems). */
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onTrade(PlayerInteractEntityEvent event) {
            if (!(event.getRightClicked() instanceof Villager villager) || !has(event.getPlayer(), "pariah")) {
                return;
            }
            if (!(plugin.relics().get("pariah") instanceof Pariah pariah) || pariah.villagerPenalty() <= 0) {
                return;
            }
            Reputation rep = villager.getReputation(event.getPlayer().getUniqueId());
            if (rep.getReputation(ReputationType.MINOR_NEGATIVE) < pariah.villagerPenalty()) {
                rep.setReputation(ReputationType.MINOR_NEGATIVE, pariah.villagerPenalty());
                villager.setReputation(event.getPlayer().getUniqueId(), rep);
            }
            villager.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, villager.getEyeLocation().add(0, 0.4, 0), 3, 0.3, 0.2, 0.3);
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onEat(PlayerItemConsumeEvent event) {
            if (MEAT.contains(event.getItem().getType()) && has(event.getPlayer(), "herbivore")) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("You can't bring yourself to eat that.", NamedTextColor.DARK_GREEN));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPickup(EntityPickupItemEvent event) {
            if (event.getEntity() instanceof Player p && MEAT.contains(event.getItem().getItemStack().getType())
                && has(p, "herbivore")) {
                Bukkit.getScheduler().runTask(plugin, () -> convertMeat(p));
            }
        }
    }
}
