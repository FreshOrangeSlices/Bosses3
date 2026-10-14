package com.bloompets;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Pig's bonus: chop the bottom log of a tree with an axe and the whole tree comes down. Huge mushrooms and
 * nether "trees" (crimson and warped fungi) come down whole, caps and wart blocks included.
 *
 * <p>Only natural trees: a log tree needs leaves that grew there (player-placed leaves don't count), so log
 * cabins are safe. Sneak to chop a single block. Every extra block costs the axe durability, and each one goes
 * through the normal break event, so protection plugins (and mcMMO) see it.</p>
 */
public final class Timber implements Listener {

    private enum Kind { LOG, MUSHROOM, FUNGUS }

    /** How far a tree may spread sideways from its trunk. */
    private static final int REACH = 6;

    /** What a tree, huge mushroom or fungus grows on. */
    private static final Set<Material> SOIL = java.util.EnumSet.of(Material.DIRT, Material.GRASS_BLOCK,
        Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.MUD,
        Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS,
        Material.FARMLAND, Material.SAND, Material.RED_SAND, Material.CLAY, Material.SNOW_BLOCK,
        Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.NETHERRACK, Material.SOUL_SOIL);

    /** The trunk stands on soil: follow it down from the cut (a log cabin's walls don't grow out of dirt). */
    private static boolean rooted(Block start, Material trunk, Kind kind) {
        Block b = start;
        for (int i = 0; i < 40; i++) {
            Block below = b.getRelative(0, -1, 0);
            Material type = below.getType();
            if (type == trunk || (kind == Kind.MUSHROOM && type == Material.MUSHROOM_STEM)) {
                b = below;
                continue;
            }
            return SOIL.contains(type);
        }
        return false;
    }

    private final BloomPets plugin;
    private boolean felling;

    public Timber(BloomPets plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChop(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (felling || p.isSneaking() || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        if (a == null || a.pet.species != Species.PIG || plugin.pets().fainting(a) || plugin.happiness().sad(a.pet)) {
            return;
        }
        Block start = event.getBlock();
        Kind kind = kind(start.getType());
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (kind == null || !Tag.ITEMS_AXES.isTagged(axe.getType())) {
            return;
        }
        int limit = (int) Math.round(64 * a.pet.power());
        List<Block> tree = collect(start, kind, limit);
        if (tree.isEmpty()) {
            return;
        }
        felling = true;
        try {
            Material trunk = start.getType();
            for (Block b : tree) {
                ItemStack tool = p.getInventory().getItemInMainHand();
                if (!Tag.ITEMS_AXES.isTagged(tool.getType())) {
                    break; // the axe broke
                }
                if (!part(b.getType(), trunk, kind)) {
                    continue; // already gone (another plugin felled it)
                }
                BlockBreakEvent each = new BlockBreakEvent(b, p);
                if (!each.callEvent()) {
                    continue;
                }
                if (each.isDropItems()) {
                    b.breakNaturally(tool, true, true);
                } else {
                    b.setType(Material.AIR);
                }
                if (b.getType().isAir() && p.getGameMode() != GameMode.CREATIVE) {
                    p.getInventory().setItemInMainHand(tool.damage(1, p)); // like chopping it yourself
                }
            }
        } finally {
            felling = false;
        }
    }

    private static Kind kind(Material m) {
        if (m == Material.CRIMSON_STEM || m == Material.WARPED_STEM) {
            return Kind.FUNGUS;
        }
        if (m == Material.MUSHROOM_STEM || m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK) {
            return Kind.MUSHROOM;
        }
        if (Tag.LOGS.isTagged(m) && !m.name().startsWith("STRIPPED_") && !m.name().endsWith("_WOOD")
            && !m.name().endsWith("_HYPHAE")) {
            return Kind.LOG;
        }
        return null;
    }

    /** Whether this block belongs to the same tree as the one that was chopped. */
    private static boolean part(Material m, Material trunk, Kind kind) {
        return switch (kind) {
            case LOG -> m == trunk;
            case MUSHROOM -> m == Material.MUSHROOM_STEM || m == Material.BROWN_MUSHROOM_BLOCK
                || m == Material.RED_MUSHROOM_BLOCK;
            case FUNGUS -> m == trunk || Tag.WART_BLOCKS.isTagged(m) || m == Material.SHROOMLIGHT;
        };
    }

    /**
     * The rest of the tree above and around the chopped block (not the block itself), or nothing if it doesn't
     * look like a tree that grew there.
     */
    private static List<Block> collect(Block start, Kind kind, int limit) {
        Material trunk = start.getType();
        if (!rooted(start, trunk, kind)) {
            return List.of();
        }
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        List<Block> found = new ArrayList<>();
        seen.add(start);
        queue.add(start);
        int leaves = 0;
        boolean natural = false;
        while (!queue.isEmpty() && found.size() < limit) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block n = b.getRelative(dx, dy, dz);
                        if (n.getY() < start.getY() || Math.abs(n.getX() - start.getX()) > REACH
                            || Math.abs(n.getZ() - start.getZ()) > REACH) {
                            continue; // never below the cut, never far to the side (that's a build, not a tree)
                        }
                        Material type = n.getType();
                        if (kind == Kind.LOG && n.getBlockData() instanceof Leaves leaf && !leaf.isPersistent()
                            && ++leaves >= 3) {
                            natural = true; // leaves that grew there (placed leaves are "persistent")
                        }
                        if (kind != Kind.LOG && (type != trunk && part(type, trunk, kind))) {
                            natural = true; // caps or wart blocks: a real huge mushroom / fungus
                        }
                        if (!part(type, trunk, kind) || !seen.add(n)) {
                            continue;
                        }
                        found.add(n);
                        queue.add(n);
                        if (found.size() >= limit) {
                            break;
                        }
                    }
                }
            }
        }
        if (!natural) {
            return List.of();
        }
        found.sort((x, y) -> Integer.compare(x.getY(), y.getY()));
        return found;
    }
}
