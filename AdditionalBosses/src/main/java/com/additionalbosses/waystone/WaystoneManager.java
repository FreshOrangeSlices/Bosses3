package com.additionalbosses.waystone;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.config.Messages;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.SafeSpots;
import com.additionalbosses.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Waystones: one public network. Every placed waystone (from any player) can reach every other one, for free,
 * after a short warm-up. Names come from an anvil rename (or a name tag used on the placed stone).
 */
public final class WaystoneManager {

    private static final int PER_PAGE = 45;

    private final AdditionalBosses plugin;
    private final File file;
    private final Map<String, Waystone> byId = new LinkedHashMap<>();
    private final Map<String, String> byLocation = new HashMap<>();
    private final Map<UUID, Integer> cooldownUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> travelling = new HashMap<>();
    private @Nullable BukkitTask upkeep;

    public WaystoneManager(AdditionalBosses plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waystones.yml");
    }

    private FeatureSettings f() {
        return plugin.settings().features;
    }

    private Messages m() {
        return plugin.settings().messages;
    }

    // =====================================================================
    // Storage
    // =====================================================================

    public void load() {
        byId.clear();
        byLocation.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("waystones");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                Waystone w = s == null ? null : Waystone.load(id, s);
                if (w != null) {
                    byId.put(id, w);
                    byLocation.put(w.key(), id);
                }
            }
        }
        if (upkeep == null) {
            upkeep = Bukkit.getScheduler().runTaskTimer(plugin, this::upkeep, 100L, 40L);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Waystone w : byId.values()) {
            w.save(yaml.createSection("waystones." + w.id));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save waystones.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (BukkitTask t : travelling.values()) {
            t.cancel();
        }
        travelling.clear();
        if (upkeep != null) {
            upkeep.cancel();
            upkeep = null;
        }
        save();
    }

    public int count() {
        return byId.size();
    }

    public List<Waystone> all() {
        return List.copyOf(byId.values());
    }

    public boolean exists(String id) {
        return byId.containsKey(id);
    }

    public @Nullable Waystone at(Block block) {
        String id = byLocation.get(Waystone.key(block));
        return id == null ? null : byId.get(id);
    }

    // =====================================================================
    // Items
    // =====================================================================

    /** A waystone item. {@code name} is kept when a placed waystone is picked back up. */
    public ItemStack createItem(@Nullable String name) {
        ItemStack item = ItemStack.of(Material.LODESTONE);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Waystone", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        if (name != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, Text.noItalic(Component.text(name, NamedTextColor.AQUA)));
        }
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Place it to join the waystone network.", NamedTextColor.GRAY));
        lore.add(Text.line("Every waystone, from every player, links", NamedTextColor.GRAY));
        lore.add(Text.line("to every other one. Travel is free.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Text.line("Rename it in an anvil to name it.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, ItemService.Kind.WAYSTONE.name());
            pdc.set(Keys.WAYSTONE, PersistentDataType.STRING, "");
        });
        return item;
    }

    // =====================================================================
    // Placing / removing
    // =====================================================================

    public Waystone place(Player player, Block block, ItemStack item) {
        Component custom = item.getData(DataComponentTypes.CUSTOM_NAME);
        String name = custom == null ? null : Text.plain(custom).trim();
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("Waystone")) {
            name = player.getName() + "'s Waystone";
            int n = 2;
            String base = name;
            while (nameTaken(name)) {
                name = base + " " + n++;
            }
        }
        name = name.length() > 32 ? name.substring(0, 32) : name;
        String id = UUID.randomUUID().toString().substring(0, 8);
        Waystone w = new Waystone(id, name, player.getUniqueId(), player.getName(), block.getWorld().getName(),
            block.getX(), block.getY(), block.getZ());
        byId.put(id, w);
        byLocation.put(w.key(), id);
        save();
        spawnLabel(w, block);
        Location c = block.getLocation().add(0.5, 1.2, 0.5);
        Fx.particle(c, Particle.END_ROD, 40, 0.4, 0.08);
        Fx.play(c, "block.respawn_anchor.charge", 1.0f, 1.2f);
        player.sendMessage(m().prefixed("waystone-placed", Placeholder.unparsed("name", name)));
        return w;
    }

    private boolean nameTaken(String name) {
        for (Waystone w : byId.values()) {
            if (w.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Removes the record and its floating name (the block itself is handled by the caller). */
    public void remove(Waystone w) {
        byId.remove(w.id);
        byLocation.remove(w.key());
        removeLabel(w);
        save();
    }

    public void rename(Waystone w, String name) {
        w.name = name.length() > 32 ? name.substring(0, 32) : name;
        save();
        removeLabel(w);
        World world = w.bukkitWorld();
        if (world != null) {
            spawnLabel(w, world.getBlockAt(w.x, w.y, w.z));
        }
    }

    public void setIcon(Waystone w, Material icon) {
        w.icon = icon;
        save();
    }

    // =====================================================================
    // Floating names + upkeep
    // =====================================================================

    private void spawnLabel(Waystone w, Block block) {
        Location at = block.getLocation().add(0.5, 1.45, 0.5);
        block.getWorld().spawn(at, TextDisplay.class, td -> {
            td.text(Component.text(w.name, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .appendNewline().append(Component.text(w.ownerName, NamedTextColor.GRAY)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            td.setPersistent(true);
            td.getPersistentDataContainer().set(Keys.WAYSTONE_LABEL, PersistentDataType.STRING, w.id);
        });
    }

    private void removeLabel(Waystone w) {
        World world = w.bukkitWorld();
        if (world == null || !world.isChunkLoaded(w.x >> 4, w.z >> 4)) {
            return;
        }
        Location at = new Location(world, w.x + 0.5, w.y + 1.45, w.z + 0.5);
        for (Entity e : world.getNearbyEntities(at, 1.5, 1.5, 1.5)) {
            if (w.id.equals(e.getPersistentDataContainer().get(Keys.WAYSTONE_LABEL, PersistentDataType.STRING))) {
                e.remove();
            }
        }
    }

    /**
     * Every 2 seconds: drop records whose block is gone (e.g. replaced by commands), restore missing floating
     * names, and let waystones near players shimmer.
     */
    private void upkeep() {
        boolean changed = false;
        for (Waystone w : List.copyOf(byId.values())) {
            World world = w.bukkitWorld();
            if (world == null || !world.isChunkLoaded(w.x >> 4, w.z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(w.x, w.y, w.z);
            if (block.getType() != Material.LODESTONE) {
                if (!world.getChunkAt(w.x >> 4, w.z >> 4).isEntitiesLoaded()) {
                    continue; // wait until its floating name is loaded too, so it can be removed with it
                }
                byId.remove(w.id);
                byLocation.remove(w.key());
                removeLabel(w);
                changed = true;
                plugin.getLogger().info("Waystone '" + w.name + "' was removed (its block is gone).");
                continue;
            }
            Location c = block.getLocation().add(0.5, 1.0, 0.5);
            boolean watched = false;
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(c) < 32 * 32) {
                    watched = true;
                    break;
                }
            }
            if (!watched || !world.getChunkAt(w.x >> 4, w.z >> 4).isEntitiesLoaded()) {
                continue;
            }
            boolean labelled = false;
            for (Entity e : world.getNearbyEntities(c.clone().add(0, 0.45, 0), 1.5, 1.5, 1.5)) {
                if (w.id.equals(e.getPersistentDataContainer().get(Keys.WAYSTONE_LABEL, PersistentDataType.STRING))) {
                    labelled = true;
                    break;
                }
            }
            if (!labelled) {
                spawnLabel(w, block);
            }
            world.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.1, 0), 2, 0.25, 0.1, 0.25, 0.01);
            world.spawnParticle(Particle.PORTAL, c, 6, 0.3, 0.2, 0.3, 0.3);
        }
        if (changed) {
            save();
        }
    }

    // =====================================================================
    // Menu
    // =====================================================================

    /** The chest menu listing every waystone on the server. */
    public static final class Menu implements InventoryHolder {
        final @Nullable String from;
        final int page;
        final List<String> slots = new ArrayList<>();
        private @Nullable Inventory inventory;

        Menu(@Nullable String from, int page) {
            this.from = from;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void openMenu(Player player, @Nullable Waystone from, int page) {
        List<Waystone> list = new ArrayList<>(byId.values());
        UUID me = player.getUniqueId();
        list.sort(Comparator.<Waystone>comparingInt(w -> from != null && w.id.equals(from.id) ? 0 : w.owner.equals(me) ? 1 : 2)
            .thenComparing(w -> w.name.toLowerCase(java.util.Locale.ROOT)));
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu(from == null ? null : from.id, p);
        Inventory inv = Bukkit.createInventory(menu, 54, Component.text("Waystones (" + list.size() + ")"));
        menu.inventory = inv;
        for (int i = 0; i < PER_PAGE && p * PER_PAGE + i < list.size(); i++) {
            Waystone w = list.get(p * PER_PAGE + i);
            inv.setItem(i, entry(player, w, from != null && w.id.equals(from.id)));
            menu.slots.add(w.id);
        }
        if (p > 0) {
            inv.setItem(45, button(Material.ARROW, "Previous page"));
        }
        inv.setItem(49, button(Material.COMPASS, "Page " + (p + 1) + " / " + pages));
        if (p < pages - 1) {
            inv.setItem(53, button(Material.ARROW, "Next page"));
        }
        player.openInventory(inv);
        Fx.playTo(player, Fx.sound("block.amethyst_block.chime", 1.0f, 1.2f));
    }

    private ItemStack entry(Player viewer, Waystone w, boolean here) {
        ItemStack item = ItemStack.of(w.icon);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(w.name, NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Owner: " + w.ownerName, NamedTextColor.GRAY));
        lore.add(Text.line(pretty(w.world) + "  " + w.x + " " + w.y + " " + w.z, NamedTextColor.DARK_GRAY));
        if (viewer.getWorld().getName().equals(w.world)) {
            lore.add(Text.line(Math.round(viewer.getLocation().distance(new Location(viewer.getWorld(), w.x, w.y, w.z)))
                + " blocks away", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(here ? Text.line("You are here", NamedTextColor.YELLOW) : Text.line("Click to travel", NamedTextColor.GREEN));
        item.lore(lore);
        if (here) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
    }

    private static String pretty(String world) {
        return switch (world) {
            case "world" -> "Overworld";
            case "world_nether" -> "Nether";
            case "world_the_end" -> "The End";
            default -> world;
        };
    }

    private static ItemStack button(Material material, String label) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(label, NamedTextColor.YELLOW));
        return item;
    }

    /** Handles a click inside the menu (the event itself is cancelled by the listener). */
    public void click(Player player, Menu menu, int slot) {
        if (slot == 45) {
            openMenu(player, menu.from == null ? null : byId.get(menu.from), menu.page - 1);
            return;
        }
        if (slot == 53) {
            openMenu(player, menu.from == null ? null : byId.get(menu.from), menu.page + 1);
            return;
        }
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        Waystone target = byId.get(menu.slots.get(slot));
        if (target == null || target.id.equals(menu.from)) {
            return;
        }
        player.closeInventory();
        travel(player, target);
    }

    // =====================================================================
    // Travel
    // =====================================================================

    private boolean inBossFight(Player player) {
        for (Boss boss : plugin.bosses().active()) {
            if (boss.engaged().containsKey(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public void travel(Player player, Waystone target) {
        FeatureSettings f = f();
        UUID id = player.getUniqueId();
        int now = Bukkit.getCurrentTick();
        int ready = cooldownUntil.getOrDefault(id, 0);
        if (now < ready) {
            player.sendMessage(m().prefixed("waystone-cooldown",
                Placeholder.unparsed("count", String.valueOf((ready - now + 19) / 20))));
            return;
        }
        if (f.waystoneBlockInCombat && inBossFight(player)) {
            player.sendMessage(m().prefixed("waystone-combat"));
            return;
        }
        World world = target.bukkitWorld();
        if (world == null || (!f.waystoneCrossDimension && !world.equals(player.getWorld()))) {
            player.sendMessage(m().prefixed("waystone-blocked"));
            return;
        }
        cancelTravel(id, false);
        player.sendMessage(m().prefixed("waystone-warmup", Placeholder.unparsed("name", target.name)));
        Location start = player.getLocation();
        int warmup = (int) Math.round(f.waystoneWarmupSeconds * 20);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || p.isDead() || !p.getWorld().equals(start.getWorld())
                    || p.getLocation().distanceSquared(start) > 0.6) {
                    if (p != null) {
                        p.sendMessage(m().prefixed("waystone-cancelled"));
                    }
                    travelling.remove(id);
                    cancel();
                    return;
                }
                double angle = ticks * 0.6;
                Location c = p.getLocation();
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * Math.PI * 2 / 3;
                    p.getWorld().spawnParticle(Particle.PORTAL, c.clone().add(Math.cos(a) * 0.8,
                        0.2 + ticks / (double) Math.max(1, warmup) * 1.8, Math.sin(a) * 0.8), 3, 0.05, 0.05, 0.05, 0.1);
                }
                if (ticks % 10 == 0) {
                    Fx.playTo(p, Fx.sound("block.portal.ambient", 0.25f, 1.6f));
                }
                if ((ticks += 2) < warmup) {
                    return;
                }
                travelling.remove(id);
                cancel();
                if (f.waystoneBlockInCombat && inBossFight(p)) {
                    p.sendMessage(m().prefixed("waystone-combat")); // a boss started hunting them mid warm-up
                    return;
                }
                arrive(p, target);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        travelling.put(id, task);
    }

    /** Called when a travelling player takes damage, or moves too far. */
    public void cancelTravel(UUID player, boolean tell) {
        BukkitTask task = travelling.remove(player);
        if (task != null) {
            task.cancel();
            Player p = Bukkit.getPlayer(player);
            if (tell && p != null) {
                p.sendMessage(m().prefixed("waystone-cancelled"));
            }
        }
    }

    public boolean isTravelling(UUID player) {
        return travelling.containsKey(player);
    }

    private void arrive(Player player, Waystone target) {
        World world = target.bukkitWorld();
        if (world == null) {
            player.sendMessage(m().prefixed("waystone-blocked"));
            return;
        }
        Location from = player.getLocation();
        world.getChunkAtAsync(target.x >> 4, target.z >> 4).thenAccept(chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Block block = world.getBlockAt(target.x, target.y, target.z);
            if (block.getType() != Material.LODESTONE) {
                remove(target);
                player.sendMessage(m().prefixed("waystone-blocked"));
                return;
            }
            Location dest = target.arrival();
            Block feet = block.getRelative(0, 1, 0);
            if (dest == null || !feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
                dest = SafeSpots.around(block.getLocation().add(0.5, 0, 0.5), 1, 3, 12);
            }
            if (dest == null) {
                player.sendMessage(m().prefixed("waystone-blocked"));
                return;
            }
            dest.setYaw(from.getYaw());
            dest.setPitch(from.getPitch());
            Location finalDest = dest;
            Fx.particle(from.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 40, 0.4, 0.1);
            Fx.play(from, "entity.enderman.teleport", 0.8f, 1.2f);
            player.teleportAsync(finalDest).thenAccept(ok -> {
                if (!ok) {
                    player.sendMessage(m().prefixed("waystone-blocked"));
                    return;
                }
                cooldownUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + (int) Math.round(f().waystoneCooldownSeconds * 20));
                Fx.particle(finalDest.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 40, 0.4, 0.1);
                Fx.play(finalDest, "block.respawn_anchor.set_spawn", 0.8f, 1.4f);
                player.sendMessage(m().prefixed("waystone-arrived", Placeholder.unparsed("name", target.name)));
            });
        });
    }

    public void forget(UUID player) {
        cancelTravel(player, false);
        cooldownUntil.remove(player);
    }
}
