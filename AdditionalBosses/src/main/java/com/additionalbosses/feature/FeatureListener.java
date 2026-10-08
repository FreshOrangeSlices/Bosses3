package com.additionalbosses.feature;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossMobs;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.item.Trophies;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.SafeSpots;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Boss Totems (summoning ritual), Nemesis statues (placing, picking up, protecting), the Hunter's Compass recipe,
 * and guards that stop the plugin's special items from doing their vanilla thing (spawn eggs, lodestones...).
 */
public final class FeatureListener implements Listener {

    private final AdditionalBosses plugin;
    private final NamespacedKey compassRecipe;
    private final Set<UUID> rituals = new HashSet<>();

    public FeatureListener(AdditionalBosses plugin) {
        this.plugin = plugin;
        this.compassRecipe = new NamespacedKey(plugin, "hunters_compass");
    }

    private ItemService items() {
        return plugin.items();
    }

    // =====================================================================
    // Hunter's Compass recipe
    // =====================================================================

    /** (Re)registers the compass recipe so it always reflects the current config. */
    public void registerRecipes() {
        Bukkit.removeRecipe(compassRecipe, false);
        FeatureSettings f = plugin.settings().features;
        if (!f.compassEnabled || !f.compassRecipe) {
            return;
        }
        ShapelessRecipe recipe = new ShapelessRecipe(compassRecipe, items().createCompass(1));
        recipe.addIngredient(Material.COMPASS);
        recipe.addIngredient(Material.ENDER_EYE);
        recipe.addIngredient(Material.BONE);
        Bukkit.addRecipe(recipe, true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.discoverRecipe(compassRecipe);
        }
    }

    public void unregisterRecipes() {
        Bukkit.removeRecipe(compassRecipe, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (Bukkit.getRecipe(compassRecipe) != null) {
            event.getPlayer().discoverRecipe(compassRecipe);
        }
    }

    // =====================================================================
    // Using items
    // =====================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        ItemService.Kind kind = items().kind(item);
        if (kind == null) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        switch (kind) {
            case STATUE -> {
                // Never let the statue behave like a real spawn egg (spawning a mob or changing a spawner).
                // Placing it happens in onStatuePlace, after protection plugins had their say.
                event.setUseItemInHand(Event.Result.DENY);
            }
            case TOTEM -> {
                EquipmentSlot hand = event.getHand();
                Block clicked = event.getClickedBlock();
                if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && usable(clicked)
                    && !player.isSneaking()) {
                    return; // let chests, doors and buttons work normally
                }
                if (rightClick && hand != null) {
                    event.setCancelled(true);
                    if (hand == EquipmentSlot.HAND
                        || items().kind(player.getInventory().getItemInMainHand()) != ItemService.Kind.TOTEM) {
                        useTotem(player, item, hand);
                    }
                }
            }
            case COMPASS -> {
                // Binding it to a lodestone would stop it tracking bosses.
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                    && event.getClickedBlock().getType() == Material.LODESTONE) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Good enough to keep chests, doors, buttons and the like working while a totem is in hand. */
    @SuppressWarnings("deprecation")
    private static boolean usable(Block block) {
        return block.getType().isInteractable();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStatuePlace(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || clicked == null
            || items().kind(event.getItem()) != ItemService.Kind.STATUE) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return; // a protection plugin said no
        }
        Player player = event.getPlayer();
        if (usable(clicked) && !player.isSneaking()) {
            return; // open the chest / door instead
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        placeStatue(player, event.getItem(), clicked.getRelative(event.getBlockFace()));
    }

    private void placeStatue(Player player, ItemStack item, Block at) {
        if (!at.isPassable() || !at.getRelative(0, 1, 0).isPassable()) {
            Fx.play(player.getLocation(), "block.note_block.bass", 0.8f, 0.6f);
            return;
        }
        Location spot = at.getLocation().add(0.5, 0, 0.5);
        if (plugin.trophies().placeStatue(player, item, spot)) {
            consumeOne(player, EquipmentSlot.HAND);
        }
    }

    private void useTotem(Player player, ItemStack item, EquipmentSlot hand) {
        FeatureSettings f = plugin.settings().features;
        if (!f.totemEnabled || !plugin.settings().worldAllowed(player.getWorld())) {
            player.sendMessage(plugin.settings().messages.prefixed("totem-blocked"));
            return;
        }
        if (!rituals.add(player.getUniqueId())) {
            return; // a ritual is already running
        }
        String raw = item.getPersistentDataContainer().getOrDefault(Keys.TOTEM_RANK, PersistentDataType.STRING, "");
        BossRank fixed = BossMobs.parseOrNull(raw);
        BossRank rank = fixed != null ? fixed : BossMobs.rollRank(f.totemRankWeights, BossRank.GREEN);
        player.sendMessage(plugin.settings().messages.prefixed("totem-ritual"));
        Fx.play(player.getLocation(), "item.totem.use", 0.7f, 0.6f);
        Fx.play(player.getLocation(), "ambient.cave", 1.0f, 0.8f);
        UUID id = player.getUniqueId();
        Location anchor = player.getLocation();
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || p.isDead() || !p.getWorld().equals(anchor.getWorld())) {
                    rituals.remove(id);
                    cancel();
                    return;
                }
                step++;
                Location c = p.getLocation().add(0, 1, 0);
                double radius = 2.5 - step * 0.35;
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + step * 0.4;
                    Location ring = c.clone().add(Math.cos(a) * radius, -0.8 + step * 0.25, Math.sin(a) * radius);
                    p.getWorld().spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(rank.bukkitColor(), 1.4f));
                }
                Fx.particle(c, Particle.SOUL, 4, 0.6, 0.02);
                Fx.play(c, "block.note_block.basedrum", 0.8f, 0.5f + step * 0.1f);
                if (step < 6) {
                    return;
                }
                cancel();
                rituals.remove(id);
                // The totem is only used up once the ritual completes.
                if (p.getGameMode() != GameMode.CREATIVE && !consumeTotem(p, raw)) {
                    p.sendMessage(plugin.settings().messages.prefixed("totem-blocked"));
                    return;
                }
                summonFromTotem(p, rank);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void summonFromTotem(Player player, BossRank rank) {
        Location spot = SafeSpots.around(player.getLocation(), 5, 9, 16);
        if (spot == null) {
            spot = SafeSpots.around(player.getLocation(), 2, 5, 16);
        }
        if (spot == null) {
            player.sendMessage(plugin.settings().messages.prefixed("totem-blocked"));
            for (ItemStack left : player.getInventory().addItem(items().createTotem(rank)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            return;
        }
        EntityType type = BossMobs.pick(plugin, player.getWorld());
        player.getWorld().strikeLightningEffect(spot);
        Fx.dust(spot.clone().add(0, 1, 0), rank.bukkitColor(), 2.0f, 40, 0.8);
        Fx.particle(spot.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 30, 0.5, 0.05);
        Boss boss = plugin.bosses().summon(type, spot, rank, null, true, BossMobs.categoryId(plugin, type));
        if (boss != null) {
            plugin.bosses().engage(boss, player);
            if (boss.entity() instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
    }

    // =====================================================================
    // Statues
    // =====================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());
        // A statue item is a spawn egg underneath: using it on a mob would make a baby.
        if (items().kind(hand) == ItemService.Kind.STATUE || items().kind(hand) == ItemService.Kind.TOTEM) {
            event.setCancelled(true);
        }
        Entity clicked = event.getRightClicked();
        if (!Trophies.isStatue(clicked)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()) {
            return;
        }
        ItemStack item = plugin.trophies().pickUp(clicked);
        if (item != null) {
            for (ItemStack left : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            Fx.play(player.getLocation(), "entity.item.pickup", 0.8f, 0.8f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStatueDamage(EntityDamageEvent event) {
        if (!Trophies.isStatue(event.getEntity())) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStatueBurn(EntityCombustEvent event) {
        if (Trophies.isStatue(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStatueTransform(EntityTransformEvent event) {
        if (Trophies.isStatue(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStatueTarget(EntityTargetEvent event) {
        if (Trophies.isStatue(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Dispensers would hatch a statue's spawn egg into a normal mob. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (items().kind(event.getItem()) == ItemService.Kind.STATUE) {
            event.setCancelled(true);
        }
    }

    /** Removes one totem of the given kind from anywhere in the inventory. False if the player no longer has one. */
    private boolean consumeTotem(Player player, String rankRaw) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (items().kind(stack) != ItemService.Kind.TOTEM || stack == null || !rankRaw.equals(
                stack.getPersistentDataContainer().getOrDefault(Keys.TOTEM_RANK, PersistentDataType.STRING, ""))) {
                continue;
            }
            inv.setItem(slot, stack.getAmount() > 1 ? stack.asQuantity(stack.getAmount() - 1) : null);
            return true;
        }
        return false;
    }

    private static void consumeOne(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack inHand = player.getInventory().getItem(hand);
        player.getInventory().setItem(hand, inHand.getAmount() > 1 ? inHand.asQuantity(inHand.getAmount() - 1) : null);
    }
}
