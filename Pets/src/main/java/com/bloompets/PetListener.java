package com.bloompets;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything players do with pets and blooms: bonding, feeding, riding, opening storage, summoning with the bloom,
 * plus keeping pets safe (they faint instead of dying, can't be hurt by their owner, can't be bred, leashed...).
 */
public final class PetListener implements Listener {

    /** A click that went to a pet is followed by a "use item" from the same click; that one is ignored. */
    private static final long CLICK_GAP = 250;

    private final BloomPets plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> lastMenu = new HashMap<>();
    private final Map<UUID, List<ItemStack>> keptBlooms = new HashMap<>();

    public PetListener(BloomPets plugin) {
        this.plugin = plugin;
    }

    private boolean recentClick(Player p) {
        return System.currentTimeMillis() - lastClick.getOrDefault(p.getUniqueId(), 0L) < CLICK_GAP;
    }

    /** True (and remembers the click) unless this player clicked a moment ago. */
    private boolean click(Player p) {
        if (recentClick(p)) {
            return false;
        }
        lastClick.put(p.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private static @Nullable Entity source(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity e ? e : null;
        }
        return damager;
    }

    // =====================================================================
    //  Right-clicking animals and pets
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        Entity clicked = event.getRightClicked();
        PetManager pets = plugin.pets();
        PetManager.Active a = pets.of(clicked);
        if (a != null) {
            ItemStack held = p.getInventory().getItem(event.getHand());
            if (a.owner.equals(p.getUniqueId()) && vanillaUse(a.pet.species, held.getType())) {
                return; // milk the cow, brush the armadillo, dress the wolf
            }
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (!a.owner.equals(p.getUniqueId())) {
                Player owner = Bukkit.getPlayer(a.owner);
                Msg.bar(p, "That's " + (owner == null ? "someone" : owner.getName()) + "'s pet, " + a.pet.name + ".",
                    NamedTextColor.GRAY);
                return;
            }
            if (!click(p)) {
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (a.pet.species.likes(hand.getType())) {
                pets.feed(p, a, EquipmentSlot.HAND);
            } else if (p.isSneaking()) {
                plugin.menus().openPetInventory(p, a.pet);
            } else if (Blooms.isBloom(hand)) {
                plugin.rides().tryMount(p, a);
            } else if (hand.getType() == Material.NAME_TAG && hand.getData(DataComponentTypes.CUSTOM_NAME) != null) {
                String name = PlainTextComponentSerializer.plainText()
                    .serialize(hand.getData(DataComponentTypes.CUSTOM_NAME));
                if (pets.rename(p, a.pet, name)) {
                    PetManager.consume(p, EquipmentSlot.HAND);
                }
            } else {
                pets.dismiss(p, true);
            }
            return;
        }
        if (Keys.isPet(clicked)) {
            event.setCancelled(true); // a leftover pet nobody is looking after
            clicked.remove();
            return;
        }
        // sneak + feed a wild animal its favourite food to bond with it (plain feeding still breeds as usual)
        if (event.isCancelled() || event.getHand() != EquipmentSlot.HAND || !p.isSneaking()) {
            return;
        }
        Species species = Species.of(clicked.getType());
        if (species == null || !(clicked instanceof Mob mob) || mob.isDead()) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!species.likes(hand.getType()) || !p.hasPermission("bloompets.use")) {
            return;
        }
        event.setCancelled(true);
        if (click(p)) {
            pets.bond(p, mob, species, EquipmentSlot.HAND);
        }
    }

    private static boolean vanillaUse(Species species, Material item) {
        return switch (species) {
            case COW -> item == Material.BUCKET;
            case ARMADILLO -> item == Material.BRUSH;
            case WOLF -> item == Material.WOLF_ARMOR || item == Material.SHEARS;
            default -> false;
        };
    }

    // =====================================================================
    //  The Pet Bloom: right-click to summon / dismiss, punch to open all pets
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // Material#isInteractable: good enough to let doors and chests open
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        boolean bloom = Blooms.isBloom(item);
        if (action.isRightClick() && recentClick(p)) {
            // the same click already went to a pet: don't also summon, eat the food, etc.
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (!bloom) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND && Blooms.isBloom(p.getInventory().getItemInMainHand())) {
            event.setUseItemInHand(Event.Result.DENY);
            return; // the main hand's bloom handles it
        }
        if (action.isLeftClick()) {
            event.setCancelled(true);
            openMenu(p);
            return;
        }
        Block block = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && !p.isSneaking() && block.getType().isInteractable()) {
            return; // open the door or chest as usual
        }
        event.setCancelled(true);
        if (click(p)) {
            useBloom(p, item, event.getHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunchEntity(PrePlayerAttackEntityEvent event) {
        Player p = event.getPlayer();
        if (Blooms.isBloom(p.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            openMenu(p);
        }
    }

    private void openMenu(Player p) {
        long now = System.currentTimeMillis();
        if (now - lastMenu.getOrDefault(p.getUniqueId(), 0L) < 300) {
            return;
        }
        lastMenu.put(p.getUniqueId(), now);
        plugin.menus().openPets(p);
    }

    private void useBloom(Player p, ItemStack item, @Nullable EquipmentSlot hand) {
        UUID petId = Blooms.petId(item);
        String owner = item.getPersistentDataContainer().get(Keys.BLOOM_OWNER, PersistentDataType.STRING);
        if (petId == null || !p.getUniqueId().toString().equals(owner)) {
            Msg.error(p, "This Pet Bloom belongs to someone else.");
            return;
        }
        Pet pet = plugin.store().get(p.getUniqueId(), petId);
        if (pet == null) {
            Msg.error(p, "That pet is gone, and its bloom wilts away.");
            if (hand != null) {
                p.getInventory().setItem(hand, null);
            }
            return;
        }
        plugin.pets().toggle(p, pet);
    }

    // =====================================================================
    //  Keeping pets safe
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetHurt(EntityDamageEvent event) {
        Entity e = event.getEntity();
        PetManager.Active a = plugin.pets().of(e);
        if (a == null) {
            if (Keys.isPet(e)) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.pets().fainting(a)) {
            event.setCancelled(true);
            return;
        }
        switch (event.getCause()) {
            case FALL, SUFFOCATION, DROWNING, CRAMMING, FLY_INTO_WALL -> {
                event.setCancelled(true);
                return;
            }
            default -> {
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity src = source(byEntity.getDamager());
            if (src == null) {
                return;
            }
            if (src.getUniqueId().equals(a.owner) || Keys.isPet(src)
                || (src instanceof Player && plugin.settings().protectFromPlayers)) {
                event.setCancelled(true);
                return;
            }
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner != null && src instanceof LivingEntity attacker && !(src instanceof Player)) {
                plugin.pets().engage(owner, attacker);
            }
        }
    }

    /** Pets never die: a blow that would kill one makes it faint instead. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetLethal(EntityDamageEvent event) {
        PetManager.Active a = plugin.pets().of(event.getEntity());
        if (a != null && event.getFinalDamage() >= a.entity.getHealth()) {
            event.setCancelled(true);
            plugin.pets().faint(a);
        }
    }

    /** Pets never hurt players (or each other). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPetAttacks(EntityDamageByEntityEvent event) {
        Entity src = source(event.getDamager());
        if (src != null && Keys.isPet(src) && (event.getEntity() instanceof Player || Keys.isPet(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    /** Whoever you fight, your combat pet fights too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent event) {
        Entity src = source(event.getDamager());
        Entity victim = event.getEntity();
        if (src instanceof Player p && victim instanceof LivingEntity target && !(victim instanceof Player)
            && !Keys.isPet(victim)) {
            plugin.pets().engage(p, target);
        } else if (victim instanceof Player p && src instanceof LivingEntity attacker && !(src instanceof Player)
            && !Keys.isPet(src)) {
            plugin.pets().engage(p, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (Keys.isPet(event.getEntity()) && event.getTarget() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true); // pets don't climb into boats or minecarts
        } else if (Keys.isPet(event.getMount()) && !plugin.rides().mounting()) {
            event.setCancelled(true); // only its owner, through the bloom
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (Keys.isPet(event.getMother()) || Keys.isPet(event.getFather())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true); // it is called to its owner's side instead
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (Keys.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // =====================================================================
    //  Deaths, XP and life cycle
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (Keys.isPet(dead)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            PetManager.Active a = plugin.pets().of(dead);
            if (a != null) {
                plugin.pets().died(a);
            }
            return;
        }
        if (dead instanceof Player) {
            return;
        }
        Settings s = plugin.settings();
        DamageSource source = event.getDamageSource();
        PetManager.Active byPet = source.getDirectEntity() == null ? null : plugin.pets().of(source.getDirectEntity());
        if (byPet != null) {
            plugin.pets().addXp(byPet.pet, s.xpPetKill);
            return;
        }
        Player killer = dead.getKiller();
        if (killer != null && dead instanceof Enemy) {
            PetManager.Active mine = plugin.pets().active(killer.getUniqueId());
            if (mine != null && !plugin.pets().fainting(mine)) {
                plugin.pets().addXp(mine.pet, s.xpOwnerKill);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        if (a != null) {
            plugin.pets().stash(a);
        }
        plugin.store().unload(p.getUniqueId());
        lastClick.remove(p.getUniqueId());
        lastMenu.remove(p.getUniqueId());
    }

    /** Your pet goes back into its bloom when you die, and your blooms stay with you. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        if (a != null) {
            plugin.pets().stash(a);
        }
        String me = p.getUniqueId().toString();
        List<ItemStack> kept = new ArrayList<>();
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack drop = it.next();
            if (Blooms.isBloom(drop)
                && me.equals(drop.getPersistentDataContainer().get(Keys.BLOOM_OWNER, PersistentDataType.STRING))) {
                kept.add(drop);
                it.remove();
            }
        }
        if (!kept.isEmpty()) {
            keptBlooms.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).addAll(kept);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        List<ItemStack> kept = keptBlooms.remove(p.getUniqueId());
        if (kept != null) {
            Bukkit.getScheduler().runTask(plugin, () -> kept.forEach(item -> PetManager.give(p, item)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            PetManager.Active a = plugin.pets().of(e);
            if (a != null) {
                plugin.pets().unloaded(a);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.DEATH) {
            return;
        }
        PetManager.Active a = plugin.pets().of(event.getEntity());
        if (a != null) {
            plugin.pets().lost(a);
        }
    }

    // =====================================================================
    //  Blooms are flowers, not paper: no crafting or trading with them
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (Blooms.isBloom(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (Blooms.isBloom(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareResultEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (Blooms.isBloom(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getType() == InventoryType.MERCHANT
            && (Blooms.isBloom(top.getItem(0)) || Blooms.isBloom(top.getItem(1)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.MERCHANT) {
            return;
        }
        ItemStack hotbar = event.getHotbarButton() >= 0
            ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (Blooms.isBloom(event.getCurrentItem()) || Blooms.isBloom(event.getCursor()) || Blooms.isBloom(hotbar)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                Msg.error(p, "Pet Blooms can't be traded.");
            }
        }
    }
}
